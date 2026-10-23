package vn.kltn.tiensanglocvay.engine;

import static vn.kltn.tiensanglocvay.engine.JsFormat.num;
import static vn.kltn.tiensanglocvay.engine.JsFormat.vnd;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import vn.kltn.tiensanglocvay.engine.ParamSet.ProductParams;

/**
 * Bộ máy tiền sàng lọc, chấm điểm và mô phỏng: bản Java của fe/src/mocks/engine.ts (đặc tả: docs/quy-tac-tinh-toan.md).
 * Giữ nguyên thứ tự kiểm tra, thứ tự phép tính số thực và câu chữ; "hôm nay" truyền vào tường minh ({@code today}).
 * Mỗi lần sửa engine.ts phải sửa tương ứng ở đây và sinh lại fixtures (be/tools/gen-engine-fixtures.ts).
 */
public final class Engine {

    public static final Map<ProductCode, String> PRODUCT_NAMES = Map.of(
            ProductCode.TD, "Vay tiêu dùng không tài sản bảo đảm",
            ProductCode.TC, "Vay thấu chi không tài sản bảo đảm");

    public static final String FLEXIBLE_PURPOSE = "Chi tiêu cá nhân linh hoạt";
    public static final String BUSINESS_PURPOSE = "Kinh doanh";

    private Engine() {
    }

    /** Kết quả kiểm tra điều kiện loại trừ; {@code term} là kỳ hạn dùng để tính hạn mức. */
    public record Exclusion(boolean eligible, List<String> reasons, List<String> hints, int term) {
    }

    static double round1(double x) {
        return Math.round(x * 10) / 10.0;
    }

    static long floorM(double x) {
        return (long) Math.max(0, Math.floor(x / 1_000_000) * 1_000_000);
    }

    /** {@code new Date(y, m, d)} của JavaScript: ngày vượt cuối tháng được cộng sang tháng sau (29/02 → 01/03). */
    static LocalDate jsDate(int year, int month, int day) {
        return LocalDate.of(year, month, 1).plusDays(day - 1L);
    }

    public static int ageAt(LocalDate dob, LocalDate at) {
        int a = at.getYear() - dob.getYear();
        int m = at.getMonthValue() - dob.getMonthValue();
        if (m < 0 || (m == 0 && at.getDayOfMonth() < dob.getDayOfMonth())) {
            a--;
        }
        return a;
    }

    /** Số tháng còn lại tới tuổi nghỉ hưu. */
    public static int monthsToRetire(EngineInput inp, ParamSet ps, LocalDate at) {
        LocalDate d = inp.dob();
        double retire = inp.gender() == Gender.MALE ? ps.retireAgeMale() : ps.retireAgeFemale();
        LocalDate end = jsDate(d.getYear() + (int) retire, d.getMonthValue(), d.getDayOfMonth());
        return (end.getYear() - at.getYear()) * 12 + (end.getMonthValue() - at.getMonthValue());
    }

    public static double otherDebtTotal(EngineInput inp) {
        double s = 0;
        for (EngineInput.Debt d : inp.otherDebts()) {
            s += d.monthlyPayment() == null ? 0 : d.monthlyPayment();
        }
        return s;
    }

    public static double livingCost(EngineInput inp, ParamSet ps) {
        return ps.livingCostBase() + ps.livingCostPerDependent() * inp.dependents();
    }

    /**
     * Nghĩa vụ trả nợ hằng tháng trên 1 đồng vốn vay: vay tiêu dùng lấy kỳ trả đầu 1/n + r/12;
     * thấu chi quy đổi thận trọng r/12 + 1/12.
     */
    public static double obligationFactor(ProductCode code, int term, double ratePct) {
        return code == ProductCode.TD ? 1.0 / term + ratePct / 100 / 12 : ratePct / 100 / 12 + 1.0 / 12;
    }

    /** Hạn mức tối đa an toàn. */
    public static long maxSafeAmount(EngineInput inp, ParamSet ps, ProductCode code, int term, double ratePct) {
        ProductParams pp = ps.product(code);
        double income = inp.avgIncome();
        double debt = otherDebtTotal(inp);
        double disposable = income - debt - livingCost(inp, ps);
        double f = obligationFactor(code, term, ratePct);
        double byDti = (ps.dtiMax() / 100 * income - debt) / f;
        double byDisposable = disposable / f;
        double byMult = pp.incomeMultiplier() != null && pp.incomeMultiplier() != 0 ? pp.incomeMultiplier() * income : Double.POSITIVE_INFINITY;
        double raw = Math.min(Math.min(Math.min(byDti, byDisposable), byMult), pp.maxAmount()) * ps.roomFactor() / 100;
        return floorM(raw);
    }

