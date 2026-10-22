/**
 * Bộ máy tiền sàng lọc, chấm điểm và mô phỏng (bản chạy ở trình duyệt để demo khi chưa có backend).
 * Backend Java cần cài đặt lại đúng các quy tắc này – xem docs/quy-tac-tinh-toan.md.
 */
import type {
  CrossSellProduct, ProductCode, ScheduleRow, ScreeningRequest, ScreeningResultItem,
  SimulationRequest, SimulationResponse, Suggestion, PolicyParam, ScoringWeight,
} from '../api/types';

export interface ParamSet {
  dtiMax: number; // %
  livingCostBase: number;
  livingCostPerDependent: number;
  retireAgeMale: number;
  retireAgeFemale: number;
  roomFactor: number; // % hạn mức được phép theo room tín dụng/tỷ trọng dư nợ tín chấp
  maxRateDiscount: number; // %/năm – mức giảm lãi tối đa từ bán chéo
  products: Record<ProductCode, {
    minAge: number;
    incomeMultiplier: number | null;
    maxAmount: number;
    minAmount: number;
    maxTerm: number;
    minTerm: number;
    refRate: number; // %/năm
  }>;
}

export type WeightSet = Record<ProductCode, Record<string, number>>;

const num = (p: PolicyParam[], key: string, code: ProductCode | null, dflt: number) => {
  const r = p.find(x => x.key === key && x.productCode === code);
  return r ? Number(r.value) : dflt;
};

/** Đọc MB11 thành bộ tham số dùng trong tính toán */
export function toParamSet(p: PolicyParam[]): ParamSet {
  const prod = (c: ProductCode) => ({
    minAge: num(p, 'MIN_AGE', c, c === 'TD' ? 22 : 19),
    incomeMultiplier: c === 'TD' ? num(p, 'INCOME_MULTIPLIER', c, 15) : null,
    maxAmount: num(p, 'MAX_AMOUNT', c, c === 'TD' ? 500e6 : 200e6),
    minAmount: num(p, 'MIN_AMOUNT', c, 10e6),
    maxTerm: num(p, 'MAX_TERM', c, c === 'TD' ? 60 : 12),
    minTerm: num(p, 'MIN_TERM', c, c === 'TD' ? 6 : 12),
    refRate: num(p, 'REF_RATE', c, c === 'TD' ? 12.5 : 18),
  });
  return {
    dtiMax: num(p, 'DTI_MAX', null, 70),
    livingCostBase: num(p, 'LIVING_COST_BASE', null, 4_000_000),
    livingCostPerDependent: num(p, 'LIVING_COST_PER_DEPENDENT', null, 2_000_000),
    retireAgeMale: num(p, 'RETIRE_AGE_MALE', null, 62),
    retireAgeFemale: num(p, 'RETIRE_AGE_FEMALE', null, 60),
    roomFactor: num(p, 'ROOM_FACTOR', null, 100),
    maxRateDiscount: num(p, 'MAX_RATE_DISCOUNT', null, 2),
    products: { TD: prod('TD'), TC: prod('TC') },
  };
}

export function toWeightSet(w: ScoringWeight[]): WeightSet {
  const res: WeightSet = { TD: {}, TC: {} };
  for (const x of w) res[x.productCode][x.criterion] = x.weight;
  return res;
}

export const PRODUCT_NAMES: Record<ProductCode, string> = {
  TD: 'Vay tiêu dùng không tài sản bảo đảm',
  TC: 'Vay thấu chi không tài sản bảo đảm',
};

export const FLEXIBLE_PURPOSE = 'Chi tiêu cá nhân linh hoạt';
export const BUSINESS_PURPOSE = 'Kinh doanh';

const round1 = (x: number) => Math.round(x * 10) / 10;
const floorM = (x: number) => Math.max(0, Math.floor(x / 1_000_000) * 1_000_000);

export function ageAt(dob: string, at = new Date()): number {
  const d = new Date(dob);
  let a = at.getFullYear() - d.getFullYear();
  const m = at.getMonth() - d.getMonth();
  if (m < 0 || (m === 0 && at.getDate() < d.getDate())) a--;
  return a;
}

