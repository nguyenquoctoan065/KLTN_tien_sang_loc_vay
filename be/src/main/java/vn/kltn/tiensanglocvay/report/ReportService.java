package vn.kltn.tiensanglocvay.report;

import java.sql.Types;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.kltn.tiensanglocvay.engine.JsFormat;
import vn.kltn.tiensanglocvay.policy.ActivePolicy;
import vn.kltn.tiensanglocvay.report.ReportDtos.AccuracyResponse;
import vn.kltn.tiensanglocvay.report.ReportDtos.BandStat;
import vn.kltn.tiensanglocvay.report.ReportDtos.BranchStat;
import vn.kltn.tiensanglocvay.report.ReportDtos.Matrix;
import vn.kltn.tiensanglocvay.report.ReportDtos.MonthStat;
import vn.kltn.tiensanglocvay.report.ReportDtos.ProductAccuracy;
import vn.kltn.tiensanglocvay.report.ReportDtos.ProductStat;
import vn.kltn.tiensanglocvay.report.ReportDtos.ReportProposal;
import vn.kltn.tiensanglocvay.report.ReportDtos.ReportResponse;
import vn.kltn.tiensanglocvay.report.ReportDtos.StatsResponse;
import vn.kltn.tiensanglocvay.report.ReportDtos.StatusCount;
import vn.kltn.tiensanglocvay.report.ReportDtos.Totals;

/**
 * 8.1–8.3 theo fe/src/mocks/reportCalc.ts và docs/quy-tac-tinh-toan.md mục 15. Mọi con số do SQL tổng hợp trả về (không nạp hồ sơ vào
 * bộ nhớ). Ngày, tháng tính theo lịch giờ Việt Nam.
 */
@Service
public class ReportService {

    static final int DEFAULT_THRESHOLD = 55;
    /** Số hồ sơ tối thiểu để độ nhạy, độ chính xác dương, tập hồ sơ bị từ chối đủ tin cậy làm căn cứ đề xuất (= MIN_SAMPLE của reportCalc.ts). */
    static final int MIN_SAMPLE = 10;
    private static final String TZ = "'Asia/Ho_Chi_Minh'";
    private static final List<String> STATUSES = List.of("DRAFTING", "WAITING_VERIFY", "NEED_MORE", "VERIFIED", "APPROVED", "DISBURSED",
            "REJECTED", "CANCELLED", "EXPIRED");
    private static final List<String> PRODUCTS = List.of("TD", "TC");
    /** Nhóm điểm [lo, hi) – nhãn đúng chữ mock */
    private static final String[] BANDS = {"< 45", "45 – 55", "55 – 65", "65 – 75", "75 – 85", "≥ 85"};

    /** Hồ sơ tạo trong khoảng ngày (lọc chi nhánh nếu có) kèm các mốc lịch sử và kết quả xét duyệt mới nhất. */
    private static final String APPS = """
            WITH a AS (
                SELECT la.id, la.status, la.branch_id, lp.code AS product, la.created_at, ar.disbursed_amount
                FROM loan_application la
                JOIN loan_plan p ON p.id = la.plan_id
                JOIN loan_product lp ON lp.id = p.product_id
                LEFT JOIN LATERAL (SELECT r.disbursed_amount FROM approval_result r WHERE r.application_id = la.id
                                   ORDER BY r.id DESC LIMIT 1) ar ON true
                WHERE (la.created_at AT TIME ZONE %1$s)::date BETWEEN :from AND :to
                  AND (CAST(:branchId AS bigint) IS NULL OR la.branch_id = :branchId)
            ), h AS (
                SELECT s.application_id,
                       bool_or(s.to_status = 'WAITING_VERIFY') AS r_submitted, bool_or(s.to_status = 'VERIFIED') AS r_verified,
                       bool_or(s.to_status = 'APPROVED') AS r_approved, bool_or(s.to_status = 'DISBURSED') AS r_disbursed,
                       bool_or(s.to_status = 'REJECTED') AS r_rejected, bool_or(s.to_status = 'CANCELLED') AS r_cancelled,
                       bool_or(s.to_status = 'EXPIRED') AS r_expired,
                       min(s.created_at) FILTER (WHERE s.to_status = 'WAITING_VERIFY') AS submitted_at,
                       min(s.created_at) FILTER (WHERE s.to_status = 'DISBURSED') AS disbursed_at,
                       min(s.created_at) FILTER (WHERE s.to_status = 'REJECTED') AS rejected_at,
                       max(s.created_at) AS last_at
                FROM application_status_history s JOIN a ON a.id = s.application_id
                GROUP BY s.application_id
            ), x AS (SELECT a.*, h.* FROM a LEFT JOIN h ON h.application_id = a.id)
            """.formatted(TZ);

