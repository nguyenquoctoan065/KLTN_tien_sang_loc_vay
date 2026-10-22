import { http, HttpResponse, delay } from 'msw';
import type { AccuracyResponse, ApplicationStatus, ProductCode, ReportResponse, StatsResponse } from '../../api/types';
import { API, db, err, currentUser, nowIso, activeVersion } from '../ctx';

const isAdmin = (r: Request) => currentUser(r)?.role === 'ADMIN';

function stats(from: string, to: string, branchId: number | null): StatsResponse {
  const inRange = (t: string) => t.slice(0, 10) >= from && t.slice(0, 10) <= to;
  const apps = db.applications.filter(a => inRange(a.createdAt) && (!branchId || a.branchId === branchId));
  const reached = (s: ApplicationStatus) => apps.filter(a => a.history.some(h => h.status === s)).length;
  const statuses: ApplicationStatus[] = ['DRAFTING', 'WAITING_VERIFY', 'NEED_MORE', 'VERIFIED', 'APPROVED', 'DISBURSED', 'REJECTED', 'CANCELLED', 'EXPIRED'];
  const months: string[] = [];
  const d = new Date(from + 'T00:00:00');
  while (d.toISOString().slice(0, 7) <= to.slice(0, 7)) { months.push(d.toISOString().slice(0, 7)); d.setMonth(d.getMonth() + 1); }
  const evMonth = (a: (typeof apps)[number], s: ApplicationStatus) => a.history.find(h => h.status === s)?.at.slice(0, 7);
  const done = apps.filter(a => a.status === 'DISBURSED' || a.status === 'REJECTED');
  const days = done.map(a => {
    const sub = a.history.find(h => h.status === 'WAITING_VERIFY')?.at ?? a.createdAt;
    const end = a.history[a.history.length - 1].at;
    return (Date.parse(end) - Date.parse(sub)) / 864e5;
  });
  const scrCount = db.past.screenings.filter(inRange).length + db.screenings.filter(s => inRange(s.createdAt)).length;
  const planCount = db.past.plans.filter(inRange).length + db.plans.filter(p => inRange(p.createdAt)).length;
  return {
    from, to,
    totals: {
      screenings: branchId ? Math.round(scrCount * apps.length / Math.max(1, db.applications.filter(a => inRange(a.createdAt)).length)) : scrCount,
      plans: branchId ? Math.round(planCount * apps.length / Math.max(1, db.applications.filter(a => inRange(a.createdAt)).length)) : planCount,
      applications: apps.length, submitted: reached('WAITING_VERIFY'), verified: reached('VERIFIED'), approved: reached('APPROVED'),
      disbursed: reached('DISBURSED'), rejected: reached('REJECTED'), cancelled: reached('CANCELLED'), expired: reached('EXPIRED'),
    },
    byStatus: statuses.map(s => ({ status: s, count: apps.filter(a => a.status === s).length })),
    byProduct: (['TD', 'TC'] as ProductCode[]).map(c => ({ productCode: c, count: apps.filter(a => a.plan.productCode === c).length,
      amount: apps.filter(a => a.plan.productCode === c && a.status === 'DISBURSED').reduce((s, a) => s + (a.approval?.disbursedAmount ?? 0), 0) })),
    byBranch: db.branches.filter(b => !branchId || b.id === branchId).map(b => {
      const x = apps.filter(a => a.branchId === b.id);
      return { branchName: b.name, count: x.length, approved: x.filter(a => ['APPROVED', 'DISBURSED'].includes(a.status)).length, rejected: x.filter(a => a.status === 'REJECTED').length };
    }),
    byMonth: months.map(m => ({ month: m, created: apps.filter(a => a.createdAt.slice(0, 7) === m).length,
      disbursed: apps.filter(a => evMonth(a, 'DISBURSED') === m).length, rejected: apps.filter(a => evMonth(a, 'REJECTED') === m).length })),
    avgProcessingDays: days.length ? Math.round(days.reduce((s, x) => s + x, 0) / days.length * 10) / 10 : 0,
    disbursedAmount: apps.filter(a => a.status === 'DISBURSED').reduce((s, a) => s + (a.approval?.disbursedAmount ?? 0), 0),
  };
}

function accuracy(threshold: number, until?: string): AccuracyResponse {
  const closed = db.applications.filter(a => ['APPROVED', 'DISBURSED', 'REJECTED'].includes(a.status) && (!until || a.updatedAt.slice(0, 10) <= until));
  const actual = (a: (typeof closed)[number]) => a.status !== 'REJECTED';
  const m = { tp: 0, fp: 0, tn: 0, fn: 0 };
  for (const a of closed) {
    const pred = a.predictedScore >= threshold;
    if (pred && actual(a)) m.tp++; else if (pred) m.fp++; else if (actual(a)) m.fn++; else m.tn++;
  }
  const n = closed.length || 1;
  const bands: [string, number, number][] = [['< 45', 0, 45], ['45 – 55', 45, 55], ['55 – 65', 55, 65], ['65 – 75', 65, 75], ['75 – 85', 75, 85], ['≥ 85', 85, 101]];
  const r1 = (x: number) => Math.round(x * 1000) / 10;
  return {
    threshold, sample: closed.length, matrix: m,
    accuracy: r1((m.tp + m.tn) / n), precision: r1(m.tp / Math.max(1, m.tp + m.fp)), recall: r1(m.tp / Math.max(1, m.tp + m.fn)),
    byBand: bands.map(([band, lo, hi]) => {
      const x = closed.filter(a => a.predictedScore >= lo && a.predictedScore < hi);
      return { band, total: x.length, approved: x.filter(actual).length, rate: x.length ? r1(x.filter(actual).length / x.length) : 0 };
    }),
    byProduct: (['TD', 'TC'] as ProductCode[]).map(c => {
      const x = closed.filter(a => a.plan.productCode === c);
      const ok = x.filter(a => (a.predictedScore >= threshold) === actual(a)).length;
      return { productCode: c, sample: x.length, accuracy: x.length ? r1(ok / x.length) : 0 };
    }),
  };
}