/** số tháng còn lại tới tuổi nghỉ hưu */
export function monthsToRetire(inp: ScreeningRequest, ps: ParamSet, at = new Date()): number {
  const d = new Date(inp.dob);
  const retire = inp.gender === 'MALE' ? ps.retireAgeMale : ps.retireAgeFemale;
  const end = new Date(d.getFullYear() + retire, d.getMonth(), d.getDate());
  return (end.getFullYear() - at.getFullYear()) * 12 + (end.getMonth() - at.getMonth());
}

export const otherDebtTotal = (inp: ScreeningRequest) => inp.otherDebts.reduce((s, d) => s + (d.monthlyPayment || 0), 0);

export const livingCost = (inp: ScreeningRequest, ps: ParamSet) =>
  ps.livingCostBase + ps.livingCostPerDependent * inp.dependents;

/**
 * Nghĩa vụ trả nợ hằng tháng trên 1 đồng vốn vay:
 * - Vay tiêu dùng: gốc trả đều, lãi trên dư nợ giảm dần -> dùng số trả kỳ đầu (lớn nhất) = 1/n + r/12
 * - Thấu chi: lãi hằng tháng + quy đổi gốc trả dần trong 12 tháng = r/12 + 1/12 (thận trọng)
 */
export const obligationFactor = (code: ProductCode, term: number, ratePct: number) =>
  code === 'TD' ? 1 / term + ratePct / 100 / 12 : ratePct / 100 / 12 + 1 / 12;

/** Hạn mức tối đa an toàn */
export function maxSafeAmount(inp: ScreeningRequest, ps: ParamSet, code: ProductCode, term: number, ratePct: number) {
  const pp = ps.products[code];
  const income = inp.avgIncome;
  const debt = otherDebtTotal(inp);
  const disposable = income - debt - livingCost(inp, ps);
  const f = obligationFactor(code, term, ratePct);
  const byDti = (ps.dtiMax / 100 * income - debt) / f;
  const byDisposable = disposable / f;
  const byMult = pp.incomeMultiplier ? pp.incomeMultiplier * income : Infinity;
  const raw = Math.min(byDti, byDisposable, byMult, pp.maxAmount) * ps.roomFactor / 100;
  return floorM(raw);
}

export function rateWith(ps: ParamSet, code: ProductCode, cs: CrossSellProduct[]) {
  const disc = Math.min(ps.maxRateDiscount, cs.reduce((s, c) => s + c.rateDiscount, 0));
  return Math.round((ps.products[code].refRate - disc) * 100) / 100;
}

/** Điểm từng tiêu chí 0–100 */
export function criterionScores(inp: ScreeningRequest, ps: ParamSet, code: ProductCode, amount: number, term: number, ratePct: number) {
  const income = inp.avgIncome;
  const dti = (otherDebtTotal(inp) + amount * obligationFactor(code, term, ratePct)) / income * 100;
  const sDti = dti <= 40 ? 100 : dti >= ps.dtiMax ? 0 : 100 * (ps.dtiMax - dti) / (ps.dtiMax - 40);
  const safe = maxSafeAmount(inp, ps, code, term, ratePct);
  const ratio = safe > 0 ? amount / safe : 9;
  const sRatio = ratio <= 0.5 ? 100 : ratio <= 1 ? 100 - (ratio - 0.5) * 80 : Math.max(0, 60 - (ratio - 1) * 60);
  const sBank = /ABBANK/i.test(inp.salaryBank) ? 100 : 50;
  const sStaff = inp.isAbbankStaff ? 100 : 40;
  const left = monthsToRetire(inp, ps);
  const sRetire = left >= term * 3 ? 100 : left <= term ? 0 : 100 * (left - term) / (term * 2);
  return { DTI: sDti, AMOUNT_RATIO: sRatio, SALARY_BANK: sBank, STAFF: sStaff, TERM_TO_RETIRE: sRetire, _dti: dti };
}

export function scoreOf(inp: ScreeningRequest, ps: ParamSet, ws: WeightSet, code: ProductCode, amount: number, term: number,
  cs: CrossSellProduct[] = []) {
  const rate = rateWith(ps, code, cs);
  const s = criterionScores(inp, ps, code, amount, term, rate);
  const w = ws[code];
  const tot = Object.values(w).reduce((a, b) => a + b, 0) || 1;
  let sc = 0;
  for (const [k, wt] of Object.entries(w)) sc += (s[k as keyof typeof s] ?? 0) * wt;
  sc = sc / tot + cs.reduce((a, c) => a + c.scoreBonus, 0);
  return round1(Math.max(0, Math.min(100, sc)));
}

