import { http, HttpResponse, delay } from 'msw';
import type { LoanPlan, ScreeningRequest, ScreeningResponse, SimulationRequest } from '../../api/types';
import { API, db, err, nextId, persist, currentUser, activeVersion, paramsOf, weightsOf, nowIso, log } from '../ctx';
import { screen, simulate, exclusion } from '../engine';
import { checklist } from './catalog';

type ScreeningRow = ScreeningResponse & { consentId?: number };
type User = ReturnType<typeof currentUser>;

/** Lượt đã lưu vào tài khoản chỉ chủ tài khoản thao tác được; lượt chưa thuộc ai thì ai có mã (UUID) cũng dùng được */
const usableBy = (s: ScreeningResponse, u: User) => s.savedToCustomerId === null || (u?.role === 'CUSTOMER' && u.id === s.savedToCustomerId);
const findScreening = (id: unknown, u: User) => {
  const s = db.screenings.find(x => x.id === String(id));
  return s && usableBy(s, u) ? (s as ScreeningRow) : null;
};
/** Mã ngắn dùng trong nhật ký (không lộ id nội bộ) */
const short = (id: string) => id.slice(0, 8);

export const screeningHandlers = [
  // QT01: 2.1 + 2.2
  http.post(`${API}/screenings`, async ({ request }) => {
    await delay(400);
    const inp = (await request.json()) as ScreeningRequest;
    if (!inp.consentA?.agreed) return err(400, 'Cần đồng ý xử lý dữ liệu trước khi sàng lọc.');
    if (!(inp.avgIncome >= 0) || !(inp.desiredAmount > 0)) return err(400, 'Thông tin thu nhập hoặc số tiền vay không hợp lệ.');
    const v = activeVersion();
    const r = screen(inp, paramsOf(v.id), weightsOf(v.id));
    const u = currentUser(request);
    const customerId = u?.role === 'CUSTOMER' ? u.id : null;
    const res: ScreeningRow = { id: crypto.randomUUID(), createdAt: nowIso(), versionNo: v.versionNo, input: inp, ...r, savedToCustomerId: customerId };
    db.consents.push({ id: nextId(), customerId, scope: 'A', docVersion: inp.consentA.docVersion, agreedAt: nowIso(), withdrawnAt: null, applicationCode: null });
    res.consentId = db.consents[db.consents.length - 1].id;
    db.screenings.push(res);
    persist();
    return HttpResponse.json(res, { status: 201 });
  }),
  // Không có, hoặc đã lưu vào tài khoản khác: cùng một thông báo để không lộ lượt có tồn tại hay không
  http.get(`${API}/screenings/:id`, ({ params, request }) => {
    const s = findScreening(params.id, currentUser(request));
    return s ? HttpResponse.json(s) : err(404, 'Không tìm thấy kết quả sàng lọc.');
  }),
  // 2.3 lưu kết quả
  http.post(`${API}/screenings/:id/save`, ({ params, request }) => {
    const u = currentUser(request);
    if (!u || u.role !== 'CUSTOMER') return err(401, 'Cần đăng nhập tài khoản khách hàng.');
    const s = findScreening(params.id, u);
    if (!s) return err(404, 'Không tìm thấy lượt sàng lọc.');
    s.savedToCustomerId = u.id;
    const c = db.consents.find(x => x.id === s.consentId);
    if (c) c.customerId = u.id;
    log(u, 'Lưu kết quả sàng lọc', `Lượt #${short(s.id)}`);
    persist();
    return HttpResponse.json(s);
  }),
  http.get(`${API}/me/screenings`, ({ request }) => {
    const u = currentUser(request);
    if (!u) return err(401, 'Chưa đăng nhập.');
    if (u.role !== 'CUSTOMER') return HttpResponse.json([]);
    return HttpResponse.json(db.screenings.filter(s => s.savedToCustomerId === u.id).reverse());
  }),

  // QT02: 3.1 mô phỏng
  http.post(`${API}/simulations`, async ({ request }) => {
    await delay(150);
    const b = (await request.json()) as SimulationRequest;
    const s = findScreening(b.screeningId, currentUser(request));
    if (!s) return err(404, 'Không tìm thấy lượt sàng lọc.');
    const v = activeVersion();
    const ps = paramsOf(v.id);
    if (!exclusion(s.input, ps, b.productCode).eligible) return err(422, 'Hồ sơ không đạt điều kiện của gói vay này.');
    return HttpResponse.json(simulate(s.input, ps, weightsOf(v.id), b, db.crossSells));
  }),
  // 3.2 lưu, xác nhận phương án
  http.post(`${API}/plans`, async ({ request }) => {
    const u = currentUser(request);
    if (!u || u.role !== 'CUSTOMER') return err(401, 'Cần đăng nhập tài khoản khách hàng để lưu phương án.');
    const b = (await request.json()) as SimulationRequest;
    const s = findScreening(b.screeningId, u);
    if (!s) return err(404, 'Không tìm thấy lượt sàng lọc.');
    const v = activeVersion();
    const ps = paramsOf(v.id);
    if (!exclusion(s.input, ps, b.productCode).eligible) return err(422, 'Hồ sơ không đạt điều kiện của gói vay này.');
    const sim = simulate(s.input, ps, weightsOf(v.id), b, db.crossSells);
    if (!sim.ok) return err(422, 'Phương án chưa đạt: ' + sim.issues.join(' '));
    s.savedToCustomerId = u.id;
    const c = db.consents.find(x => x.id === s.consentId);
    if (c) c.customerId = u.id;
    const plan: LoanPlan & { customerId: number } = {
      id: crypto.randomUUID(), customerId: u.id, screeningId: s.id, productCode: sim.productCode, productName: sim.productName, amount: sim.amount,
      termMonths: sim.termMonths, refRate: sim.rate, monthlyPayment: sim.firstPayment, totalInterest: sim.totalInterest,
      dtiAfter: sim.dtiAfter, score: sim.score, status: 'DRAFT',
      crossSells: db.crossSells.filter(cs => b.crossSellIds.includes(cs.id)).map(cs => ({ id: cs.id, name: cs.name, extraCost: cs.estCost,
        benefit: `Giảm ${cs.rateDiscount}%/năm lãi suất tham chiếu, +${cs.scoreBonus} điểm ưu tiên` })),
      createdAt: nowIso(), checklist: checklist(sim.productCode),
    };
    db.plans.push(plan);
    log(u, 'Lưu phương án vay', `Phương án #${short(plan.id)}`);
    persist();
    return HttpResponse.json(plan, { status: 201 });
  }),
  http.get(`${API}/plans/:id`, ({ params, request }) => {
    const u = currentUser(request);
    const p = db.plans.find(x => x.id === String(params.id));
    if (!p || !u || p.customerId !== u.id) return err(404, 'Không tìm thấy phương án.');
    const app = db.applications.find(a => a.plan.id === p.id && a.status !== 'CANCELLED');
    return HttpResponse.json({ ...p, applicationId: app?.id ?? null });
  }),
  http.post(`${API}/plans/:id/confirm`, ({ params, request }) => {
    const u = currentUser(request);
    const p = db.plans.find(x => x.id === String(params.id));
    if (!p || !u || p.customerId !== u.id) return err(404, 'Không tìm thấy phương án.');
    p.status = 'CONFIRMED';
    log(u, 'Xác nhận phương án vay', `Phương án #${short(p.id)}`);
    persist();
    return HttpResponse.json(p);
  }),
  http.get(`${API}/me/plans`, ({ request }) => {
    const u = currentUser(request);
    if (!u) return err(401, 'Chưa đăng nhập.');
    if (u.role !== 'CUSTOMER') return HttpResponse.json([]);
    return HttpResponse.json(db.plans.filter(p => p.customerId === u.id).reverse());
  }),
];
