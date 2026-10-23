import { http, HttpResponse, delay } from 'msw';
import { API, db, err, currentUser, nowIso, activeVersion } from '../ctx';
import { accuracyOf, mb14Of, statsOf, type ReportData } from '../reportCalc';

const isAdmin = (r: Request) => currentUser(r)?.role === 'ADMIN';

const data = (): ReportData => ({
  applications: db.applications,
  branches: db.branches,
  screenings: [...db.past.screenings, ...db.screenings.map(s => s.createdAt)],
  plans: [...db.past.plans, ...db.plans.map(p => p.createdAt)],
});

export const reportHandlers = [
  // 8.1
  http.get(`${API}/admin/stats`, ({ request }) => {
    if (!isAdmin(request)) return err(403, 'Không có quyền.');
    const u = new URL(request.url);
    const to = u.searchParams.get('to') || nowIso().slice(0, 10);
    const from = u.searchParams.get('from') || new Date(Date.now() - 180 * 864e5).toISOString().slice(0, 10);
    const b = Number(u.searchParams.get('branchId')) || null;
    return HttpResponse.json(statsOf(data(), from, to, b));
  }),
  // 8.2
  http.get(`${API}/admin/model-accuracy`, ({ request }) => {
    if (!isAdmin(request)) return err(403, 'Không có quyền.');
    const t = Number(new URL(request.url).searchParams.get('threshold')) || 55;
    return HttpResponse.json(accuracyOf(db.applications, t));
  }),
  // 8.3 MB14
  http.get(`${API}/admin/reports/mb14`, async ({ request }) => {
    await delay(300);
    if (!isAdmin(request)) return err(403, 'Không có quyền.');
    const month = new URL(request.url).searchParams.get('month') || nowIso().slice(0, 7);
    const params = db.params[activeVersion().id];
    const param = (k: string) => params.find(p => p.key === k && p.productCode === null)?.value ?? null;
    return HttpResponse.json(mb14Of(data(), month, param, nowIso()));
  }),
];