/** Kiểm tra điều kiện loại trừ (bước 3) */
export function exclusion(inp: ScreeningRequest, ps: ParamSet, code: ProductCode) {
  const pp = ps.products[code];
  const reasons: string[] = [];
  const hints: string[] = [];
  const age = ageAt(inp.dob);
  if (inp.nationality !== 'Việt Nam') {
    reasons.push('Gói vay chỉ áp dụng cho công dân Việt Nam.');
  }
  if (age < pp.minAge) {
    reasons.push(`Chưa đủ ${pp.minAge} tuổi.`);
    hints.push(code === 'TD' ? 'Có thể cân nhắc gói vay thấu chi (từ đủ 19 tuổi).' : `Quay lại khi đủ ${pp.minAge} tuổi.`);
  }
  const left = monthsToRetire(inp, ps);
  let term = code === 'TC' ? pp.maxTerm : Math.min(Math.max(inp.desiredTerm, pp.minTerm), pp.maxTerm);
  if (code === 'TD' && left >= pp.minTerm) term = Math.min(term, left);
  if (left < Math.min(term, pp.minTerm)) {
    reasons.push('Tuổi khi đáo hạn vượt tuổi nghỉ hưu.');
  } else if (code === 'TD' && left < Math.min(Math.max(inp.desiredTerm, pp.minTerm), pp.maxTerm)) {
    hints.push(`Kỳ hạn cần rút xuống tối đa ${left} tháng để không vượt tuổi nghỉ hưu.`);
  }
  if (inp.selfDeclaredBadDebt) {
    reasons.push('Có nợ xấu từ nhóm 3 trở lên.');
    hints.push('Tất toán khoản nợ xấu và chờ thông tin tín dụng được cập nhật trước khi đề nghị vay.');
  }
  if (!(inp.avgIncome > 0)) {
    reasons.push('Không có thu nhập lương chuyển khoản.');
    hints.push('Ngân hàng chỉ chấp nhận lương chuyển khoản, có sao kê tối thiểu 03 tháng.');
  }
  if (inp.purpose === BUSINESS_PURPOSE) {
    reasons.push('Mục đích kinh doanh không thuộc phạm vi vay tiêu dùng, thấu chi.');
  } else if (code === 'TD' && inp.purpose === FLEXIBLE_PURPOSE) {
    reasons.push('Vay tiêu dùng cần mục đích mua sắm, dịch vụ cụ thể có chứng từ.');
    hints.push('Chọn mục đích cụ thể (có hóa đơn, hợp đồng) hoặc dùng gói vay thấu chi.');
  }
  if (reasons.length === 0 && inp.avgIncome > 0) {
    const disposable = inp.avgIncome - otherDebtTotal(inp) - livingCost(inp, ps);
    const safe = maxSafeAmount(inp, ps, code, term, pp.refRate);
    if (disposable <= 0) {
      reasons.push('Thu nhập khả dụng sau chi phí sinh hoạt và nghĩa vụ nợ hiện có không còn dư.');
      hints.push('Giảm bớt nghĩa vụ trả nợ tại tổ chức tín dụng khác trước khi đề nghị vay.');
    } else if (safe < pp.minAmount) {
      reasons.push('Hạn mức an toàn thấp hơn số tiền vay tối thiểu của gói.');
      hints.push('Giảm nghĩa vụ nợ hiện có hoặc bổ sung nguồn thu nhập chuyển khoản.');
    }
  }
  return { eligible: reasons.length === 0, reasons, hints, term };
}

