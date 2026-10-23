package vn.kltn.tiensanglocvay.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import vn.kltn.tiensanglocvay.document.LocalStorageService;
import vn.kltn.tiensanglocvay.document.OcrResult;
import vn.kltn.tiensanglocvay.document.OcrService;
import vn.kltn.tiensanglocvay.document.StoredFile;

/**
 * B4: /applications/**, /me/applications và tệp xem trước cục bộ. Thông báo lỗi đối chiếu với fe/src/mocks/handlers/applications.ts.
 * Gói TD, danh mục giấy tờ theo dữ liệu mẫu: CCCD(2), cư trú(3), HĐLĐ(4), sao kê(5), hóa đơn(6), giấy đề nghị hệ thống(1).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class ApplicationApiTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    private static final AtomicInteger SEQ = new AtomicInteger(40_000_000);
    private static final String NOT_FOUND = "Không tìm thấy hồ sơ.";
    private static final String NID = "001092012345";

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ObjectMapper json;

    @Autowired
    LocalStorageService storage;

    /** FakeOcrService thật, chỉ giả lập kết quả trong vài test (Mockito tự đặt lại sau mỗi test). */
    @SpyBean
    OcrService ocr;

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

    private String newCustomer() throws Exception {
        String phone = "09" + SEQ.incrementAndGet();
        send(post("/auth/otp"), Map.of("phone", phone), null).andExpect(status().isOk());
        return read(send(post("/auth/register"), Map.of("phone", phone, "password", "123456", "otp", "123456"), null)
                .andExpect(status().isOk()), "$.token");
    }

    private String confirmedPlan(String token) throws Exception {
        String screeningId = read(send(post("/screenings"), screening(), null).andExpect(status().isCreated()), "$.id");
        String planId = read(send(post("/plans"), Map.of("screeningId", screeningId, "productCode", "TD", "amount", 150_000_000,
                "termMonths", 36, "crossSellIds", List.of()), token).andExpect(status().isCreated()), "$.id");
        send(post("/plans/" + planId + "/confirm"), null, token).andExpect(status().isOk());
        return planId;
    }

    private static Map<String, Object> createBody(String planId) {
        return Map.of("planId", planId, "branchId", 1, "consentB", Map.of("agreed", true, "docVersion", "DK-1.0"));
    }

    private String newApp(String token) throws Exception {
        return read(send(post("/applications"), createBody(confirmedPlan(token)), token).andExpect(status().isCreated()), "$.id");
    }

    private static Map<String, Object> personal(String nationalId) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("fullName", "Nguyễn Văn Minh");
        p.put("nationalId", nationalId);
        p.put("dob", "1992-05-15");
        p.put("gender", "MALE");
        p.put("address", "12 Láng Hạ, Đống Đa, Hà Nội");
        p.put("phone", "0000000000");
        p.put("email", "minh@example.com");
        return p;
    }

    /** Mã giấy tờ (docId) của loại giấy tờ trong hồ sơ. */
    private long docId(String appId, String token, int typeId) throws Exception {
        List<Number> ids = read(send(get("/applications/" + appId), null, token),
                "$.documents[?(@.documentTypeId == " + typeId + ")].id");
        return ids.get(0).longValue();
    }

    private ResultActions upload(String appId, long docId, String token, String fileName, String type, int kb) throws Exception {
        return upload(appId, docId, token, new MockMultipartFile("file", fileName, type, new byte[kb * 1024]));
    }

    private ResultActions upload(String appId, long docId, String token, MockMultipartFile file) throws Exception {
        MockMultipartHttpServletRequestBuilder req = multipart("/applications/" + appId + "/documents/" + docId);
        if (file != null) {
            req.file(file);
        }
        return mvc.perform(req.header("Authorization", "Bearer " + token));
    }

    private ResultActions confirm(String appId, long docId, String token, Map<String, String> changes) throws Exception {
        String app = body(send(get("/applications/" + appId), null, token));
        List<Map<String, Object>> fs = JsonPath.read(app, "$.documents[?(@.id == " + docId + ")].fields[*]");
        List<Map<String, Object>> sent = new ArrayList<>();
        for (Map<String, Object> f : fs) {
            String name = (String) f.get("name");
            Object value = changes.containsKey(name) ? changes.get(name) : f.get("ocrValue");
            sent.add(Map.of("id", f.get("id"), "confirmedValue", value));
        }
        return send(put("/applications/" + appId + "/documents/" + docId + "/fields"), Map.of("fields", sent), token);
    }

    /** Thông tin cá nhân + tải và xác nhận đủ 5 giấy tờ bắt buộc của gói TD (sao kê 32 KB → thu nhập đúng bằng khai báo). */
    private void completeDocs(String appId, String token) throws Exception {
        send(put("/applications/" + appId + "/personal"), personal(NID), token).andExpect(status().isOk());
        int[][] files = {{2, 60}, {3, 60}, {4, 60}, {5, 32}, {6, 60}};
        for (int[] f : files) {
            long id = docId(appId, token, f[0]);
            String name = f[0] == 5 ? "saoke.pdf" : "giayto.jpg";
            upload(appId, id, token, name, f[0] == 5 ? "application/pdf" : "image/jpeg", f[1]).andExpect(status().isOk());
            confirm(appId, id, token, Map.of()).andExpect(status().isOk());
        }
    }

    private void sendOtp(String token) throws Exception {
        send(post("/auth/otp"), Map.of(), token).andExpect(status().isOk());
    }

    // ---------------------------------------------------------------- POST /applications

    @Test
    void create_errorsInMockOrder() throws Exception {
        String token = newCustomer();
        String planId = confirmedPlan(token);
        send(post("/applications"), createBody(planId), null).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Cần đăng nhập tài khoản khách hàng."));
        send(post("/applications"), createBody(planId), staffHn()).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Cần đăng nhập tài khoản khách hàng."));
        send(post("/applications"), Map.of("planId", planId, "branchId", 1, "consentB", Map.of("agreed", false, "docVersion", "DK-1.0")),
                token).andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Cần đồng ý lưu trữ giấy tờ và lập hồ sơ."));
        send(post("/applications"), createBody(UUID.randomUUID().toString()), token).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Không tìm thấy phương án."));
        // Phương án của khách khác
        send(post("/applications"), createBody(planId), newCustomer()).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Không tìm thấy phương án."));
        // Phương án chưa xác nhận
        String screeningId = read(send(post("/screenings"), screening(), null), "$.id");
        String draft = read(send(post("/plans"), Map.of("screeningId", screeningId, "productCode", "TD", "amount", 150_000_000,
                "termMonths", 36, "crossSellIds", List.of()), token), "$.id");
        send(post("/applications"), createBody(draft), token).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Cần xác nhận phương án trước khi lập hồ sơ."));
        send(post("/applications"), Map.of("planId", planId, "branchId", 999, "consentB", Map.of("agreed", true, "docVersion", "DK-1.0")),
                token).andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Chi nhánh không hợp lệ."));
        send(post("/applications"), createBody(planId), token).andExpect(status().isCreated());
        send(post("/applications"), createBody(planId), token).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Phương án này đã có hồ sơ."));
    }

    @Test
    void create_buildsChecklistHistoryAndConsent() throws Exception {
        String token = newCustomer();
        String planId = confirmedPlan(token);
        ResultActions r = send(post("/applications"), createBody(planId), token).andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(org.hamcrest.Matchers.matchesPattern("^HS\\d{4}-\\d{4}$")))
                .andExpect(jsonPath("$.status").value("DRAFTING"))
                .andExpect(jsonPath("$.branchId").value(1))
                .andExpect(jsonPath("$.branchName").value("Chi nhánh Hà Nội"))
                .andExpect(jsonPath("$.branchAddress").value("Hà Nội"))
                .andExpect(jsonPath("$.plan.id").value(planId))
                .andExpect(jsonPath("$.purpose").value("Mua xe máy, ô tô"))
                .andExpect(jsonPath("$.personal.dob").value("1992-05-15"))
                .andExpect(jsonPath("$.personal.gender").value("MALE"))
                .andExpect(jsonPath("$.personal.fullName").value(""))
                .andExpect(jsonPath("$.personal.phone").value(org.hamcrest.Matchers.startsWith("09")))
                .andExpect(jsonPath("$.versionNo").value("2026.10"))
                .andExpect(jsonPath("$.consentBAt").isNotEmpty())
                .andExpect(jsonPath("$.contentHash").value((Object) null))
                .andExpect(jsonPath("$.qrToken").value((Object) null))
                .andExpect(jsonPath("$.documents[*].documentTypeId").value(org.hamcrest.Matchers.contains(2, 3, 4, 5, 6, 1)))
                .andExpect(jsonPath("$.documents[0].typeName").value("Căn cước công dân"))
                .andExpect(jsonPath("$.documents[0].status").value("MISSING"))
                .andExpect(jsonPath("$.documents[0].versionNo").value(0))
                .andExpect(jsonPath("$.documents[0].fields.length()").value(0))
                .andExpect(jsonPath("$.documents[3].allowedFormats").value("PDF,JPG,PNG"))
                .andExpect(jsonPath("$.warnings.length()").value(0))
                .andExpect(jsonPath("$.recalc").value((Object) null))
                .andExpect(jsonPath("$.requestForm").value((Object) null))
                .andExpect(jsonPath("$.verifications.length()").value(0))
                .andExpect(jsonPath("$.approval").value((Object) null))
                .andExpect(jsonPath("$.history.length()").value(1))
                .andExpect(jsonPath("$.history[0].status").value("DRAFTING"))
                .andExpect(jsonPath("$.history[0].note").value("Tạo hồ sơ"))
                .andExpect(jsonPath("$.history[0].actor").value(org.hamcrest.Matchers.startsWith("Khách hàng ")))
                .andExpect(jsonPath("$.can.edit").value(true))
                .andExpect(jsonPath("$.can.submit").value(true))
                .andExpect(jsonPath("$.can.resubmit").value(false))
                .andExpect(jsonPath("$.can.cancel").value(true))
                .andExpect(jsonPath("$.can.verify").value(false));
        String appId = read(r, "$.id");
        String code = read(r, "$.code");
        assertThat(jdbc.queryForObject("select c.scope from loan_application a join consent c on c.id = a.consent_id where a.public_id = ?::uuid",
                String.class, appId)).isEqualTo("B");
        assertThat(jdbc.queryForObject("select count(*) from activity_log l join loan_application a on a.id = l.application_id "
                + "where a.public_id = ?::uuid and l.action = 'Tạo hồ sơ vay' and l.detail = 'Chi nhánh Hà Nội'", Integer.class, appId))
                .isEqualTo(1);
        // Phương án trỏ tới hồ sơ; /me/applications có hồ sơ
        send(get("/plans/" + planId), null, token).andExpect(jsonPath("$.applicationId").value(appId));
        send(get("/me/applications"), null, token).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(appId))
                .andExpect(jsonPath("$[0].code").value(code))
                .andExpect(jsonPath("$[0].customerName").value("Chưa khai"))
                .andExpect(jsonPath("$[0].productCode").value("TD"))
                .andExpect(jsonPath("$[0].amount").value(150_000_000))
                .andExpect(jsonPath("$[0].branchName").value("Chi nhánh Hà Nội"))
                .andExpect(jsonPath("$[0].status").value("DRAFTING"));
        send(get("/me/applications"), null, staffHn()).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        send(get("/me/applications"), null, null).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Chưa đăng nhập."));
    }

    // ---------------------------------------------------------------- GET /applications/{id}

    @Test
    void get_visibleToOwnerBranchStaffAndAdminOnly() throws Exception {
        String token = newCustomer();
        String appId = newApp(token);
        send(get("/applications/" + appId), null, token).andExpect(status().isOk());
        send(get("/applications/" + appId), null, newCustomer()).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(NOT_FOUND));
        send(get("/applications/" + appId), null, login("STAFF", "nv.hcm@tiensangloc.local")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(NOT_FOUND));
        send(get("/applications/" + appId), null, staffHn()).andExpect(status().isOk())
                .andExpect(jsonPath("$.can.edit").value(false)).andExpect(jsonPath("$.can.cancel").value(false));
        send(get("/applications/" + appId), null, login("STAFF", "quantri@tiensangloc.local")).andExpect(status().isOk());
        send(get("/applications/123"), null, token).andExpect(status().isNotFound()).andExpect(jsonPath("$.message").value(NOT_FOUND));
        send(get("/applications/" + UUID.randomUUID()), null, token).andExpect(status().isNotFound());
        send(get("/applications/" + appId), null, null).andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("select count(*) from activity_log l join loan_application a on a.id = l.application_id "
                + "where a.public_id = ?::uuid and l.action = 'Xem hồ sơ' and l.actor_type = 'STAFF'", Integer.class, appId)).isEqualTo(2);
    }

    // ---------------------------------------------------------------- PUT /personal

    @Test
    void personal_validatesAndMerges() throws Exception {
        String token = newCustomer();
        String appId = newApp(token);
        send(put("/applications/" + appId + "/personal"), personal("123"), token).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Số CCCD phải gồm 12 chữ số."));
        send(put("/applications/" + appId + "/personal"), personal(NID), token).andExpect(status().isOk())
                .andExpect(jsonPath("$.personal.fullName").value("Nguyễn Văn Minh"))
                .andExpect(jsonPath("$.personal.nationalId").value(NID))
                .andExpect(jsonPath("$.personal.phone").value(org.hamcrest.Matchers.startsWith("09")));
        // Trường không gửi giữ nguyên
        send(put("/applications/" + appId + "/personal"), Map.of("email", "moi@example.com"), token).andExpect(status().isOk())
                .andExpect(jsonPath("$.personal.fullName").value("Nguyễn Văn Minh"))
                .andExpect(jsonPath("$.personal.email").value("moi@example.com"));
        send(put("/applications/" + appId + "/personal"), personal(NID), newCustomer()).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(NOT_FOUND));
        jdbc.update("update loan_application set status = 'WAITING_VERIFY' where public_id = ?::uuid", appId);
        send(put("/applications/" + appId + "/personal"), personal(NID), token).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Hồ sơ đã khóa, không sửa được."));
    }

    // ---------------------------------------------------------------- upload

    @Test
    void upload_qualityChecksOcrAndPreview() throws Exception {
        String token = newCustomer();
        String appId = newApp(token);
        send(put("/applications/" + appId + "/personal"), personal(NID), token).andExpect(status().isOk());
        long cccd = docId(appId, token, 2);
        String path = "$.documents[?(@.id == " + cccd + ")]";

        upload(appId, cccd, token, "anh.gif", "image/gif", 60).andExpect(status().isOk())
                .andExpect(jsonPath(path + ".status").value("REJECTED_QUALITY"))
                .andExpect(jsonPath(path + ".qualityStatus").value("WRONG_FORMAT"))
                .andExpect(jsonPath(path + ".qualityMessage").value("Định dạng GIF không được chấp nhận (cho phép JPG,PNG,PDF)."))
                .andExpect(jsonPath(path + ".versionNo").value(1));
        upload(appId, cccd, token, "to.png", "image/png", 10 * 1024 + 1).andExpect(status().isOk())
                .andExpect(jsonPath(path + ".qualityStatus").value("TOO_LARGE"))
                .andExpect(jsonPath(path + ".qualityMessage").value("Tệp lớn hơn 10 MB."));
        upload(appId, cccd, token, "mo.jpeg", "image/jpeg", 12).andExpect(status().isOk())
                .andExpect(jsonPath(path + ".status").value("REJECTED_QUALITY"))
                .andExpect(jsonPath(path + ".qualityStatus").value("BLURRY"))
                .andExpect(jsonPath(path + ".qualityMessage").value("Ảnh có độ phân giải thấp hoặc bị mờ, vui lòng chụp lại rõ nét, đủ bốn góc."))
                .andExpect(jsonPath(path + ".fileFormat").value("JPG"))
                .andExpect(jsonPath(path + ".fileSize").value(12))
                .andExpect(jsonPath(path + ".previewUrl").value(org.hamcrest.Matchers.contains((Object) null)))
                .andExpect(jsonPath(path + ".versionNo").value(3));
        assertThat(jdbc.queryForObject("select storage_path from application_document where id = ?", String.class, cccd)).isNull();

        byte[] data = new byte[60 * 1024];
        data[0] = 7;
        ResultActions ok = upload(appId, cccd, token, new MockMultipartFile("file", "cccd.jpg", "image/jpeg", data))
                .andExpect(status().isOk())
                .andExpect(jsonPath(path + ".status").value("UPLOADED"))
                .andExpect(jsonPath(path + ".qualityStatus").value("OK"))
                .andExpect(jsonPath(path + ".qualityMessage").value(org.hamcrest.Matchers.contains((Object) null)))
                .andExpect(jsonPath(path + ".fileName").value("cccd.jpg"))
                .andExpect(jsonPath(path + ".versionNo").value(4))
                .andExpect(jsonPath(path + ".fields[*].name").value(org.hamcrest.Matchers.contains("full_name", "national_id", "dob", "gender", "address")))
                .andExpect(jsonPath(path + ".fields[0].ocrValue").value("NGUYỄN VĂN MINH"))
                .andExpect(jsonPath(path + ".fields[0].label").value("Họ và tên"))
                .andExpect(jsonPath(path + ".fields[0].confidence").value(97.0))
                .andExpect(jsonPath(path + ".fields[1].ocrValue").value(NID))
                .andExpect(jsonPath(path + ".fields[2].ocrValue").value("15/05/1992"))
                .andExpect(jsonPath(path + ".fields[3].ocrValue").value("Nam"))
                .andExpect(jsonPath(path + ".fields[4].ocrValue").value("12 Láng Hạ, Đống Đa, Hà Nội"))
                .andExpect(jsonPath(path + ".fields[0].confirmedValue").value(org.hamcrest.Matchers.contains((Object) null)));
        List<String> previews = read(ok, path + ".previewUrl");
        String preview = previews.get(0);
        assertThat(preview).startsWith("/api/files/local?path=");
        mvc.perform(get(URI.create(preview.substring("/api".length())))).andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG)).andExpect(content().bytes(data));
        // Chữ ký sai → 404
        mvc.perform(get(URI.create(preview.substring("/api".length()).replaceAll("sig=[^&]+", "sig=abc")))).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Không tìm thấy."));
        assertThat(jdbc.queryForObject("select count(*) from activity_log l join loan_application a on a.id = l.application_id "
                + "where a.public_id = ?::uuid and l.action = 'Tải giấy tờ' and l.detail = 'Căn cước công dân (phiên bản 4)'",
                Integer.class, appId)).isEqualTo(1);

        // Tải lại: tệp phiên bản cũ bị xóa khỏi kho
        String oldPath = jdbc.queryForObject("select storage_path from application_document where id = ?", String.class, cccd);
        upload(appId, cccd, token, "cccd2.png", "image/png", 40).andExpect(status().isOk())
                .andExpect(jsonPath(path + ".versionNo").value(5));
        assertThat(Files.exists(Path.of("target/test-uploads", oldPath + ".jpg"))).isFalse();
        mvc.perform(get(URI.create(preview.substring("/api".length())))).andExpect(status().isNotFound());

        // Sao kê PDF: không có ảnh xem trước; giấy đề nghị hệ thống không tải được; thiếu tệp
        long statement = docId(appId, token, 5);
        upload(appId, statement, token, "saoke.pdf", "application/pdf", 20).andExpect(status().isOk())
                .andExpect(jsonPath("$.documents[?(@.id == " + statement + ")].status").value("UPLOADED"))
                .andExpect(jsonPath("$.documents[?(@.id == " + statement + ")].previewUrl").value(org.hamcrest.Matchers.contains((Object) null)));
        upload(appId, docId(appId, token, 1), token, "x.pdf", "application/pdf", 40).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Không tìm thấy giấy tờ."));
        upload(appId, 999_999, token, "x.pdf", "application/pdf", 40).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Không tìm thấy giấy tờ."));
        upload(appId, cccd, token, null).andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Thiếu tệp."));
        upload(appId, cccd, newCustomer(), "cccd.jpg", "image/jpeg", 60).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(NOT_FOUND));
        jdbc.update("update loan_application set status = 'VERIFIED' where public_id = ?::uuid", appId);
        upload(appId, cccd, token, "cccd.jpg", "image/jpeg", 60).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Hồ sơ đã khóa, không tải thêm được."));
    }

    @Test
    void upload_ocrResultMapping() throws Exception {
        String token = newCustomer();
        String appId = newApp(token);
        long hdld = docId(appId, token, 4);
        List<String> names = read(send(get("/applications/" + appId), null, token), "$.documents[?(@.id == " + hdld + ")].typeName");
        String typeName = names.get(0);

        // Máy đọc lỗi (mạng, hết hạn mức, JSON sai) → vẫn UPLOADED, nhắc nhập tay, dòng trống để khách nhập và xác nhận
        doThrow(new IllegalStateException("Gemini lỗi")).when(ocr).extract(any(), any(), any(), any());
        String path = "$.documents[?(@.id == " + hdld + ")]";
        upload(appId, hdld, token, "hdld.jpg", "image/jpeg", 60).andExpect(status().isOk())
                .andExpect(jsonPath(path + ".status").value("UPLOADED"))
                .andExpect(jsonPath(path + ".qualityStatus").value("OK"))
                .andExpect(jsonPath(path + ".qualityMessage").value("Chưa đọc được thông tin tự động, vui lòng nhập tay"))
                .andExpect(jsonPath(path + ".previewUrl").value(org.hamcrest.Matchers.contains(org.hamcrest.Matchers.startsWith("/api/files/local?"))))
                .andExpect(jsonPath(path + ".fields[*].name").value(org.hamcrest.Matchers.contains("employer", "salary")))
                .andExpect(jsonPath(path + ".fields[*].label").value(org.hamcrest.Matchers.contains("Đơn vị công tác", "Mức lương theo hợp đồng")))
                .andExpect(jsonPath(path + ".fields[*].ocrValue").value(org.hamcrest.Matchers.contains("", "")))
                .andExpect(jsonPath(path + ".fields[*].confidence").value(org.hamcrest.Matchers.contains(0.0, 0.0)));
        confirm(appId, hdld, token, Map.of("employer", "Công ty ABC", "salary", "21.000.000")).andExpect(status().isOk())
                .andExpect(jsonPath(path + ".status").value("CONFIRMED"))
                .andExpect(jsonPath(path + ".fields[*].confirmedValue").value(org.hamcrest.Matchers.contains("Công ty ABC", "21.000.000")));

        // Không đúng loại giấy tờ → WRONG_TYPE, không lưu tệp, không có trường
        doReturn(new OcrResult(true, false, null, List.of())).when(ocr).extract(any(), any(), any(), any());
        upload(appId, hdld, token, "hdld.jpg", "image/jpeg", 60).andExpect(status().isOk())
                .andExpect(jsonPath(path + ".status").value("REJECTED_QUALITY"))
                .andExpect(jsonPath(path + ".qualityStatus").value("WRONG_TYPE"))
                .andExpect(jsonPath(path + ".qualityMessage").value("Tệp không phải " + typeName + "."))
                .andExpect(jsonPath(path + ".previewUrl").value(org.hamcrest.Matchers.contains((Object) null)))
                .andExpect(jsonPath(path + ".fields.length()").value(0));

        // Máy đọc báo không đọc được → BLURRY kèm lời nhắc của máy đọc
        doReturn(new OcrResult(false, true, "Ảnh bị lóa ở phần mức lương, vui lòng chụp lại.", List.of()))
                .when(ocr).extract(any(), any(), any(), any());
        upload(appId, hdld, token, "hdld.jpg", "image/jpeg", 60).andExpect(status().isOk())
                .andExpect(jsonPath(path + ".status").value("REJECTED_QUALITY"))
                .andExpect(jsonPath(path + ".qualityStatus").value("BLURRY"))
                .andExpect(jsonPath(path + ".qualityMessage").value("Ảnh bị lóa ở phần mức lương, vui lòng chụp lại."));
    }

    @Test
    void localFile_rejectsExpiredAndTraversal() throws Exception {
        StoredFile f = storage.upload(new byte[] {1, 2, 3}, "doc-1-v1.png", "tien-sang-loc-vay/test/app-x");
        URI url = URI.create(storage.signedUrl(f, Duration.ofMinutes(1)).substring("/api".length()));
        mvc.perform(get(url)).andExpect(status().isOk()).andExpect(content().bytes(new byte[] {1, 2, 3}));
        mvc.perform(get(URI.create(storage.signedUrl(f, Duration.ofSeconds(-5)).substring("/api".length())))).andExpect(status().isNotFound());
        mvc.perform(get("/files/local").param("path", "../pom.xml").param("exp", "9999999999").param("sig", "x"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/files/local")).andExpect(status().isNotFound());
        assertThat(storage.open("../../pom.xml", Long.MAX_VALUE / 2, "x")).isEmpty();
        storage.delete(f);
        mvc.perform(get(url)).andExpect(status().isNotFound());
    }

    // ---------------------------------------------------------------- fields, đối chiếu, phương án

    @Test
    void confirmFields_fillsPersonalFromCccdAndCrossChecks() throws Exception {
        String token = newCustomer();
        String appId = newApp(token);
        long cccd = docId(appId, token, 2);
        confirm(appId, cccd, token, Map.of()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Giấy tờ chưa có thông tin trích xuất."));
        upload(appId, cccd, token, "cccd.jpg", "image/jpeg", 60).andExpect(status().isOk());
        // Thông tin cá nhân còn trống → điền từ CCCD (họ tên viết hoa chữ đầu)
        confirm(appId, cccd, token, Map.of("full_name", "NGUYỄN VĂN MINH", "national_id", NID, "address", "12 Láng Hạ"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documents[0].status").value("CONFIRMED"))
                .andExpect(jsonPath("$.documents[0].fields[0].confirmedValue").value("NGUYỄN VĂN MINH"))
                .andExpect(jsonPath("$.documents[0].fields[2].confirmedValue").value("15/05/1992"))
                .andExpect(jsonPath("$.personal.fullName").value("Nguyễn Văn Minh"))
                .andExpect(jsonPath("$.personal.nationalId").value(NID))
                .andExpect(jsonPath("$.personal.address").value("12 Láng Hạ"))
                .andExpect(jsonPath("$.warnings.length()").value(0));

        // Số CCCD khai khác giấy tờ → BLOCKING, chặn lập giấy đề nghị
        send(put("/applications/" + appId + "/personal"), Map.of("nationalId", "001092099999"), token).andExpect(status().isOk())
                .andExpect(jsonPath("$.warnings[0].field").value("Số CCCD"))
                .andExpect(jsonPath("$.warnings[0].declared").value("001092099999"))
                .andExpect(jsonPath("$.warnings[0].verified").value(NID))
                .andExpect(jsonPath("$.warnings[0].message").value("Số CCCD trên giấy tờ khác số đã khai."))
                .andExpect(jsonPath("$.warnings[0].severity").value("BLOCKING"));
        send(post("/applications/" + appId + "/request-form"), null, token).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Chưa tải lên: Xác nhận cư trú. Chưa tải lên: Hợp đồng lao động hoặc xác nhận nhân sự. "
                        + "Chưa tải lên: Sao kê tài khoản nhận lương 03 tháng. Chưa tải lên: Hóa đơn, hợp đồng mua bán hoặc hợp đồng 3 bên. "
                        + "Còn sai lệch cần xử lý (xem mục Cảnh báo đối chiếu)."));

        // Sao kê 40 KB → thu nhập 19.000.000 (thấp hơn 5%), tính lại theo sao kê
        long statement = docId(appId, token, 5);
        upload(appId, statement, token, "saoke.pdf", "application/pdf", 40).andExpect(status().isOk())
                .andExpect(jsonPath("$.documents[3].fields[1].ocrValue").value("19.000.000"))
                .andExpect(jsonPath("$.documents[3].fields[0].ocrValue").value("Vietcombank"))
                .andExpect(jsonPath("$.recalc").value((Object) null));
        ResultActions r = confirm(appId, statement, token, Map.of()).andExpect(status().isOk())
                .andExpect(jsonPath("$.recalc.verifiedIncome").value(19_000_000))
                .andExpect(jsonPath("$.recalc.ok").value(true))
                .andExpect(jsonPath("$.warnings[?(@.field == 'Thu nhập bình quân')].message").value(org.hamcrest.Matchers.contains(
                        "Thu nhập trên sao kê thấp hơn khai báo 5%; DTI và điểm được tính lại theo sao kê.")))
                .andExpect(jsonPath("$.warnings[?(@.field == 'Thu nhập bình quân')].declared").value(org.hamcrest.Matchers.contains("20.000.000")))
                .andExpect(jsonPath("$.warnings[?(@.field == 'Thu nhập bình quân')].severity").value(org.hamcrest.Matchers.contains("WARNING")));
        assertThat((Integer) read(r, "$.recalc.maxSafeAmount")).isPositive();
    }

    @Test
    void updatePlan_recalculatesWithVerifiedIncome() throws Exception {
        String token = newCustomer();
        String appId = newApp(token);
        send(put("/applications/" + appId + "/plan"), Map.of("amount", 2_000_000_000L, "termMonths", 36, "crossSellIds", List.of()), token)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.startsWith("Phương án chưa đạt: ")));
        send(put("/applications/" + appId + "/plan"), Map.of("amount", 100_000_000, "termMonths", 24), token)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.crossSellIds").exists());
        send(put("/applications/" + appId + "/plan"), Map.of("amount", 100_000_000, "termMonths", 24, "crossSellIds", List.of(3)), token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan.amount").value(100_000_000))
                .andExpect(jsonPath("$.plan.termMonths").value(24))
                .andExpect(jsonPath("$.plan.refRate").value(11.8))
                .andExpect(jsonPath("$.plan.crossSells[0].id").value(3));
        assertThat(jdbc.queryForObject("select count(*) from activity_log l join loan_application a on a.id = l.application_id "
                + "where a.public_id = ?::uuid and l.action = 'Điều chỉnh phương án trong hồ sơ' and l.detail = '100.000.000 đ, 24 tháng'",
                Integer.class, appId)).isEqualTo(1);
        // Đổi lần nữa: thay danh sách bán chéo
        send(put("/applications/" + appId + "/plan"), Map.of("amount", 120_000_000, "termMonths", 36, "crossSellIds", List.of()), token)
                .andExpect(status().isOk()).andExpect(jsonPath("$.plan.crossSells.length()").value(0));
        jdbc.update("update loan_application set status = 'WAITING_VERIFY' where public_id = ?::uuid", appId);
        send(put("/applications/" + appId + "/plan"), Map.of("amount", 100_000_000, "termMonths", 24, "crossSellIds", List.of()), token)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("Hồ sơ đã khóa."));
    }

    // ---------------------------------------------------------------- giấy đề nghị, nộp, nộp lại

    @Test
    void requestFormAndSubmit_locksAndNotifies() throws Exception {
        String token = newCustomer();
        String appId = newApp(token);
        send(put("/applications/" + appId + "/request-form"), Map.of("values", Map.of()), token).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Chưa có bản nháp giấy đề nghị."));
        completeDocs(appId, token);
        ResultActions form = send(post("/applications/" + appId + "/request-form"), null, token).andExpect(status().isOk())
                .andExpect(jsonPath("$.warnings.length()").value(0))
                .andExpect(jsonPath("$.requestForm.status").value("DRAFT"))
                .andExpect(jsonPath("$.requestForm.content.length()").value(19))
                .andExpect(jsonPath("$.requestForm.content[0].key").value("fullName"))
                .andExpect(jsonPath("$.requestForm.content[0].label").value("Họ và tên người vay"))
                .andExpect(jsonPath("$.requestForm.content[0].value").value("Nguyễn Văn Minh"))
                .andExpect(jsonPath("$.requestForm.content[0].editable").value(false))
                .andExpect(jsonPath("$.requestForm.content[2].value").value("15/05/1992"))
                .andExpect(jsonPath("$.requestForm.content[7].value").value("Công ty CP Công nghệ Sao Mai"))
                .andExpect(jsonPath("$.requestForm.content[8].value").value("20.000.000"))
                .andExpect(jsonPath("$.requestForm.content[10].value").value("150.000.000 đồng"))
                .andExpect(jsonPath("$.requestForm.content[11].value").value("36 tháng"))
                .andExpect(jsonPath("$.requestForm.content[12].value").value("12.5%/năm (theo thông báo của ngân hàng tại thời điểm giải ngân)"))
                .andExpect(jsonPath("$.requestForm.content[15].value").value("Công ty TNHH Thương mại Hòa Bình"))
                .andExpect(jsonPath("$.requestForm.content[17].value").value("Chi nhánh Hà Nội"))
                .andExpect(jsonPath("$.requestForm.otpConfirmedAt").value((Object) null));
        assertThat((String) read(form, "$.requestForm.content[5].value")).startsWith("09");
        send(put("/applications/" + appId + "/request-form"), Map.of("values", Map.of("note", "Gọi giờ hành chính", "fullName", "X")), token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestForm.content[18].value").value("Gọi giờ hành chính"))
                .andExpect(jsonPath("$.requestForm.content[0].value").value("Nguyễn Văn Minh"));

        // OTP sai, chưa gửi mã
        send(post("/applications/" + appId + "/submit"), Map.of("otp", "123456"), token).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Mã OTP không đúng."));
        sendOtp(token);
        send(post("/applications/" + appId + "/submit"), Map.of("otp", "000000"), token).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Mã OTP không đúng."));
        OffsetDateTime before = OffsetDateTime.now();
        ResultActions r = send(post("/applications/" + appId + "/submit"), Map.of("otp", "123456"), token).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WAITING_VERIFY"))
                .andExpect(jsonPath("$.qrToken").value(org.hamcrest.Matchers.matchesPattern("^QR[A-Z0-9]{8}$")))
                .andExpect(jsonPath("$.contentHash").value(org.hamcrest.Matchers.matchesPattern("^[0-9a-f]{64}$")))
                .andExpect(jsonPath("$.lockedAt").isNotEmpty())
                .andExpect(jsonPath("$.requestForm.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.requestForm.otpConfirmedAt").isNotEmpty())
                .andExpect(jsonPath("$.requestForm.content[18].value").value("Gọi giờ hành chính"))
                .andExpect(jsonPath("$.documents[5].status").value("CONFIRMED"))
                .andExpect(jsonPath("$.documents[5].fileName").value(org.hamcrest.Matchers.endsWith("-giay-de-nghi.pdf")))
                .andExpect(jsonPath("$.documents[5].fileSize").value(120))
                .andExpect(jsonPath("$.documents[5].versionNo").value(1))
                .andExpect(jsonPath("$.history.length()").value(2))
                .andExpect(jsonPath("$.history[1].status").value("WAITING_VERIFY"))
                .andExpect(jsonPath("$.history[1].note").value("Xác nhận giấy đề nghị bằng OTP, nộp hồ sơ"))
                .andExpect(jsonPath("$.can.edit").value(false))
                .andExpect(jsonPath("$.can.submit").value(false))
                .andExpect(jsonPath("$.can.cancel").value(true));
        String qr = read(r, "$.qrToken");
        String code = read(r, "$.code");
        OffsetDateTime expires = OffsetDateTime.parse(read(r, "$.expiresAt"));
        assertThat(expires).isBetween(before.plusDays(30).minusMinutes(1), OffsetDateTime.now().plusDays(30).plusMinutes(1));
        String hash = read(r, "$.contentHash");
        assertThat(jdbc.queryForObject("select count(*) from activity_log l join loan_application a on a.id = l.application_id "
                + "where a.public_id = ?::uuid and l.action = 'Nộp hồ sơ' and l.detail = ?", Integer.class, appId,
                "Khóa nội dung, mã băm " + hash.substring(0, 12) + "…")).isEqualTo(1);

        // Thông báo: khách hàng và chi nhánh tiếp nhận
        send(get("/notifications"), null, token).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Đã nộp hồ sơ"))
                .andExpect(jsonPath("$[0].body").value("Hồ sơ " + code + " đang chờ đối chiếu tại Chi nhánh Hà Nội. "
                        + "Mang bản gốc giấy tờ và Phiếu tóm tắt khi đến quầy."))
                .andExpect(jsonPath("$[0].link").value("/ho-so/" + appId))
                .andExpect(jsonPath("$[0].read").value(false));
        send(get("/notifications"), null, staffHn()).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.link == '/noi-bo/ho-so/" + appId + "')].title")
                        .value(org.hamcrest.Matchers.contains("Có hồ sơ mới chờ đối chiếu")))
                .andExpect(jsonPath("$[?(@.link == '/noi-bo/ho-so/" + appId + "')].body")
                        .value(org.hamcrest.Matchers.contains(code + " – Nguyễn Văn Minh")));
        send(get("/me/applications"), null, token).andExpect(jsonPath("$[0].qrToken").value(qr))
                .andExpect(jsonPath("$[0].customerName").value("Nguyễn Văn Minh"))
                .andExpect(jsonPath("$[0].expiresAt").isNotEmpty());

        // Đã khóa: không sửa, không nộp lại được
        send(put("/applications/" + appId + "/personal"), personal(NID), token).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Hồ sơ đã khóa, không sửa được."));
        send(post("/applications/" + appId + "/request-form"), null, token).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Hồ sơ đã khóa."));
        sendOtp(token);
        send(post("/applications/" + appId + "/submit"), Map.of("otp", "123456"), token).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Hồ sơ không ở trạng thái nộp được."));

        // Nộp lại sau khi nhân viên báo sao kê không khớp (B5 sẽ ghi verify_result; ở đây đặt bằng SQL)
        long statement = docId(appId, token, 5);
        jdbc.update("update loan_application set status = 'NEED_MORE' where public_id = ?::uuid", appId);
        jdbc.update("update application_document set verify_result = 'MISMATCH', verify_reason = 'Sai tên' where id = ?", statement);
        send(get("/applications/" + appId), null, token)
                .andExpect(jsonPath("$.documents[3].verifyResult").value("MISMATCH"))
                .andExpect(jsonPath("$.can.resubmit").value(true));
        send(post("/applications/" + appId + "/submit"), Map.of("otp", "123456"), token).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Cần tải lại: Sao kê tài khoản nhận lương 03 tháng."));
        upload(appId, statement, token, "saoke.pdf", "application/pdf", 32).andExpect(status().isOk())
                .andExpect(jsonPath("$.documents[3].verifyResult").value((Object) null))
                .andExpect(jsonPath("$.requestForm").value((Object) null));
        confirm(appId, statement, token, Map.of()).andExpect(status().isOk());
        send(post("/applications/" + appId + "/submit"), Map.of("otp", "123456"), token).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WAITING_VERIFY"))
                .andExpect(jsonPath("$.qrToken").value(qr))
                .andExpect(jsonPath("$.history[2].note").value("Bổ sung giấy tờ, nộp lại"))
                .andExpect(jsonPath("$.documents[5].versionNo").value(2));
        send(get("/notifications"), null, staffHn())
                .andExpect(jsonPath("$[?(@.link == '/noi-bo/ho-so/" + appId + "')].title")
                        .value(org.hamcrest.Matchers.contains("Hồ sơ đã bổ sung", "Có hồ sơ mới chờ đối chiếu")));
        assertThat(jdbc.queryForObject("select count(*) from activity_log l join loan_application a on a.id = l.application_id "
                + "where a.public_id = ?::uuid and l.action = 'Nộp lại hồ sơ'", Integer.class, appId)).isEqualTo(1);
    }

    @Test
    void submit_expiredMovesThroughNeedMore() throws Exception {
        String token = newCustomer();
        String appId = newApp(token);
        completeDocs(appId, token);
        jdbc.update("update loan_application set status = 'EXPIRED' where public_id = ?::uuid", appId);
        // Giấy tờ tải lên quá DOC_VALIDITY_DAYS (30) ngày
        jdbc.update("update application_document set uploaded_at = now() - interval '31 days' where document_type_id = 4 and "
                + "application_id = (select id from loan_application where public_id = ?::uuid)", appId);
        sendOtp(token);
        send(post("/applications/" + appId + "/submit"), Map.of("otp", "123456"), token).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Giấy tờ đã quá hạn hiệu lực, cần tải bản mới: Hợp đồng lao động hoặc xác nhận nhân sự."));
        long contract = docId(appId, token, 4);
        upload(appId, contract, token, "hdld.jpg", "image/jpeg", 60).andExpect(status().isOk());
        confirm(appId, contract, token, Map.of()).andExpect(status().isOk());
        send(post("/applications/" + appId + "/submit"), Map.of("otp", "123456"), token).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WAITING_VERIFY"))
                .andExpect(jsonPath("$.history[1].status").value("NEED_MORE"))
                .andExpect(jsonPath("$.history[1].note").value("Cập nhật giấy tờ hết hạn"))
                .andExpect(jsonPath("$.history[2].status").value("WAITING_VERIFY"));
    }

    // ---------------------------------------------------------------- hủy

    @Test
    void cancel_onlyOpenOrExpired() throws Exception {
        String token = newCustomer();
        String planId = confirmedPlan(token);
        String appId = read(send(post("/applications"), createBody(planId), token), "$.id");
        String code = read(send(get("/applications/" + appId), null, token), "$.code");
        send(post("/applications/" + appId + "/cancel"), Map.of("reason", "Đổi ý"), token).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.history[1].status").value("CANCELLED"))
                .andExpect(jsonPath("$.history[1].note").value("Đổi ý"))
                .andExpect(jsonPath("$.can.cancel").value(false));
        assertThat(jdbc.queryForObject("select cancel_reason from loan_application where public_id = ?::uuid", String.class, appId))
                .isEqualTo("Đổi ý");
        send(post("/applications/" + appId + "/cancel"), null, token).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Hồ sơ đang ở trạng thái “Đã hủy”, không hủy trực tuyến được. Vui lòng liên hệ chi nhánh."));
        send(get("/notifications"), null, staffHn())
                .andExpect(jsonPath("$[?(@.link == '/noi-bo/ho-so/" + appId + "')].title").value(org.hamcrest.Matchers.contains("Hồ sơ đã bị hủy")))
                .andExpect(jsonPath("$[?(@.link == '/noi-bo/ho-so/" + appId + "')].body")
                        .value(org.hamcrest.Matchers.contains(code + " – khách hàng hủy hồ sơ")));

        // Phương án đã có hồ sơ hủy thì lập hồ sơ mới được; hủy không kèm thân yêu cầu
        String second = read(send(post("/applications"), createBody(planId), token).andExpect(status().isCreated()), "$.id");
        mvc.perform(post("/applications/" + second + "/cancel").header("Authorization", "Bearer " + token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.history[1].note").value("Khách hàng hủy hồ sơ"));

        String third = read(send(post("/applications"), createBody(planId), token).andExpect(status().isCreated()), "$.id");
        jdbc.update("update loan_application set status = 'APPROVED' where public_id = ?::uuid", third);
        send(post("/applications/" + third + "/cancel"), Map.of(), token).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Hồ sơ đang ở trạng thái “Được phê duyệt”, không hủy trực tuyến được. Vui lòng liên hệ chi nhánh."));
        send(post("/applications/" + third + "/cancel"), Map.of(), newCustomer()).andExpect(status().isNotFound());
    }
}
