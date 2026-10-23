/**
 * Sinh fixtures đối chứng cho EngineFixtureTest từ chính fe/src/mocks/engine.ts (docs/be-huong-dan.md mục 5.5).
 * Chạy ở thư mục gốc dự án:  npx -y tsx@4.19.2 be/tools/gen-engine-fixtures.ts
 * Mỗi lần sửa engine.ts phải chạy lại và commit các file trong be/src/test/resources/engine-fixtures/.
 */
process.env.TZ = 'Asia/Ho_Chi_Minh';

import { mkdirSync, readdirSync, rmSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import type { CrossSellProduct, PolicyParam, ProductCode, ScoringWeight, ScreeningRequest, SimulationRequest } from '../../fe/src/api/types';
import { exclusion, maxSafeAmount, screen, simulate, toParamSet, toWeightSet } from '../../fe/src/mocks/engine';

// "Hôm nay" cố định để kết quả lặp lại được; Java nhận cùng ngày qua trường `today`.
const TODAY = '2026-10-23';
const FIXED = Date.parse(`${TODAY}T10:00:00+07:00`);
const RealDate = Date;
class FixedDate extends RealDate {
  constructor(...a: unknown[]) {
    if (a.length === 0) super(FIXED);
    else super(...(a as [string]));
  }
  static now() { return FIXED; }
}
globalThis.Date = FixedDate as unknown as DateConstructor;

if (new Date().getTimezoneOffset() !== -420) throw new Error('Không đặt được múi giờ Asia/Ho_Chi_Minh (TZ).');
if ((500_000_000).toLocaleString('vi-VN') !== '500.000.000') throw new Error('Node thiếu ICU tiếng Việt (toLocaleString vi-VN).');

const OUT = join(dirname(fileURLToPath(import.meta.url)), '../src/test/resources/engine-fixtures');

// Tham số và trọng số giống V2__seed.sql / seedParams() trong fe/src/mocks/db.ts
const PARAM_ROWS: [ProductCode | null, string, string][] = [
  [null, 'DTI_MAX', '70'], [null, 'LIVING_COST_BASE', '4000000'], [null, 'LIVING_COST_PER_DEPENDENT', '2000000'],
  [null, 'RETIRE_AGE_MALE', '62'], [null, 'RETIRE_AGE_FEMALE', '60'], [null, 'ROOM_FACTOR', '100'], [null, 'MAX_RATE_DISCOUNT', '2'],
  ['TD', 'MIN_AGE', '22'], ['TD', 'INCOME_MULTIPLIER', '15'], ['TD', 'MAX_AMOUNT', '500000000'], ['TD', 'MIN_AMOUNT', '10000000'],
  ['TD', 'MAX_TERM', '60'], ['TD', 'MIN_TERM', '6'], ['TD', 'REF_RATE', '12.5'],
  ['TC', 'MIN_AGE', '19'], ['TC', 'MAX_AMOUNT', '200000000'], ['TC', 'MIN_AMOUNT', '10000000'],
  ['TC', 'MAX_TERM', '12'], ['TC', 'MIN_TERM', '12'], ['TC', 'REF_RATE', '18'],
];
const WEIGHT_ROWS: [ProductCode, string, number][] = [
  ['TD', 'DTI', 35], ['TD', 'AMOUNT_RATIO', 25], ['TD', 'SALARY_BANK', 15], ['TD', 'STAFF', 10], ['TD', 'TERM_TO_RETIRE', 15],
  ['TC', 'DTI', 30], ['TC', 'AMOUNT_RATIO', 20], ['TC', 'SALARY_BANK', 25], ['TC', 'STAFF', 20], ['TC', 'TERM_TO_RETIRE', 5],
];
const CROSS_SELLS: CrossSellProduct[] = [
  { id: 1, name: 'Bảo hiểm nhân thọ', productType: 'INSURANCE', estCost: 8_000_000, scoreBonus: 5, rateDiscount: 1.0, status: 'ACTIVE' },
  { id: 2, name: 'Thẻ tín dụng', productType: 'CARD', estCost: 0, scoreBonus: 3, rateDiscount: 0.5, status: 'ACTIVE' },
  { id: 3, name: 'Chứng chỉ tiền gửi', productType: 'DEPOSIT', estCost: 0, scoreBonus: 4, rateDiscount: 0.7, status: 'ACTIVE' },
  { id: 4, name: 'Tài khoản số đẹp', productType: 'ACCOUNT', estCost: 1_000_000, scoreBonus: 2, rateDiscount: 0.3, status: 'ACTIVE' },
  // Đã ngừng: không được gợi ý bán chéo nhưng vẫn tính nếu yêu cầu chọn (giống mock truyền toàn bộ db.crossSells)
  { id: 5, name: 'Gói tạm ngưng', productType: 'CARD', estCost: 500_000, scoreBonus: 1, rateDiscount: 1.5, status: 'INACTIVE' },
];

function params(over: Record<string, string> = {}): PolicyParam[] {
  return PARAM_ROWS.map(([c, k, v], i) => ({ id: i + 1, productCode: c, key: k, label: k, value: over[`${c ?? ''}:${k}`] ?? v, unit: '' }));
}
function weights(over: Partial<Record<string, number>> = {}): ScoringWeight[] {
  return WEIGHT_ROWS.map(([p, c, w], i) => ({ id: i + 1, productCode: p, criterion: c, label: c, weight: over[`${p}:${c}`] ?? w }));
}

const BASE: ScreeningRequest = {
  consentA: { agreed: true, docVersion: 'DK-1.0' }, nationality: 'Việt Nam', dob: '1992-05-15', gender: 'MALE', maritalStatus: 'MARRIED',
  dependents: 1, avgIncome: 20_000_000, salaryBank: 'Vietcombank', isAbbankStaff: false, creditHistory: 'ON_TIME', otherDebts: [],
  purpose: 'Mua xe máy, ô tô', desiredAmount: 150_000_000, desiredTerm: 36,
};

type Case = { name: string; input: Partial<ScreeningRequest>; params?: Record<string, string>; weights?: Partial<Record<string, number>>;
  extraSims?: Omit<SimulationRequest, 'screeningId'>[] };

const CASES: Case[] = [
  { name: 'co-ban', input: {} },
  { name: 'chua-tung-vay', input: { creditHistory: 'NEVER_BORROWED' } },
  { name: 'khong-nho-ro-lich-su', input: { creditHistory: 'UNSURE' } },
  { name: 'tre-han-qua-90-ngay', input: { creditHistory: 'LATE_OVER_90' } },
  { name: 'nu-co-ban', input: { gender: 'FEMALE', dob: '1995-11-02', maritalStatus: 'SINGLE', dependents: 0, avgIncome: 15_000_000, desiredAmount: 80_000_000, desiredTerm: 24 } },
  { name: 'du-22-tuoi-dung-ngay', input: { dob: '2004-10-23', avgIncome: 12_000_000, dependents: 0, desiredAmount: 50_000_000 } },
  { name: 'thieu-22-tuoi-mot-ngay', input: { dob: '2004-10-24', avgIncome: 12_000_000, dependents: 0, desiredAmount: 50_000_000 } },
  { name: 'du-19-tuoi-dung-ngay', input: { dob: '2007-10-23', avgIncome: 9_000_000, dependents: 0, desiredAmount: 20_000_000, desiredTerm: 12 } },
  { name: 'thieu-19-tuoi-mot-ngay', input: { dob: '2007-10-24', avgIncome: 9_000_000, dependents: 0, desiredAmount: 20_000_000 } },
  { name: 'sap-nghi-huu-nam-rut-ky-han', input: { dob: '1966-01-10', desiredTerm: 36 } },
  { name: 'qua-tuoi-nghi-huu-nam', input: { dob: '1964-12-01' } },
  { name: 'sap-nghi-huu-nu', input: { gender: 'FEMALE', dob: '1968-03-01', desiredTerm: 48 } },
  { name: 'sinh-ngay-29-02', input: { dob: '1968-02-29', desiredTerm: 60, desiredAmount: 200_000_000, avgIncome: 30_000_000 } },
  { name: 'dti-sat-nguong', input: { otherDebts: [{ lender: 'Ngân hàng A', monthlyPayment: 9_000_000 }], avgIncome: 20_000_000, desiredAmount: 120_000_000 } },
  { name: 'nhieu-no-khac', input: { otherDebts: [{ lender: 'Ngân hàng A', monthlyPayment: 6_000_000 }, { lender: 'Công ty tài chính B', monthlyPayment: 5_500_000 }, { monthlyPayment: 0 }] } },
  { name: 'thu-nhap-kha-dung-am', input: { avgIncome: 10_000_000, dependents: 3 } },
  { name: 'han-muc-duoi-toi-thieu', input: { avgIncome: 4_300_000, dependents: 0 } },
  { name: 'thu-nhap-bang-0', input: { avgIncome: 0 } },
  { name: 'khong-phai-cong-dan-viet-nam', input: { nationality: 'Hàn Quốc' } },
  { name: 'muc-dich-kinh-doanh', input: { purpose: 'Kinh doanh' } },
  { name: 'chi-tieu-linh-hoat', input: { purpose: 'Chi tiêu cá nhân linh hoạt', desiredAmount: 60_000_000 } },
  { name: 'nhan-luong-abbank-va-cbnv', input: { salaryBank: 'ABBANK', isAbbankStaff: true } },
  { name: 'nhieu-nguoi-phu-thuoc', input: { avgIncome: 40_000_000, dependents: 4, desiredAmount: 300_000_000, desiredTerm: 48 } },
  { name: 'vuot-tran-han-muc', input: { avgIncome: 80_000_000, desiredAmount: 800_000_000, desiredTerm: 60 } },
  { name: 'ky-han-duoi-toi-thieu', input: { desiredTerm: 3, desiredAmount: 40_000_000 } },
  { name: 'ky-han-tren-toi-da', input: { desiredTerm: 84 } },
  { name: 'cham-he-so-thu-nhap', input: { avgIncome: 25_000_000, dependents: 0, desiredAmount: 400_000_000, desiredTerm: 60 } },
  { name: 'ban-cheo-vuot-muc-giam-toi-da', input: {}, extraSims: [
    { productCode: 'TD', amount: 150_000_000, termMonths: 36, crossSellIds: [1, 2, 3] },
    { productCode: 'TD', amount: 150_000_000, termMonths: 36, crossSellIds: [5] },
    { productCode: 'TC', amount: 100_000_000, termMonths: 12, crossSellIds: [1, 3, 4] },
  ] },
  { name: 'ky-han-ngan-can-keo-dai', input: { avgIncome: 15_000_000, desiredAmount: 100_000_000, desiredTerm: 12 }, extraSims: [
    { productCode: 'TD', amount: 100_000_000, termMonths: 6, crossSellIds: [] },
    { productCode: 'TD', amount: 250_000_000, termMonths: 12, crossSellIds: [2] },
  ] },
  { name: 'tham-so-dti-65', input: {}, params: { ':DTI_MAX': '65', 'TD:REF_RATE': '13.2' } },
  { name: 'tham-so-room-80', input: { avgIncome: 30_000_000 }, params: { ':ROOM_FACTOR': '80', ':MAX_RATE_DISCOUNT': '1.5' } },
  { name: 'trong-so-khac', input: { salaryBank: 'ABBANK' }, weights: { 'TD:DTI': 50, 'TD:STAFF': 0, 'TC:SALARY_BANK': 40, 'TC:TERM_TO_RETIRE': 0 } },
];

/** Các lần mô phỏng cho từng gói đạt điều kiện: theo mong muốn, có bán chéo, vượt hạn mức an toàn, dưới mức tối thiểu */
function simsFor(inp: ScreeningRequest, ps: ReturnType<typeof toParamSet>): Omit<SimulationRequest, 'screeningId'>[] {
  const out: Omit<SimulationRequest, 'screeningId'>[] = [];
  for (const code of ['TD', 'TC'] as ProductCode[]) {
    const ex = exclusion(inp, ps, code);
    if (!ex.eligible) continue;
    const safe = maxSafeAmount(inp, ps, code, ex.term, ps.products[code].refRate);
    const amount = Math.min(inp.desiredAmount, ps.products[code].maxAmount);
    out.push({ productCode: code, amount, termMonths: inp.desiredTerm, crossSellIds: [] });
    out.push({ productCode: code, amount, termMonths: inp.desiredTerm, crossSellIds: [2, 3] });
    out.push({ productCode: code, amount: Math.round(safe * 1.4 / 1e6) * 1e6 + 1_000_000, termMonths: inp.desiredTerm, crossSellIds: [] });
    out.push({ productCode: code, amount: 5_000_000, termMonths: inp.desiredTerm, crossSellIds: [] });
  }
  return out;
}

mkdirSync(OUT, { recursive: true });
for (const f of readdirSync(OUT)) if (f.endsWith('.json')) rmSync(join(OUT, f));

CASES.forEach((c, i) => {
  const input: ScreeningRequest = { ...BASE, ...c.input };
  const ps = toParamSet(params(c.params));
  const ws = toWeightSet(weights(c.weights));
  const sims = [...simsFor(input, ps), ...(c.extraSims ?? [])];
  const fixture = {
    name: c.name,
    today: TODAY,
    params: ps,
    weights: ws,
    crossSells: CROSS_SELLS,
    input,
    screening: screen(input, ps, ws),
    simulations: sims.map(s => {
      const request: SimulationRequest = { screeningId: 1, ...s };
      return { request, expected: simulate(input, ps, ws, request, CROSS_SELLS) };
    }),
  };
  const file = `${String(i + 1).padStart(2, '0')}-${c.name}.json`;
  writeFileSync(join(OUT, file), JSON.stringify(fixture, null, 2) + '\n', 'utf8');
});

console.log(`Đã sinh ${CASES.length} fixtures vào ${OUT}`);