/** Bước (3)–(8): lập MB03 */
export function screen(inp: ScreeningRequest, ps: ParamSet, ws: WeightSet) {
  const items: ScreeningResultItem[] = (['TD', 'TC'] as ProductCode[]).map(code => {
    const pp = ps.products[code];
    const ex = exclusion(inp, ps, code);
    if (!ex.eligible) {
      return { productCode: code, productName: PRODUCT_NAMES[code], eligible: false, reasons: ex.reasons, hints: ex.hints,
        dti: null, maxSafeAmount: null, refRate: pp.refRate, maxTerm: pp.maxTerm, score: null, rankNo: null };
    }
    const safe = maxSafeAmount(inp, ps, code, ex.term, pp.refRate);
    const amount = Math.min(inp.desiredAmount, pp.maxAmount);
    const dti = (otherDebtTotal(inp) + amount * obligationFactor(code, ex.term, pp.refRate)) / inp.avgIncome * 100;
    const hints = [...ex.hints];
    if (inp.desiredAmount > safe) hints.push(`Số tiền mong muốn vượt hạn mức an toàn; nên vay tối đa ${safe.toLocaleString('vi-VN')} đ hoặc điều chỉnh ở bước mô phỏng.`);
    return { productCode: code, productName: PRODUCT_NAMES[code], eligible: true, reasons: [], hints,
      dti: round1(dti), maxSafeAmount: safe, refRate: pp.refRate, maxTerm: pp.maxTerm,
      score: scoreOf(inp, ps, ws, code, amount, ex.term), rankNo: null };
  });
  items.filter(i => i.eligible).sort((a, b) => (b.score ?? 0) - (a.score ?? 0)).forEach((x, i) => { x.rankNo = i + 1; });
  items.sort((a, b) => (a.rankNo ?? 99) - (b.rankNo ?? 99));
  const debt = otherDebtTotal(inp);
  return {
    age: ageAt(inp.dob),
    livingCost: livingCost(inp, ps),
    disposableIncome: inp.avgIncome - debt - livingCost(inp, ps),
    currentDti: inp.avgIncome > 0 ? round1(debt / inp.avgIncome * 100) : 0,
    results: items,
  };
}

export function schedule(code: ProductCode, amount: number, term: number, ratePct: number): ScheduleRow[] {
  const r = ratePct / 100 / 12;
  const rows: ScheduleRow[] = [];
  let bal = amount;
  for (let k = 1; k <= term; k++) {
    const interest = Math.round(bal * r);
    const principal = code === 'TD' ? (k === term ? bal : Math.round(amount / term)) : (k === term ? bal : 0);
    bal -= principal;
    rows.push({ period: k, principal, interest, payment: principal + interest, balance: bal });
  }
  return rows;
}

