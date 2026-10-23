import { http, HttpResponse, delay } from 'msw';
import type { Verification } from '../../api/types';
import { API, db, err, nextId, persist, currentUser, nowIso, log, notify, setStatus, view, summary, fmtMoney } from '../ctx';
import { SYSTEM_DOC_TYPE } from '../db';
import { APP_STATUS } from '../../utils/status';

function staffApp(request: Request, id: unknown) {
  const u = currentUser(request);
  if (!u || u.role !== 'BRANCH_STAFF') return { e: err(403, 'Không có quyền.') } as const;
  const app = db.applications.find(a => a.id === String(id) && a.branchId === u.branchId);
  if (!app) return { e: err(404, 'Không tìm thấy hồ sơ của chi nhánh.') } as const;
  return { u, app } as const;
}

export const staffHandlers = [
  // 5.1 Tra cứu hồ sơ của chi nhánh
  http.get(`${API}/staff/applications`, ({ request }) => {
    const u = currentUser(request);
    if (!u || u.role !== 'BRANCH_STAFF') return err(403, 'Không có quyền.');
    const url = new URL(request.url);
    const st = url.searchParams.get('status');
    const kw = (url.searchParams.get('keyword') || '').trim().toLowerCase();
    const list = db.applications.filter(a => a.branchId === u.branchId && a.status !== 'DRAFTING' && (!st || st.split(',').includes(a.status)) &&
      (!kw || a.code.toLowerCase().includes(kw) || a.personal.fullName.toLowerCase().includes(kw) || a.personal.phone.includes(kw) || (a.qrToken ?? '').toLowerCase() === kw))
      .sort((a, b) => b.updatedAt.localeCompare(a.updatedAt));
    return HttpResponse.json(list.map(summary));
  }),
  http.get(`${API}/staff/applications/by-qr/:token`, ({ params, request }) => {
    const u = currentUser(request);
    if (!u || u.role !== 'BRANCH_STAFF') return err(403, 'Không có quyền.');
    const app = db.applications.find(a => a.qrToken === String(params.token).toUpperCase());
    if (!app) return err(404, 'Không tìm thấy hồ sơ theo mã QR.');
    if (app.branchId !== u.branchId) return err(403, 'Hồ sơ thuộc chi nhánh khác.');
    return HttpResponse.json({ id: app.id });
  }),
  http.get(`${API}/staff/summary`, ({ request }) => {
    const u = currentUser(request);
    if (!u || u.role !== 'BRANCH_STAFF') return err(403, 'Không có quyền.');
    const mineApps = db.applications.filter(a => a.branchId === u.branchId);
    const c = (s: string) => mineApps.filter(a => a.status === s).length;
    return HttpResponse.json({ waitingVerify: c('WAITING_VERIFY'), needMore: c('NEED_MORE'), verified: c('VERIFIED'), approved: c('APPROVED') });
  }),

  // 5.2 Đối chiếu giấy tờ bản gốc (MB09)
  http.post(`${API}/staff/applications/:id/verification`, async ({ params, request }) => {
    await delay(300);
    const r = staffApp(request, params.id); if ('e' in r) return r.e;
    const { u, app } = r;
    if (app.status !== 'WAITING_VERIFY') return err(409, 'Hồ sơ không ở trạng thái chờ đối chiếu.');
    const b = (await request.json()) as { details: { documentId: number; result: 'MATCH' | 'MISMATCH' | 'MISSING'; reason: string }[]; note: string };
    const docs = app.documents.filter(d => d.documentTypeId !== SYSTEM_DOC_TYPE && d.fileName);
    if (docs.some(d => !b.details.find(x => x.documentId === d.id))) return err(400, 'Cần ghi kết quả cho từng giấy tờ.');
    if (b.details.some(x => x.result !== 'MATCH' && !x.reason?.trim())) return err(400, 'Cần ghi lý do cho giấy tờ không khớp hoặc thiếu.');
    const ok = b.details.every(x => x.result === 'MATCH');
    const v: Verification = { id: nextId(), staffName: u.name, overallResult: ok ? 'MATCH' : 'MISMATCH', note: b.note || '', verifiedAt: nowIso(),
      details: b.details.map(x => ({ ...x, typeName: app.documents.find(d => d.id === x.documentId)?.typeName ?? '' })) };
    app.verifications.push(v);
    for (const x of b.details) {
      const d = app.documents.find(y => y.id === x.documentId);
      if (d) { d.verifyResult = x.result; d.verifyReason = x.reason || null; }
    }
    if (ok) {
      setStatus(app, 'VERIFIED', u.name, 'Tất cả giấy tờ khớp bản gốc');
      notify({ customerId: app.customerId }, 'Hồ sơ đã đối chiếu xong', `Hồ sơ ${app.code} đã được chi nhánh đối chiếu và chuyển ngân hàng thẩm định.`, `/ho-so/${app.id}`);
    } else {
      const bad = v.details.filter(x => x.result !== 'MATCH');
      setStatus(app, 'NEED_MORE', u.name, bad.map(x => `${x.typeName}: ${x.reason}`).join('; '));
      notify({ customerId: app.customerId }, 'Hồ sơ cần bổ sung giấy tờ', bad.map(x => `${x.typeName} – ${x.reason}`).join('; '), `/ho-so/${app.id}`);
    }
    log(u, 'Đối chiếu giấy tờ', ok ? 'Khớp toàn bộ' : 'Có giấy tờ không khớp', app);
    persist();
    return HttpResponse.json(view(app, u));
  }),

  // 5.3 Cập nhật kết quả xét duyệt (MB16)
  http.post(`${API}/staff/applications/:id/decision`, async ({ params, request }) => {
    const r = staffApp(request, params.id); if ('e' in r) return r.e;
    const { u, app } = r;
    if (app.status !== 'VERIFIED') return err(409, 'Chỉ ghi nhận kết quả cho hồ sơ đã đối chiếu.');
    const b = (await request.json()) as { resultType: 'APPROVED' | 'REJECTED' | 'MORE_INFO'; approvedAmount?: number; approvedTerm?: number; approvedRate?: number; reason?: string };
    if (b.resultType !== 'APPROVED' && !b.reason?.trim()) return err(400, 'Cần ghi lý do.');
    if (b.resultType === 'APPROVED' && !(b.approvedAmount && b.approvedTerm && b.approvedRate)) return err(400, 'Cần ghi số tiền, kỳ hạn, lãi suất được duyệt.');
    app.approval = { id: nextId(), resultType: b.resultType, approvedAmount: b.approvedAmount ?? null, approvedTerm: b.approvedTerm ?? null,
      approvedRate: b.approvedRate ?? null, reason: b.reason ?? null, recordedAt: nowIso(), staffName: u.name, disbursedAt: null, disbursedAmount: null, beneficiary: null };
    if (b.resultType === 'APPROVED') {
      setStatus(app, 'APPROVED', u.name, `Duyệt ${fmtMoney(b.approvedAmount!)} đ, ${b.approvedTerm} tháng, ${b.approvedRate}%/năm`);
      notify({ customerId: app.customerId }, 'Hồ sơ được phê duyệt', `Ngân hàng phê duyệt ${fmtMoney(b.approvedAmount!)} đ, kỳ hạn ${b.approvedTerm} tháng, lãi suất ${b.approvedRate}%/năm. Chi nhánh sẽ liên hệ ký hợp đồng.`, `/ho-so/${app.id}`);
    } else if (b.resultType === 'REJECTED') {
      setStatus(app, 'REJECTED', u.name, b.reason!);
      notify({ customerId: app.customerId }, 'Hồ sơ không được phê duyệt', `Lý do: ${b.reason}`, `/ho-so/${app.id}`);
    } else {
      setStatus(app, 'NEED_MORE', u.name, `Ngân hàng yêu cầu bổ sung: ${b.reason}`);
      notify({ customerId: app.customerId }, 'Ngân hàng yêu cầu bổ sung hồ sơ', b.reason!, `/ho-so/${app.id}`);
    }
    log(u, 'Cập nhật kết quả xét duyệt', APP_STATUS[b.resultType === 'MORE_INFO' ? 'NEED_MORE' : b.resultType].label, app);
    persist();
    return HttpResponse.json(view(app, u));
  }),

  // 5.3 Cập nhật thông tin giải ngân
  http.post(`${API}/staff/applications/:id/disbursement`, async ({ params, request }) => {
    const r = staffApp(request, params.id); if ('e' in r) return r.e;
    const { u, app } = r;
    if (app.status !== 'APPROVED' || !app.approval) return err(409, 'Chỉ giải ngân hồ sơ đã được phê duyệt.');
    const b = (await request.json()) as { disbursedAt: string; disbursedAmount: number; beneficiary: string };
    if (!b.disbursedAt || !(b.disbursedAmount > 0) || !b.beneficiary) return err(400, 'Cần đủ ngày, số tiền giải ngân và bên thụ hưởng.');
    if (b.disbursedAmount > (app.approval.approvedAmount ?? 0)) return err(400, 'Số tiền giải ngân vượt số tiền được duyệt.');
    Object.assign(app.approval, b);
    setStatus(app, 'DISBURSED', u.name, `Giải ngân ${fmtMoney(b.disbursedAmount)} đ cho ${b.beneficiary}`);
    notify({ customerId: app.customerId }, 'Khoản vay đã được giải ngân', `${fmtMoney(b.disbursedAmount)} đ ngày ${b.disbursedAt.split('-').reverse().join('/')} vào ${b.beneficiary}.`, `/ho-so/${app.id}`);
    log(u, 'Cập nhật giải ngân', `${fmtMoney(b.disbursedAmount)} đ`, app);
    persist();
    return HttpResponse.json(view(app, u));
  }),
];