    public static double rateWith(ParamSet ps, ProductCode code, List<CrossSell> cs) {
        double sum = 0;
        for (CrossSell c : cs) {
            sum += c.rateDiscount();
        }
        double disc = Math.min(ps.maxRateDiscount(), sum);
        return Math.round((ps.product(code).refRate() - disc) * 100) / 100.0;
    }

    /** Điểm từng tiêu chí 0–100; khóa "_dti" là DTI sau vay (%). */
    public static Map<String, Double> criterionScores(EngineInput inp, ParamSet ps, ProductCode code, double amount, int term,
                                                      double ratePct, LocalDate today) {
        double income = inp.avgIncome();
        double dti = (otherDebtTotal(inp) + amount * obligationFactor(code, term, ratePct)) / income * 100;
        double sDti = dti <= 40 ? 100 : dti >= ps.dtiMax() ? 0 : 100 * (ps.dtiMax() - dti) / (ps.dtiMax() - 40);
        long safe = maxSafeAmount(inp, ps, code, term, ratePct);
        double ratio = safe > 0 ? amount / safe : 9;
        double sRatio = ratio <= 0.5 ? 100 : ratio <= 1 ? 100 - (ratio - 0.5) * 80 : Math.max(0, 60 - (ratio - 1) * 60);
        double sBank = inp.salaryBank() != null && inp.salaryBank().toUpperCase(Locale.ROOT).contains("ABBANK") ? 100 : 50;
        double sStaff = inp.isAbbankStaff() ? 100 : 40;
        int left = monthsToRetire(inp, ps, today);
        double sRetire = left >= term * 3 ? 100 : left <= term ? 0 : 100.0 * (left - term) / (term * 2);
        Map<String, Double> s = new LinkedHashMap<>();
        s.put("DTI", sDti);
        s.put("AMOUNT_RATIO", sRatio);
        s.put("SALARY_BANK", sBank);
        s.put("STAFF", sStaff);
        s.put("TERM_TO_RETIRE", sRetire);
        s.put("_dti", dti);
        return s;
    }

    public static double scoreOf(EngineInput inp, ParamSet ps, WeightSet ws, ProductCode code, double amount, int term,
                                 List<CrossSell> cs, LocalDate today) {
        double rate = rateWith(ps, code, cs);
        Map<String, Double> s = criterionScores(inp, ps, code, amount, term, rate, today);
        Map<String, Double> w = ws.of(code);
        double tot = 0;
        for (double x : w.values()) {
            tot += x;
        }
        if (tot == 0 || Double.isNaN(tot)) {
            tot = 1;
        }
        double sc = 0;
        for (Map.Entry<String, Double> e : w.entrySet()) {
            sc += s.getOrDefault(e.getKey(), 0.0) * e.getValue();
        }
        double bonus = 0;
        for (CrossSell c : cs) {
            bonus += c.scoreBonus();
        }
        sc = sc / tot + bonus;
        return round1(Math.max(0, Math.min(100, sc)));
    }

