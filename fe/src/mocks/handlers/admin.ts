import { http, HttpResponse, delay } from 'msw';
import type {
  Branch, CrossSellProduct, DocumentType, LoanProduct, PolicyParam, ProductCode, ProductDocRule, ScoringWeight, ScreeningRequest,
  StaffAccount, TestRunRow,
} from '../../api/types';
import { API, db, err, nextId, persist, currentUser, nowIso, log, notify, setStatus, activeVersion, paramsOf, weightsOf, paramValue, OPEN_STATUSES } from '../ctx';
import { CRITERIA_LABELS, PARAM_LABELS, SYSTEM_DOC_TYPE, type Account } from '../db';
import { exclusion, maxSafeAmount, scoreOf } from '../engine';

const admin = (request: Request) => {
  const u = currentUser(request);
  return u && u.role === 'ADMIN' ? u : null;
};
const forbid = () => err(403, 'Không có quyền.');

/** Bộ hồ sơ mẫu dùng khi chạy thử phiên bản tham số (7.4) */
const SAMPLE_PROFILES: { name: string; inp: ScreeningRequest }[] = (() => {
  const base: ScreeningRequest = {
    consentA: { agreed: true, docVersion: 'DK-1.0' }, nationality: 'Việt Nam', dob: '1992-05-10', gender: 'MALE',
    maritalStatus: 'MARRIED', dependents: 1, avgIncome: 20_000_000, salaryBank: 'Vietcombank', isAbbankStaff: false,
    creditHistory: 'ON_TIME', otherDebts: [], purpose: 'Mua sắm đồ dùng, thiết bị', desiredAmount: 100_000_000, desiredTerm: 36,
  };
  return [
    { name: 'Nhân viên văn phòng, thu nhập 20 triệu', inp: base },
    { name: 'Có nghĩa vụ nợ 10 triệu/tháng (ví dụ biên bản)', inp: { ...base, avgIncome: 15_000_000, otherDebts: [{ lender: 'TCTD khác', monthlyPayment: 10_000_000 }], desiredAmount: 50_000_000 } },
    { name: 'CBNV ABBANK, thu nhập 30 triệu', inp: { ...base, avgIncome: 30_000_000, salaryBank: 'ABBANK', isAbbankStaff: true, desiredAmount: 300_000_000, desiredTerm: 60 } },
    { name: 'Sinh viên mới đi làm 20 tuổi', inp: { ...base, dob: '2006-03-01', maritalStatus: 'SINGLE', dependents: 0, avgIncome: 9_000_000, purpose: 'Chi tiêu cá nhân linh hoạt', desiredAmount: 20_000_000, desiredTerm: 12 } },
    { name: 'Gần tuổi nghỉ hưu (nữ, 57 tuổi)', inp: { ...base, dob: '1969-08-20', gender: 'FEMALE', avgIncome: 25_000_000, desiredAmount: 150_000_000, desiredTerm: 48 } },
    { name: 'Thu nhập cao, vay lớn', inp: { ...base, avgIncome: 60_000_000, dependents: 2, desiredAmount: 500_000_000, desiredTerm: 60, otherDebts: [{ lender: 'Thẻ tín dụng', monthlyPayment: 5_000_000 }] } },
  ];
})();

const staffOut = (a: Account): StaffAccount => ({
  id: a.id, fullName: a.name, email: a.username, role: a.role as 'BRANCH_STAFF' | 'ADMIN', branchId: a.branchId,
  branchName: a.branchId ? db.branches.find(b => b.id === a.branchId)?.name ?? null : null, status: a.status, createdAt: a.createdAt,
});

