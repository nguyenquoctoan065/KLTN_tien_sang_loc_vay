package vn.kltn.tiensanglocvay.plan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import vn.kltn.tiensanglocvay.engine.CreditHistory;
import vn.kltn.tiensanglocvay.engine.Engine;
import vn.kltn.tiensanglocvay.engine.EngineInput;
import vn.kltn.tiensanglocvay.engine.Gender;
import vn.kltn.tiensanglocvay.engine.ProductCode;
import vn.kltn.tiensanglocvay.engine.SimulationInput;
import vn.kltn.tiensanglocvay.engine.SimulationResponse;
import vn.kltn.tiensanglocvay.policy.ActivePolicy;
import vn.kltn.tiensanglocvay.policy.EngineParams;
import vn.kltn.tiensanglocvay.screening.SimulationService;

/** B3: /plans, /plans/{id}, /plans/{id}/confirm, /me/plans. Thông báo lỗi đối chiếu với fe/src/mocks/handlers/screening.ts. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class PlanApiTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    private static final AtomicInteger SEQ = new AtomicInteger(30_000_000);
    private static final String UUID_RE = "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$";

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ObjectMapper json;

    @Autowired
    ActivePolicy policy;

    @Autowired
    SimulationService simulations;

    // ---------------------------------------------------------------- tiện ích

    private static Map<String, Object> screening() {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("consentA", Map.of("agreed", true, "docVersion", "DK-1.0"));
        r.put("nationality", "Việt Nam");
        r.put("dob", "1992-05-15");
        r.put("gender", "MALE");
        r.put("maritalStatus", "MARRIED");
        r.put("dependents", 1);
        r.put("avgIncome", 20_000_000);
        r.put("salaryBank", "Vietcombank");
        r.put("isAbbankStaff", false);
        r.put("creditHistory", "ON_TIME");
        r.put("otherDebts", List.of());
        r.put("purpose", "Mua xe máy, ô tô");
        r.put("desiredAmount", 150_000_000);
        r.put("desiredTerm", 36);
        return r;
    }

    /** Cùng input như {@link #screening()} cho bộ máy, để tính thông báo 422 mong đợi. */
    private static EngineInput engineInput() {
        return new EngineInput("Việt Nam", LocalDate.of(1992, 5, 15), Gender.MALE, 1, 20_000_000, "Vietcombank", false,
                CreditHistory.ON_TIME, List.of(), "Mua xe máy, ô tô", 150_000_000, 36);
    }

    private static Map<String, Object> plan(Object screeningId, String product, long amount, int term, List<Integer> cs) {
        return Map.of("screeningId", screeningId, "productCode", product, "amount", amount, "termMonths", term, "crossSellIds", cs);
    }

    private ResultActions send(MockHttpServletRequestBuilder req, Object body, String token) throws Exception {
        if (body != null) {
            req = req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        }
        if (token != null) {
            req = req.header("Authorization", "Bearer " + token);
        }
        return mvc.perform(req);
    }

    private static String id(ResultActions r) throws Exception {
        return JsonPath.read(r.andReturn().getResponse().getContentAsString(), "$.id");
    }

    private String customer() throws Exception {
        String body = "{\"kind\":\"CUSTOMER\",\"username\":\"0901234567\",\"password\":\"123456\"}";
        return JsonPath.read(mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.token");
    }

    private String staff() throws Exception {
        String body = "{\"kind\":\"STAFF\",\"username\":\"nhanvien@tiensangloc.local\",\"password\":\"123456\"}";
        return JsonPath.read(mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.token");
    }

    private String newCustomer() throws Exception {
        String phone = "09" + SEQ.incrementAndGet();
        send(post("/auth/otp"), Map.of("phone", phone), null).andExpect(status().isOk());
        return JsonPath.read(send(post("/auth/register"), Map.of("phone", phone, "password", "123456", "otp", "123456"), null)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.token");
    }

    private String newScreening(String token) throws Exception {
        return id(send(post("/screenings"), screening(), token).andExpect(status().isCreated()));
    }

    private String newPlan(String token) throws Exception {
        return id(send(post("/plans"), plan(newScreening(null), "TD", 150_000_000, 36, List.of(2, 3)), token)
                .andExpect(status().isCreated()));
    }

    // ---------------------------------------------------------------- POST /plans

    @Test
    void create_savesDraftWithChecklistAndCrossSells() throws Exception {
        String token = newCustomer();
        String screeningId = newScreening(null);
        String planId = id(send(post("/plans"), plan(screeningId, "TD", 150_000_000, 36, List.of(3, 2)), token)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.screeningId").value(screeningId))
                .andExpect(jsonPath("$.productCode").value("TD"))
                .andExpect(jsonPath("$.productName").value("Vay tiêu dùng không tài sản bảo đảm"))
                .andExpect(jsonPath("$.amount").value(150_000_000))
                .andExpect(jsonPath("$.termMonths").value(36))
                .andExpect(jsonPath("$.refRate").value(11.3))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.applicationId").value((Object) null))
                .andExpect(jsonPath("$.crossSells.length()").value(2))
                .andExpect(jsonPath("$.crossSells[0].id").value(2))
                .andExpect(jsonPath("$.crossSells[0].name").value("Thẻ tín dụng"))
                .andExpect(jsonPath("$.crossSells[0].extraCost").value(0))
                .andExpect(jsonPath("$.crossSells[0].benefit").value("Giảm 0.5%/năm lãi suất tham chiếu, +3 điểm ưu tiên"))
                .andExpect(jsonPath("$.crossSells[1].benefit").value("Giảm 0.7%/năm lãi suất tham chiếu, +4 điểm ưu tiên"))
                .andExpect(jsonPath("$.checklist.length()").value(6))
                .andExpect(jsonPath("$.checklist[0].name").value("Giấy đề nghị vay vốn (mẫu ngân hàng) – hệ thống tự lập")));

        // Phương án gắn lượt sàng lọc ẩn danh vào khách hàng; số liệu khớp mô phỏng.
        Map<String, Object> row = jdbc.queryForMap("select p.monthly_payment, p.total_interest, p.status, s.customer_id from loan_plan p "
                + "join screening s on s.id = p.screening_id where p.public_id = ?::uuid", planId);
        assertThat(row.get("status")).isEqualTo("DRAFT");
        assertThat(row.get("customer_id")).isNotNull();
        assertThat(planId).matches(UUID_RE);
        // Lượt sàng lọc nay thuộc khách hàng: chỉ chủ mô phỏng được
        String sim = send(post("/simulations"), plan(screeningId, "TD", 150_000_000, 36, List.of(3, 2)), token)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        send(post("/simulations"), plan(screeningId, "TD", 150_000_000, 36, List.of(3, 2)), null).andExpect(status().isNotFound());
        assertThat(((Number) row.get("monthly_payment")).longValue()).isEqualTo(((Number) JsonPath.read(sim, "$.firstPayment")).longValue());
        assertThat(((Number) row.get("total_interest")).longValue()).isEqualTo(((Number) JsonPath.read(sim, "$.totalInterest")).longValue());
        assertThat(jdbc.queryForObject("select count(*) from activity_log where action = 'Lưu phương án vay' and detail = ?",
                Integer.class, "Phương án #" + planId.substring(0, 8))).isEqualTo(1);
    }

    @Test
    void create_requiresCustomer() throws Exception {
        String screeningId = newScreening(null);
        send(post("/plans"), plan(screeningId, "TD", 150_000_000, 36, List.of()), null).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Cần đăng nhập tài khoản khách hàng để lưu phương án."));
        send(post("/plans"), plan(screeningId, "TD", 150_000_000, 36, List.of()), staff()).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Cần đăng nhập tài khoản khách hàng để lưu phương án."));
        // Kiểm tra đăng nhập trước dữ liệu, như mock
        send(post("/plans"), Map.of(), null).andExpect(status().isUnauthorized());
        // ...và trước khi đọc body: body sai định dạng (screeningId không phải UUID) hoặc không phải JSON vẫn 401
        send(post("/plans"), Map.of("screeningId", 12), null).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Cần đăng nhập tài khoản khách hàng để lưu phương án."));
        mvc.perform(post("/plans").contentType(MediaType.APPLICATION_JSON).content("{khong phai json"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Cần đăng nhập tài khoản khách hàng để lưu phương án."));
        mvc.perform(post("/plans").contentType(MediaType.APPLICATION_JSON).content("{khong phai json")
                .header("Authorization", "Bearer " + staff())).andExpect(status().isUnauthorized());
        // Đã đăng nhập khách hàng thì body sai là 400
        send(post("/plans"), Map.of("screeningId", 12), customer()).andExpect(status().isBadRequest());
    }

    @Test
    void create_overThreshold_is422WithIssues() throws Exception {
        String screeningId = newScreening(null);
        var p = policy.current();
        SimulationResponse expected = Engine.simulate(engineInput(), EngineParams.paramSet(p), EngineParams.weightSet(p),
                new SimulationInput(ProductCode.TD, 450_000_000, 36, List.of()), simulations.allCrossSells(),
                LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")));
        assertThat(expected.ok()).isFalse();

        send(post("/plans"), plan(screeningId, "TD", 450_000_000, 36, List.of()), customer()).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("Phương án chưa đạt: " + String.join(" ", expected.issues())));
        assertThat(jdbc.queryForObject("select count(*) from loan_plan where screening_id = (select id from screening where public_id = ?::uuid)", Integer.class, screeningId)).isZero();
    }

    @Test
    void create_errors() throws Exception {
        String token = customer();
        send(post("/plans"), plan(UUID.randomUUID().toString(), "TD", 150_000_000, 36, List.of()), token).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Không tìm thấy lượt sàng lọc."));

        Map<String, Object> business = screening();
        business.put("purpose", "Kinh doanh");
        String excluded = id(send(post("/screenings"), business, null).andExpect(status().isCreated()));
        send(post("/plans"), plan(excluded, "TC", 50_000_000, 12, List.of()), token).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("Hồ sơ không đạt điều kiện của gói vay này."));

        String ofOther = newScreening(newCustomer());
        send(post("/plans"), plan(ofOther, "TD", 150_000_000, 36, List.of()), token).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Không tìm thấy lượt sàng lọc."));

        send(post("/plans"), Map.of("screeningId", ofOther), token).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Dữ liệu không hợp lệ."));
    }

    // ---------------------------------------------------------------- GET /plans/{id}, POST /plans/{id}/confirm, GET /me/plans

    @Test
    void get_onlyOwner() throws Exception {
        String owner = newCustomer();
        String planId = newPlan(owner);

        send(get("/plans/" + planId), null, owner).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(planId))
                .andExpect(jsonPath("$.applicationId").value((Object) null));
        for (String other : new String[] {null, customer(), staff()}) {
            send(get("/plans/" + planId), null, other).andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("Không tìm thấy phương án."));
        }
        String numericId = String.valueOf(jdbc.queryForObject("select id from loan_plan where public_id = ?::uuid", Long.class, planId));
        for (String bad : new String[] {"999999", numericId, "abc", UUID.randomUUID().toString()}) {
            send(get("/plans/" + bad), null, owner).andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("Không tìm thấy phương án."));
            send(post("/plans/" + bad + "/confirm"), null, owner).andExpect(status().isNotFound());
        }
    }

    @Test
    void get_applicationIdIsPublicIdOfOpenApplication() throws Exception {
        String owner = newCustomer();
        String planId = newPlan(owner);
        Long customerId = jdbc.queryForObject("select s.customer_id from loan_plan p join screening s on s.id = p.screening_id "
                + "where p.public_id = ?::uuid", Long.class, planId);
        Long consentId = jdbc.queryForObject("insert into consent (customer_id, scope, doc_version) values (?, 'B', 'DK-1.0') returning id",
                Long.class, customerId);
        String appId = jdbc.queryForObject("insert into loan_application (code, customer_id, plan_id, branch_id, consent_id, status) "
                + "select 'HS-TEST-' || p.id, ?, p.id, 1, ?, 'DRAFTING' from loan_plan p where p.public_id = ?::uuid returning public_id::text",
                String.class, customerId, consentId, planId);

        send(get("/plans/" + planId), null, owner).andExpect(status().isOk())
                .andExpect(jsonPath("$.applicationId").value(appId));
        assertThat(appId).matches(UUID_RE);

        jdbc.update("update loan_application set status = 'CANCELLED' where public_id = ?::uuid", appId);
        send(get("/plans/" + planId), null, owner).andExpect(jsonPath("$.applicationId").value((Object) null));
    }

    @Test
    void confirm_setsConfirmedAndLogs() throws Exception {
        String owner = newCustomer();
        String planId = newPlan(owner);

        send(post("/plans/" + planId + "/confirm"), null, customer()).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Không tìm thấy phương án."));
        send(post("/plans/" + planId + "/confirm"), null, null).andExpect(status().isNotFound());

        send(post("/plans/" + planId + "/confirm"), null, owner).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
        send(get("/plans/" + planId), null, owner).andExpect(jsonPath("$.status").value("CONFIRMED"));
        assertThat(jdbc.queryForObject("select count(*) from activity_log where action = 'Xác nhận phương án vay' and detail = ?",
                Integer.class, "Phương án #" + planId.substring(0, 8))).isEqualTo(1);
    }

    @Test
    void mine_listsOwnPlansNewestFirst() throws Exception {
        String owner = newCustomer();
        String first = newPlan(owner);
        String second = newPlan(owner);
        newPlan(newCustomer());

        send(get("/me/plans"), null, owner).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(second))
                .andExpect(jsonPath("$[1].id").value(first));
        send(get("/me/plans"), null, staff()).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        send(get("/me/plans"), null, null).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Chưa đăng nhập."));
    }
}
