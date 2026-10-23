package vn.kltn.tiensanglocvay.application;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import vn.kltn.tiensanglocvay.catalog.DocumentType;
import vn.kltn.tiensanglocvay.engine.Engine;
import vn.kltn.tiensanglocvay.engine.EngineInput;
import vn.kltn.tiensanglocvay.engine.JsFormat;
import vn.kltn.tiensanglocvay.engine.ParamSet;
import vn.kltn.tiensanglocvay.engine.SimulationInput;
import vn.kltn.tiensanglocvay.engine.SimulationResponse;
import vn.kltn.tiensanglocvay.policy.ActivePolicy;
import vn.kltn.tiensanglocvay.policy.EngineParams;
import vn.kltn.tiensanglocvay.policy.PolicySnapshot;
import vn.kltn.tiensanglocvay.screening.SimulationService;

/**
 * Đối chiếu MB06 ↔ MB01 và tính lại theo thu nhập kiểm chứng (docs/quy-tac-tinh-toan.md mục 10). Chép {@code crossCheck},
 * {@code refreshChecks}, {@code missingForSubmit} trong fe/src/mocks/ctx.ts; câu chữ giữ nguyên.
 */
@Service
public class CrossCheckService {

    static final int CCCD = 2;
    static final int STATEMENT = 5;
    static final int INVOICE = 6;

    private final ActivePolicy policy;
    private final SimulationService simulations;
    private final Clock clock;

    public CrossCheckService(ActivePolicy policy, SimulationService simulations, Clock clock) {
        this.policy = policy;
        this.simulations = simulations;
        this.clock = clock;
    }

    /** Chạy lại đối chiếu và lưu vào hồ sơ (mock {@code refreshChecks}). */
    public void refresh(AppBundle b) {
        List<CrossCheckWarning> w = new ArrayList<>();
        Recalculation recalc = check(b, w);
        b.app.setChecks(w, recalc);
    }

    private Recalculation check(AppBundle b, List<CrossCheckWarning> w) {
        LoanApplication p = b.app;
        EngineInput inp = b.input;
        String fullName = nz(p.getFullName());
        String nationalId = nz(p.getNationalId());
        String name = b.value(CCCD, "full_name");
        if (notEmpty(name) && !fullName.isEmpty()
                && !name.trim().toUpperCase(Locale.ROOT).equals(fullName.trim().toUpperCase(Locale.ROOT))) {
            w.add(new CrossCheckWarning("Họ và tên", fullName, name, "Họ tên trên CCCD khác thông tin cá nhân đã khai.",
                    CrossCheckWarning.WARNING));
        }
        String nid = b.value(CCCD, "national_id");
        if (notEmpty(nid) && !nationalId.isEmpty() && !nid.replaceAll("\\s", "").equals(nationalId)) {
            w.add(new CrossCheckWarning("Số CCCD", nationalId, nid, "Số CCCD trên giấy tờ khác số đã khai.", CrossCheckWarning.BLOCKING));
        }
        if (notEmpty(nid) && !nid.replaceAll("\\s", "").matches("\\d{12}")) {
            w.add(new CrossCheckWarning("Số CCCD", nationalId, nid, "Số CCCD phải gồm 12 chữ số.", CrossCheckWarning.BLOCKING));
        }
        String dob = b.value(CCCD, "dob");
        if (notEmpty(dob) && inp != null) {
            String iso = reverse(dob, "/", "-");
            String declared = inp.dob().toString();
            if (!iso.equals(declared)) {
                w.add(new CrossCheckWarning("Ngày sinh", reverse(declared, "-", "/"), dob,
                        "Ngày sinh trên CCCD khác ngày sinh đã khai khi sàng lọc; điều kiện tuổi được kiểm tra lại.",
                        CrossCheckWarning.WARNING));
            }
        }
        String bank = b.value(STATEMENT, "salary_bank");
        if (notEmpty(bank) && inp != null && !bank.equals(inp.salaryBank())) {
            w.add(new CrossCheckWarning("Ngân hàng nhận lương", inp.salaryBank(), bank, "Ngân hàng trên sao kê khác ngân hàng đã khai.",
                    CrossCheckWarning.INFO));
        }
        long amount = b.plan.getAmount().longValueExact();
        String inv = b.value(INVOICE, "invoice_amount");
        if (notEmpty(inv) && parseMoney(inv) > 0 && parseMoney(inv) < amount) {
            w.add(new CrossCheckWarning("Giá trị hóa đơn", JsFormat.vnd(amount), inv, "Số tiền vay vượt giá trị hóa đơn, hợp đồng mua bán.",
                    CrossCheckWarning.BLOCKING));
        }
        Recalculation recalc = null;
        String incStr = b.value(STATEMENT, "avg_income");
        if (notEmpty(incStr) && inp != null) {
            long verified = parseMoney(incStr);
            if (verified > 0) {
                double diff = (verified - inp.avgIncome()) / inp.avgIncome();
                if (Math.abs(diff) >= 0.05) {
                    w.add(new CrossCheckWarning("Thu nhập bình quân", JsFormat.vnd(inp.avgIncome()), incStr,
                            "Thu nhập trên sao kê " + (diff < 0 ? "thấp" : "cao") + " hơn khai báo " + Math.abs(Math.round(diff * 100))
                                    + "%; DTI và điểm được tính lại theo sao kê.", CrossCheckWarning.WARNING));
                }
                SimulationResponse sim = simulate(withIncome(inp, verified), b, amount, b.plan.getTermMonths(), b.crossSellIds);
                recalc = new Recalculation(verified, sim.dtiAfter(), sim.maxSafeAmount(), sim.score(), sim.ok(), sim.issues());
                if (!sim.ok()) {
                    w.add(new CrossCheckWarning("Phương án vay", JsFormat.vnd(amount), JsFormat.vnd(sim.maxSafeAmount()),
                            "Theo thu nhập kiểm chứng, phương án không còn đạt ngưỡng an toàn; cần điều chỉnh số tiền hoặc kỳ hạn.",
                            CrossCheckWarning.BLOCKING));
                }
            }
        }
        LocalDate realDob = p.getDob();
        if (inp != null && realDob != null && !realDob.equals(inp.dob())) {
            LocalDate today = LocalDate.now(clock);
            int age = Engine.ageAt(realDob, today);
            // Mock viết cứng 22/19; ở đây đọc MIN_AGE của gói trong phiên bản đang áp dụng (cùng giá trị dữ liệu mẫu).
            ParamSet ps = EngineParams.paramSet(policy.current());
            if (age < ps.product(b.productCode).minAge()) {
                w.add(new CrossCheckWarning("Tuổi", String.valueOf(Engine.ageAt(inp.dob(), today)), String.valueOf(age),
                        "Theo ngày sinh thực tế, chưa đủ tuổi tối thiểu của gói vay.", CrossCheckWarning.BLOCKING));
            }
        }
        return recalc;
    }

