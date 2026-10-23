package vn.kltn.tiensanglocvay.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import vn.kltn.tiensanglocvay.admin.AdminTestHelper;

/**
 * B8: 8.1–8.3 đối chiếu với cách tính của mock. Bộ dữ liệu và kết quả mong đợi sinh từ fe/src/mocks/reportCalc.ts bằng
 * be/tools/gen-report-fixtures.ts (report-fixtures/report-cases.json); dữ liệu được nạp thẳng vào các bảng thật.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class ReportApiTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    private static JsonNode fixture;

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    org.springframework.core.env.Environment env;

    AdminTestHelper h;

    @BeforeEach
    void setUp() throws Exception {
        h = new AdminTestHelper(mvc, json);
        if (fixture == null) {
            try (InputStream in = getClass().getResourceAsStream("/report-fixtures/report-cases.json")) {
                fixture = json.readTree(in);
            }
            load(fixture.get("dataset"));
        }
    }

    /** Nạp bộ dữ liệu: mỗi hồ sơ một lượt sàng lọc, một phương án, lịch sử trạng thái và kết quả xét duyệt (khách hàng mẫu id 1). */
    private void load(JsonNode ds) {
        long[] staffOfBranch = {0, 101, 103, 104};
        for (JsonNode a : ds.get("applications")) {
            long screening = screening(time(a, "screeningAt"));
            long plan = plan(screening, a.get("productCode").asText(), a.get("score").decimalValue(), time(a, "planAt"));
            Long consent = consent(time(a, "createdAt"));
            Long app = jdbc.queryForObject("""
                    INSERT INTO loan_application (code, customer_id, plan_id, branch_id, consent_id, version_id, status, created_at, updated_at)
                    VALUES (?, 1, ?, ?, ?, 1, ?, ?, ?) RETURNING id
                    """, Long.class, a.get("key").asText(), plan, a.get("branchId").asInt(), consent, a.get("status").asText(),
                    time(a, "createdAt"), time(a, "updatedAt"));
            String from = null;
            for (JsonNode e : a.get("history")) {
                jdbc.update("""
                        INSERT INTO application_status_history (application_id, from_status, to_status, actor_type, actor_name, created_at)
                        VALUES (?, ?, ?, 'STAFF', 'Nhân viên thử', ?)
                        """, app, from, e.get("status").asText(), time(e, "at"));
                from = e.get("status").asText();
            }
            JsonNode r = a.get("approval");
            if (!r.isNull()) {
                jdbc.update("""
                        INSERT INTO approval_result (application_id, staff_id, result_type, reason, recorded_at, disbursed_amount)
                        VALUES (?, ?, ?, ?, ?, ?)
                        """, app, staffOfBranch[a.get("branchId").asInt()], r.get("resultType").asText(),
                        r.get("reason").isNull() ? null : r.get("reason").asText(), time(r, "recordedAt"),
                        r.get("disbursedAmount").isNull() ? null : r.get("disbursedAmount").asLong());
            }
        }
        for (JsonNode t : ds.get("extraScreenings")) {
            screening(OffsetDateTime.parse(t.asText()));
        }
        // phương án nháp không thành hồ sơ, gắn vào một lượt sàng lọc đã có (không thêm lượt sàng lọc)
        Long anyScreening = jdbc.queryForObject("SELECT min(id) FROM screening", Long.class);
        for (JsonNode t : ds.get("extraPlans")) {
            plan(anyScreening, "TD", new java.math.BigDecimal("60"), OffsetDateTime.parse(t.asText()));
        }
    }

    private Long consent(OffsetDateTime at) {
        return jdbc.queryForObject("INSERT INTO consent (customer_id, scope, doc_version, agreed_at) VALUES (1, 'A', 'DK-1.0', ?) RETURNING id",
                Long.class, at);
    }

    private long screening(OffsetDateTime at) {
        return jdbc.queryForObject("""
                INSERT INTO screening (customer_id, version_id, consent_id, nationality, dob, gender, marital_status, dependents, avg_income,
                                       salary_bank, is_abbank_staff, credit_history, purpose, desired_amount, desired_term, created_at)
                VALUES (NULL, 1, ?, 'Việt Nam', DATE '1990-05-15', 'MALE', 'SINGLE', 0, 20000000, 'ABBANK', false, 'ON_TIME',
                        'Mua sắm', 50000000, 12, ?) RETURNING id
                """, Long.class, consent(at), at);
    }

    private long plan(long screening, String product, java.math.BigDecimal score, OffsetDateTime at) {
        return jdbc.queryForObject("""
                INSERT INTO loan_plan (screening_id, product_id, amount, term_months, ref_rate, monthly_payment, total_interest, dti_after,
                                       score, status, created_at)
                VALUES (?, (SELECT id FROM loan_product WHERE code = ?), 50000000, 12, 12.5, 4687500, 3385417, 30, ?, 'CONFIRMED', ?)
                RETURNING id
                """, Long.class, screening, product, score, at);
    }

    private static OffsetDateTime time(JsonNode n, String field) {
        return OffsetDateTime.parse(n.get(field).asText());
    }

    @Test
    void matchesMockCalculation() throws Exception {
        String admin = h.admin();
        List<String> diffs = new ArrayList<>();
        for (JsonNode c : fixture.get("cases")) {
            String body = AdminTestHelper.body(h.send(get(c.get("path").asText()), null, admin).andExpect(status().isOk()));
            compare(c.get("name").asText(), "$", c.get("expected"), json.readTree(body), diffs);
        }
        assertThat(diffs).isEmpty();
    }

    /** So cây JSON: số so theo giá trị (55 = 55.0), bỏ qua generatedAt; trường thừa ở backend cũng tính là lệch. */
    private static void compare(String name, String path, JsonNode exp, JsonNode act, List<String> diffs) {
        if (act == null) {
            diffs.add(name + " " + path + ": thiếu");
        } else if (exp.isNumber()) {
            if (!act.isNumber() || Math.abs(exp.doubleValue() - act.doubleValue()) > 1e-9) {
                diffs.add(name + " " + path + ": mong đợi " + exp + ", nhận " + act);
            }
        } else if (exp.isObject()) {
            if (!act.isObject()) {
                diffs.add(name + " " + path + ": mong đợi object, nhận " + act);
                return;
            }
            for (Iterator<Map.Entry<String, JsonNode>> it = exp.fields(); it.hasNext(); ) {
                Map.Entry<String, JsonNode> e = it.next();
                if (!e.getKey().equals("generatedAt")) {
                    compare(name, path + "." + e.getKey(), e.getValue(), act.get(e.getKey()), diffs);
                }
            }
            for (Iterator<String> it = act.fieldNames(); it.hasNext(); ) {
                String k = it.next();
                if (!exp.has(k)) {
                    diffs.add(name + " " + path + "." + k + ": thừa");
                }
            }
        } else if (exp.isArray()) {
            if (!act.isArray() || act.size() != exp.size()) {
                diffs.add(name + " " + path + ": mong đợi " + exp + ", nhận " + act);
                return;
            }
            for (int i = 0; i < exp.size(); i++) {
                compare(name, path + "[" + i + "]", exp.get(i), act.get(i), diffs);
            }
        } else if (!exp.equals(act)) {
            diffs.add(name + " " + path + ": mong đợi " + exp + ", nhận " + act);
        }
    }

    @Test
    void roles_onlyAdmin() throws Exception {
        String staff = h.staffToken("nhanvien@tiensangloc.local");
        String customer = h.newCustomer();
        for (String path : new String[] {"/admin/stats", "/admin/model-accuracy", "/admin/reports/mb14"}) {
            h.send(get(path), null, null).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.message").value("Chưa đăng nhập."));
            h.send(get(path), null, staff).andExpect(status().isForbidden()).andExpect(jsonPath("$.message").value(AdminTestHelper.FORBIDDEN));
            h.send(get(path), null, customer).andExpect(status().isForbidden()).andExpect(jsonPath("$.message").value(AdminTestHelper.FORBIDDEN));
        }
    }

    @Test
    void invalidParams_400() throws Exception {
        String admin = h.admin();
        h.send(get("/admin/stats?from=2026-13-01&to=abc&branchId=x"), null, admin).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Dữ liệu không hợp lệ."))
                .andExpect(jsonPath("$.fields.from").exists()).andExpect(jsonPath("$.fields.to").exists())
                .andExpect(jsonPath("$.fields.branchId").exists());
        h.send(get("/admin/stats?from=2000-01-01&to=2026-10-01"), null, admin).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.from").value("Khoảng thời gian tối đa 10 năm."));
        for (String m : new String[] {"2026-1", "2026-13", "10-2026", "abc"}) {
            h.send(get("/admin/reports/mb14?month=" + m), null, admin).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fields.month").value("Tháng phải có dạng YYYY-MM."));
        }
    }

    @Test
    void defaults_todayVietnam() throws Exception {
        String admin = h.admin();
        LocalDate today = LocalDate.now(VN);
        JsonNode s = json.readTree(AdminTestHelper.body(h.send(get("/admin/stats"), null, admin).andExpect(status().isOk())));
        assertThat(s.get("to").asText()).isEqualTo(today.toString());
        assertThat(s.get("from").asText()).isEqualTo(today.minusDays(180).toString());
        assertThat(s.get("byMonth").get(0).get("month").asText()).isEqualTo(YearMonth.from(today.minusDays(180)).toString());
        assertThat(s.get("byMonth").get(s.get("byMonth").size() - 1).get("month").asText()).isEqualTo(YearMonth.from(today).toString());

        h.send(get("/admin/reports/mb14"), null, admin).andExpect(status().isOk())
                .andExpect(jsonPath("$.period").value(YearMonth.from(today).toString()))
                .andExpect(jsonPath("$.stats.from").value(YearMonth.from(today).atDay(1).toString()));

        // mock: Number(branchId) || null – 0 là toàn hệ thống; chi nhánh không tồn tại → không có số liệu
        String all = AdminTestHelper.body(h.send(get("/admin/stats?from=2026-07-01&to=2026-10-31"), null, admin));
        String zero = AdminTestHelper.body(h.send(get("/admin/stats?from=2026-07-01&to=2026-10-31&branchId=0"), null, admin));
        assertThat(json.readTree(zero)).isEqualTo(json.readTree(all));
        h.send(get("/admin/stats?from=2026-07-01&to=2026-10-31&branchId=999"), null, admin).andExpect(status().isOk())
                .andExpect(jsonPath("$.totals.applications").value(0)).andExpect(jsonPath("$.byBranch.length()").value(0));
        h.send(get("/admin/model-accuracy?threshold=0"), null, admin).andExpect(jsonPath("$.threshold").value(55.0));
    }

    @Test
    void devHistoryNotAppliedInTests() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE version = '1000'", Integer.class)).isZero();
        // out-of-order và thư mục db/dev chỉ bật ở profile dev (application-dev.yml), không ở cấu hình mặc định/test
        assertThat(env.getProperty("spring.flyway.out-of-order", Boolean.class, false)).isFalse();
        assertThat(env.getProperty("spring.flyway.locations")).isEqualTo("classpath:db/migration");
    }
}