    /** Kiểm tra điều kiện loại trừ (bước 3). */
    public static Exclusion exclusion(EngineInput inp, ParamSet ps, ProductCode code, LocalDate today) {
        ProductParams pp = ps.product(code);
        List<String> reasons = new ArrayList<>();
        List<String> hints = new ArrayList<>();
        int age = ageAt(inp.dob(), today);
        if (!"Việt Nam".equals(inp.nationality())) {
            reasons.add("Gói vay chỉ áp dụng cho công dân Việt Nam.");
        }
        if (age < pp.minAge()) {
            reasons.add("Chưa đủ " + num(pp.minAge()) + " tuổi.");
            hints.add(code == ProductCode.TD ? "Có thể cân nhắc gói vay thấu chi (từ đủ 19 tuổi)." : "Quay lại khi đủ " + num(pp.minAge()) + " tuổi.");
        }
        int left = monthsToRetire(inp, ps, today);
        int term = code == ProductCode.TC ? pp.maxTerm() : Math.min(Math.max(inp.desiredTerm(), pp.minTerm()), pp.maxTerm());
        if (code == ProductCode.TD && left >= pp.minTerm()) {
            term = Math.min(term, left);
        }
        if (left < Math.min(term, pp.minTerm())) {
            reasons.add("Tuổi khi đáo hạn vượt tuổi nghỉ hưu.");
        } else if (code == ProductCode.TD && left < Math.min(Math.max(inp.desiredTerm(), pp.minTerm()), pp.maxTerm())) {
            hints.add("Kỳ hạn cần rút xuống tối đa " + left + " tháng để không vượt tuổi nghỉ hưu.");
        }
        if (inp.creditHistory() == CreditHistory.LATE_OVER_90) {
            reasons.add("Bạn cho biết từng trả chậm quá 3 tháng hoặc phải xin giãn, hoãn nợ trong 5 năm gần đây.");
            hints.add("Tất toán khoản nợ đó và chờ thông tin tín dụng được cập nhật; bạn có thể tự kiểm tra trên ứng dụng CIC Credit Connect trước khi đề nghị vay.");
        }
        if (!(inp.avgIncome() > 0)) {
            reasons.add("Không có thu nhập lương chuyển khoản.");
            hints.add("Ngân hàng chỉ chấp nhận lương chuyển khoản, có sao kê tối thiểu 03 tháng.");
        }
        if (BUSINESS_PURPOSE.equals(inp.purpose())) {
            reasons.add("Mục đích kinh doanh không thuộc phạm vi vay tiêu dùng, thấu chi.");
        } else if (code == ProductCode.TD && FLEXIBLE_PURPOSE.equals(inp.purpose())) {
            reasons.add("Vay tiêu dùng cần mục đích mua sắm, dịch vụ cụ thể có chứng từ.");
            hints.add("Chọn mục đích cụ thể (có hóa đơn, hợp đồng) hoặc dùng gói vay thấu chi.");
        }
        if (reasons.isEmpty() && inp.avgIncome() > 0) {
            double disposable = inp.avgIncome() - otherDebtTotal(inp) - livingCost(inp, ps);
            long safe = maxSafeAmount(inp, ps, code, term, pp.refRate());
            if (disposable <= 0) {
                reasons.add("Thu nhập khả dụng sau chi phí sinh hoạt và nghĩa vụ nợ hiện có không còn dư.");
                hints.add("Giảm bớt nghĩa vụ trả nợ tại tổ chức tín dụng khác trước khi đề nghị vay.");
            } else if (safe < pp.minAmount()) {
                reasons.add("Hạn mức an toàn thấp hơn số tiền vay tối thiểu của gói.");
                hints.add("Giảm nghĩa vụ nợ hiện có hoặc bổ sung nguồn thu nhập chuyển khoản.");
            }
        }
        return new Exclusion(reasons.isEmpty(), reasons, hints, term);
    }

    /** Bước (3)–(8): lập MB03. */
    public static ScreeningOutcome screen(EngineInput inp, ParamSet ps, WeightSet ws, LocalDate today) {
        List<ScreeningResultItem> items = new ArrayList<>();
        for (ProductCode code : List.of(ProductCode.TD, ProductCode.TC)) {
            ProductParams pp = ps.product(code);
            Exclusion ex = exclusion(inp, ps, code, today);
            if (!ex.eligible()) {
                items.add(new ScreeningResultItem(code, PRODUCT_NAMES.get(code), false, ex.reasons(), ex.hints(), null, null,
                        pp.refRate(), pp.maxTerm(), null, null));
                continue;
            }
            long safe = maxSafeAmount(inp, ps, code, ex.term(), pp.refRate());
            double amount = Math.min(inp.desiredAmount(), pp.maxAmount());
            double dti = (otherDebtTotal(inp) + amount * obligationFactor(code, ex.term(), pp.refRate())) / inp.avgIncome() * 100;
            List<String> hints = new ArrayList<>(ex.hints());
            if (inp.desiredAmount() > safe) {
                hints.add("Số tiền mong muốn vượt hạn mức an toàn; nên vay tối đa " + vnd(safe) + " đ hoặc điều chỉnh ở bước mô phỏng.");
            }
            items.add(new ScreeningResultItem(code, PRODUCT_NAMES.get(code), true, List.of(), hints, round1(dti), safe, pp.refRate(),
                    pp.maxTerm(), scoreOf(inp, ps, ws, code, amount, ex.term(), List.of(), today), null));
        }
        Map<ScreeningResultItem, Integer> rank = new IdentityHashMap<>();
        List<ScreeningResultItem> eligible = new ArrayList<>(items.stream().filter(ScreeningResultItem::eligible).toList());
        eligible.sort(Comparator.comparingDouble((ScreeningResultItem i) -> i.score() == null ? 0 : i.score()).reversed());
        for (int i = 0; i < eligible.size(); i++) {
            rank.put(eligible.get(i), i + 1);
        }
        List<ScreeningResultItem> results = new ArrayList<>(items.stream().map(i -> i.withRank(rank.get(i))).toList());
        results.sort(Comparator.comparingInt(i -> i.rankNo() == null ? 99 : i.rankNo()));
        double debt = otherDebtTotal(inp);
        List<ScreeningNote> notes = new ArrayList<>();
        if (inp.creditHistory() == CreditHistory.UNSURE) {
            notes.add(new ScreeningNote("WARNING", "Bạn chưa chắc về lịch sử trả nợ. Ngân hàng sẽ tra cứu CIC khi bạn ký hồ sơ; nếu có khoản trả chậm quá 3 tháng, hồ sơ có thể không được duyệt."));
        } else if (inp.creditHistory() == CreditHistory.NEVER_BORROWED) {
            notes.add(new ScreeningNote("INFO", "Bạn chưa có lịch sử vay – điều này không ảnh hưởng tới kết quả sàng lọc."));
        }
        double living = livingCost(inp, ps);
        return new ScreeningOutcome(notes, ageAt(inp.dob(), today), Math.round(living), Math.round(inp.avgIncome() - debt - living),
                inp.avgIncome() > 0 ? round1(debt / inp.avgIncome() * 100) : 0, results);
    }

