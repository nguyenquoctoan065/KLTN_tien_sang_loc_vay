import { http, HttpResponse } from 'msw';
import type { MyData, WithdrawConsentResult } from '../../api/types';
import { API, db, err, persist, currentUser, nowIso, log, notify, setStatus, DEV_OTP } from '../ctx';
import { SYSTEM_DOC_TYPE } from '../db';

const mine = (u: NonNullable<ReturnType<typeof currentUser>>) =>
  db.notifications.filter(n => (u.role === 'CUSTOMER' ? n.customerId === u.id : u.role === 'BRANCH_STAFF' && n.branchId === u.branchId && n.customerId === null));

export const meHandlers = [
  // Thông báo trong ứng dụng (5.4)
  http.get(`${API}/notifications`, ({ request }) => {
    const u = currentUser(request);
    if (!u) return err(401, 'Chưa đăng nhập.');
    return HttpResponse.json(mine(u).slice().reverse().slice(0, 50).map(({ customerId: _c, branchId: _b, ...n }) => n));
  }),
  http.post(`${API}/notifications/:id/read`, ({ params, request }) => {
    const u = currentUser(request);
    if (!u) return err(401, 'Chưa đăng nhập.');
    const n = mine(u).find(x => x.id === Number(params.id));
    if (n) { n.read = true; persist(); }
    return HttpResponse.json({ ok: true });
  }),
  http.post(`${API}/notifications/read-all`, ({ request }) => {
    const u = currentUser(request);
    if (!u) return err(401, 'Chưa đăng nhập.');
    mine(u).forEach(n => { n.read = true; });
    persist();
    return HttpResponse.json({ ok: true });
  }),

  // 6.1 Xem dữ liệu cá nhân và nhật ký truy cập
  http.get(`${API}/me/data`, ({ request }) => {
    const u = currentUser(request);
    if (!u || u.role !== 'CUSTOMER') return err(401, 'Cần đăng nhập tài khoản khách hàng.');
    const apps = db.applications.filter(a => a.customerId === u.id);
    const latest = apps.slice().reverse().find(a => a.personal.fullName) ?? null;
    const out: MyData = {
      account: { phone: u.username, name: u.name, createdAt: u.createdAt },
      personal: latest?.personal ?? null,
      consents: db.consents.filter(c => c.customerId === u.id).map(({ customerId: _c, ...c }) => c).reverse(),
      screenings: db.screenings.filter(s => s.savedToCustomerId === u.id).length,
      plans: db.plans.filter(p => p.customerId === u.id).length,
      applications: apps.map(a => ({ code: a.code, status: a.status, documents: a.documents.filter(d => d.fileName && d.documentTypeId !== SYSTEM_DOC_TYPE).length })),
      logs: db.logs.filter(l => l.customerId === u.id).slice().reverse().slice(0, 100).map(({ customerId: _c, ...l }) => l),
    };
    log(u, 'Xem dữ liệu cá nhân', '');
    persist();
    return HttpResponse.json(out);
  }),

  // 6.3 Rút lại sự đồng ý
  http.post(`${API}/me/consents/withdraw`, async ({ request }) => {
    const u = currentUser(request);
    if (!u || u.role !== 'CUSTOMER') return err(401, 'Cần đăng nhập tài khoản khách hàng.');
    const b = (await request.json()) as { scope: 'A' | 'B'; otp: string };
    if (b.otp !== DEV_OTP) return err(400, 'Mã OTP không đúng.');
    const res: WithdrawConsentResult = { scope: b.scope, deletedScreenings: 0, deletedDocuments: 0, cancelledApplications: [], keptApplications: [], message: '' };
    const at = nowIso();
    if (b.scope === 'A') {
      const linked = new Set(db.applications.filter(a => a.customerId === u.id).map(a => a.screeningId));
      const del = db.screenings.filter(s => s.savedToCustomerId === u.id && !linked.has(s.id));
      res.deletedScreenings = del.length;
      db.screenings = db.screenings.filter(s => !del.includes(s));
      db.plans = db.plans.filter(p => !(p.customerId === u.id && del.some(s => s.id === p.screeningId)));
      db.consents.filter(c => c.customerId === u.id && c.scope === 'A' && !c.withdrawnAt).forEach(c => { c.withdrawnAt = at; });
      res.message = `Đã xóa ${del.length} lượt sàng lọc và phương án liên quan. Lượt sàng lọc gắn với hồ sơ vay được giữ theo đồng ý phần (b).`;
    } else {
      for (const a of db.applications.filter(x => x.customerId === u.id)) {
        if (['DRAFTING', 'WAITING_VERIFY', 'NEED_MORE', 'EXPIRED', 'CANCELLED'].includes(a.status)) {
          if (a.status !== 'CANCELLED') { setStatus(a, 'CANCELLED', u.name, 'Khách hàng rút lại sự đồng ý'); res.cancelledApplications.push(a.code);
            notify({ branchId: a.branchId }, 'Hồ sơ đã hủy do khách hàng rút đồng ý', a.code, `/noi-bo/ho-so/${a.id}`); }
          for (const d of a.documents) {
            if (d.fileName && d.documentTypeId !== SYSTEM_DOC_TYPE) res.deletedDocuments++;
            Object.assign(d, { status: 'MISSING', fileName: null, fileFormat: null, fileSize: null, previewUrl: null, fileUrl: null, fields: [], issuedDate: null, qualityStatus: null });
          }
          a.personal = { ...a.personal, nationalId: '', address: '', email: '' };
          a.warnings = []; a.recalc = null; a.requestForm = null; // giấy đề nghị chứa CCCD, địa chỉ nên xóa cùng (backend làm vậy)
        } else {
          res.keptApplications.push(a.code);
        }
      }
      db.consents.filter(c => c.customerId === u.id && c.scope === 'B' && !c.withdrawnAt).forEach(c => { c.withdrawnAt = at; });
      res.message = `Đã xóa ${res.deletedDocuments} tài liệu và thông tin định danh của hồ sơ chưa đối chiếu xong.` +
        (res.keptApplications.length ? ' Hồ sơ đã đối chiếu tại chi nhánh được ngân hàng lưu trữ theo quy định; liên hệ chi nhánh nếu cần hỗ trợ.' : '');
    }
    log(u, 'Rút lại sự đồng ý', `Phần (${b.scope.toLowerCase()})`);
    persist();
    return HttpResponse.json(res);
  }),
];
