import { http, HttpResponse, delay } from 'msw';
import type { LoanPlan, ScreeningRequest, ScreeningResponse, SimulationRequest } from '../../api/types';
import { API, db, err, nextId, persist, currentUser, activeVersion, paramsOf, weightsOf, nowIso, log } from '../ctx';
import { screen, simulate, exclusion } from '../engine';
import { checklist } from './catalog';

export const screeningHandlers = [
  // QT01: 2.1 + 2.2
  http.post(`${API}/screenings`, async ({ request }) => {
    await delay(400);
    const inp = (await request.json()) as ScreeningRequest;
    if (!inp.consentA?.agreed) return err(400, 'Cần đồng ý xử lý dữ liệu (MB02 phần a) trước khi sàng lọc.');
    if (!(inp.avgIncome >= 0) || !(inp.desiredAmount > 0)) return err(400, 'Thông tin thu nhập hoặc số tiền vay không hợp lệ.');
    const v = activeVersion();
    const r = screen(inp, paramsOf(v.id), weightsOf(v.id));
    const u = currentUser(request);
    const customerId = u?.role === 'CUSTOMER' ? u.id : null;
    const res: ScreeningResponse = { id: nextId(), createdAt: nowIso(), versionNo: v.versionNo, input: inp, ...r, savedToCustomerId: customerId };
    db.screenings.push(res);
    db.consents.push({ id: nextId(), customerId, scope: 'A', docVersion: inp.consentA.docVersion, agreedAt: nowIso(), withdrawnAt: null, applicationCode: null });
    (res as ScreeningResponse & { consentId?: number }).consentId = db.consents[db.consents.length - 1].id;
    persist();
    return HttpResponse.json(res, { status: 201 });
  }),
  http.get(`${API}/screenings/:id`, ({ params }) => {
    const s = db.screenings.find(x => x.id === Number(params.id));
    return s ? HttpResponse.json(s) : err(404, 'Không tìm thấy lượt sàng lọc.');
  }),
  // 2.3 lưu kết quả
  http.post(`${API}/screenings/:id/save`, ({ params, request }) => {
    const u = currentUser(request);
    if (!u || u.role !== 'CUSTOMER') return err(401, 'Cần đăng nhập tài khoản khách hàng.');
    const s = db.screenings.find(x => x.id === Number(params.id));
    if (!s) return err(404, 'Không tìm thấy lượt sàng lọc.');
    s.savedToCustomerId = u.id;
    const cid = (s as ScreeningResponse & { consentId?: number }).consentId;
    const c = db.consents.find(x => x.id === cid);
    if (c) c.customerId = u.id;
    log(u, 'Lưu kết quả sàng lọc', `Lượt #${s.id}`);
    persist();
    return HttpResponse.json(s);
  }),
  http.get(`${API}/me/screenings`, ({ request }) => {
    const u = currentUser(request);
    if (!u) return err(401, 'Chưa đăng nhập.');
    return HttpResponse.json(db.screenings.filter(s => s.savedToCustomerId === u.id).reverse());
  }),

  // QT02: 3.1 mô phỏng
  http.post(`${API}/simulations`, async ({ request }) => {
    await delay(150);
    const b = (await request.json()) as SimulationRequest;
    const s = db.screenings.find(x => x.id === b.screeningId);
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
    const s = db.screenings.find(x => x.id === b.screeningId);
    if (!s) return err(404, 'Không tìm thấy lượt sàng lọc.');
    const v = activeVersion();
    const sim = simulate(s.input, paramsOf(v.id), weightsOf(v.id), b, db.crossSells);
    if (!sim.ok) return err(422, 'Phương án chưa đạt: ' + sim.issues.join(' '));
    s.savedToCustomerId = u.id;
    const plan: LoanPlan & { customerId: number } = {
      id: nextId(), customerId: u.id, screeningId: s.id, productCode: sim.productCode, productName: sim.productName, amount: sim.amount,
      termMonths: sim.termMonths, refRate: sim.rate, monthlyPayment: sim.firstPayment, totalInterest: sim.totalInterest,
      dtiAfter: sim.dtiAfter, score: sim.score, status: 'DRAFT',
      crossSells: db.crossSells.filter(c => b.crossSellIds.includes(c.id)).map(c => ({ id: c.id, name: c.name, extraCost: c.estCost,
        benefit: `Giảm ${c.rateDiscount}%/năm lãi suất tham chiếu, +${c.scoreBonus} điểm ưu tiên` })),
      createdAt: nowIso(), checklist: checklist(sim.productCode),
    };
    db.plans.push(plan);
    log(u, 'Lưu phương án vay', `Phương án #${plan.id}`);
    persist();
    return HttpResponse.json(plan, { status: 201 });
  }),
  http.get(`${API}/plans/:id`, ({ params, request }) => {
    const u = currentUser(request);
    const p = db.plans.find(x => x.id === Number(params.id));
    if (!p || !u || p.customerId !== u.id) return err(404, 'Không tìm thấy phương án.');
    const app = db.applications.find(a => a.plan.id === p.id && a.status !== 'CANCELLED');
    return HttpResponse.json({ ...p, applicationId: app?.id ?? null });
  }),
  http.post(`${API}/plans/:id/confirm`, ({ params, request }) => {
    const u = currentUser(request);
    const p = db.plans.find(x => x.id === Number(params.id));
    if (!p || !u || p.customerId !== u.id) return err(404, 'Không tìm thấy phương án.');
    p.status = 'CONFIRMED';
    log(u, 'Xác nhận phương án vay', `Phương án #${p.id}`);
    persist();
    return HttpResponse.json(p);
  }),
  http.get(`${API}/me/plans`, ({ request }) => {
    const u = currentUser(request);
    if (!u) return err(401, 'Chưa đăng nhập.');
    return HttpResponse.json(db.plans.filter(p => p.customerId === u.id).reverse());
  }),
];
