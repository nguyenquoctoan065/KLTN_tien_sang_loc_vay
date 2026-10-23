package vn.kltn.tiensanglocvay.screening;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
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

/**
 * B3: /screenings, /screenings/{id}, /screenings/{id}/save, /me/screenings, /simulations.
 * Thông báo lỗi đối chiếu với fe/src/mocks/handlers/screening.ts; con số chi tiết của bộ máy đã có EngineFixtureTest.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class ScreeningApiTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    private static final AtomicInteger SEQ = new AtomicInteger(20_000_000);
    static final String UUID_RE = "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$";

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ObjectMapper json;

    // ---------------------------------------------------------------- tiện ích

    static Map<String, Object> request() {
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
        r.put("otherDebts", List.of(Map.of("lender", "Ngân hàng A", "monthlyPayment", 1_000_000)));
        r.put("purpose", "Mua xe máy, ô tô");
        r.put("desiredAmount", 150_000_000);
        r.put("desiredTerm", 36);
        return r;
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

    private String token(String kind, String username) throws Exception {
        String body = "{\"kind\":\"%s\",\"username\":\"%s\",\"password\":\"123456\"}".formatted(kind, username);
        return JsonPath.read(mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.token");
    }

    private String customer() throws Exception {
        return token("CUSTOMER", "0901234567");
    }

    /** Khách hàng mới đăng ký qua OTP dev (để thử lượt sàng lọc của khách khác). */
    private String newCustomer() throws Exception {
        String phone = "09" + SEQ.incrementAndGet();
        send(post("/auth/otp"), Map.of("phone", phone), null).andExpect(status().isOk());
        return JsonPath.read(send(post("/auth/register"), Map.of("phone", phone, "password", "123456", "otp", "123456"), null)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.token");
    }

    private String createScreening(Map<String, Object> body, String token) throws Exception {
        return JsonPath.read(send(post("/screenings"), body, token).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");
    }

    private static Map<String, Object> sim(Object screeningId, String product, long amount, int term, List<Integer> cs) {
        return Map.of("screeningId", screeningId, "productCode", product, "amount", amount, "termMonths", term, "crossSellIds", cs);
    }

    // ---------------------------------------------------------------- POST /screenings

    @Test
    void create_anonymous_returnsMb03AndRecordsConsentA() throws Exception {
        String body = send(post("/screenings"), request(), null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(org.hamcrest.Matchers.matchesPattern(UUID_RE)))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.versionNo").value("2026.10"))
                .andExpect(jsonPath("$.savedToCustomerId").value((Object) null))
                .andExpect(jsonPath("$.input.consentA.docVersion").value("DK-1.0"))
                .andExpect(jsonPath("$.input.isAbbankStaff").value(false))
                .andExpect(jsonPath("$.input.otherDebts[0].lender").value("Ngân hàng A"))
                .andExpect(jsonPath("$.input.otherDebts[0].monthlyPayment").value(1_000_000))
                .andExpect(jsonPath("$.livingCost").value(6_000_000))
                .andExpect(jsonPath("$.disposableIncome").value(13_000_000))
                .andExpect(jsonPath("$.currentDti").value(5.0))
                .andExpect(jsonPath("$.notes.length()").value(0))
                .andExpect(jsonPath("$.results.length()").value(2))
                .andExpect(jsonPath("$.results[0].productCode").value("TD"))
                .andExpect(jsonPath("$.results[0].productName").value("Vay tiêu dùng không tài sản bảo đảm"))
                .andExpect(jsonPath("$.results[0].eligible").value(true))
                .andExpect(jsonPath("$.results[0].rankNo").value(1))
                .andExpect(jsonPath("$.results[0].refRate").value(12.5))
                .andExpect(jsonPath("$.results[0].maxTerm").value(60))
                .andExpect(jsonPath("$.results[1].productCode").value("TC"))
                .andExpect(jsonPath("$.results[1].rankNo").value(2))
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(body, "$.id");

        Map<String, Object> consent = jdbc.queryForMap(
                "select c.customer_id, c.scope, c.doc_version from consent c join screening s on s.consent_id = c.id where s.public_id = ?::uuid", id);
        assertThat(consent.get("customer_id")).isNull();
        assertThat(consent.get("scope")).isEqualTo("A");
        assertThat(consent.get("doc_version")).isEqualTo("DK-1.0");
        assertThat(jdbc.queryForObject("select count(*) from screening_result where screening_id = (select id from screening where public_id = ?::uuid)", Integer.class, id)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from other_debt where screening_id = (select id from screening where public_id = ?::uuid)", Integer.class, id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select credit_history from screening where public_id = ?::uuid", String.class, id)).isEqualTo("ON_TIME");
    }

    @Test
    void create_withCustomerToken_attachesToCustomer() throws Exception {
        String token = customer();
        String id = createScreening(request(), token);
        send(get("/screenings/" + id), null, token).andExpect(jsonPath("$.savedToCustomerId").value(1));
        assertThat(jdbc.queryForObject("select c.customer_id from consent c join screening s on s.consent_id = c.id where s.public_id = ?::uuid",
                Long.class, id)).isEqualTo(1L);
    }

    @Test
    void create_withStaffToken_staysAnonymous() throws Exception {
        String id = createScreening(request(), token("STAFF", "nhanvien@tiensangloc.local"));
        send(get("/screenings/" + id), null, null).andExpect(jsonPath("$.savedToCustomerId").value((Object) null));
    }

    @Test
    void create_notesForCreditHistory() throws Exception {
        Map<String, Object> unsure = request();
        unsure.put("creditHistory", "UNSURE");
        send(post("/screenings"), unsure, null).andExpect(status().isCreated())
                .andExpect(jsonPath("$.notes[0].type").value("WARNING"));

        Map<String, Object> never = request();
        never.put("creditHistory", "NEVER_BORROWED");
        send(post("/screenings"), never, null).andExpect(status().isCreated())
                .andExpect(jsonPath("$.notes[0].type").value("INFO"))
                .andExpect(jsonPath("$.notes[0].text").value("Bạn chưa có lịch sử vay – điều này không ảnh hưởng tới kết quả sàng lọc."));

        Map<String, Object> late = request();
        late.put("creditHistory", "LATE_OVER_90");
        send(post("/screenings"), late, null).andExpect(status().isCreated())
                .andExpect(jsonPath("$.results[0].eligible").value(false))
                .andExpect(jsonPath("$.results[0].rankNo").value((Object) null))
                .andExpect(jsonPath("$.results[0].dti").value((Object) null))
                .andExpect(jsonPath("$.results[0].reasons[0]")
                        .value("Bạn cho biết từng trả chậm quá 3 tháng hoặc phải xin giãn, hoãn nợ trong 5 năm gần đây."));
    }

    @Test
    void create_withoutConsent_is400() throws Exception {
        Map<String, Object> r = request();
        r.put("consentA", Map.of("agreed", false, "docVersion", "DK-1.0"));
        send(post("/screenings"), r, null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Cần đồng ý xử lý dữ liệu trước khi sàng lọc."));
        r.remove("consentA");
        send(post("/screenings"), r, null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Cần đồng ý xử lý dữ liệu trước khi sàng lọc."));
    }

    @Test
    void create_invalidIncomeOrAmount_is400() throws Exception {
        for (Object[] bad : new Object[][] {{"avgIncome", -1}, {"desiredAmount", 0}, {"avgIncome", null}}) {
            Map<String, Object> r = request();
            r.put((String) bad[0], bad[1]);
            send(post("/screenings"), r, null).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Thông tin thu nhập hoặc số tiền vay không hợp lệ."));
        }
    }

    @Test
    void create_missingFields_is400WithFields() throws Exception {
        Map<String, Object> r = request();
        r.remove("dob");
        r.put("purpose", "x".repeat(101));
        r.put("desiredTerm", 0);
        send(post("/screenings"), r, null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Dữ liệu không hợp lệ."))
                .andExpect(jsonPath("$.fields.dob").value("Không được để trống."))
                .andExpect(jsonPath("$.fields.purpose").value("Tối đa 100 ký tự."))
                .andExpect(jsonPath("$.fields.desiredTerm").value("Kỳ hạn phải lớn hơn 0."));

        Map<String, Object> badEnum = request();
        badEnum.put("creditHistory", "KHONG_CO");
        send(post("/screenings"), badEnum, null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Dữ liệu không hợp lệ."));
    }

    @Test
    void create_hugeDti_isStoredCapped() throws Exception {
        Map<String, Object> r = request();
        r.put("avgIncome", 1_000_000);
        r.put("otherDebts", List.of(Map.of("monthlyPayment", 50_000_000)));
        String id = createScreening(r, null);
        send(get("/screenings/" + id), null, null).andExpect(jsonPath("$.currentDti").value(5000.0));
        assertThat(jdbc.queryForObject("select dti from screening where public_id = ?::uuid", Double.class, id)).isEqualTo(999.99);
    }

    // ---------------------------------------------------------------- GET /screenings/{id}

    @Test
    void get_returnsSameAsCreate() throws Exception {
        Map<String, Object> r = request();
        r.put("creditHistory", "UNSURE");
        r.put("desiredAmount", 400_000_000);
        String created = send(post("/screenings"), r, null).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(created, "$.id");
        String again = send(get("/screenings/" + id), null, null).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        JsonNode a = json.readTree(created);
        JsonNode b = json.readTree(again);
        assertThat(b).isEqualTo(a);
    }

    @Test
    void get_unknownOrMalformedId_is404() throws Exception {
        String createdNumericId = String.valueOf(jdbc.queryForObject("select max(id) from screening", Long.class));
        for (String bad : new String[] {"999999", "1", createdNumericId, UUID.randomUUID().toString(), "abc", "1-1-1-1-1",
                UUID.randomUUID().toString().replace("-", "")}) {
            send(get("/screenings/" + bad), null, null).andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("Không tìm thấy kết quả sàng lọc."));
        }
    }

    @Test
    void responses_useUuidOnly() throws Exception {
        String id = createScreening(request(), null);
        assertThat(id).matches(UUID_RE);
        assertThat(jdbc.queryForObject("select public_id::text from screening where public_id = ?::uuid", String.class, id)).isEqualTo(id);
        send(post("/simulations"), sim(id, "TD", 150_000_000, 36, List.of()), null).andExpect(status().isOk());
        // screeningId không phải UUID trong JSON là lỗi kiểu dữ liệu
        send(post("/simulations"), sim(12, "TD", 150_000_000, 36, List.of()), null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Dữ liệu không hợp lệ."));
    }

    @Test
    void get_savedScreening_onlyOwnerCanSee() throws Exception {
        String owner = newCustomer();
        String id = createScreening(request(), owner);

        send(get("/screenings/" + id), null, owner).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id));
        for (String other : new String[] {null, newCustomer(), token("STAFF", "nhanvien@tiensangloc.local"),
                token("STAFF", "quantri@tiensangloc.local")}) {
            send(get("/screenings/" + id), null, other).andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("Không tìm thấy kết quả sàng lọc."));
        }
    }

    // ---------------------------------------------------------------- POST /screenings/{id}/save, GET /me/screenings

    @Test
    void save_requiresCustomer() throws Exception {
        String id = createScreening(request(), null);
        send(post("/screenings/" + id + "/save"), null, null).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Cần đăng nhập tài khoản khách hàng."));
        send(post("/screenings/" + id + "/save"), null, token("STAFF", "nhanvien@tiensangloc.local")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Cần đăng nhập tài khoản khách hàng."));
    }

    @Test
    void save_anonymousScreening_attachesAndLogs() throws Exception {
        String token = newCustomer();
        long customerId = ((Number) JsonPath.read(send(get("/auth/me"), null, token).andReturn().getResponse().getContentAsString(), "$.id"))
                .longValue();
        String id = createScreening(request(), null);
        send(get("/screenings/" + id), null, null).andExpect(status().isOk());

        send(post("/screenings/" + id + "/save"), null, token).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.savedToCustomerId").value(customerId));

        // Đã lưu: chủ tài khoản vẫn xem được, người khác (kể cả ẩn danh vừa tạo lượt này) thì không
        send(get("/screenings/" + id), null, token).andExpect(status().isOk());
        send(get("/screenings/" + id), null, null).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Không tìm thấy kết quả sàng lọc."));
        send(get("/screenings/" + id), null, newCustomer()).andExpect(status().isNotFound());

        assertThat(jdbc.queryForObject("select c.customer_id from consent c join screening s on s.consent_id = c.id where s.public_id = ?::uuid",
                Long.class, id)).isEqualTo(customerId);
        assertThat(jdbc.queryForObject("select count(*) from activity_log where actor_type = 'CUSTOMER' and actor_id = ? "
                + "and action = 'Lưu kết quả sàng lọc' and detail = ?", Integer.class, customerId, "Lượt #" + id.substring(0, 8))).isEqualTo(1);
        for (String bad : new String[] {"999999", UUID.randomUUID().toString(), "abc"}) {
            send(post("/screenings/" + bad + "/save"), null, token).andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("Không tìm thấy lượt sàng lọc."));
        }
    }

    @Test
    void save_screeningOfAnotherCustomer_is404() throws Exception {
        String id = createScreening(request(), customer());
        send(post("/screenings/" + id + "/save"), null, newCustomer()).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Không tìm thấy lượt sàng lọc."));
        send(post("/screenings/" + id + "/save"), null, customer()).andExpect(status().isOk());
    }

    @Test
    void mine_listsNewestFirst() throws Exception {
        String token = newCustomer();
        String first = createScreening(request(), token);
        String second = createScreening(request(), token);
        createScreening(request(), null);

        send(get("/me/screenings"), null, token).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(second))
                .andExpect(jsonPath("$[1].id").value(first));
        send(get("/me/screenings"), null, token("STAFF", "nhanvien@tiensangloc.local")).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        send(get("/me/screenings"), null, null).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Chưa đăng nhập."));
    }

    // ---------------------------------------------------------------- POST /simulations

    @Test
    void simulate_okPlanWithSchedule() throws Exception {
        String id = createScreening(request(), null);
        send(post("/simulations"), sim(id, "TD", 150_000_000, 36, List.of(2, 3)), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productCode").value("TD"))
                .andExpect(jsonPath("$.termMonths").value(36))
                .andExpect(jsonPath("$.rate").value(11.3))
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.issues.length()").value(0))
                .andExpect(jsonPath("$.schedule.length()").value(36))
                .andExpect(jsonPath("$.schedule[35].balance").value(0))
                .andExpect(jsonPath("$.extraCost").value(0))
                .andExpect(jsonPath("$.suggestions.length()").value(0));
    }

    @Test
    void simulate_overThreshold_hasIssuesAndSuggestions() throws Exception {
        String id = createScreening(request(), null);
        send(post("/simulations"), sim(id, "TD", 450_000_000, 36, List.of()), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.issues[0]").value(org.hamcrest.Matchers.startsWith("Số tiền vay vượt hạn mức tối đa an toàn (")))
                .andExpect(jsonPath("$.suggestions[0].type").value("REDUCE_AMOUNT"));
    }

    @Test
    void simulate_errors() throws Exception {
        send(post("/simulations"), sim(UUID.randomUUID().toString(), "TD", 150_000_000, 36, List.of()), null).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Không tìm thấy lượt sàng lọc."));

        Map<String, Object> flexible = request();
        flexible.put("purpose", "Chi tiêu cá nhân linh hoạt");
        String id = createScreening(flexible, null);
        send(post("/simulations"), sim(id, "TD", 50_000_000, 12, List.of()), null).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("Hồ sơ không đạt điều kiện của gói vay này."));
        send(post("/simulations"), sim(id, "TC", 50_000_000, 12, List.of()), null).andExpect(status().isOk());

        send(post("/simulations"), Map.of("screeningId", id), null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Dữ liệu không hợp lệ."))
                .andExpect(jsonPath("$.fields.productCode").value("Không được để trống."));
    }

    @Test
    void simulate_savedScreening_onlyOwner() throws Exception {
        String owner = newCustomer();
        String id = createScreening(request(), owner);
        Map<String, Object> body = sim(id, "TD", 150_000_000, 36, List.of());

        send(post("/simulations"), body, owner).andExpect(status().isOk());
        for (String other : new String[] {null, newCustomer(), token("STAFF", "nhanvien@tiensangloc.local")}) {
            send(post("/simulations"), body, other).andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("Không tìm thấy lượt sàng lọc."));
        }
    }
}
