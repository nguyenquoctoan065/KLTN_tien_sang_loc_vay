package vn.kltn.tiensanglocvay.privacy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import vn.kltn.tiensanglocvay.document.StorageService;

/**
 * B6: GET /me/data và POST /me/consents/withdraw. Thông báo lỗi đối chiếu với fe/src/mocks/handlers/me.ts.
 * Hồ sơ gói TD tại Chi nhánh Hà Nội (id 1); giấy tờ: CCCD(2), cư trú(3), HĐLĐ(4), sao kê(5, PDF), hóa đơn(6), giấy đề nghị(1).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class PrivacyApiTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    private static final AtomicInteger SEQ = new AtomicInteger(60_000_000);
    private static final String LOGIN = "Cần đăng nhập tài khoản khách hàng.";
    private static final String NID = "001092012345";

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ObjectMapper json;

    /** LocalStorageService thật, chỉ giả lập lỗi xóa tệp trong một test (Mockito tự đặt lại sau mỗi test). */
    @SpyBean
    StorageService storage;

    // ---------------------------------------------------------------- tiện ích

    private ResultActions send(MockHttpServletRequestBuilder req, Object body, String token) throws Exception {
        if (body != null) {
            req = req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        }
        if (token != null) {
            req = req.header("Authorization", "Bearer " + token);
        }
        return mvc.perform(req);
    }

    private static String body(ResultActions r) throws Exception {
        return r.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private static <T> T read(ResultActions r, String path) throws Exception {
        return JsonPath.read(body(r), path);
    }

    private String login(String kind, String username) throws Exception {
        String b = "{\"kind\":\"" + kind + "\",\"username\":\"" + username + "\",\"password\":\"123456\"}";
        return JsonPath.read(mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(b))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.token");
    }

    private String staffHn() throws Exception {
        return login("STAFF", "nhanvien@tiensangloc.local");
    }

    private String admin() throws Exception {
        return login("STAFF", "quantri@tiensangloc.local");
    }

    private String newCustomer(String[] phoneOut) throws Exception {
        String phone = "09" + SEQ.incrementAndGet();
        send(post("/auth/otp"), Map.of("phone", phone), null).andExpect(status().isOk());
        if (phoneOut != null) {
            phoneOut[0] = phone;
        }
        return read(send(post("/auth/register"), Map.of("phone", phone, "password", "123456", "otp", "123456"), null)
                .andExpect(status().isOk()), "$.token");
    }

    private String newCustomer() throws Exception {
        return newCustomer(null);
    }

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
        r.put("otherDebts", List.of(Map.of("lender", "Ngân hàng A", "monthlyPayment", 1_000_000)));
        r.put("purpose", "Mua xe máy, ô tô");
        r.put("desiredAmount", 150_000_000);
        r.put("desiredTerm", 36);
        return r;
    }

    /** Lượt sàng lọc gắn với khách (gửi kèm token) rồi lập phương án có bán chéo; trả {screeningId, planId}. */
    private String[] screeningAndPlan(String token, List<Integer> crossSell) throws Exception {
        String screeningId = read(send(post("/screenings"), screening(), token).andExpect(status().isCreated()), "$.id");
        String planId = read(send(post("/plans"), Map.of("screeningId", screeningId, "productCode", "TD", "amount", 150_000_000,
                "termMonths", 36, "crossSellIds", crossSell), token).andExpect(status().isCreated()), "$.id");
        return new String[] {screeningId, planId};
    }

    private String newApp(String token) throws Exception {
        String[] sp = screeningAndPlan(token, List.of());
        send(post("/plans/" + sp[1] + "/confirm"), null, token).andExpect(status().isOk());
        return read(send(post("/applications"), Map.of("planId", sp[1], "branchId", 1,
                "consentB", Map.of("agreed", true, "docVersion", "DK-1.0")), token).andExpect(status().isCreated()), "$.id");
    }

    private long docId(String appId, String token, int typeId) throws Exception {
        List<Number> ids = read(send(get("/applications/" + appId), null, token), "$.documents[?(@.documentTypeId == " + typeId + ")].id");
        return ids.get(0).longValue();
    }

    private void uploadAndConfirm(String appId, String token, int typeId, byte[] data) throws Exception {
        long id = docId(appId, token, typeId);
        String name = typeId == 5 ? "saoke.pdf" : "giayto.jpg";
        mvc.perform(multipart("/applications/" + appId + "/documents/" + id)
                        .file(new MockMultipartFile("file", name, typeId == 5 ? "application/pdf" : "image/jpeg", data))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        String app = body(send(get("/applications/" + appId), null, token));
        List<Map<String, Object>> fs = JsonPath.read(app, "$.documents[?(@.id == " + id + ")].fields[*]");
        List<Map<String, Object>> sent = new ArrayList<>();
        for (Map<String, Object> f : fs) {
            sent.add(Map.of("id", f.get("id"), "confirmedValue", f.get("ocrValue")));
        }
        send(put("/applications/" + appId + "/documents/" + id + "/fields"), Map.of("fields", sent), token).andExpect(status().isOk());
    }

    private static byte[] bytes(int kb, int seed) {
        byte[] b = new byte[kb * 1024];
        for (int i = 0; i < b.length; i++) {
            b[i] = (byte) (i * 31 + seed);
        }
        return b;
    }

    private void personal(String appId, String token) throws Exception {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("fullName", "Trần Thị Lan");
        p.put("nationalId", NID);
        p.put("dob", "1992-05-15");
        p.put("gender", "MALE");
        p.put("address", "12 Láng Hạ, Đống Đa, Hà Nội");
        p.put("phone", "0000000000");
        p.put("email", "lan@example.com");
        send(put("/applications/" + appId + "/personal"), p, token).andExpect(status().isOk());
    }

    /** Hồ sơ nộp đủ 5 giấy tờ (WAITING_VERIFY, có giấy đề nghị). */
    private String submittedApp(String token) throws Exception {
        String appId = newApp(token);
        personal(appId, token);
        int[][] files = {{2, 60}, {3, 60}, {4, 60}, {5, 32}, {6, 60}};
        for (int[] f : files) {
            uploadAndConfirm(appId, token, f[0], bytes(f[1], f[0]));
        }
        send(post("/auth/otp"), Map.of(), token).andExpect(status().isOk());
        send(post("/applications/" + appId + "/submit"), Map.of("otp", "123456"), token).andExpect(status().isOk());
        return appId;
    }

    private String code(String appId, String token) throws Exception {
        return read(send(get("/applications/" + appId), null, token), "$.code");
    }

    private ResultActions withdraw(String token, String scope) throws Exception {
        send(post("/auth/otp"), Map.of(), token).andExpect(status().isOk());
        return send(post("/me/consents/withdraw"), Map.of("scope", scope, "otp", "123456"), token);
    }

    private Path file(String storagePath, String ext) {
        return Path.of("target/test-uploads", storagePath + "." + ext);
    }

    // ---------------------------------------------------------------- quyền

    @Test
    void roles_onlyCustomer() throws Exception {
        for (String token : new String[] {null, staffHn(), admin()}) {
            send(get("/me/data"), null, token).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.message").value(LOGIN));
            send(post("/me/consents/withdraw"), Map.of("scope", "A", "otp", "123456"), token).andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value(LOGIN));
            // Kiểm tra vai trò trước khi đọc body
            mvc.perform(post("/me/consents/withdraw").contentType(MediaType.APPLICATION_JSON).content("{sai")
                    .header("Authorization", token == null ? "" : "Bearer " + token)).andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value(LOGIN));
        }
    }

    // ---------------------------------------------------------------- 6.1 xem dữ liệu

    @Test
    void myData_emptyThenWithAccessLog() throws Exception {
        String[] phone = new String[1];
        String token = newCustomer(phone);
        String name = "Khách hàng " + phone[0].substring(phone[0].length() - 4);
        send(get("/me/data"), null, token).andExpect(status().isOk())
                .andExpect(jsonPath("$.account.phone").value(phone[0]))
                .andExpect(jsonPath("$.account.name").value(name))
                .andExpect(jsonPath("$.account.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.personal").value((Object) null))
                .andExpect(jsonPath("$.consents.length()").value(0))
                .andExpect(jsonPath("$.screenings").value(0))
                .andExpect(jsonPath("$.plans").value(0))
                .andExpect(jsonPath("$.applications.length()").value(0))
                // chỉ có dòng đăng ký; lượt xem được ghi sau khi dựng kết quả nên chưa nằm trong đó
                .andExpect(jsonPath("$.logs.length()").value(1))
                .andExpect(jsonPath("$.logs[0].action").value("Đăng ký tài khoản"));

        // Lượt xem đầu tiên nằm trong nhật ký của lượt thứ hai
        send(get("/me/data"), null, token).andExpect(jsonPath("$.logs.length()").value(2))
                .andExpect(jsonPath("$.logs[0].action").value("Xem dữ liệu cá nhân"))
                .andExpect(jsonPath("$.logs[0].detail").value(""))
                .andExpect(jsonPath("$.logs[0].actorType").value("CUSTOMER"))
                .andExpect(jsonPath("$.logs[0].actorName").value(name))
                .andExpect(jsonPath("$.logs[0].applicationCode").value((Object) null));

        // Sàng lọc + phương án + hồ sơ có giấy tờ
        String appId = newApp(token);
        String code = code(appId, token);
        personal(appId, token);
        uploadAndConfirm(appId, token, 2, bytes(60, 2));
        uploadAndConfirm(appId, token, 5, bytes(32, 5));
        // Nhân viên chi nhánh xem hồ sơ: lượt truy cập này hiện trong nhật ký của khách
        send(get("/applications/" + appId), null, staffHn()).andExpect(status().isOk());
        // Khách khác không thấy dữ liệu của khách này
        String other = newCustomer();
        send(get("/me/data"), null, other).andExpect(jsonPath("$.applications.length()").value(0))
                .andExpect(jsonPath("$.logs.length()").value(1));

        send(get("/me/data"), null, token).andExpect(status().isOk())
                .andExpect(jsonPath("$.personal.fullName").value("Trần Thị Lan"))
                .andExpect(jsonPath("$.personal.nationalId").value(NID))
                .andExpect(jsonPath("$.personal.address").value("12 Láng Hạ, Đống Đa, Hà Nội"))
                .andExpect(jsonPath("$.personal.phone").value(phone[0]))
                .andExpect(jsonPath("$.screenings").value(1))
                .andExpect(jsonPath("$.plans").value(1))
                .andExpect(jsonPath("$.applications.length()").value(1))
                .andExpect(jsonPath("$.applications[0].code").value(code))
                .andExpect(jsonPath("$.applications[0].status").value("DRAFTING"))
                .andExpect(jsonPath("$.applications[0].documents").value(2))
                .andExpect(jsonPath("$.consents.length()").value(2))
                // mới nhất trước: đồng ý phần B (gắn mã hồ sơ), rồi phần A
                .andExpect(jsonPath("$.consents[0].scope").value("B"))
                .andExpect(jsonPath("$.consents[0].applicationCode").value(code))
                .andExpect(jsonPath("$.consents[0].withdrawnAt").value((Object) null))
                .andExpect(jsonPath("$.consents[0].docVersion").value("DK-1.0"))
                .andExpect(jsonPath("$.consents[1].scope").value("A"))
                .andExpect(jsonPath("$.consents[1].applicationCode").value((Object) null))
                .andExpect(jsonPath("$.logs[*].action").value(hasItem("Tải giấy tờ")))
                .andExpect(jsonPath("$.logs[*].action").value(hasItem("Tạo hồ sơ vay")))
                .andExpect(jsonPath("$.logs[?(@.actorType == 'STAFF')].actorName").value(hasItem("Nguyễn Thu Hà")))
                .andExpect(jsonPath("$.logs[?(@.actorType == 'STAFF')].applicationCode").value(hasItem(code)))
                .andExpect(jsonPath("$.logs[*].action").value(hasItem("Xem dữ liệu cá nhân")))
                // mới nhất trước: lượt nhân viên vừa xem hồ sơ
                .andExpect(jsonPath("$.logs[0].action").value("Xem hồ sơ"))
                .andExpect(jsonPath("$.logs[0].actorType").value("STAFF"));
    }

    // ---------------------------------------------------------------- 6.3 rút đồng ý: lỗi

    @Test
    void withdraw_errors() throws Exception {
        String token = newCustomer();
        // scope sai → 400 trước khi kiểm tra OTP
        send(post("/me/consents/withdraw"), Map.of("scope", "C", "otp", "123456"), token).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Dữ liệu không hợp lệ.")).andExpect(jsonPath("$.fields.scope").exists());
        send(post("/me/consents/withdraw"), Map.of("otp", "123456"), token).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Dữ liệu không hợp lệ."));
        // Chưa gửi OTP, OTP sai
        send(post("/me/consents/withdraw"), Map.of("scope", "A", "otp", "123456"), token).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Mã OTP không đúng."));
        send(post("/auth/otp"), Map.of(), token).andExpect(status().isOk());
        send(post("/me/consents/withdraw"), Map.of("scope", "A", "otp", "000000"), token).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Mã OTP không đúng."));
        // Mã đúng vẫn dùng được sau lần sai; dùng một lần
        send(post("/me/consents/withdraw"), Map.of("scope", "A", "otp", "123456"), token).andExpect(status().isOk());
        send(post("/me/consents/withdraw"), Map.of("scope", "A", "otp", "123456"), token).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Mã OTP không đúng."));
    }

    // ---------------------------------------------------------------- 6.3 rút đồng ý phần (a)

    @Test
    void withdrawA_deletesUnlinkedScreeningsAndPlans() throws Exception {
        String token = newCustomer();
        String[] free = screeningAndPlan(token, List.of(1)); // có nợ khác + bán chéo, chưa lập hồ sơ
        String linkedApp = newApp(token); // lượt sàng lọc gắn hồ sơ
        String linkedCode = code(linkedApp, token);
        long linkedScreening = jdbc.queryForObject("select s.id from screening s join loan_plan p on p.screening_id = s.id "
                + "join loan_application a on a.plan_id = p.id where a.public_id = ?::uuid", Long.class, linkedApp);
        long freeId = jdbc.queryForObject("select id from screening where public_id = ?::uuid", Long.class, free[0]);
        assertThat(jdbc.queryForObject("select count(*) from other_debt where screening_id = ?", Integer.class, freeId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from plan_cross_sell x join loan_plan p on p.id = x.plan_id where p.screening_id = ?",
                Integer.class, freeId)).isEqualTo(1);
        send(get("/me/data"), null, token).andExpect(jsonPath("$.screenings").value(2)).andExpect(jsonPath("$.plans").value(2));

        withdraw(token, "A").andExpect(status().isOk())
                .andExpect(jsonPath("$.scope").value("A"))
                .andExpect(jsonPath("$.deletedScreenings").value(1))
                .andExpect(jsonPath("$.deletedDocuments").value(0))
                .andExpect(jsonPath("$.cancelledApplications.length()").value(0))
                .andExpect(jsonPath("$.keptApplications.length()").value(0))
                .andExpect(jsonPath("$.message").value("Đã xóa 1 lượt sàng lọc và phương án liên quan. "
                        + "Lượt sàng lọc gắn với hồ sơ vay được giữ theo đồng ý phần (b)."));

        // Lượt không gắn hồ sơ biến mất cùng phương án và dữ liệu con; lượt gắn hồ sơ còn nguyên
        send(get("/screenings/" + free[0]), null, token).andExpect(status().isNotFound());
        send(get("/plans/" + free[1]), null, token).andExpect(status().isNotFound());
        for (String table : new String[] {"screening", "other_debt", "screening_result", "loan_plan"}) {
            String col = table.equals("screening") ? "id" : "screening_id";
            assertThat(jdbc.queryForObject("select count(*) from " + table + " where " + col + " = ?", Integer.class, freeId))
                    .as(table).isZero();
        }
        assertThat(jdbc.queryForObject("select count(*) from plan_cross_sell x join loan_plan p on p.id = x.plan_id where p.screening_id = ?",
                Integer.class, freeId)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from screening where id = ?", Integer.class, linkedScreening)).isEqualTo(1);
        send(get("/applications/" + linkedApp), null, token).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(linkedCode));

        // Mọi đồng ý phần A đã rút, phần B còn hiệu lực
        send(get("/me/data"), null, token).andExpect(jsonPath("$.screenings").value(1)).andExpect(jsonPath("$.plans").value(1))
                .andExpect(jsonPath("$.consents[?(@.scope == 'A')].withdrawnAt").value(not(hasItem((Object) null))))
                .andExpect(jsonPath("$.consents[?(@.scope == 'B')].withdrawnAt").value(contains((Object) null)))
                .andExpect(jsonPath("$.logs[*].action").value(hasItem("Rút lại sự đồng ý")))
                .andExpect(jsonPath("$.logs[?(@.action == 'Rút lại sự đồng ý')].detail").value(contains("Phần (a)")));

        // Rút lại lần nữa: không còn gì để xóa
        withdraw(token, "A").andExpect(status().isOk()).andExpect(jsonPath("$.deletedScreenings").value(0))
                .andExpect(jsonPath("$.message").value("Đã xóa 0 lượt sàng lọc và phương án liên quan. "
                        + "Lượt sàng lọc gắn với hồ sơ vay được giữ theo đồng ý phần (b)."));
    }

    // ---------------------------------------------------------------- 6.3 rút đồng ý phần (b)

    @Test
    void withdrawB_cancelsOpenApplicationsAndDeletesFiles() throws Exception {
        String token = newCustomer();
        String app1 = submittedApp(token); // WAITING_VERIFY, 5 giấy tờ + giấy đề nghị
        String app2 = newApp(token); // DRAFTING, 1 giấy tờ
        personal(app2, token);
        uploadAndConfirm(app2, token, 2, bytes(60, 7));
        String app3 = newApp(token); // đã đối chiếu: được giữ
        uploadAndConfirm(app3, token, 2, bytes(60, 8));
        jdbc.update("update loan_application set status = 'VERIFIED' where public_id = ?::uuid", app3);
        String app4 = newApp(token); // đã hủy trước đó nhưng còn giấy tờ
        uploadAndConfirm(app4, token, 2, bytes(60, 9));
        send(post("/applications/" + app4 + "/cancel"), Map.of(), token).andExpect(status().isOk());
        String c1 = code(app1, token);
        String c2 = code(app2, token);
        String c3 = code(app3, token);

        List<String> paths = jdbc.queryForList("select d.storage_path || '.' || lower(d.file_format) from application_document d "
                + "join loan_application a on a.id = d.application_id where a.customer_id = (select customer_id from loan_application "
                + "where public_id = ?::uuid) and d.storage_path is not null", String.class, app1);
        assertThat(paths).hasSize(5 + 1 + 1 + 1);
        for (String p : paths) {
            assertThat(Files.exists(Path.of("target/test-uploads", p))).as(p).isTrue();
        }
        List<String> keptPaths = jdbc.queryForList("select d.storage_path || '.' || lower(d.file_format) from application_document d "
                + "join loan_application a on a.id = d.application_id where a.public_id = ?::uuid and d.storage_path is not null",
                String.class, app3);
        assertThat(keptPaths).hasSize(1);
        assertThat(jdbc.queryForObject("select count(*) from loan_request_form f join loan_application a on a.id = f.application_id "
                + "where a.public_id = ?::uuid", Integer.class, app1)).isEqualTo(1);

        // Xóa tệp trong kho lỗi → hoàn tác toàn bộ (502), không đổi gì; thử lại sau khi hết lỗi
        doThrow(new UncheckedIOException(new java.io.IOException("Cloudinary lỗi"))).when(storage).delete(any());
        withdraw(token, "B").andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message").value("Không xóa được tệp trên kho lưu trữ, vui lòng thử lại sau."));
        assertThat(jdbc.queryForObject("select status from loan_application where public_id = ?::uuid", String.class, app1))
                .isEqualTo("WAITING_VERIFY");
        assertThat(jdbc.queryForObject("select count(*) from consent where customer_id = (select customer_id from loan_application "
                + "where public_id = ?::uuid) and scope = 'B' and withdrawn_at is not null", Integer.class, app1)).isZero();
        assertThat(jdbc.queryForObject("select national_id from loan_application where public_id = ?::uuid", String.class, app1))
                .isEqualTo(NID);
        org.mockito.Mockito.reset(storage);

        withdraw(token, "B").andExpect(status().isOk())
                .andExpect(jsonPath("$.scope").value("B"))
                .andExpect(jsonPath("$.deletedScreenings").value(0))
                .andExpect(jsonPath("$.deletedDocuments").value(5 + 1 + 1))
                .andExpect(jsonPath("$.cancelledApplications").value(contains(c1, c2)))
                .andExpect(jsonPath("$.keptApplications").value(contains(c3)))
                .andExpect(jsonPath("$.message").value("Đã xóa 7 tài liệu và thông tin định danh của hồ sơ chưa đối chiếu xong. "
                        + "Hồ sơ đã đối chiếu tại chi nhánh được ngân hàng lưu trữ theo quy định; liên hệ chi nhánh nếu cần hỗ trợ."));

        // Tệp biến mất khỏi kho (trừ hồ sơ được giữ)
        for (String p : paths) {
            assertThat(Files.exists(Path.of("target/test-uploads", p))).as(p).isEqualTo(keptPaths.contains(p));
        }
        // Hồ sơ: hủy, giấy tờ về MISSING, trường trích xuất, giấy đề nghị, CCCD/địa chỉ/email, cảnh báo đều bị xóa
        send(get("/applications/" + app1), null, token).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.personal.fullName").value("Trần Thị Lan"))
                .andExpect(jsonPath("$.personal.nationalId").value(""))
                .andExpect(jsonPath("$.personal.address").value(""))
                .andExpect(jsonPath("$.personal.email").value(""))
                .andExpect(jsonPath("$.warnings.length()").value(0))
                .andExpect(jsonPath("$.recalc").value((Object) null))
                .andExpect(jsonPath("$.requestForm").value((Object) null))
                .andExpect(jsonPath("$.documents[*].status").value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is("MISSING"))))
                .andExpect(jsonPath("$.documents[*].fileName").value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.nullValue())))
                .andExpect(jsonPath("$.documents[*].fileUrl").value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.nullValue())))
                .andExpect(jsonPath("$.documents[*].previewUrl").value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.nullValue())))
                .andExpect(jsonPath("$.history[-1].status").value("CANCELLED"))
                .andExpect(jsonPath("$.history[-1].note").value("Khách hàng rút lại sự đồng ý"))
                .andExpect(jsonPath("$.can.cancel").value(false));
        assertThat(jdbc.queryForObject("select count(*) from extracted_field f join application_document d on d.id = f.document_id "
                + "join loan_application a on a.id = d.application_id where a.public_id in (?::uuid, ?::uuid)", Integer.class, app1, app2))
                .isZero();
        assertThat(jdbc.queryForObject("select count(*) from loan_request_form f join loan_application a on a.id = f.application_id "
                + "where a.public_id = ?::uuid", Integer.class, app1)).isZero();
        send(get("/applications/" + app2), null, token).andExpect(jsonPath("$.status").value("CANCELLED"));
        send(get("/applications/" + app3), null, token).andExpect(jsonPath("$.status").value("VERIFIED"))
                .andExpect(jsonPath("$.documents[?(@.documentTypeId == 2)].fileName").value(contains("giayto.jpg")));
        send(get("/applications/" + app4), null, token).andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.documents[?(@.documentTypeId == 2)].fileName").value(contains((Object) null)));

        // Chi nhánh được thông báo (hồ sơ đã hủy từ trước không thông báo lại); đồng ý phần B đã rút, phần A còn hiệu lực
        send(get("/notifications"), null, staffHn()).andExpect(jsonPath("$[?(@.title == 'Hồ sơ đã hủy do khách hàng rút đồng ý')].body")
                .value(hasItem(c1))).andExpect(jsonPath("$[?(@.title == 'Hồ sơ đã hủy do khách hàng rút đồng ý')].body").value(hasItem(c2)))
                .andExpect(jsonPath("$[?(@.title == 'Hồ sơ đã hủy do khách hàng rút đồng ý')].body").value(not(hasItem(c3))));
        send(get("/me/data"), null, token).andExpect(jsonPath("$.consents[?(@.scope == 'B')].withdrawnAt").value(not(hasItem((Object) null))))
                .andExpect(jsonPath("$.consents[?(@.scope == 'A')].withdrawnAt").value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.nullValue())))
                .andExpect(jsonPath("$.applications[?(@.code == '" + c1 + "')].documents").value(contains(0)))
                .andExpect(jsonPath("$.applications[?(@.code == '" + c3 + "')].documents").value(contains(1)))
                .andExpect(jsonPath("$.logs[?(@.action == 'Rút lại sự đồng ý')].detail").value(contains("Phần (b)")));

        // Rút lại lần nữa: không còn gì để xóa, hồ sơ được giữ vẫn nằm trong keptApplications
        withdraw(token, "B").andExpect(status().isOk()).andExpect(jsonPath("$.deletedDocuments").value(0))
                .andExpect(jsonPath("$.cancelledApplications.length()").value(0))
                .andExpect(jsonPath("$.keptApplications").value(contains(c3)));
    }
}