    /** Hồ sơ đã có kết quả ngân hàng, cập nhật đến hết ngày :until (nếu có); điểm dự đoán = điểm phương án trong hồ sơ. */
    private static final String CLOSED = """
            WITH c AS (
                SELECT la.status, la.status <> 'REJECTED' AS actual, p.score, lp.code AS product, ar.reason
                FROM loan_application la
                JOIN loan_plan p ON p.id = la.plan_id
                JOIN loan_product lp ON lp.id = p.product_id
                LEFT JOIN LATERAL (SELECT r.reason FROM approval_result r WHERE r.application_id = la.id
                                   ORDER BY r.id DESC LIMIT 1) ar ON true
                WHERE la.status IN ('APPROVED', 'DISBURSED', 'REJECTED')
                  AND (CAST(:until AS date) IS NULL OR (la.updated_at AT TIME ZONE %1$s)::date <= :until)
            )
            """.formatted(TZ);

    private final NamedParameterJdbcTemplate jdbc;
    private final ActivePolicy policy;
    private final Clock clock;

    public ReportService(NamedParameterJdbcTemplate jdbc, ActivePolicy policy, Clock clock) {
        this.jdbc = jdbc;
        this.policy = policy;
        this.clock = clock;
    }

    LocalDate today() {
        return LocalDate.now(clock);
    }

    // ============================ 8.1 Thống kê ============================