export const reportHandlers = [
  // 8.1
  http.get(`${API}/admin/stats`, ({ request }) => {
    if (!isAdmin(request)) return err(403, 'Không có quyền.');
    const u = new URL(request.url);
    const to = u.searchParams.get('to') || nowIso().slice(0, 10);
    const from = u.searchParams.get('from') || new Date(Date.now() - 180 * 864e5).toISOString().slice(0, 10);
    const b = Number(u.searchParams.get('branchId')) || null;
    return HttpResponse.json(stats(from, to, b));
  }),
  // 8.2
  http.get(`${API}/admin/model-accuracy`, ({ request }) => {
    if (!isAdmin(request)) return err(403, 'Không có quyền.');
    const t = Number(new URL(request.url).searchParams.get('threshold')) || 55;
    return HttpResponse.json(accuracy(t));
  }),
  // 8.3 MB14
  http.get(`${API}/admin/reports/mb14`, async ({ request }) => {
    await delay(300);
    if (!isAdmin(request)) return err(403, 'Không có quyền.');
    const month = new URL(request.url).searchParams.get('month') || nowIso().slice(0, 7);
    const [y, mo] = month.split('-').map(Number);
    const from = `${month}-01`;
    const to = new Date(Date.UTC(y, mo, 0)).toISOString().slice(0, 10);
    const st = stats(from, to, null);
    const acc = accuracy(55, to);
    const params = db.params[activeVersion().id];
    const val = (k: string, c: ProductCode | null = null) => params.find(p => p.key === k && p.productCode === c)?.value ?? null;
    const proposals: ReportResponse['proposals'] = [];
    const lowBand = acc.byBand.find(b => b.band === '45 – 55');
    if (acc.recall < 75 && lowBand && lowBand.rate >= 40) {
      const cur = Number(val('DTI_MAX'));
      if (cur < 80) proposals.push({ id: 'dti-up', title: 'Nới ngưỡng DTI tối đa', detail: `Tỷ lệ bỏ sót hồ sơ được duyệt cao (độ nhạy ${acc.recall}%), nhóm điểm 45–55 vẫn được duyệt ${lowBand.rate}%. Đề xuất tăng DTI tối đa thêm 2 điểm phần trăm.`,
        paramKey: 'DTI_MAX', productCode: null, currentValue: String(cur), proposedValue: String(Math.min(80, cur + 2)) });
    }
    if (acc.precision < 75) {
      const cur = Number(val('DTI_MAX'));
      if (cur > 65) proposals.push({ id: 'dti-down', title: 'Siết ngưỡng DTI tối đa', detail: `Độ chính xác dương thấp (${acc.precision}%): nhiều hồ sơ hệ thống đánh giá đạt nhưng bị từ chối. Đề xuất giảm DTI tối đa 2 điểm phần trăm.`,
        paramKey: 'DTI_MAX', productCode: null, currentValue: String(cur), proposedValue: String(Math.max(65, cur - 2)) });
    }
    const incomeRejects = db.applications.filter(a => a.status === 'REJECTED' && /Thu nhập/.test(a.approval?.reason ?? '')).length;
    const allRejects = db.applications.filter(a => a.status === 'REJECTED').length;
    if (allRejects && incomeRejects / allRejects >= 0.3) {
      const cur = Number(val('LIVING_COST_BASE'));
      proposals.push({ id: 'living-up', title: 'Tăng chi phí sinh hoạt định mức', detail: `${incomeRejects}/${allRejects} hồ sơ bị từ chối vì thu nhập thực tế không đủ trả nợ. Đề xuất tăng chi phí sinh hoạt định mức 500.000 đ để hạn mức an toàn thận trọng hơn.`,
        paramKey: 'LIVING_COST_BASE', productCode: null, currentValue: String(cur), proposedValue: String(cur + 500_000) });
    }
    const tc = acc.byProduct.find(p => p.productCode === 'TC');
    if (tc && tc.sample >= 5 && tc.accuracy < acc.accuracy - 5)
      proposals.push({ id: 'tc-weight', title: 'Xem lại ma trận trọng số vay thấu chi', detail: `Độ chính xác của gói thấu chi (${tc.accuracy}%) thấp hơn mức chung (${acc.accuracy}%). Cân nhắc tăng trọng số “Ngân hàng nhận lương” và “CBNV ABBANK”.`,
        paramKey: null, productCode: 'TC', currentValue: null, proposedValue: null });
    if (!proposals.length) proposals.push({ id: 'keep', title: 'Giữ nguyên tham số', detail: 'Các chỉ số trong kỳ ổn định, chưa cần hiệu chỉnh.', paramKey: null, productCode: null, currentValue: null, proposedValue: null });
    const res: ReportResponse = { period: month, generatedAt: nowIso(), stats: st, accuracy: acc, proposals };
    return HttpResponse.json(res);
  }),
];
