import { http, HttpResponse, delay } from 'msw';
import type { AppDocument, LoanRequestForm, PersonalInfo, SimulationRequest } from '../../api/types';
import {
  API, db, err, nextId, persist, currentUser, nowIso, log, notify, setStatus, view, summary, canSee, screeningOf,
  ocrFields, refreshChecks, missingForSubmit, makeQr, sha256, thumbnail, paramValue, activeVersion, paramsOf, weightsOf,
  fmtMoney, DEV_OTP, OPEN_STATUSES, STATUS_LABEL,
} from '../ctx';
import { SYSTEM_DOC_TYPE, type AppRecord } from '../db';
import { simulate } from '../engine';

const findApp = (id: unknown) => db.applications.find(a => a.id === Number(id));

function own(request: Request, id: unknown): { app: AppRecord; u: NonNullable<ReturnType<typeof currentUser>> } | Response {
  const u = currentUser(request);
  if (!u) return err(401, 'Chưa đăng nhập.');
  const app = findApp(id);
  if (!app || u.role !== 'CUSTOMER' || app.customerId !== u.id) return err(404, 'Không tìm thấy hồ sơ.');
  return { app, u };
}

const editable = (app: AppRecord) => ['DRAFTING', 'NEED_MORE', 'EXPIRED'].includes(app.status);

function newCode() {
  const d = new Date();
  const n = db.applications.filter(a => a.code.startsWith(`HS${String(d.getFullYear()).slice(2)}${String(d.getMonth() + 1).padStart(2, '0')}`)).length + 1;
  return `HS${String(d.getFullYear()).slice(2)}${String(d.getMonth() + 1).padStart(2, '0')}-${String(500 + n).padStart(4, '0')}`;
}

function buildForm(app: AppRecord): LoanRequestForm['content'] {
  const p = app.personal;
  const val = (typeId: number, name: string) => {
    const d = app.documents.find(x => x.documentTypeId === typeId && x.status === 'CONFIRMED');
    const f = d?.fields.find(x => x.name === name);
    return f ? (f.confirmedValue ?? f.ocrValue) : '';
  };
  const old = Object.fromEntries((app.requestForm?.content ?? []).filter(c => c.editable).map(c => [c.key, c.value]));
  const branch = db.branches.find(b => b.id === app.branchId);
  const rows: [string, string, string, boolean][] = [
    ['fullName', 'Họ và tên người vay', p.fullName, false],
    ['nationalId', 'Số CCCD', p.nationalId, false],
    ['dob', 'Ngày sinh', p.dob.split('-').reverse().join('/'), false],
    ['address', 'Địa chỉ thường trú', p.address, false],
    ['contactAddress', 'Địa chỉ liên hệ', old.contactAddress ?? p.address, true],
    ['phone', 'Số điện thoại', p.phone, false],
    ['email', 'Email', old.email ?? p.email, true],
    ['employer', 'Đơn vị công tác', val(4, 'employer'), false],
    ['income', 'Thu nhập bình quân (theo sao kê)', val(5, 'avg_income') || (app.recalc ? fmtMoney(app.recalc.verifiedIncome) : ''), false],
    ['product', 'Sản phẩm vay', app.plan.productName, false],
    ['amount', 'Số tiền đề nghị vay', `${fmtMoney(app.plan.amount)} đồng`, false],
    ['term', 'Thời hạn vay', `${app.plan.termMonths} tháng`, false],
    ['rate', 'Lãi suất tham chiếu', `${app.plan.refRate}%/năm (theo thông báo của ngân hàng tại thời điểm giải ngân)`, false],
    ['purpose', 'Mục đích sử dụng vốn', app.purpose, false],
    ['method', 'Phương thức trả nợ', app.plan.productCode === 'TD' ? 'Gốc trả đều hằng tháng, lãi trên dư nợ giảm dần' : 'Lãi trả hằng tháng, gốc trả cuối kỳ', false],
    ['beneficiary', 'Bên thụ hưởng giải ngân', old.beneficiary ?? (val(6, 'beneficiary') || (app.plan.productCode === 'TC' ? 'Tài khoản thanh toán của người vay' : '')), true],
    ['repaymentSource', 'Nguồn trả nợ', old.repaymentSource ?? 'Lương chuyển khoản hằng tháng', true],
    ['branch', 'Chi nhánh tiếp nhận', branch?.name ?? '', false],
    ['note', 'Ghi chú', old.note ?? '', true],
  ];
  return rows.map(([key, label, value, ed]) => ({ key, label, value: value ?? '', editable: ed }));
}