    @Transactional(readOnly = true)
    public StatsResponse stats(LocalDate from, LocalDate to, Long branchId) {
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("from", from, Types.DATE).addValue("to", to, Types.DATE).addValue("branchId", branchId, Types.BIGINT);

        Map<String, Object> t = jdbc.queryForMap(APPS + """
                SELECT count(*) AS applications,
                       count(*) FILTER (WHERE r_submitted) AS submitted, count(*) FILTER (WHERE r_verified) AS verified,
                       count(*) FILTER (WHERE r_approved) AS approved, count(*) FILTER (WHERE r_disbursed) AS disbursed,
                       count(*) FILTER (WHERE r_rejected) AS rejected, count(*) FILTER (WHERE r_cancelled) AS cancelled,
                       count(*) FILTER (WHERE r_expired) AS expired,
                       count(*) FILTER (WHERE status IN ('DISBURSED', 'REJECTED')) AS done,
                       coalesce(sum(extract(epoch FROM coalesce(last_at, created_at) - coalesce(submitted_at, created_at)))
                                FILTER (WHERE status IN ('DISBURSED', 'REJECTED')), 0) AS done_seconds,
                       coalesce(sum(disbursed_amount) FILTER (WHERE status = 'DISBURSED'), 0) AS disbursed_amount
                FROM x
                """, p);
        long applications = num(t, "applications");

        Map<String, Object> f = jdbc.queryForMap("""
                SELECT (SELECT count(*) FROM screening WHERE (created_at AT TIME ZONE %1$s)::date BETWEEN :from AND :to) AS screenings,
                       (SELECT count(*) FROM loan_plan WHERE (created_at AT TIME ZONE %1$s)::date BETWEEN :from AND :to) AS plans,
                       (SELECT count(*) FROM loan_application
                        WHERE (created_at AT TIME ZONE %1$s)::date BETWEEN :from AND :to) AS all_applications
                """.formatted(TZ), p);
        long screenings = num(f, "screenings");
        long plans = num(f, "plans");
        if (branchId != null) {
            // lượt sàng lọc, phương án không gắn chi nhánh: ước lượng theo tỷ trọng hồ sơ của chi nhánh (như mock)
            double share = (double) applications / Math.max(1, num(f, "all_applications"));
            screenings = Math.round(screenings * share);
            plans = Math.round(plans * share);
        }
        Totals totals = new Totals(screenings, plans, applications, num(t, "submitted"), num(t, "verified"), num(t, "approved"),
                num(t, "disbursed"), num(t, "rejected"), num(t, "cancelled"), num(t, "expired"));

        Map<String, Long> byStatusRaw = new HashMap<>();
        jdbc.query(APPS + "SELECT status, count(*) AS n FROM x GROUP BY status", p,
                rs -> { byStatusRaw.put(rs.getString("status"), rs.getLong("n")); });
        List<StatusCount> byStatus = STATUSES.stream().map(s -> new StatusCount(s, byStatusRaw.getOrDefault(s, 0L))).toList();

        Map<String, ProductStat> byProductRaw = new HashMap<>();
        jdbc.query(APPS + """
                SELECT product, count(*) AS n, coalesce(sum(disbursed_amount) FILTER (WHERE status = 'DISBURSED'), 0) AS amount
                FROM x GROUP BY product
                """, p, rs -> {
                    byProductRaw.put(rs.getString("product"),
                            new ProductStat(rs.getString("product"), rs.getLong("n"), rs.getLong("amount")));
                });
        List<ProductStat> byProduct = PRODUCTS.stream()
                .map(c -> byProductRaw.getOrDefault(c, new ProductStat(c, 0, 0))).toList();

        List<BranchStat> byBranch = jdbc.query(APPS + """
                SELECT b.name, count(x.id) AS n,
                       count(x.id) FILTER (WHERE x.status IN ('APPROVED', 'DISBURSED')) AS approved,
                       count(x.id) FILTER (WHERE x.status = 'REJECTED') AS rejected
                FROM branch b LEFT JOIN x ON x.branch_id = b.id
                WHERE CAST(:branchId AS bigint) IS NULL OR b.id = :branchId
                GROUP BY b.id, b.name ORDER BY b.id
                """, p, (rs, i) -> new BranchStat(rs.getString("name"), rs.getLong("n"), rs.getLong("approved"), rs.getLong("rejected")));

        Map<String, long[]> monthRaw = new HashMap<>();
        jdbc.query(APPS + """
                SELECT k, m, count(*) AS n FROM (
                    SELECT 0 AS k, to_char(created_at AT TIME ZONE %1$s, 'YYYY-MM') AS m FROM x
                    UNION ALL SELECT 1, to_char(disbursed_at AT TIME ZONE %1$s, 'YYYY-MM') FROM x WHERE disbursed_at IS NOT NULL
                    UNION ALL SELECT 2, to_char(rejected_at AT TIME ZONE %1$s, 'YYYY-MM') FROM x WHERE rejected_at IS NOT NULL
                ) e GROUP BY k, m
                """.formatted(TZ), p, rs -> {
                    monthRaw.computeIfAbsent(rs.getString("m"), m -> new long[3])[rs.getInt("k")] = rs.getLong("n");
                });
        List<MonthStat> byMonth = new ArrayList<>();
        for (YearMonth m = YearMonth.from(from); !m.isAfter(YearMonth.from(to)); m = m.plusMonths(1)) {
            long[] v = monthRaw.getOrDefault(m.toString(), new long[3]);
            byMonth.add(new MonthStat(m.toString(), v[0], v[1], v[2]));
        }

        long done = num(t, "done");
        double avgDays = done == 0 ? 0 : Math.round(((Number) t.get("done_seconds")).doubleValue() / 86400 / done * 10) / 10.0;
        return new StatsResponse(from, to, totals, byStatus, byProduct, byBranch, byMonth, avgDays, num(t, "disbursed_amount"));
    }

    // ============================ 8.2 Độ chính xác ============================