export const adminHandlers = [
  // ---------------- 7.1, 7.2, 7.4, 7.5 phiên bản tham số ----------------
  http.get(`${API}/admin/param-versions`, ({ request }) => admin(request) ? HttpResponse.json([...db.versions].reverse()) : forbid()),
  http.get(`${API}/admin/param-versions/:id`, ({ params, request }) => {
    if (!admin(request)) return forbid();
    const v = db.versions.find(x => x.id === Number(params.id));
    if (!v) return err(404, 'Không tìm thấy phiên bản.');
    return HttpResponse.json({ ...v, params: db.params[v.id], weights: db.weights[v.id], testResult: db.testResults[v.id] ?? null });
  }),
  http.post(`${API}/admin/param-versions/:id/clone`, async ({ params, request }) => {
    const u = admin(request); if (!u) return forbid();
    if (db.versions.some(v => v.status === 'DRAFT')) return err(409, 'Đang có một phiên bản nháp, hãy hoàn tất hoặc xóa trước.');
    const src = db.versions.find(x => x.id === Number(params.id));
    if (!src) return err(404, 'Không tìm thấy phiên bản.');
    const b = (await request.json()) as { versionNo: string; legalBasis: string; changes?: { key: string; productCode: ProductCode | null; value: string }[] };
    if (db.versions.some(v => v.versionNo === b.versionNo)) return err(409, 'Số phiên bản đã tồn tại.');
    const id = nextId();
    db.versions.push({ id, versionNo: b.versionNo, legalBasis: b.legalBasis, status: 'DRAFT', createdBy: u.name, createdAt: nowIso(), activatedAt: null });
    db.params[id] = db.params[src.id].map(p => {
      const ch = b.changes?.find(c => c.key === p.key && c.productCode === p.productCode);
      return { ...p, id: nextId(), value: ch ? ch.value : p.value };
    });
    db.weights[id] = db.weights[src.id].map(w => ({ ...w, id: nextId() }));
    log(u, 'Tạo bản nháp tham số', b.versionNo);
    persist();
    return HttpResponse.json({ id }, { status: 201 });
  }),
  http.put(`${API}/admin/param-versions/:id`, async ({ params, request }) => {
    const u = admin(request); if (!u) return forbid();
    const v = db.versions.find(x => x.id === Number(params.id));
    if (!v || v.status !== 'DRAFT') return err(409, 'Chỉ sửa được phiên bản nháp.');
    const b = (await request.json()) as { params: PolicyParam[]; weights: ScoringWeight[]; legalBasis?: string };
    for (const pc of ['TD', 'TC']) {
      const sum = b.weights.filter(w => w.productCode === pc).reduce((s, w) => s + Number(w.weight), 0);
      if (Math.abs(sum - 100) > 0.01) return err(400, `Tổng trọng số của gói ${pc} phải bằng 100% (hiện ${sum}%).`);
    }
    const dti = b.params.find(p => p.key === 'DTI_MAX');
    if (dti && (Number(dti.value) < 65 || Number(dti.value) > 80)) return err(400, 'DTI tối đa phải nằm trong khoảng 65%–80%.');
    db.params[v.id] = b.params.map(p => ({ ...p, label: PARAM_LABELS[p.key] ?? p.label }));
    db.weights[v.id] = b.weights.map(w => ({ ...w, weight: Number(w.weight), label: CRITERIA_LABELS[w.criterion] ?? w.label }));
    if (b.legalBasis) v.legalBasis = b.legalBasis;
    delete db.testResults[v.id];
    log(u, 'Cập nhật bản nháp tham số', v.versionNo);
    persist();
    return HttpResponse.json({ ok: true });
  }),
  http.delete(`${API}/admin/param-versions/:id`, ({ params, request }) => {
    const u = admin(request); if (!u) return forbid();
    const i = db.versions.findIndex(x => x.id === Number(params.id) && x.status === 'DRAFT');
    if (i < 0) return err(409, 'Chỉ xóa được phiên bản nháp.');
    log(u, 'Xóa bản nháp tham số', db.versions[i].versionNo);
    db.versions.splice(i, 1); persist();
    return HttpResponse.json({ ok: true });
  }),
  http.post(`${API}/admin/param-versions/:id/test`, async ({ params, request }) => {
    await delay(400);
    const u = admin(request); if (!u) return forbid();
    const v = db.versions.find(x => x.id === Number(params.id));
    if (!v || v.status !== 'DRAFT') return err(409, 'Chỉ chạy thử phiên bản nháp.');
    const a = activeVersion();
    const psA = paramsOf(a.id), wsA = weightsOf(a.id), psD = paramsOf(v.id), wsD = weightsOf(v.id);
    const rows: TestRunRow[] = [];
    for (const pr of SAMPLE_PROFILES) {
      for (const code of ['TD', 'TC'] as ProductCode[]) {
        const ea = exclusion(pr.inp, psA, code), ed = exclusion(pr.inp, psD, code);
        rows.push({
          profile: pr.name, productCode: code, activeEligible: ea.eligible, draftEligible: ed.eligible,
          activeMaxSafe: ea.eligible ? maxSafeAmount(pr.inp, psA, code, ea.term, psA.products[code].refRate) : null,
          draftMaxSafe: ed.eligible ? maxSafeAmount(pr.inp, psD, code, ed.term, psD.products[code].refRate) : null,
          activeScore: ea.eligible ? scoreOf(pr.inp, psA, wsA, code, pr.inp.desiredAmount, ea.term) : null,
          draftScore: ed.eligible ? scoreOf(pr.inp, psD, wsD, code, pr.inp.desiredAmount, ed.term) : null,
        });
      }
    }
    const changed = rows.filter(r => r.activeEligible !== r.draftEligible || r.activeMaxSafe !== r.draftMaxSafe || r.activeScore !== r.draftScore).length;
    db.testResults[v.id] = { runAt: nowIso(), rows, changed };
    log(u, 'Chạy thử phiên bản tham số', `${v.versionNo}: ${changed} dòng thay đổi`);
    persist();
    return HttpResponse.json(db.testResults[v.id]);
  }),
  http.post(`${API}/admin/param-versions/:id/activate`, ({ params, request }) => {
    const u = admin(request); if (!u) return forbid();
    const v = db.versions.find(x => x.id === Number(params.id));
    if (!v || v.status !== 'DRAFT') return err(409, 'Chỉ kích hoạt phiên bản nháp.');
    if (!db.testResults[v.id]) return err(409, 'Cần chạy thử trên bộ hồ sơ mẫu trước khi kích hoạt.');
    activeVersion().status = 'EXPIRED';
    v.status = 'ACTIVE'; v.activatedAt = nowIso();
    log(u, 'Kích hoạt phiên bản tham số', v.versionNo);
    persist();
    return HttpResponse.json(v);
  }),

  // ---------------- 1.5 tài khoản nhân viên ----------------
  http.get(`${API}/admin/staff`, ({ request }) => admin(request) ? HttpResponse.json(db.accounts.filter(a => a.kind === 'STAFF').map(staffOut)) : forbid()),
  http.post(`${API}/admin/staff`, async ({ request }) => {
    const u = admin(request); if (!u) return forbid();
    const b = (await request.json()) as { fullName: string; email: string; role: 'BRANCH_STAFF' | 'ADMIN'; branchId: number | null };
    if (!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(b.email)) return err(400, 'Email không hợp lệ.');
    if (db.accounts.some(a => a.kind === 'STAFF' && a.username === b.email)) return err(409, 'Email đã được dùng.');
    if (b.role === 'BRANCH_STAFF' && !b.branchId) return err(400, 'Nhân viên chi nhánh cần chọn chi nhánh.');
    const acc: Account = { id: nextId(), kind: 'STAFF', username: b.email, password: '123456', name: b.fullName, role: b.role,
      branchId: b.role === 'ADMIN' ? null : b.branchId, status: 'ACTIVE', createdAt: nowIso() };
    db.accounts.push(acc);
    log(u, 'Tạo tài khoản nhân viên', b.email);
    persist();
    return HttpResponse.json({ ...staffOut(acc), tempPassword: '123456' }, { status: 201 });
  }),
  http.put(`${API}/admin/staff/:id`, async ({ params, request }) => {
    const u = admin(request); if (!u) return forbid();
    const acc = db.accounts.find(a => a.id === Number(params.id) && a.kind === 'STAFF');
    if (!acc) return err(404, 'Không tìm thấy tài khoản.');
    const b = (await request.json()) as { fullName: string; role: 'BRANCH_STAFF' | 'ADMIN'; branchId: number | null };
    if (b.role === 'BRANCH_STAFF' && !b.branchId) return err(400, 'Nhân viên chi nhánh cần chọn chi nhánh.');
    if (acc.id === u.id && b.role !== 'ADMIN') return err(409, 'Không tự hạ quyền của chính mình.');
    acc.name = b.fullName; acc.role = b.role; acc.branchId = b.role === 'ADMIN' ? null : b.branchId;
    log(u, 'Cập nhật tài khoản nhân viên', acc.username);
    persist();
    return HttpResponse.json(staffOut(acc));
  }),
  http.post(`${API}/admin/staff/:id/:action`, ({ params, request }) => {
    const u = admin(request); if (!u) return forbid();
    const acc = db.accounts.find(a => a.id === Number(params.id) && a.kind === 'STAFF');
    if (!acc) return err(404, 'Không tìm thấy tài khoản.');
    if (acc.id === u.id && params.action === 'lock') return err(409, 'Không tự khóa tài khoản của mình.');
    if (params.action === 'lock') acc.status = 'LOCKED';
    else if (params.action === 'unlock') acc.status = 'ACTIVE';
    else if (params.action === 'reset-password') acc.password = '123456';
    else return err(404, 'Thao tác không hợp lệ.');
    // khóa và đặt lại mật khẩu đều thu hồi mọi phiên của tài khoản (backend thu hồi auth_session)
    if (params.action === 'lock' || params.action === 'reset-password') for (const [t, id] of Object.entries(db.tokens)) if (id === acc.id) delete db.tokens[t];
    log(u, 'Quản lý tài khoản nhân viên', `${({ lock: 'Khóa', unlock: 'Mở khóa', 'reset-password': 'Đặt lại mật khẩu' } as Record<string, string>)[params.action as string]} ${acc.username}`);
    persist();
    return HttpResponse.json({ ...staffOut(acc), tempPassword: params.action === 'reset-password' ? '123456' : undefined });
  }),

  // ---------------- 1.6 chi nhánh ----------------
  http.get(`${API}/admin/branches`, ({ request }) => {
    if (!admin(request)) return forbid();
    return HttpResponse.json(db.branches.map(b => ({ ...b, openApplications: db.applications.filter(a => a.branchId === b.id && [...OPEN_STATUSES, 'VERIFIED', 'APPROVED'].includes(a.status)).length })));
  }),
  http.post(`${API}/admin/branches`, async ({ request }) => {
    const u = admin(request); if (!u) return forbid();
    const b = (await request.json()) as Omit<Branch, 'id' | 'status'>;
    if (db.branches.some(x => x.code === b.code)) return err(409, 'Mã chi nhánh đã tồn tại.');
    const br: Branch = { id: nextId(), ...b, status: 'ACTIVE' };
    db.branches.push(br);
    log(u, 'Thêm chi nhánh', br.name);
    persist();
    return HttpResponse.json(br, { status: 201 });
  }),
  http.put(`${API}/admin/branches/:id`, async ({ params, request }) => {
    const u = admin(request); if (!u) return forbid();
    const br = db.branches.find(x => x.id === Number(params.id));
    if (!br) return err(404, 'Không tìm thấy chi nhánh.');
    const b = (await request.json()) as Omit<Branch, 'id' | 'status'>;
    if (db.branches.some(x => x.code === b.code && x.id !== br.id)) return err(409, 'Mã chi nhánh đã tồn tại.');
    Object.assign(br, { code: b.code, name: b.name, address: b.address, phone: b.phone });
    log(u, 'Cập nhật chi nhánh', br.name);
    persist();
    return HttpResponse.json(br);
  }),
  http.post(`${API}/admin/branches/:id/deactivate`, async ({ params, request }) => {
    const u = admin(request); if (!u) return forbid();
    const br = db.branches.find(x => x.id === Number(params.id));
    if (!br) return err(404, 'Không tìm thấy chi nhánh.');
    const b = (await request.json().catch(() => ({}))) as { transferToBranchId?: number };
    const open = db.applications.filter(a => a.branchId === br.id && [...OPEN_STATUSES, 'VERIFIED', 'APPROVED'].includes(a.status));
    // nhân viên chi nhánh chuyển cùng hồ sơ sang chi nhánh nhận (đã chốt 23/10/2026)
    const people = db.accounts.filter(a => a.kind === 'STAFF' && a.role === 'BRANCH_STAFF' && a.branchId === br.id);
    if (open.length || people.length) {
      const to = db.branches.find(x => x.id === b.transferToBranchId && x.status === 'ACTIVE' && x.id !== br.id);
      if (!to) return err(409, open.length
        ? `Chi nhánh còn ${open.length} hồ sơ đang xử lý; chọn chi nhánh nhận chuyển.`
        : `Chi nhánh còn ${people.length} nhân viên; chọn chi nhánh nhận chuyển.`);
      for (const a of open) {
        a.branchId = to.id; a.updatedAt = nowIso();
        a.history.push({ status: a.status, at: nowIso(), actor: u.name, note: `Chuyển từ ${br.name} sang ${to.name}` });
        notify({ customerId: a.customerId }, 'Hồ sơ được chuyển chi nhánh', `Hồ sơ ${a.code} được chuyển sang ${to.name} (${to.address}).`, `/ho-so/${a.id}`);
        notify({ branchId: to.id }, 'Nhận hồ sơ chuyển chi nhánh', `${a.code} từ ${br.name}`, `/noi-bo/ho-so/${a.id}`);
      }
      for (const p of people) p.branchId = to.id;
    }
    br.status = 'INACTIVE';
    log(u, 'Ngừng hoạt động chi nhánh', `${br.name}${open.length ? `, chuyển ${open.length} hồ sơ` : ''}${people.length ? `, chuyển ${people.length} nhân viên` : ''}`);
    persist();
    return HttpResponse.json({ ...br, transferred: open.length, transferredStaff: people.length });
  }),
  http.post(`${API}/admin/branches/:id/activate`, ({ params, request }) => {
    const u = admin(request); if (!u) return forbid();
    const br = db.branches.find(x => x.id === Number(params.id));
    if (!br) return err(404, 'Không tìm thấy chi nhánh.');
    br.status = 'ACTIVE';
    log(u, 'Mở lại chi nhánh', br.name);
    persist();
    return HttpResponse.json(br);
  }),

  // ---------------- 1.7 tác vụ định kỳ ----------------
  http.get(`${API}/admin/jobs`, ({ request }) => admin(request) ? HttpResponse.json(db.jobs) : forbid()),
  http.post(`${API}/admin/jobs/:code/run`, async ({ params, request }) => {
    await delay(400);
    const u = admin(request); if (!u) return forbid();
    const job = db.jobs.find(j => j.code === params.code);
    if (!job) return err(404, 'Không có tác vụ.');
    let result = '';
    if (job.code === 'EXPIRE_DOCUMENTS') {
      const now = Date.now();
      const hit = db.applications.filter(a => ['WAITING_VERIFY', 'NEED_MORE'].includes(a.status) && a.expiresAt && Date.parse(a.expiresAt) < now);
      for (const a of hit) {
        setStatus(a, 'EXPIRED', 'Hệ thống', 'Giấy tờ quá thời hạn hiệu lực');
        notify({ customerId: a.customerId }, 'Hồ sơ hết hạn', `Giấy tờ của hồ sơ ${a.code} đã quá thời hạn hiệu lực. Cập nhật giấy tờ để nộp lại.`, `/ho-so/${a.id}`);
        log(null, 'Chuyển hết hạn', 'Giấy tờ quá thời hạn hiệu lực', a, 'SYSTEM');
      }
      result = `${hit.length} hồ sơ chuyển “Hết hạn”.`;
    } else if (job.code === 'PURGE_RETENTION') {
      const months = paramValue('RETENTION_MONTHS') || 12;
      const limit = Date.now() - months * 30 * 864e5;
      let n = 0;
      for (const a of db.applications) {
        if (['REJECTED', 'CANCELLED', 'EXPIRED'].includes(a.status) && Date.parse(a.updatedAt) < limit) {
          for (const d of a.documents) if (d.fileName && d.documentTypeId !== SYSTEM_DOC_TYPE) {
            Object.assign(d, { status: 'MISSING', fileName: null, previewUrl: null, fileUrl: null, fields: [] }); n++;
          }
          a.personal = { ...a.personal, nationalId: '', address: '' };
          a.requestForm = null; // giấy đề nghị có CCCD, địa chỉ (backend xóa loan_request_form như khi rút đồng ý)
        }
      }
      result = `Xóa ${n} tài liệu của hồ sơ đã đóng quá ${months} tháng.`;
    } else {
      const old = db.applications.filter(a => a.status === 'DRAFTING' && Date.now() - Date.parse(a.updatedAt) > 3 * 864e5);
      for (const a of old) notify({ customerId: a.customerId }, 'Nhắc hoàn tất hồ sơ', `Hồ sơ ${a.code} còn đang lập. Hoàn tất giấy tờ để nộp hồ sơ.`, `/ho-so/${a.id}`);
      result = `Gửi nhắc ${old.length} khách hàng.`;
    }
    job.lastRunAt = nowIso(); job.lastResult = result;
    log(u, 'Chạy tác vụ định kỳ', `${job.name}: ${result}`);
    persist();
    return HttpResponse.json(job);
  }),

  // ---------------- 7.3 danh mục ----------------
  http.get(`${API}/admin/catalog`, ({ request }) => admin(request)
    ? HttpResponse.json({ products: db.products, documentTypes: db.docTypes, productDocuments: db.productDocs, crossSells: db.crossSells }) : forbid()),
  http.put(`${API}/admin/products/:id`, async ({ params, request }) => {
    const u = admin(request); if (!u) return forbid();
    const p = db.products.find(x => x.id === Number(params.id));
    if (!p) return err(404, 'Không tìm thấy gói vay.');
    const b = (await request.json()) as Partial<LoanProduct>;
    Object.assign(p, { name: b.name ?? p.name, description: b.description ?? p.description, repaymentMethod: b.repaymentMethod ?? p.repaymentMethod, status: b.status ?? p.status });
    log(u, 'Cập nhật gói vay', p.name);
    persist();
    return HttpResponse.json(p);
  }),
  http.post(`${API}/admin/document-types`, async ({ request }) => {
    const u = admin(request); if (!u) return forbid();
    const b = (await request.json()) as Omit<DocumentType, 'id'>;
    const t: DocumentType = { ...b, id: nextId() };
    db.docTypes.push(t);
    log(u, 'Thêm loại giấy tờ', t.name);
    persist();
    return HttpResponse.json(t, { status: 201 });
  }),
  http.put(`${API}/admin/document-types/:id`, async ({ params, request }) => {
    const u = admin(request); if (!u) return forbid();
    const t = db.docTypes.find(x => x.id === Number(params.id));
    if (!t) return err(404, 'Không tìm thấy loại giấy tờ.');
    const b = (await request.json()) as Omit<DocumentType, 'id'>;
    if (t.id === SYSTEM_DOC_TYPE && b.status === 'INACTIVE') return err(409, 'Không ngừng áp dụng giấy đề nghị vay vốn.');
    Object.assign(t, b);
    log(u, 'Cập nhật loại giấy tờ', t.name);
    persist();
    return HttpResponse.json(t);
  }),
  http.put(`${API}/admin/product-documents`, async ({ request }) => {
    const u = admin(request); if (!u) return forbid();
    const b = (await request.json()) as ProductDocRule[];
    for (const pc of ['TD', 'TC']) if (!b.some(r => r.productCode === pc && r.documentTypeId === SYSTEM_DOC_TYPE))
      return err(400, 'Mỗi gói phải có giấy đề nghị vay vốn.');
    db.productDocs = b;
    log(u, 'Cập nhật giấy tờ theo gói vay', `${b.length} dòng`);
    persist();
    return HttpResponse.json(db.productDocs);
  }),
  http.post(`${API}/admin/cross-sell-products`, async ({ request }) => {
    const u = admin(request); if (!u) return forbid();
    const b = (await request.json()) as Omit<CrossSellProduct, 'id'>;
    const c: CrossSellProduct = { ...b, id: nextId() };
    db.crossSells.push(c);
    log(u, 'Thêm sản phẩm bán chéo', c.name);
    persist();
    return HttpResponse.json(c, { status: 201 });
  }),
  http.put(`${API}/admin/cross-sell-products/:id`, async ({ params, request }) => {
    const u = admin(request); if (!u) return forbid();
    const c = db.crossSells.find(x => x.id === Number(params.id));
    if (!c) return err(404, 'Không tìm thấy sản phẩm.');
    Object.assign(c, (await request.json()) as Omit<CrossSellProduct, 'id'>);
    log(u, 'Cập nhật sản phẩm bán chéo', c.name);
    persist();
    return HttpResponse.json(c);
  }),

  // Nhật ký hệ thống
  http.get(`${API}/admin/logs`, ({ request }) => {
    if (!admin(request)) return forbid();
    const url = new URL(request.url);
    const kw = (url.searchParams.get('keyword') || '').toLowerCase();
    const type = url.searchParams.get('actorType');
    const rows = db.logs.filter(l => (!type || l.actorType === type) && (!kw || `${l.actorName} ${l.action} ${l.detail} ${l.applicationCode ?? ''}`.toLowerCase().includes(kw)));
    return HttpResponse.json(rows.slice().sort((a, b) => b.createdAt.localeCompare(a.createdAt)).slice(0, 500).map(({ customerId: _c, ...l }) => l));
  }),
];
