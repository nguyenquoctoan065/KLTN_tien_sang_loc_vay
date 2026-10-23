/**
 * Sinh fixtures đối chứng cho ReportApiTest từ chính fe/src/mocks/reportCalc.ts (cách tính 8.1–8.3 của mock).
 * Chạy ở thư mục gốc dự án:  npx -y tsx@4.19.2 be/tools/gen-report-fixtures.ts
 * Mỗi lần sửa reportCalc.ts phải chạy lại và commit be/src/test/resources/report-fixtures/report-cases.json.
 *
 * Mọi mốc thời gian đặt trong 09:00–18:00 giờ Việt Nam nên ngày UTC (mock cắt chuỗi ISO) trùng ngày giờ VN (backend).
 */
process.env.TZ = 'Asia/Ho_Chi_Minh';

import { mkdirSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import type { ApplicationStatus, ProductCode } from '../../fe/src/api/types';
import { accuracyOf, mb14Of, statsOf, type ReportApp, type ReportData } from '../../fe/src/mocks/reportCalc';

const OUT = join(dirname(fileURLToPath(import.meta.url)), '../src/test/resources/report-fixtures');

// Chi nhánh và tham số giống seed V2 (phiên bản 2026.10 đang áp dụng trong DB test)
const BRANCHES = [{ id: 1, name: 'Chi nhánh Hà Nội' }, { id: 2, name: 'Chi nhánh Hồ Chí Minh' }, { id: 3, name: 'Chi nhánh Đà Nẵng' }];
const PARAMS: Record<string, string> = { DTI_MAX: '70', LIVING_COST_BASE: '4000000' };

const CIC = 'Lịch sử tín dụng (CIC) có nợ cần chú ý';
const INCOME = 'Thu nhập thực tế không đủ đáp ứng nghĩa vụ trả nợ';
const ROOM = 'Vượt room tín dụng của chi nhánh trong kỳ';

/** Thời điểm `date` + `days` ngày, lúc `hour` giờ VN (ISO UTC như mock) */
function at(date: string, days: number, hour: number, minute = 0): string {
  const d = new Date(`${date}T00:00:00+07:00`);
  d.setDate(d.getDate() + days);
  d.setHours(hour, minute, 0, 0);
  return d.toISOString();
}

/** Mốc lịch sử theo trạng thái cuối, giống trình tự của historical() trong db.ts; `close` = số ngày từ lúc tạo tới kết quả cuối */
function history(created: string, status: ApplicationStatus, close: number): { status: ApplicationStatus; at: string }[] {
  const h: { status: ApplicationStatus; at: string }[] = [{ status: 'DRAFTING', at: at(created, 0, 10) }];
  if (status === 'DRAFTING') return h;
  if (status === 'CANCELLED') return [...h, { status: 'CANCELLED', at: at(created, 2, 11) }];
  h.push({ status: 'WAITING_VERIFY', at: at(created, 1, 9, 30) });
  if (status === 'WAITING_VERIFY') return h;
  if (status === 'EXPIRED') return [...h, { status: 'EXPIRED', at: at(created, close, 9) }];
  if (status === 'NEED_MORE') return [...h, { status: 'NEED_MORE', at: at(created, 2, 14) }];
  h.push({ status: 'VERIFIED', at: at(created, 2, 15, 45) });
  if (status === 'VERIFIED') return h;
  if (status === 'REJECTED') return [...h, { status: 'REJECTED', at: at(created, close, 16, 20) }];
  h.push({ status: 'APPROVED', at: at(created, close - 1, 10, 15) });
  if (status === 'APPROVED') return h;
  return [...h, { status: 'DISBURSED', at: at(created, close, 17, 5) }];
}

interface FixtureApp extends ReportApp {
  key: string;
  /** thời điểm lượt sàng lọc gốc và phương án của hồ sơ */
  screeningAt: string;
  planAt: string;
}

// [ngày tạo, chi nhánh, gói, điểm, trạng thái, số ngày tới kết quả, lý do từ chối, số tiền giải ngân]
// Kết quả luôn rơi trong cùng tháng với ngày tạo. Chỉ số lũy kế đến cuối mỗi tháng (ngưỡng 55) tạo ra các tình huống MB14:
//   05: chưa có hồ sơ nào có kết quả → "Chưa đủ dữ liệu để đề xuất hiệu chỉnh"
//   06: 6 hồ sơ có kết quả (< 10) → độ nhạy, độ chính xác dương chưa đủ dữ liệu, không đề xuất → "Giữ nguyên tham số (dữ liệu còn ít…)"
//   07: 13 hồ sơ, độ nhạy thấp, độ chính xác dương chưa đủ mẫu (< 10 hồ sơ được đánh giá đạt) → chỉ nới DTI (+ xem lại trọng số gói TC)
//   08: độ nhạy đạt, độ chính xác dương thấp → chỉ siết DTI (+ xem lại trọng số gói TC); 9 hồ sơ bị từ chối với 33% vì thu nhập → KHÔNG tăng chi phí sinh hoạt (chưa đủ 10)
//   09: độ nhạy và độ chính xác dương cùng thấp → chỉ xem lại ma trận trọng số (+ tăng chi phí sinh hoạt: đúng 10 hồ sơ bị từ chối, 4 vì thu nhập)
//   10: cả hai đạt, gói TC cũng đủ chính xác → giữ nguyên
type Row = [string, number, ProductCode, number, ApplicationStatus, number, string | null, number | null];
const ROWS: Row[] = [
  // tháng 5: chưa có kết quả nào
  ['2026-05-04', 1, 'TD', 70, 'CANCELLED', 0, null, null],
  ['2026-05-12', 2, 'TC', 60, 'DRAFTING', 0, null, null],
  ['2026-05-19', 3, 'TD', 48, 'EXPIRED', 31, null, null],
  // tháng 6: 6 hồ sơ có kết quả
  ['2026-06-02', 1, 'TD', 80, 'DISBURSED', 6, null, 120_000_000],
  ['2026-06-05', 2, 'TD', 85, 'DISBURSED', 5, null, 200_000_000],
  ['2026-06-08', 3, 'TD', 40, 'REJECTED', 4, CIC, null],
  ['2026-06-11', 1, 'TC', 35, 'REJECTED', 3, CIC, null],
  ['2026-06-15', 2, 'TD', 62, 'DISBURSED', 7, null, 80_000_000],
  ['2026-06-20', 3, 'TC', 58, 'REJECTED', 3, ROOM, null],
  ['2026-06-24', 1, 'TC', 70, 'EXPIRED', 31, null, null],
  // tháng 7: thêm 7 hồ sơ có kết quả, nhiều hồ sơ điểm 47–53 vẫn được duyệt (bỏ sót)
  ['2026-07-03', 1, 'TD', 75, 'DISBURSED', 6, null, 150_000_000],
  ['2026-07-06', 2, 'TC', 70, 'DISBURSED', 5, null, 40_000_000],
  ['2026-07-09', 3, 'TD', 47, 'DISBURSED', 6, null, 60_000_000],
  ['2026-07-12', 1, 'TD', 50, 'DISBURSED', 7, null, 90_000_000],
  ['2026-07-15', 2, 'TC', 52, 'DISBURSED', 5, null, 30_000_000],
  ['2026-07-18', 3, 'TC', 53, 'DISBURSED', 6, null, 25_000_000],
  ['2026-07-21', 1, 'TD', 48, 'REJECTED', 4, CIC, null],
  ['2026-07-27', 2, 'TD', 70, 'EXPIRED', 31, null, null],
  // tháng 8: nhiều hồ sơ được duyệt đúng (độ nhạy lên) và 5 hồ sơ điểm cao bị từ chối (độ chính xác dương xuống); lũy kế 9 hồ sơ bị từ chối, 3 vì thu nhập (≥ 30%) nhưng chưa đủ 10
  ['2026-08-02', 1, 'TD', 90, 'DISBURSED', 5, null, 300_000_000],
  ['2026-08-04', 2, 'TD', 82, 'DISBURSED', 6, null, 210_000_000],
  ['2026-08-06', 3, 'TD', 78, 'DISBURSED', 7, null, 180_000_000],
  ['2026-08-08', 1, 'TD', 66, 'DISBURSED', 6, null, 100_000_000],
  ['2026-08-10', 2, 'TC', 72, 'DISBURSED', 5, null, 50_000_000],
  ['2026-08-12', 3, 'TD', 60, 'DISBURSED', 8, null, 70_000_000],
  ['2026-08-14', 1, 'TD', 68, 'DISBURSED', 6, null, 110_000_000],
  ['2026-08-16', 2, 'TD', 88, 'DISBURSED', 7, null, 250_000_000],
  ['2026-08-03', 3, 'TD', 58, 'REJECTED', 4, INCOME, null],
  ['2026-08-05', 1, 'TD', 60, 'REJECTED', 5, INCOME, null],
  ['2026-08-07', 2, 'TD', 65, 'REJECTED', 4, CIC, null],
  ['2026-08-09', 3, 'TD', 56, 'REJECTED', 6, INCOME, null],
  ['2026-08-11', 1, 'TD', 63, 'REJECTED', 5, ROOM, null],
  ['2026-08-20', 2, 'TC', 58, 'CANCELLED', 0, null, null],
  // tháng 9: thêm hồ sơ điểm thấp được duyệt (độ nhạy xuống) và 1 hồ sơ điểm thấp bị từ chối vì thu nhập (đủ 10 hồ sơ bị từ chối)
  ['2026-09-02', 2, 'TC', 50, 'DISBURSED', 5, null, 45_000_000],
  ['2026-09-03', 3, 'TD', 52, 'DISBURSED', 8, null, 95_000_000],
  ['2026-09-08', 2, 'TD', 30, 'REJECTED', 4, INCOME, null],
  ['2026-09-18', 2, 'TD', 90, 'NEED_MORE', 0, null, null],
  // tháng 10: nhiều hồ sơ điểm cao được duyệt, thêm hồ sơ bị từ chối vì lý do khác; còn các hồ sơ đang mở
  ['2026-10-01', 1, 'TD', 92, 'DISBURSED', 5, null, 320_000_000],
  ['2026-10-01', 2, 'TD', 86, 'DISBURSED', 4, null, 260_000_000],
  ['2026-10-02', 3, 'TD', 79, 'DISBURSED', 5, null, 140_000_000],
  ['2026-10-02', 1, 'TD', 74, 'DISBURSED', 4, null, 130_000_000],
  ['2026-10-03', 2, 'TD', 81, 'DISBURSED', 5, null, 220_000_000],
  ['2026-10-03', 3, 'TC', 76, 'DISBURSED', 3, null, 35_000_000],
  ['2026-10-04', 1, 'TD', 69, 'DISBURSED', 4, null, 105_000_000],
  ['2026-10-04', 2, 'TD', 91, 'DISBURSED', 4, null, 280_000_000],
  ['2026-10-05', 3, 'TD', 84, 'DISBURSED', 5, null, 190_000_000],
  ['2026-10-05', 1, 'TD', 77, 'DISBURSED', 4, null, 160_000_000],
  ['2026-10-06', 2, 'TD', 73, 'DISBURSED', 5, null, 120_000_000],
  ['2026-10-06', 3, 'TD', 67, 'DISBURSED', 4, null, 90_000_000],
  ['2026-10-07', 1, 'TC', 71, 'DISBURSED', 3, null, 28_000_000],
  ['2026-10-07', 2, 'TD', 89, 'DISBURSED', 4, null, 240_000_000],
  ['2026-10-02', 2, 'TC', 64, 'DISBURSED', 3, null, 20_000_000],
  ['2026-10-02', 3, 'TC', 70, 'DISBURSED', 3, null, 32_000_000],
  ['2026-10-03', 1, 'TC', 78, 'DISBURSED', 3, null, 26_000_000],
  ['2026-10-03', 2, 'TC', 82, 'DISBURSED', 3, null, 41_000_000],
  ['2026-10-04', 3, 'TC', 66, 'DISBURSED', 3, null, 18_000_000],
  ['2026-10-04', 1, 'TC', 74, 'DISBURSED', 3, null, 33_000_000],
  ['2026-10-05', 2, 'TC', 88, 'DISBURSED', 3, null, 45_000_000],
  ['2026-10-05', 3, 'TC', 69, 'DISBURSED', 3, null, 22_000_000],
  ['2026-10-06', 1, 'TC', 61, 'DISBURSED', 3, null, 15_000_000],
  ['2026-10-06', 2, 'TC', 80, 'DISBURSED', 3, null, 38_000_000],
  ['2026-10-08', 3, 'TD', 33, 'REJECTED', 2, CIC, null],
  ['2026-10-08', 1, 'TC', 36, 'REJECTED', 2, ROOM, null],
  ['2026-10-09', 2, 'TD', 41, 'REJECTED', 3, CIC, null],
  ['2026-10-09', 3, 'TD', 37, 'REJECTED', 2, ROOM, null],
  ['2026-10-12', 1, 'TD', 68, 'WAITING_VERIFY', 0, null, null],
  ['2026-10-13', 2, 'TD', 57, 'DRAFTING', 0, null, null],
  ['2026-10-14', 3, 'TC', 72, 'VERIFIED', 0, null, null],
  ['2026-10-16', 3, 'TD', 95, 'APPROVED', 4, null, null],
  ['2026-10-20', 1, 'TC', 49, 'DRAFTING', 0, null, null],
];

const apps: FixtureApp[] = ROWS.map(([created, branchId, code, score, status, close, reason, amount], i) => {
  const h = history(created, status, close);
  const decided = ['APPROVED', 'DISBURSED', 'REJECTED'].includes(status);
  return {
    key: `HT${String(i + 1).padStart(4, '0')}`,
    createdAt: h[0].at, updatedAt: h[h.length - 1].at, status, branchId, predictedScore: score,
    plan: { productCode: code }, history: h,
    approval: decided ? { disbursedAmount: status === 'DISBURSED' ? amount : null, reason } : null,
    screeningAt: at(created, -1, 15), planAt: at(created, 0, 9, 15),
  };
});

// Lượt sàng lọc, phương án không thành hồ sơ (ẩn danh / nháp)
const extraScreenings = ['2026-05-02', '2026-06-30', '2026-07-30', '2026-08-31', '2026-09-01', '2026-09-20', '2026-10-01', '2026-10-21']
  .map((d, i) => at(d, 0, 9 + i));
const extraPlans = ['2026-06-30', '2026-09-20', '2026-10-21'].map(d => at(d, 0, 17));

const data: ReportData = {
  applications: apps, branches: BRANCHES,
  screenings: [...apps.map(a => a.screeningAt), ...extraScreenings],
  plans: [...apps.map(a => a.planAt), ...extraPlans],
};
const param = (k: string) => PARAMS[k] ?? null;
const GEN = '2026-10-23T03:00:00.000Z';

/** Số hồ sơ bị từ chối (lũy kế đến hết `month`) và số trong đó vì thu nhập – để khẳng định ca biên 9 và 10 hồ sơ */
function rejectsUntil(month: string): { rejects: number; income: number } {
  const end = new Date(Date.UTC(Number(month.slice(0, 4)), Number(month.slice(5)), 0)).toISOString().slice(0, 10);
  const r = apps.filter(a => a.status === 'REJECTED' && a.updatedAt.slice(0, 10) <= end);
  return { rejects: r.length, income: r.filter(a => /Thu nhập/.test(a.approval?.reason ?? '')).length };
}

const MB14_MONTHS = ['2026-05', '2026-06', '2026-07', '2026-08', '2026-09', '2026-10'];

const cases: { name: string; path: string; expected: unknown }[] = [
  { name: 'Thống kê toàn bộ', path: '/admin/stats?from=2026-05-01&to=2026-10-31', expected: statsOf(data, '2026-05-01', '2026-10-31', null) },
  { name: 'Thống kê một chi nhánh', path: '/admin/stats?from=2026-05-01&to=2026-10-31&branchId=2', expected: statsOf(data, '2026-05-01', '2026-10-31', 2) },
  { name: 'Thống kê khoảng hẹp, chi nhánh 3', path: '/admin/stats?from=2026-08-10&to=2026-09-15&branchId=3', expected: statsOf(data, '2026-08-10', '2026-09-15', 3) },
  { name: 'Thống kê khoảng hẹp', path: '/admin/stats?from=2026-08-28&to=2026-09-10', expected: statsOf(data, '2026-08-28', '2026-09-10', null) },
  { name: 'Thống kê khoảng trống', path: '/admin/stats?from=2026-01-01&to=2026-02-15', expected: statsOf(data, '2026-01-01', '2026-02-15', null) },
  { name: 'Độ chính xác ngưỡng mặc định', path: '/admin/model-accuracy', expected: accuracyOf(apps, 55) },
  { name: 'Độ chính xác ngưỡng không phải số', path: '/admin/model-accuracy?threshold=abc', expected: accuracyOf(apps, Number('abc') || 55) },
  { name: 'Độ chính xác ngưỡng 60', path: '/admin/model-accuracy?threshold=60', expected: accuracyOf(apps, 60) },
  { name: 'Độ chính xác ngưỡng 45', path: '/admin/model-accuracy?threshold=45', expected: accuracyOf(apps, 45) },
  { name: 'Độ chính xác ngưỡng 52.5', path: '/admin/model-accuracy?threshold=52.5', expected: accuracyOf(apps, 52.5) },
  // ngưỡng rất cao: ít hồ sơ được đánh giá đạt → độ chính xác dương chưa đủ dữ liệu (null)
  { name: 'Độ chính xác ngưỡng 88', path: '/admin/model-accuracy?threshold=88', expected: accuracyOf(apps, 88) },
  ...MB14_MONTHS.map(m => ({ name: `MB14 tháng ${m}`, path: `/admin/reports/mb14?month=${m}`, expected: mb14Of(data, m, param, GEN) })),
];

// Mỗi tình huống MB14 phải xuất hiện đúng như thiết kế ở đầu ROWS – sai thì dừng, tránh sinh fixtures không còn phủ ca nào
const mb14 = (m: string) => cases.find(c => c.name === `MB14 tháng ${m}`)!.expected as ReturnType<typeof mb14Of>;
const ids = (m: string) => mb14(m).proposals.map(p => p.id).join(',');
const WANT: Record<string, string> = {
  '2026-05': 'no-data', '2026-06': 'keep-low-data', '2026-07': 'dti-up,tc-weight', '2026-08': 'dti-down,tc-weight', '2026-09': 'weights-review,living-up', '2026-10': 'keep',
};
const summary = (m: string) => `${m}: mẫu ${mb14(m).accuracy.sample}, độ nhạy ${mb14(m).accuracy.recall}, độ chính xác dương ${mb14(m).accuracy.precision} → ${ids(m)}`;
const wrong = Object.entries(WANT).filter(([m, want]) => ids(m) !== want);
if (wrong.length) {
  console.error(MB14_MONTHS.map(summary).join('\n'));
  throw new Error('MB14 lệch thiết kế: ' + wrong.map(([m, want]) => `${m} mong đợi ${want}`).join('; '));
}
const r8 = rejectsUntil('2026-08'), r9 = rejectsUntil('2026-09');
if (r8.rejects !== 9 || r8.income / r8.rejects < 0.3) throw new Error(`Tháng 8 phải có 9 hồ sơ bị từ chối với ≥ 30% vì thu nhập, nhận ${JSON.stringify(r8)}`);
if (r9.rejects !== 10 || r9.income / r9.rejects < 0.3) throw new Error(`Tháng 9 phải có 10 hồ sơ bị từ chối với ≥ 30% vì thu nhập, nhận ${JSON.stringify(r9)}`);
const high = cases.find(c => c.name === 'Độ chính xác ngưỡng 88')!.expected as ReturnType<typeof accuracyOf>;
if (high.precision !== null || high.recall === null) throw new Error('Ca ngưỡng 88 phải có độ chính xác dương = null và độ nhạy có giá trị');

mkdirSync(OUT, { recursive: true });
const dataset = {
  applications: apps.map(a => ({ key: a.key, branchId: a.branchId, productCode: a.plan.productCode, score: a.predictedScore, status: a.status,
    createdAt: a.createdAt, updatedAt: a.updatedAt, screeningAt: a.screeningAt, planAt: a.planAt, history: a.history,
    approval: a.approval ? { resultType: a.status === 'REJECTED' ? 'REJECTED' : 'APPROVED', recordedAt: a.history[a.history.length - 1].at, ...a.approval } : null })),
  extraScreenings, extraPlans,
};
writeFileSync(join(OUT, 'report-cases.json'), JSON.stringify({ dataset, cases }, null, 2) + '\n', 'utf8');
console.log(`Đã ghi ${cases.length} ca, ${apps.length} hồ sơ.\n` + MB14_MONTHS.map(summary).join('\n'));
