/** Cách tính 8.1–8.3 (thống kê, độ chính xác mô hình, MB14) – hàm thuần, dùng cho handlers/reports.ts và be/tools/gen-report-fixtures.ts */
import type { AccuracyResponse, ApplicationStatus, ProductCode, ReportResponse, StatsResponse } from '../api/types';

/** Phần của hồ sơ mà báo cáo cần (AppRecord thỏa kiểu này) */
export interface ReportApp {
  createdAt: string;
  updatedAt: string;
  status: ApplicationStatus;
  branchId: number;
  predictedScore: number;
  plan: { productCode: ProductCode };
  history: { status: ApplicationStatus; at: string }[];
  approval: { disbursedAmount: number | null; reason: string | null } | null;
}

export interface ReportData {
  applications: ReportApp[];
  branches: { id: number; name: string }[];
  /** mốc thời gian các lượt sàng lọc, phương án */
  screenings: string[];
  plans: string[];
}

/** Số hồ sơ tối thiểu để một chỉ số (độ nhạy, độ chính xác dương) hoặc tập hồ sơ bị từ chối đủ tin cậy làm căn cứ đề xuất; thiếu thì trả null / không đề xuất */
export const MIN_SAMPLE = 10;

const STATUSES: ApplicationStatus[] = ['DRAFTING', 'WAITING_VERIFY', 'NEED_MORE', 'VERIFIED', 'APPROVED', 'DISBURSED', 'REJECTED', 'CANCELLED', 'EXPIRED'];
const PRODUCTS: ProductCode[] = ['TD', 'TC'];

/** Các tháng 'YYYY-MM' từ tháng của `from` đến tháng của `to` */
function monthsBetween(from: string, to: string): string[] {
  const out: string[] = [];
  let [y, m] = from.slice(0, 7).split('-').map(Number);
  for (let k = `${y}-${String(m).padStart(2, '0')}`; k <= to.slice(0, 7); k = `${y}-${String(m).padStart(2, '0')}`) {
    out.push(k);
    if (++m > 12) { m = 1; y++; }
  }
  return out;
}

export function statsOf(data: ReportData, from: string, to: string, branchId: number | null): StatsResponse {
  const inRange = (t: string) => t.slice(0, 10) >= from && t.slice(0, 10) <= to;
  const all = data.applications.filter(a => inRange(a.createdAt));
  const apps = all.filter(a => !branchId || a.branchId === branchId);
  const reached = (s: ApplicationStatus) => apps.filter(a => a.history.some(h => h.status === s)).length;
  const evMonth = (a: ReportApp, s: ApplicationStatus) => a.history.find(h => h.status === s)?.at.slice(0, 7);
  const done = apps.filter(a => a.status === 'DISBURSED' || a.status === 'REJECTED');
  const days = done.map(a => {
    const sub = a.history.find(h => h.status === 'WAITING_VERIFY')?.at ?? a.createdAt;
    const end = a.history[a.history.length - 1].at;
    return (Date.parse(end) - Date.parse(sub)) / 864e5;
  });
  const scrCount = data.screenings.filter(inRange).length;
  const planCount = data.plans.filter(inRange).length;
  const disbursed = (x: ReportApp[]) => x.filter(a => a.status === 'DISBURSED').reduce((s, a) => s + (a.approval?.disbursedAmount ?? 0), 0);
  return {
    from, to,
    totals: {
      // lượt sàng lọc, phương án không gắn chi nhánh: khi lọc chi nhánh ước lượng theo tỷ trọng hồ sơ
      screenings: branchId ? Math.round(scrCount * apps.length / Math.max(1, all.length)) : scrCount,
      plans: branchId ? Math.round(planCount * apps.length / Math.max(1, all.length)) : planCount,
      applications: apps.length, submitted: reached('WAITING_VERIFY'), verified: reached('VERIFIED'), approved: reached('APPROVED'),
      disbursed: reached('DISBURSED'), rejected: reached('REJECTED'), cancelled: reached('CANCELLED'), expired: reached('EXPIRED'),
    },
    byStatus: STATUSES.map(s => ({ status: s, count: apps.filter(a => a.status === s).length })),
    byProduct: PRODUCTS.map(c => ({ productCode: c, count: apps.filter(a => a.plan.productCode === c).length,
      amount: disbursed(apps.filter(a => a.plan.productCode === c)) })),
    byBranch: data.branches.filter(b => !branchId || b.id === branchId).map(b => {
      const x = apps.filter(a => a.branchId === b.id);
      return { branchName: b.name, count: x.length, approved: x.filter(a => ['APPROVED', 'DISBURSED'].includes(a.status)).length, rejected: x.filter(a => a.status === 'REJECTED').length };
    }),
    byMonth: monthsBetween(from, to).map(m => ({ month: m, created: apps.filter(a => a.createdAt.slice(0, 7) === m).length,
      disbursed: apps.filter(a => evMonth(a, 'DISBURSED') === m).length, rejected: apps.filter(a => evMonth(a, 'REJECTED') === m).length })),
    avgProcessingDays: days.length ? Math.round(days.reduce((s, x) => s + x, 0) / days.length * 10) / 10 : 0,
    disbursedAmount: disbursed(apps),
  };
}