    @Transactional(readOnly = true)
    public AccuracyResponse accuracy(double threshold, LocalDate until) {
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("t", threshold, Types.DOUBLE).addValue("until", until, Types.DATE);

        Map<String, Object> m = jdbc.queryForMap(CLOSED + """
                SELECT count(*) AS n,
                       count(*) FILTER (WHERE score >= :t AND actual) AS tp, count(*) FILTER (WHERE score >= :t AND NOT actual) AS fp,
                       count(*) FILTER (WHERE score < :t AND NOT actual) AS tn, count(*) FILTER (WHERE score < :t AND actual) AS fn
                FROM c
                """, p);
        long n = num(m, "n");
        Matrix matrix = new Matrix(num(m, "tp"), num(m, "fp"), num(m, "tn"), num(m, "fn"));

        long[][] bands = new long[BANDS.length][2];
        jdbc.query(CLOSED + """
                SELECT band, count(*) AS total, count(*) FILTER (WHERE actual) AS approved FROM (
                    SELECT actual, CASE WHEN score >= 0 AND score < 45 THEN 0 WHEN score >= 45 AND score < 55 THEN 1
                                        WHEN score >= 55 AND score < 65 THEN 2 WHEN score >= 65 AND score < 75 THEN 3
                                        WHEN score >= 75 AND score < 85 THEN 4 WHEN score >= 85 AND score < 101 THEN 5 END AS band
                    FROM c
                ) b WHERE band IS NOT NULL GROUP BY band
                """, p, rs -> {
                    bands[rs.getInt("band")][0] = rs.getLong("total");
                    bands[rs.getInt("band")][1] = rs.getLong("approved");
                });
        List<BandStat> byBand = new ArrayList<>();
        for (int i = 0; i < BANDS.length; i++) {
            long total = bands[i][0];
            byBand.add(new BandStat(BANDS[i], total, bands[i][1], total == 0 ? 0 : r1((double) bands[i][1] / total)));
        }

        Map<String, long[]> prod = new HashMap<>();
        jdbc.query(CLOSED + """
                SELECT product, count(*) AS n, count(*) FILTER (WHERE (score >= :t) = actual) AS ok FROM c GROUP BY product
                """, p, rs -> {
                    prod.put(rs.getString("product"), new long[] {rs.getLong("n"), rs.getLong("ok")});
                });
        List<ProductAccuracy> byProduct = PRODUCTS.stream().map(c -> {
            long[] v = prod.getOrDefault(c, new long[2]);
            return new ProductAccuracy(c, v[0], v[0] == 0 ? 0 : r1((double) v[1] / v[0]));
        }).toList();

        // null = chưa đủ dữ liệu: độ chính xác khi chưa có hồ sơ; độ nhạy khi < MIN_SAMPLE hồ sơ có kết quả;
        // độ chính xác dương khi < MIN_SAMPLE hồ sơ được đánh giá đạt
        long tp = matrix.tp();
        Double accuracy = n == 0 ? null : r1((double) (tp + matrix.tn()) / n);
        Double precision = tp + matrix.fp() >= MIN_SAMPLE ? r1((double) tp / (tp + matrix.fp())) : null;
        Double recall = n >= MIN_SAMPLE ? r1((double) tp / Math.max(1, tp + matrix.fn())) : null;
        return new AccuracyResponse(threshold, n, matrix, accuracy, precision, recall, byBand, byProduct);
    }

    // ============================ 8.3 MB14 ============================