async function lock(app: AppRecord) {
  const days = paramValue('DOC_VALIDITY_DAYS') || 30;
  app.contentHash = await sha256(JSON.stringify({ form: app.requestForm?.content, plan: app.plan, docs: app.documents.map(d => [d.id, d.versionNo, d.fields.map(f => f.confirmedValue ?? f.ocrValue)]) }));
  app.lockedAt = nowIso();
  app.qrToken = app.qrToken ?? makeQr();
  app.expiresAt = new Date(Date.now() + days * 864e5).toISOString();
  app.versionNo = activeVersion().versionNo;
}

export const applicationHandlers = [
  http.get(`${API}/me/applications`, ({ request }) => {
    const u = currentUser(request);
    if (!u) return err(401, 'Chưa đăng nhập.');
    return HttpResponse.json(db.applications.filter(a => a.customerId === u.id).map(summary).reverse());
  }),

  // 4.1 Tạo hồ sơ vay: chọn chi nhánh, đồng ý phần (b), lập MB05
  http.post(`${API}/applications`, async ({ request }) => {
    const u = currentUser(request);
    if (!u || u.role !== 'CUSTOMER') return err(401, 'Cần đăng nhập tài khoản khách hàng.');
    const b = (await request.json()) as { planId: number; branchId: number; consentB: { agreed: boolean; docVersion: string } };
    if (!b.consentB?.agreed) return err(400, 'Cần đồng ý lưu trữ giấy tờ và lập hồ sơ.');
    const plan = db.plans.find(p => p.id === b.planId && p.customerId === u.id);
    if (!plan) return err(404, 'Không tìm thấy phương án.');
    if (plan.status !== 'CONFIRMED') return err(409, 'Cần xác nhận phương án trước khi lập hồ sơ.');
    if (db.applications.some(a => a.plan.id === plan.id && a.status !== 'CANCELLED')) return err(409, 'Phương án này đã có hồ sơ.');
    const branch = db.branches.find(x => x.id === b.branchId && x.status === 'ACTIVE');
    if (!branch) return err(400, 'Chi nhánh không hợp lệ.');
    const scr = db.screenings.find(s => s.id === plan.screeningId);
    const personal: PersonalInfo = { fullName: '', nationalId: '', dob: scr?.input.dob ?? '', gender: scr?.input.gender ?? 'MALE', address: '', phone: u.username, email: '' };
    const docs: AppDocument[] = db.productDocs.filter(r => r.productCode === plan.productCode).map(r => {
      const t = db.docTypes.find(d => d.id === r.documentTypeId)!;
      return { id: nextId(), documentTypeId: t.id, typeName: t.name, group: t.group, required: r.required, allowedFormats: t.allowedFormats,
        status: 'MISSING' as const, fileName: null, fileFormat: null, fileSize: null, versionNo: 0, issuedDate: null, qualityStatus: null, qualityMessage: null,
        uploadedAt: null, previewUrl: null, fields: [], verifyResult: null, verifyReason: null };
    }).sort((a, b2) => (a.documentTypeId === SYSTEM_DOC_TYPE ? 1 : 0) - (b2.documentTypeId === SYSTEM_DOC_TYPE ? 1 : 0));
    const app: AppRecord = {
      id: nextId(), code: newCode(), customerId: u.id, status: 'DRAFTING', branchId: branch.id, plan: { ...plan }, purpose: scr?.input.purpose ?? '',
      personal, consentBAt: nowIso(), versionNo: activeVersion().versionNo, contentHash: null, lockedAt: null, qrToken: null, expiresAt: null,
      createdAt: nowIso(), updatedAt: nowIso(), documents: docs, warnings: [], recalc: null, requestForm: null, verifications: [], approval: null,
      history: [{ status: 'DRAFTING', at: nowIso(), actor: u.name, note: 'Tạo hồ sơ' }], predictedScore: plan.score, screeningId: plan.screeningId,
    };
    db.applications.push(app);
    db.consents.push({ id: nextId(), customerId: u.id, scope: 'B', docVersion: b.consentB.docVersion, agreedAt: nowIso(), withdrawnAt: null, applicationCode: app.code });
    log(u, 'Tạo hồ sơ vay', `Chi nhánh ${branch.name}`, app);
    persist();
    return HttpResponse.json(view(app, u), { status: 201 });
  }),

  http.get(`${API}/applications/:id`, ({ params, request }) => {
    const u = currentUser(request);
    if (!u) return err(401, 'Chưa đăng nhập.');
    const app = findApp(params.id);
    if (!app || !canSee(app, u)) return err(404, 'Không tìm thấy hồ sơ.');
    if (u.role !== 'CUSTOMER') { log(u, 'Xem hồ sơ', 'Xem chi tiết hồ sơ', app); persist(); }
    return HttpResponse.json(view(app, u));
  }),

  http.put(`${API}/applications/:id/personal`, async ({ params, request }) => {
    const r = own(request, params.id); if (r instanceof Response) return r;
    const { app, u } = r;
    if (!editable(app)) return err(409, 'Hồ sơ đã khóa, không sửa được.');
    const b = (await request.json()) as PersonalInfo;
    if (b.nationalId && !/^\d{12}$/.test(b.nationalId)) return err(400, 'Số CCCD phải gồm 12 chữ số.');
    app.personal = { ...app.personal, ...b, phone: u.username };
    if (app.requestForm) app.requestForm = null;
    refreshChecks(app); app.updatedAt = nowIso();
    log(u, 'Cập nhật thông tin cá nhân', '', app);
    persist();
    return HttpResponse.json(view(app, u));
  }),

  // Tải giấy tờ: kiểm tra chất lượng, lưu, trích xuất MB06
  http.post(`${API}/applications/:id/documents/:docId`, async ({ params, request }) => {
    await delay(600);
    const r = own(request, params.id); if (r instanceof Response) return r;
    const { app, u } = r;
    if (!editable(app)) return err(409, 'Hồ sơ đã khóa, không tải thêm được.');
    const d = app.documents.find(x => x.id === Number(params.docId));
    if (!d || d.documentTypeId === SYSTEM_DOC_TYPE) return err(404, 'Không tìm thấy giấy tờ.');
    const fd = await request.formData();
    const file = fd.get('file');
    if (!(file instanceof File)) return err(400, 'Thiếu tệp.');
    const ext = (file.name.split('.').pop() || '').toUpperCase().replace('JPEG', 'JPG');
    const kb = Math.max(1, Math.round(file.size / 1024));
    d.fileName = file.name; d.fileFormat = ext; d.fileSize = kb; d.versionNo += 1; d.uploadedAt = nowIso();
    d.issuedDate = (fd.get('issuedDate') as string) || null;
    d.verifyResult = null; d.verifyReason = null;
    d.previewUrl = await thumbnail(file);
    if (!d.allowedFormats.split(',').includes(ext)) {
      d.status = 'REJECTED_QUALITY'; d.qualityStatus = 'WRONG_FORMAT'; d.qualityMessage = `Định dạng ${ext} không được chấp nhận (cho phép ${d.allowedFormats}).`; d.fields = [];
    } else if (kb > 10 * 1024) {
      d.status = 'REJECTED_QUALITY'; d.qualityStatus = 'TOO_LARGE'; d.qualityMessage = 'Tệp lớn hơn 10 MB.'; d.fields = [];
    } else if (ext !== 'PDF' && kb < 25) {
      d.status = 'REJECTED_QUALITY'; d.qualityStatus = 'BLURRY'; d.qualityMessage = 'Ảnh có độ phân giải thấp hoặc bị mờ, vui lòng chụp lại rõ nét, đủ bốn góc.'; d.fields = [];
    } else {
      d.status = 'UPLOADED'; d.qualityStatus = 'OK'; d.qualityMessage = null;
      d.fields = ocrFields(d.documentTypeId, app, screeningOf(app)?.input ?? null, kb);
    }
    if (app.requestForm) app.requestForm = null;
    refreshChecks(app); app.updatedAt = nowIso();
    log(u, 'Tải giấy tờ', `${d.typeName} (phiên bản ${d.versionNo})`, app);
    persist();
    return HttpResponse.json(view(app, u));
  }),

  // Xác nhận thông tin trích xuất (MB06)
  http.put(`${API}/applications/:id/documents/:docId/fields`, async ({ params, request }) => {
    const r = own(request, params.id); if (r instanceof Response) return r;
    const { app, u } = r;
    if (!editable(app)) return err(409, 'Hồ sơ đã khóa.');
    const d = app.documents.find(x => x.id === Number(params.docId));
    if (!d || d.status === 'MISSING' || d.status === 'REJECTED_QUALITY') return err(409, 'Giấy tờ chưa có thông tin trích xuất.');
    const b = (await request.json()) as { fields: { id: number; confirmedValue: string }[] };
    for (const f of d.fields) {
      const x = b.fields.find(y => y.id === f.id);
      f.confirmedValue = x ? x.confirmedValue : f.ocrValue;
    }
    d.status = 'CONFIRMED';
    if (d.documentTypeId === 2) {
      const g = (n: string) => d.fields.find(f => f.name === n)?.confirmedValue ?? '';
      if (!app.personal.fullName && g('full_name')) app.personal.fullName = g('full_name').replace(/\S+/g, w => w[0] + w.slice(1).toLowerCase());
      if (!app.personal.nationalId && g('national_id')) app.personal.nationalId = g('national_id');
      if (!app.personal.address && g('address')) app.personal.address = g('address');
    }
    if (app.requestForm) app.requestForm = null;
    refreshChecks(app); app.updatedAt = nowIso();
    log(u, 'Xác nhận thông tin trích xuất', d.typeName, app);
    persist();
    return HttpResponse.json(view(app, u));
  }),

  // Điều chỉnh phương án ngay trong hồ sơ khi giá trị kiểm chứng làm phương án không đạt
  http.put(`${API}/applications/:id/plan`, async ({ params, request }) => {
    const r = own(request, params.id); if (r instanceof Response) return r;
    const { app, u } = r;
    if (!editable(app)) return err(409, 'Hồ sơ đã khóa.');
    const scr = screeningOf(app);
    if (!scr) return err(409, 'Không có dữ liệu sàng lọc gốc.');
    const b = (await request.json()) as Pick<SimulationRequest, 'amount' | 'termMonths' | 'crossSellIds'>;
    const v = activeVersion();
    const inp = app.recalc ? { ...scr.input, avgIncome: app.recalc.verifiedIncome } : scr.input;
    const sim = simulate(inp, paramsOf(v.id), weightsOf(v.id), { screeningId: scr.id, productCode: app.plan.productCode, ...b }, db.crossSells);
    if (!sim.ok) return err(422, 'Phương án chưa đạt: ' + sim.issues.join(' '));
    app.plan = { ...app.plan, amount: sim.amount, termMonths: sim.termMonths, refRate: sim.rate, monthlyPayment: sim.firstPayment,
      totalInterest: sim.totalInterest, dtiAfter: sim.dtiAfter, score: sim.score,
      crossSells: db.crossSells.filter(c => b.crossSellIds.includes(c.id)).map(c => ({ id: c.id, name: c.name, extraCost: c.estCost,
        benefit: `Giảm ${c.rateDiscount}%/năm lãi suất tham chiếu, +${c.scoreBonus} điểm ưu tiên` })) };
    app.predictedScore = sim.score;
    if (app.requestForm) app.requestForm = null;
    refreshChecks(app); app.updatedAt = nowIso();
    log(u, 'Điều chỉnh phương án trong hồ sơ', `${fmtMoney(sim.amount)} đ, ${sim.termMonths} tháng`, app);
    persist();
    return HttpResponse.json(view(app, u));
  }),

  // 4.2 Lập giấy đề nghị (MB15) tự điền
  http.post(`${API}/applications/:id/request-form`, ({ params, request }) => {
    const r = own(request, params.id); if (r instanceof Response) return r;
    const { app, u } = r;
    if (!editable(app)) return err(409, 'Hồ sơ đã khóa.');
    const miss = missingForSubmit(app);
    if (miss.length) return err(409, miss.join(' '));
    app.requestForm = { status: 'DRAFT', content: buildForm(app), createdAt: nowIso(), otpConfirmedAt: null };
    log(u, 'Lập giấy đề nghị vay vốn', 'Bản nháp giấy đề nghị', app);
    persist();
    return HttpResponse.json(view(app, u));
  }),
  http.put(`${API}/applications/:id/request-form`, async ({ params, request }) => {
    const r = own(request, params.id); if (r instanceof Response) return r;
    const { app, u } = r;
    if (!app.requestForm || app.requestForm.status !== 'DRAFT') return err(409, 'Chưa có bản nháp giấy đề nghị.');
    const b = (await request.json()) as { values: Record<string, string> };
    for (const c of app.requestForm.content) if (c.editable && b.values[c.key] !== undefined) c.value = b.values[c.key];
    persist();
    return HttpResponse.json(view(app, u));
  }),

  // Xác nhận OTP, khóa hồ sơ, sinh MB08, chuyển "Chờ đối chiếu"
  http.post(`${API}/applications/:id/submit`, async ({ params, request }) => {
    await delay(500);
    const r = own(request, params.id); if (r instanceof Response) return r;
    const { app, u } = r;
    const b = (await request.json()) as { otp: string };
    if (b.otp !== DEV_OTP) return err(400, 'Mã OTP không đúng.');
    const resub = app.status === 'NEED_MORE' || app.status === 'EXPIRED';
    if (app.status !== 'DRAFTING' && !resub) return err(409, 'Hồ sơ không ở trạng thái nộp được.');
    const miss = missingForSubmit(app);
    if (resub) {
      const pending = app.documents.filter(d => d.verifyResult === 'MISMATCH' || d.verifyResult === 'MISSING');
      if (pending.length) miss.push(`Cần tải lại: ${pending.map(d => d.typeName).join(', ')}.`);
      if (app.status === 'EXPIRED') {
        const days = paramValue('DOC_VALIDITY_DAYS') || 30;
        const old = app.documents.filter(d => d.documentTypeId !== SYSTEM_DOC_TYPE && d.status === 'CONFIRMED' && d.uploadedAt && Date.now() - Date.parse(d.uploadedAt) > days * 864e5);
        if (old.length) miss.push(`Giấy tờ đã quá hạn hiệu lực, cần tải bản mới: ${old.map(d => d.typeName).join(', ')}.`);
      }
    }
    if (miss.length) return err(409, miss.join(' '));
    if (!app.requestForm) app.requestForm = { status: 'DRAFT', content: buildForm(app), createdAt: nowIso(), otpConfirmedAt: null };
    app.requestForm.status = 'CONFIRMED';
    app.requestForm.otpConfirmedAt = nowIso();
    const sys = app.documents.find(d => d.documentTypeId === SYSTEM_DOC_TYPE);
    if (sys) { sys.status = 'CONFIRMED'; sys.fileName = `${app.code}-giay-de-nghi.pdf`; sys.fileFormat = 'PDF'; sys.fileSize = 120; sys.versionNo += 1; sys.uploadedAt = nowIso(); sys.qualityStatus = 'OK'; }
    await lock(app);
    if (app.status === 'EXPIRED') setStatus(app, 'NEED_MORE', u.name, 'Cập nhật giấy tờ hết hạn');
    setStatus(app, 'WAITING_VERIFY', u.name, resub ? 'Bổ sung giấy tờ, nộp lại' : 'Xác nhận giấy đề nghị bằng OTP, nộp hồ sơ');
    notify({ branchId: app.branchId }, resub ? 'Hồ sơ đã bổ sung' : 'Có hồ sơ mới chờ đối chiếu', `${app.code} – ${app.personal.fullName}`, `/noi-bo/ho-so/${app.id}`);
    notify({ customerId: app.customerId }, 'Đã nộp hồ sơ', `Hồ sơ ${app.code} đang chờ đối chiếu tại ${db.branches.find(x => x.id === app.branchId)?.name}. Mang bản gốc giấy tờ và Phiếu tóm tắt khi đến quầy.`, `/ho-so/${app.id}`);
    log(u, resub ? 'Nộp lại hồ sơ' : 'Nộp hồ sơ', `Khóa nội dung, mã băm ${app.contentHash?.slice(0, 12)}…`, app);
    persist();
    return HttpResponse.json(view(app, u));
  }),

  // 6.2 Hủy hồ sơ
  http.post(`${API}/applications/:id/cancel`, async ({ params, request }) => {
    const r = own(request, params.id); if (r instanceof Response) return r;
    const { app, u } = r;
    if (!OPEN_STATUSES.concat(['EXPIRED']).includes(app.status))
      return err(409, `Hồ sơ đang ở trạng thái “${STATUS_LABEL[app.status]}”, không hủy trực tuyến được. Vui lòng liên hệ chi nhánh.`);
    const b = (await request.json().catch(() => ({}))) as { reason?: string };
    setStatus(app, 'CANCELLED', u.name, b.reason || 'Khách hàng hủy hồ sơ');
    notify({ branchId: app.branchId }, 'Hồ sơ đã bị hủy', `${app.code} – khách hàng hủy hồ sơ`, `/noi-bo/ho-so/${app.id}`);
    log(u, 'Hủy hồ sơ', b.reason || '', app);
    persist();
    return HttpResponse.json(view(app, u));
  }),
];