    public static List<ScheduleRow> schedule(ProductCode code, long amount, int term, double ratePct) {
        double r = ratePct / 100 / 12;
        List<ScheduleRow> rows = new ArrayList<>();
        long bal = amount;
        for (int k = 1; k <= term; k++) {
            long interest = Math.round(bal * r);
            long principal = code == ProductCode.TD ? (k == term ? bal : Math.round((double) amount / term)) : (k == term ? bal : 0);
            bal -= principal;
            rows.add(new ScheduleRow(k, principal, interest, principal + interest, bal));
        }
        return rows;
    }

    private static List<CrossSell> pick(List<CrossSell> all, List<Integer> ids) {
        return all.stream().filter(c -> ids.contains(c.id())).toList();
    }

    private static long estCost(List<CrossSell> cs) {
        long s = 0;
        for (CrossSell c : cs) {
            s += c.estCost();
        }
        return s;
    }

    /** Bước (12)–(14): MB04, kiểm tra phương án và đề xuất điều chỉnh ngược. */
    public static SimulationResponse simulate(EngineInput inp, ParamSet ps, WeightSet ws, SimulationInput req, List<CrossSell> allCs,
                                              LocalDate today) {
        ProductCode code = req.productCode();
        ProductParams pp = ps.product(code);
        List<CrossSell> cs = pick(allCs, req.crossSellIds());
        int term = code == ProductCode.TC ? pp.maxTerm() : Math.min(Math.max(req.termMonths(), pp.minTerm()), pp.maxTerm());
        double rate = rateWith(ps, code, cs);
        long amount = req.amount();
        List<ScheduleRow> sch = schedule(code, amount, term, rate);
        long obligation = Math.round(amount * obligationFactor(code, term, rate));
        double debt = otherDebtTotal(inp);
        double dtiAfter = round1((debt + obligation) / inp.avgIncome() * 100);
        long disposableAfter = Math.round(inp.avgIncome() - debt - livingCost(inp, ps) - obligation);
        long safe = maxSafeAmount(inp, ps, code, term, rate);
        int left = monthsToRetire(inp, ps, today);
        int maxTermAllowed = code == ProductCode.TC ? pp.maxTerm() : Math.max(pp.minTerm(), Math.min(pp.maxTerm(), left));
        List<String> issues = new ArrayList<>();
        if (amount > safe) {
            issues.add("Số tiền vay vượt hạn mức tối đa an toàn (" + vnd(safe) + " đ).");
        }
        if (dtiAfter > ps.dtiMax()) {
            issues.add("DTI sau vay " + num(dtiAfter) + "% vượt ngưỡng tối đa " + num(ps.dtiMax()) + "%.");
        }
        if (disposableAfter < 0) {
            issues.add("Số tiền trả hằng tháng vượt thu nhập khả dụng.");
        }
        if (amount < pp.minAmount()) {
            issues.add("Số tiền vay tối thiểu là " + vnd(pp.minAmount()) + " đ.");
        }
        if (code == ProductCode.TD && term > left) {
            issues.add("Kỳ hạn làm tuổi đáo hạn vượt tuổi nghỉ hưu.");
        }
        boolean ok = issues.isEmpty();
        double score = scoreOf(inp, ps, ws, code, amount, term, cs, today);
        List<Suggestion> sugg = new ArrayList<>();
        if (!ok) {
            if (safe >= pp.minAmount()) {
                sugg.add(new Suggestion("REDUCE_AMOUNT", "Giảm số tiền vay", "Giảm về mức tối đa an toàn với kỳ hạn " + term + " tháng.",
                        code, safe, term, req.crossSellIds(), scoreOf(inp, ps, ws, code, safe, term, cs, today), estCost(cs)));
            }
            if (code == ProductCode.TD && term < maxTermAllowed) {
                long safeLong = maxSafeAmount(inp, ps, code, maxTermAllowed, rate);
                int t = term;
                while (t < maxTermAllowed && maxSafeAmount(inp, ps, code, t, rate) < amount) {
                    t++;
                }
                if (maxSafeAmount(inp, ps, code, t, rate) >= amount) {
                    sugg.add(new Suggestion("EXTEND_TERM", "Kéo dài kỳ hạn",
                            "Giữ số tiền, kéo dài kỳ hạn lên " + t + " tháng (tối đa cho phép " + maxTermAllowed + " tháng).",
                            code, amount, t, req.crossSellIds(), scoreOf(inp, ps, ws, code, amount, t, cs, today), estCost(cs)));
                } else if (safeLong > safe) {
                    sugg.add(new Suggestion("EXTEND_TERM", "Kéo dài kỳ hạn và giảm số tiền",
                            "Kỳ hạn " + maxTermAllowed + " tháng cho hạn mức an toàn " + vnd(safeLong) + " đ.",
                            code, safeLong, maxTermAllowed, req.crossSellIds(),
                            scoreOf(inp, ps, ws, code, safeLong, maxTermAllowed, cs, today), estCost(cs)));
                }
            }
            List<CrossSell> notUsed = new ArrayList<>(allCs.stream()
                    .filter(c -> c.active() && !req.crossSellIds().contains(c.id()) && c.rateDiscount() > 0).toList());
            notUsed.sort(Comparator.comparingDouble(CrossSell::rateDiscount).reversed());
            if (!notUsed.isEmpty()) {
                CrossSell first = notUsed.get(0);
                List<Integer> ids = new ArrayList<>(req.crossSellIds());
                ids.add(first.id());
                List<CrossSell> cs2 = pick(allCs, ids);
                double r2 = rateWith(ps, code, cs2);
                long safe2 = maxSafeAmount(inp, ps, code, term, r2);
                long amt2 = Math.min(amount, safe2);
                if (amt2 >= pp.minAmount() && safe2 > safe) {
                    sugg.add(new Suggestion("CROSS_SELL", "Tham gia " + first.name(),
                            "Lãi suất tham chiếu còn " + num(r2) + "%/năm, hạn mức an toàn tăng lên " + vnd(safe2) + " đ.",
                            code, amt2, term, ids, scoreOf(inp, ps, ws, code, amt2, term, cs2, today), estCost(cs2)));
                }
            }
            ProductCode other = code.other();
            Exclusion ex = exclusion(inp, ps, other, today);
            if (ex.eligible()) {
                ProductParams op = ps.product(other);
                long s2 = maxSafeAmount(inp, ps, other, ex.term(), op.refRate());
                long amt = Math.min(amount, s2);
                if (amt >= op.minAmount()) {
                    sugg.add(new Suggestion("SWITCH_PRODUCT", "Chuyển sang " + PRODUCT_NAMES.get(other).toLowerCase(Locale.ROOT),
                            "Hạn mức an toàn của gói " + vnd(s2) + " đ, kỳ hạn " + ex.term() + " tháng.",
                            other, amt, ex.term(), List.of(), scoreOf(inp, ps, ws, other, amt, ex.term(), List.of(), today), 0));
                }
            }
        }
        long totalInterest = 0;
        for (ScheduleRow r : sch) {
            totalInterest += r.interest();
        }
        return new SimulationResponse(code, PRODUCT_NAMES.get(code), amount, term, rate, obligation, sch.isEmpty() ? 0 : sch.get(0).payment(),
                totalInterest, amount + totalInterest, dtiAfter, ps.dtiMax(), disposableAfter, safe, maxTermAllowed, score, ok, issues,
                estCost(cs), sch, sugg);
    }
}