    @Transactional(readOnly = true)
    public ReportResponse mb14(YearMonth month) {
        LocalDate from = month.atDay(1);
        LocalDate to = month.atEndOfMonth();
        StatsResponse st = stats(from, to, null);
        AccuracyResponse acc = accuracy(DEFAULT_THRESHOLD, to);
        Map<String, String> params = policy.current().common();
        List<ReportProposal> proposals = new ArrayList<>();

        if (acc.sample() == 0) {
            // chưa có hồ sơ nào có kết quả đến cuối kỳ: không có căn cứ để đề xuất
            proposals.add(new ReportProposal("no-data", "Chưa đủ dữ liệu để đề xuất hiệu chỉnh",
                    "Chưa có hồ sơ nào có kết quả ngân hàng đến cuối kỳ.", null, null, null, null));
            return new ReportResponse(month.toString(), OffsetDateTime.now(clock), st, acc, proposals);
        }
        Double recall = acc.recall();
        Double precision = acc.precision();
        BandStat lowBand = acc.byBand().get(1);
        boolean bothLow = recall != null && precision != null && recall < 75 && precision < 75;
        if (bothLow) {
            // bỏ sót nhiều và đánh giá đạt nhầm nhiều cùng lúc: đổi ngưỡng DTI không giải quyết được, lỗi nằm ở cách chấm điểm
            proposals.add(new ReportProposal("weights-review", "Xem lại ma trận trọng số",
                    "Độ nhạy (" + JsFormat.num(recall) + "%) và độ chính xác dương (" + JsFormat.num(precision)
                            + "%) cùng thấp hơn 75%: đổi ngưỡng DTI chưa giải quyết được. Cân nhắc xem lại ma trận trọng số điểm "
                            + "tương thích của các gói.", null, null, null, null));
        } else {
            if (recall != null && recall < 75 && lowBand.rate() >= 40) {
                double cur = number(params.get("DTI_MAX"));
                if (cur < 80) {
                    proposals.add(new ReportProposal("dti-up", "Nới ngưỡng DTI tối đa",
                            "Tỷ lệ bỏ sót hồ sơ được duyệt cao (độ nhạy " + JsFormat.num(recall) + "%), nhóm điểm 45–55 vẫn được duyệt "
                                    + JsFormat.num(lowBand.rate()) + "%. Đề xuất tăng DTI tối đa thêm 2 điểm phần trăm.",
                            "DTI_MAX", null, JsFormat.num(cur), JsFormat.num(Math.min(80, cur + 2))));
                }
            }
            if (precision != null && precision < 75) {
                double cur = number(params.get("DTI_MAX"));
                if (cur > 65) {
                    proposals.add(new ReportProposal("dti-down", "Siết ngưỡng DTI tối đa",
                            "Độ chính xác dương thấp (" + JsFormat.num(precision)
                                    + "%): nhiều hồ sơ hệ thống đánh giá đạt nhưng bị từ chối. Đề xuất giảm DTI tối đa 2 điểm phần trăm.",
                            "DTI_MAX", null, JsFormat.num(cur), JsFormat.num(Math.max(65, cur - 2))));
                }
            }
        }
        // lũy kế đến cuối kỳ, cùng phạm vi với phần độ chính xác
        Map<String, Object> rj = jdbc.queryForMap(CLOSED + """
                SELECT count(*) FILTER (WHERE status = 'REJECTED') AS rejects,
                       count(*) FILTER (WHERE status = 'REJECTED' AND reason LIKE :income) AS income
                FROM c
                """, new MapSqlParameterSource().addValue("until", to, Types.DATE).addValue("income", "%Thu nhập%"));
        long rejects = num(rj, "rejects");
        long income = num(rj, "income");
        if (rejects >= MIN_SAMPLE && (double) income / rejects >= 0.3) {
            double cur = number(params.get("LIVING_COST_BASE"));
            proposals.add(new ReportProposal("living-up", "Tăng chi phí sinh hoạt định mức",
                    income + "/" + rejects + " hồ sơ bị từ chối vì thu nhập thực tế không đủ trả nợ. Đề xuất tăng chi phí sinh hoạt định mức "
                            + "500.000 đ để hạn mức an toàn thận trọng hơn.",
                    "LIVING_COST_BASE", null, JsFormat.num(cur), JsFormat.num(cur + 500_000)));
        }
        ProductAccuracy tc = acc.byProduct().get(1);
        // sample > 0 ở đây nên accuracy khác null; đã có đề xuất xem lại trọng số chung thì không nhắc lại riêng gói thấu chi
        if (!bothLow && tc.sample() >= 5 && tc.accuracy() < acc.accuracy() - 5) {
            proposals.add(new ReportProposal("tc-weight", "Xem lại ma trận trọng số vay thấu chi",
                    "Độ chính xác của gói thấu chi (" + JsFormat.num(tc.accuracy()) + "%) thấp hơn mức chung (" + JsFormat.num(acc.accuracy())
                            + "%). Cân nhắc tăng trọng số “Ngân hàng nhận lương” và “CBNV ABBANK”.",
                    null, "TC", null, null));
        }
        if (proposals.isEmpty() && acc.sample() < MIN_SAMPLE) {
            proposals.add(new ReportProposal("keep-low-data", "Giữ nguyên tham số (dữ liệu còn ít: " + acc.sample()
                    + " hồ sơ có kết quả, cần thêm để đánh giá chính xác)",
                    "Chưa đủ hồ sơ có kết quả để kết luận độ nhạy, độ chính xác dương; giữ nguyên tham số cho đến khi có thêm dữ liệu.",
                    null, null, null, null));
        }
        if (proposals.isEmpty()) {
            proposals.add(new ReportProposal("keep", "Giữ nguyên tham số", "Các chỉ số trong kỳ ổn định, chưa cần hiệu chỉnh.",
                    null, null, null, null));
        }
        return new ReportResponse(month.toString(), OffsetDateTime.now(clock), st, acc, proposals);
    }

    /** {@code Math.round(x * 1000) / 10} của mock: tỷ lệ % làm tròn 1 chữ số. */
    private static double r1(double x) {
        return Math.round(x * 1000) / 10.0;
    }

    /** {@code Number(value)} của JS cho giá trị tham số (thiếu → 0, như {@code Number(null)}). */
    private static double number(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    private static long num(Map<String, Object> row, String key) {
        Object v = row.get(key);
        return v == null ? 0 : ((Number) v).longValue();
    }
}