/** Hồ sơ đã có kết quả ngân hàng, cập nhật đến hết ngày `until` (nếu có) */
const closedUntil = (apps: ReportApp[], until?: string) =>
  apps.filter(a => ['APPROVED', 'DISBURSED', 'REJECTED'].includes(a.status) && (!until || a.updatedAt.slice(0, 10) <= until));

export function accuracyOf(apps: ReportApp[], threshold: number, until?: string): AccuracyResponse {
  const closed = closedUntil(apps, until);
  const actual = (a: ReportApp) => a.status !== 'REJECTED';
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
    // null = chưa đủ dữ liệu: độ chính xác khi chưa có hồ sơ nào; độ nhạy khi < MIN_SAMPLE hồ sơ có kết quả; độ chính xác dương khi < MIN_SAMPLE hồ sơ được đánh giá đạt
    accuracy: closed.length ? r1((m.tp + m.tn) / n) : null,
    precision: m.tp + m.fp >= MIN_SAMPLE ? r1(m.tp / (m.tp + m.fp)) : null,
    recall: closed.length >= MIN_SAMPLE ? r1(m.tp / Math.max(1, m.tp + m.fn)) : null,
    byBand: bands.map(([band, lo, hi]) => {
      const x = closed.filter(a => a.predictedScore >= lo && a.predictedScore < hi);
      return { band, total: x.length, approved: x.filter(actual).length, rate: x.length ? r1(x.filter(actual).length / x.length) : 0 };
    }),
    byProduct: PRODUCTS.map(c => {
      const x = closed.filter(a => a.plan.productCode === c);
      const ok = x.filter(a => (a.predictedScore >= threshold) === actual(a)).length;
      return { productCode: c, sample: x.length, accuracy: x.length ? r1(ok / x.length) : 0 };
    }),
  };
}