/** Bước (12)–(14): MB04, kiểm tra phương án và đề xuất điều chỉnh ngược */
export function simulate(inp: ScreeningRequest, ps: ParamSet, ws: WeightSet, req: SimulationRequest,
  allCs: CrossSellProduct[]): SimulationResponse {
  const code = req.productCode;
  const pp = ps.products[code];
  const cs = allCs.filter(c => req.crossSellIds.includes(c.id));
  const term = code === 'TC' ? pp.maxTerm : Math.min(Math.max(req.termMonths, pp.minTerm), pp.maxTerm);
  const rate = rateWith(ps, code, cs);
  const amount = req.amount;
  const sch = schedule(code, amount, term, rate);
  const obligation = Math.round(amount * obligationFactor(code, term, rate));
  const debt = otherDebtTotal(inp);
  const dtiAfter = round1((debt + obligation) / inp.avgIncome * 100);
  const disposableAfter = inp.avgIncome - debt - livingCost(inp, ps) - obligation;
  const safe = maxSafeAmount(inp, ps, code, term, rate);
  const left = monthsToRetire(inp, ps);
  const maxTermAllowed = code === 'TC' ? pp.maxTerm : Math.max(pp.minTerm, Math.min(pp.maxTerm, left));
  const issues: string[] = [];
  if (amount > safe) issues.push(`Số tiền vay vượt hạn mức tối đa an toàn (${safe.toLocaleString('vi-VN')} đ).`);
  if (dtiAfter > ps.dtiMax) issues.push(`DTI sau vay ${dtiAfter}% vượt ngưỡng tối đa ${ps.dtiMax}%.`);
  if (disposableAfter < 0) issues.push('Số tiền trả hằng tháng vượt thu nhập khả dụng.');
  if (amount < pp.minAmount) issues.push(`Số tiền vay tối thiểu là ${pp.minAmount.toLocaleString('vi-VN')} đ.`);
  if (code === 'TD' && term > left) issues.push('Kỳ hạn làm tuổi đáo hạn vượt tuổi nghỉ hưu.');
  const ok = issues.length === 0;
  const score = scoreOf(inp, ps, ws, code, amount, term, cs);
  const sugg: Suggestion[] = [];
  if (!ok) {
    if (safe >= pp.minAmount) {
      sugg.push({ type: 'REDUCE_AMOUNT', title: 'Giảm số tiền vay', detail: `Giảm về mức tối đa an toàn với kỳ hạn ${term} tháng.`,
        productCode: code, amount: safe, termMonths: term, crossSellIds: req.crossSellIds,
        score: scoreOf(inp, ps, ws, code, safe, term, cs), extraCost: cs.reduce((s, c) => s + c.estCost, 0) });
    }
    if (code === 'TD' && term < maxTermAllowed) {
      const safeLong = maxSafeAmount(inp, ps, code, maxTermAllowed, rate);
      let t = term;
      while (t < maxTermAllowed && maxSafeAmount(inp, ps, code, t, rate) < amount) t++;
      if (maxSafeAmount(inp, ps, code, t, rate) >= amount) {
        sugg.push({ type: 'EXTEND_TERM', title: 'Kéo dài kỳ hạn', detail: `Giữ số tiền, kéo dài kỳ hạn lên ${t} tháng (tối đa cho phép ${maxTermAllowed} tháng).`,
          productCode: code, amount, termMonths: t, crossSellIds: req.crossSellIds, score: scoreOf(inp, ps, ws, code, amount, t, cs),
          extraCost: cs.reduce((s, c) => s + c.estCost, 0) });
      } else if (safeLong > safe) {
        sugg.push({ type: 'EXTEND_TERM', title: 'Kéo dài kỳ hạn và giảm số tiền', detail: `Kỳ hạn ${maxTermAllowed} tháng cho hạn mức an toàn ${safeLong.toLocaleString('vi-VN')} đ.`,
          productCode: code, amount: safeLong, termMonths: maxTermAllowed, crossSellIds: req.crossSellIds,
          score: scoreOf(inp, ps, ws, code, safeLong, maxTermAllowed, cs), extraCost: cs.reduce((s, c) => s + c.estCost, 0) });
      }
    }
    const notUsed = allCs.filter(c => c.status === 'ACTIVE' && !req.crossSellIds.includes(c.id) && c.rateDiscount > 0)
      .sort((a, b) => b.rateDiscount - a.rateDiscount);
    if (notUsed.length) {
      const ids = [...req.crossSellIds, notUsed[0].id];
      const cs2 = allCs.filter(c => ids.includes(c.id));
      const r2 = rateWith(ps, code, cs2);
      const safe2 = maxSafeAmount(inp, ps, code, term, r2);
      const amt2 = Math.min(amount, safe2);
      if (amt2 >= pp.minAmount && safe2 > safe) {
        sugg.push({ type: 'CROSS_SELL', title: `Tham gia ${notUsed[0].name}`, detail: `Lãi suất tham chiếu còn ${r2}%/năm, hạn mức an toàn tăng lên ${safe2.toLocaleString('vi-VN')} đ.`,
          productCode: code, amount: amt2, termMonths: term, crossSellIds: ids, score: scoreOf(inp, ps, ws, code, amt2, term, cs2),
          extraCost: cs2.reduce((s, c) => s + c.estCost, 0) });
      }
    }
    const other: ProductCode = code === 'TD' ? 'TC' : 'TD';
    const ex = exclusion(inp, ps, other);
    if (ex.eligible) {
      const s2 = maxSafeAmount(inp, ps, other, ex.term, ps.products[other].refRate);
      const amt = Math.min(amount, s2);
      if (amt >= ps.products[other].minAmount) {
        sugg.push({ type: 'SWITCH_PRODUCT', title: `Chuyển sang ${PRODUCT_NAMES[other].toLowerCase()}`,
          detail: `Hạn mức an toàn của gói ${s2.toLocaleString('vi-VN')} đ, kỳ hạn ${ex.term} tháng.`,
          productCode: other, amount: amt, termMonths: ex.term, crossSellIds: [], score: scoreOf(inp, ps, ws, other, amt, ex.term), extraCost: 0 });
      }
    }
  }
  const totalInterest = sch.reduce((s, r) => s + r.interest, 0);
  return {
    productCode: code, productName: PRODUCT_NAMES[code], amount, termMonths: term, rate,
    monthlyObligation: obligation, firstPayment: sch[0]?.payment ?? 0, totalInterest, totalPayment: amount + totalInterest,
    dtiAfter, dtiMax: ps.dtiMax, disposableAfter, maxSafeAmount: safe, maxTermAllowed, score, ok, issues,
    extraCost: cs.reduce((s, c) => s + c.estCost, 0), schedule: sch, suggestions: sugg,
  };
}