    /** Mô phỏng theo phiên bản đang áp dụng, không kiểm tra điều kiện loại trừ (như mock). */
    SimulationResponse simulate(EngineInput inp, AppBundle b, long amount, int termMonths, List<Integer> crossSellIds) {
        PolicySnapshot ps = policy.current();
        return Engine.simulate(inp, EngineParams.paramSet(ps), EngineParams.weightSet(ps),
                new SimulationInput(b.productCode, amount, termMonths, List.copyOf(crossSellIds)), simulations.allCrossSells(),
                LocalDate.now(clock));
    }

    /** Mock {@code { ...scr.input, avgIncome: verified }}. */
    static EngineInput withIncome(EngineInput i, double income) {
        return new EngineInput(i.nationality(), i.dob(), i.gender(), i.dependents(), income, i.salaryBank(), i.isAbbankStaff(),
                i.creditHistory(), i.otherDebts(), i.purpose(), i.desiredAmount(), i.desiredTerm());
    }

    /** Điều kiện được nộp còn thiếu (mock {@code missingForSubmit}); rỗng là đủ. */
    public List<String> missingForSubmit(AppBundle b) {
        List<String> m = new ArrayList<>();
        LoanApplication p = b.app;
        if (nz(p.getFullName()).isEmpty() || nz(p.getNationalId()).isEmpty() || nz(p.getAddress()).isEmpty() || p.getDob() == null) {
            m.add("Chưa khai đủ thông tin cá nhân (họ tên, số CCCD, ngày sinh, địa chỉ).");
        }
        for (ApplicationDocument d : b.docs) {
            if (d.getDocumentTypeId() == DocumentType.SYSTEM_DOC_TYPE) {
                continue;
            }
            if (d.isRequired() && !ApplicationDocument.CONFIRMED.equals(d.getStatus())) {
                m.add("Chưa " + (ApplicationDocument.UPLOADED.equals(d.getStatus()) ? "xác nhận thông tin" : "tải lên") + ": "
                        + b.typeName(d) + ".");
            }
        }
        if (p.getWarnings().stream().anyMatch(CrossCheckWarning::blocking)) {
            m.add("Còn sai lệch cần xử lý (xem mục Cảnh báo đối chiếu).");
        }
        return m;
    }

    /** Mock {@code parseMoney}: bỏ mọi ký tự không phải chữ số; rỗng là 0. */
    static long parseMoney(String s) {
        String digits = s == null ? "" : s.replaceAll("[^\\d]", "");
        if (digits.isEmpty()) {
            return 0;
        }
        return digits.length() > 15 ? 999_999_999_999_999L : Long.parseLong(digits);
    }

    /** {@code s.split(from).reverse().join(to)}. */
    static String reverse(String s, String from, String to) {
        String[] parts = s.split(Pattern.quote(from), -1);
        StringBuilder out = new StringBuilder();
        for (int i = parts.length - 1; i >= 0; i--) {
            out.append(parts[i]);
            if (i > 0) {
                out.append(to);
            }
        }
        return out.toString();
    }

    private static boolean notEmpty(String s) {
        return s != null && !s.isEmpty();
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