/** MB14 của tháng `month` ('YYYY-MM'); `param(key)` đọc tham số chung của phiên bản đang áp dụng */
export function mb14Of(data: ReportData, month: string, param: (key: string) => string | null, generatedAt: string): ReportResponse {
  const [y, mo] = month.split('-').map(Number);
  const from = `${month}-01`;
  const to = new Date(Date.UTC(y, mo, 0)).toISOString().slice(0, 10);
  const st = statsOf(data, from, to, null);
  const acc = accuracyOf(data.applications, 55, to);
  const proposals: ReportResponse['proposals'] = [];
  const none = { paramKey: null, productCode: null, currentValue: null, proposedValue: null };
  if (acc.sample === 0) {
    // chưa có hồ sơ nào có kết quả đến cuối kỳ: không có căn cứ để đề xuất
    proposals.push({ id: 'no-data', title: 'Chưa đủ dữ liệu để đề xuất hiệu chỉnh', detail: 'Chưa có hồ sơ nào có kết quả ngân hàng đến cuối kỳ.', ...none });
    return { period: month, generatedAt, stats: st, accuracy: acc, proposals };
  }
  const { recall, precision } = acc;
  const lowBand = acc.byBand.find(b => b.band === '45 – 55');
  const bothLow = recall !== null && precision !== null && recall < 75 && precision < 75;
  if (bothLow) {
    // bỏ sót nhiều và đánh giá đạt nhầm nhiều cùng lúc: đổi ngưỡng DTI không giải quyết được, lỗi nằm ở cách chấm điểm
    proposals.push({ id: 'weights-review', title: 'Xem lại ma trận trọng số', detail: `Độ nhạy (${recall}%) và độ chính xác dương (${precision}%) cùng thấp hơn 75%: đổi ngưỡng DTI chưa giải quyết được. Cân nhắc xem lại ma trận trọng số điểm tương thích của các gói.`, ...none });
  } else {
    if (recall !== null && recall < 75 && lowBand && lowBand.rate >= 40) {
      const cur = Number(param('DTI_MAX'));
      if (cur < 80) proposals.push({ id: 'dti-up', title: 'Nới ngưỡng DTI tối đa', detail: `Tỷ lệ bỏ sót hồ sơ được duyệt cao (độ nhạy ${recall}%), nhóm điểm 45–55 vẫn được duyệt ${lowBand.rate}%. Đề xuất tăng DTI tối đa thêm 2 điểm phần trăm.`,
        paramKey: 'DTI_MAX', productCode: null, currentValue: String(cur), proposedValue: String(Math.min(80, cur + 2)) });
    }
    if (precision !== null && precision < 75) {
      const cur = Number(param('DTI_MAX'));
      if (cur > 65) proposals.push({ id: 'dti-down', title: 'Siết ngưỡng DTI tối đa', detail: `Độ chính xác dương thấp (${precision}%): nhiều hồ sơ hệ thống đánh giá đạt nhưng bị từ chối. Đề xuất giảm DTI tối đa 2 điểm phần trăm.`,
        paramKey: 'DTI_MAX', productCode: null, currentValue: String(cur), proposedValue: String(Math.max(65, cur - 2)) });
    }
  }
  // lũy kế đến cuối kỳ, cùng phạm vi với phần độ chính xác
  const rejects = closedUntil(data.applications, to).filter(a => a.status === 'REJECTED');
  const incomeRejects = rejects.filter(a => /Thu nhập/.test(a.approval?.reason ?? '')).length;
  if (rejects.length >= MIN_SAMPLE && incomeRejects / rejects.length >= 0.3) {
    const cur = Number(param('LIVING_COST_BASE'));
    proposals.push({ id: 'living-up', title: 'Tăng chi phí sinh hoạt định mức', detail: `${incomeRejects}/${rejects.length} hồ sơ bị từ chối vì thu nhập thực tế không đủ trả nợ. Đề xuất tăng chi phí sinh hoạt định mức 500.000 đ để hạn mức an toàn thận trọng hơn.`,
      paramKey: 'LIVING_COST_BASE', productCode: null, currentValue: String(cur), proposedValue: String(cur + 500_000) });
  }
  const tc = acc.byProduct.find(p => p.productCode === 'TC');
  // sample > 0 ở đây nên accuracy khác null; đã có đề xuất xem lại trọng số chung thì không nhắc lại riêng gói thấu chi
  if (!bothLow && tc && tc.sample >= 5 && tc.accuracy < (acc.accuracy ?? 0) - 5)
    proposals.push({ id: 'tc-weight', title: 'Xem lại ma trận trọng số vay thấu chi', detail: `Độ chính xác của gói thấu chi (${tc.accuracy}%) thấp hơn mức chung (${acc.accuracy}%). Cân nhắc tăng trọng số “Ngân hàng nhận lương” và “CBNV ABBANK”.`,
      paramKey: null, productCode: 'TC', currentValue: null, proposedValue: null });
  if (!proposals.length && acc.sample < MIN_SAMPLE)
    proposals.push({ id: 'keep-low-data', title: `Giữ nguyên tham số (dữ liệu còn ít: ${acc.sample} hồ sơ có kết quả, cần thêm để đánh giá chính xác)`,
      detail: 'Chưa đủ hồ sơ có kết quả để kết luận độ nhạy, độ chính xác dương; giữ nguyên tham số cho đến khi có thêm dữ liệu.', ...none });
  if (!proposals.length) proposals.push({ id: 'keep', title: 'Giữ nguyên tham số', detail: 'Các chỉ số trong kỳ ổn định, chưa cần hiệu chỉnh.', ...none });
  return { period: month, generatedAt, stats: st, accuracy: acc, proposals };
}
