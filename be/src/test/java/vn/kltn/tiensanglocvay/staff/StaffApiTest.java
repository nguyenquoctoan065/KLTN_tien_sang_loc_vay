package vn.kltn.tiensanglocvay.staff;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
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
import java.util.ArrayList;
import java.util.HashMap;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * B5: /staff/** và AppDocument.fileUrl. Thông báo lỗi đối chiếu với fe/src/mocks/handlers/staff.ts.
 * Hồ sơ gói TD nộp tại Chi nhánh Hà Nội (id 1); giấy tờ: CCCD(2), cư trú(3), HĐLĐ(4), sao kê(5, PDF), hóa đơn(6), giấy đề nghị(1).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class StaffApiTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    private static final AtomicInteger SEQ = new AtomicInteger(50_000_000);
    private static final String NOT_FOUND = "Không tìm thấy hồ sơ của chi nhánh.";
    private static final String FORBIDDEN = "Không có quyền.";

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ObjectMapper json;

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

    private String staffHcm() throws Exception {
        return login("STAFF", "nv.hcm@tiensangloc.local");
    }

    private String admin() throws Exception {
        return login("STAFF", "quantri@tiensangloc.local");
    }

    private String newCustomer() throws Exception {
        String phone = "09" + SEQ.incrementAndGet();
        send(post("/auth/otp"), Map.of("phone", phone), null).andExpect(status().isOk());
        return read(send(post("/auth/register"), Map.of("phone", phone, "password", "123456", "otp", "123456"), null)
                .andExpect(status().isOk()), "$.token");
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
        r.put("otherDebts", List.of());
        r.put("purpose", "Mua xe máy, ô tô");
        r.put("desiredAmount", 150_000_000);
        r.put("desiredTerm", 36);
        return r;
    }

    private String newApp(String token) throws Exception {
        String screeningId = read(send(post("/screenings"), screening(), null).andExpect(status().isCreated()), "$.id");
        String planId = read(send(post("/plans"), Map.of("screeningId", screeningId, "productCode", "TD", "amount", 150_000_000,
                "termMonths", 36, "crossSellIds", List.of()), token).andExpect(status().isCreated()), "$.id");
        send(post("/plans/" + planId + "/confirm"), null, token).andExpect(status().isOk());
        return read(send(post("/applications"), Map.of("planId", planId, "branchId", 1,
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

    private void submit(String appId, String token) throws Exception {
        send(post("/auth/otp"), Map.of(), token).andExpect(status().isOk());
        send(post("/applications/" + appId + "/submit"), Map.of("otp", "123456"), token).andExpect(status().isOk());
    }

    /** Khách mới nộp hồ sơ tại Chi nhánh Hà Nội; trả {token, appId}. */
    private String[] submittedApp() throws Exception {
        String token = newCustomer();
        String appId = newApp(token);
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("fullName", "Trần Thị Lan");
        p.put("nationalId", "001092012345");
        p.put("dob", "1992-05-15");
        p.put("gender", "MALE");
        p.put("address", "12 Láng Hạ, Đống Đa, Hà Nội");
        p.put("phone", "0000000000");
        p.put("email", "lan@example.com");
        send(put("/applications/" + appId + "/personal"), p, token).andExpect(status().isOk());
        int[][] files = {{2, 60}, {3, 60}, {4, 60}, {5, 32}, {6, 60}};
        for (int[] f : files) {
            uploadAndConfirm(appId, token, f[0], bytes(f[1], f[0]));
        }
        submit(appId, token);
        return new String[] {token, appId};
    }

    private List<Map<String, Object>> allMatch(String appId, String token) throws Exception {
        List<Number> ids = read(send(get("/applications/" + appId), null, token),
                "$.documents[?(@.documentTypeId != 1 && @.fileName != null)].id");
        List<Map<String, Object>> details = new ArrayList<>();
        for (Number id : ids) {
            Map<String, Object> d = new HashMap<>();
            d.put("documentId", id);
            d.put("result", "MATCH");
            d.put("reason", "");
            details.add(d);
        }
        return details;
    }

    private String qr(String appId, String token) throws Exception {
        return read(send(get("/applications/" + appId), null, token), "$.qrToken");
    }

    // ---------------------------------------------------------------- phân quyền

    @Test
    void roles_onlyBranchStaff() throws Exception {
        String customer = newCustomer();
        String id = UUID.randomUUID().toString();
        for (String token : new String[] {customer, admin()}) {
            send(get("/staff/applications"), null, token).andExpect(status().isForbidden()).andExpect(jsonPath("$.message").value(FORBIDDEN));
            send(get("/staff/summary"), null, token).andExpect(status().isForbidden());
            send(get("/staff/applications/by-qr/QRABCDEFGH"), null, token).andExpect(status().isForbidden());
            // Kiểm tra vai trò trước khi đọc body (body sai định dạng vẫn 403)
            mvc.perform(post("/staff/applications/" + id + "/verification").header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON).content("{sai")).andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.message").value(FORBIDDEN));
            send(post("/staff/applications/" + id + "/decision"), Map.of("resultType", "APPROVED"), token).andExpect(status().isForbidden());
            send(post("/staff/applications/" + id + "/disbursement"), Map.of(), token).andExpect(status().isForbidden());
        }
        send(get("/staff/applications"), null, null).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.message").value("Chưa đăng nhập."));
    }

    // ---------------------------------------------------------------- tra cứu

    @Test
    void list_byQr_summary() throws Exception {
        String hn = staffHn();
        Map<String, Object> before = JsonPath.read(body(send(get("/staff/summary"), null, hn).andExpect(status().isOk())), "$");

        String[] a = submittedApp();
        String code = read(send(get("/applications/" + a[1]), null, a[0]), "$.code");
        String token = a[0];
        String draft = newApp(token); // DRAFTING: nhân viên không thấy
        String draftCode = read(send(get("/applications/" + draft), null, token), "$.code");

        send(get("/staff/applications"), null, hn).andExpect(status().isOk())
                .andExpect(jsonPath("$[*].code").value(hasItem(code)))
                .andExpect(jsonPath("$[*].code").value(not(hasItem(draftCode))))
                .andExpect(jsonPath("$[*].status").value(not(hasItem("DRAFTING"))));
        // Sắp theo updatedAt giảm dần
        List<String> updated = read(send(get("/staff/applications"), null, hn), "$[*].updatedAt");
        List<String> sorted = new ArrayList<>(updated);
        sorted.sort((x, y) -> java.time.OffsetDateTime.parse(y).compareTo(java.time.OffsetDateTime.parse(x)));
        assertThat(updated).isEqualTo(sorted);

        // Lọc nhiều trạng thái, trạng thái lạ
        send(get("/staff/applications").param("status", "WAITING_VERIFY,NEED_MORE"), null, hn)
                .andExpect(jsonPath("$[*].code").value(hasItem(code)));
        send(get("/staff/applications").param("status", "APPROVED"), null, hn)
                .andExpect(jsonPath("$[*].code").value(not(hasItem(code))));
        send(get("/staff/applications").param("status", "KHONG_CO"), null, hn).andExpect(jsonPath("$.length()").value(0));

        // Từ khóa: mã (chứa, không phân biệt hoa thường), tên, số điện thoại, mã QR khớp đúng
        String qr = qr(a[1], token);
        String phone = read(send(get("/applications/" + a[1]), null, token), "$.personal.phone");
        send(get("/staff/applications").param("keyword", " " + code.toLowerCase() + " "), null, hn)
                .andExpect(jsonPath("$[*].code").value(contains(code)));
        send(get("/staff/applications").param("keyword", "thị lan"), null, hn).andExpect(jsonPath("$[*].code").value(hasItem(code)));
        send(get("/staff/applications").param("keyword", phone), null, hn).andExpect(jsonPath("$[*].code").value(contains(code)));
        send(get("/staff/applications").param("keyword", qr.toLowerCase()), null, hn).andExpect(jsonPath("$[*].code").value(contains(code)));
        send(get("/staff/applications").param("keyword", qr.substring(0, 6)), null, hn)
                .andExpect(jsonPath("$[*].code").value(not(hasItem(code))));
        send(get("/staff/applications").param("keyword", "%"), null, hn).andExpect(jsonPath("$.length()").value(0));
        // Nhân viên chi nhánh khác không thấy
        send(get("/staff/applications"), null, staffHcm()).andExpect(jsonPath("$[*].code").value(not(hasItem(code))));

        // by-qr: không phân biệt hoa thường; không có → 404; chi nhánh khác → 403
        send(get("/staff/applications/by-qr/" + qr.toLowerCase()), null, hn).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(a[1]));
        send(get("/staff/applications/by-qr/QR00000000"), null, hn).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Không tìm thấy hồ sơ theo mã QR."));
        send(get("/staff/applications/by-qr/" + qr), null, staffHcm()).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Hồ sơ thuộc chi nhánh khác."));

        send(get("/staff/summary"), null, hn).andExpect(status().isOk())
                .andExpect(jsonPath("$.waitingVerify").value(((Number) before.get("waitingVerify")).intValue() + 1))
                .andExpect(jsonPath("$.needMore").value(((Number) before.get("needMore")).intValue()));
    }

    // ---------------------------------------------------------------- đối chiếu → bổ sung → nộp lại → khớp → duyệt → giải ngân

    @Test
    void fullFlow_verifyMismatchResubmitApproveDisburse() throws Exception {
        String[] a = submittedApp();
        String token = a[0];
        String appId = a[1];
        String hn = staffHn();
        String base = "/staff/applications/" + appId;

        // Khác chi nhánh, mã sai → 404
        send(post(base + "/verification"), Map.of("details", List.of(), "note", ""), staffHcm()).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(NOT_FOUND));
        send(post("/staff/applications/123/verification"), Map.of("details", List.of(), "note", ""), hn).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(NOT_FOUND));
        // Chưa đối chiếu thì chưa ghi kết quả/giải ngân được
        send(post(base + "/decision"), Map.of("resultType", "REJECTED", "reason", "x"), hn).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Chỉ ghi nhận kết quả cho hồ sơ đã đối chiếu."));
        send(post(base + "/disbursement"), Map.of(), hn).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Chỉ giải ngân hồ sơ đã được phê duyệt."));

        // Nhân viên xem hồ sơ: can.verify
        send(get("/applications/" + appId), null, hn).andExpect(jsonPath("$.can.verify").value(true))
                .andExpect(jsonPath("$.can.decide").value(false));

        List<Map<String, Object>> details = allMatch(appId, token);
        assertThat(details).hasSize(5);
        // Thiếu một giấy tờ
        send(post(base + "/verification"), Map.of("details", details.subList(0, 4), "note", ""), hn).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Cần ghi kết quả cho từng giấy tờ."));
        // Không khớp mà không có lý do
        long cccd = docId(appId, token, 2);
        details.get(0).put("result", "MISMATCH");
        details.get(0).put("reason", "  ");
        send(post(base + "/verification"), Map.of("details", details, "note", ""), hn).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Cần ghi lý do cho giấy tờ không khớp hoặc thiếu."));
        // Giấy tờ không thuộc hồ sơ, kết quả lạ → 400 kèm fields
        List<Map<String, Object>> wrong = new ArrayList<>(allMatch(appId, token));
        wrong.add(new HashMap<>(Map.of("documentId", 999_999, "result", "OK", "reason", "x")));
        send(post(base + "/verification"), Map.of("details", wrong, "note", ""), hn).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Dữ liệu không hợp lệ."))
                .andExpect(jsonPath("$.fields['details[5].documentId']").exists())
                .andExpect(jsonPath("$.fields['details[5].result']").exists());

        // CCCD không khớp → NEED_MORE
        assertThat(((Number) details.get(0).get("documentId")).longValue()).isEqualTo(cccd);
        details.get(0).put("reason", "Ảnh chân dung không khớp bản gốc");
        String path = "$.documents[?(@.id == " + cccd + ")]";
        send(post(base + "/verification"), Map.of("details", details, "note", "Khách mang thiếu bản gốc"), hn).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NEED_MORE"))
                .andExpect(jsonPath(path + ".verifyResult").value(contains("MISMATCH")))
                .andExpect(jsonPath(path + ".verifyReason").value(contains("Ảnh chân dung không khớp bản gốc")))
                .andExpect(jsonPath("$.verifications.length()").value(1))
                .andExpect(jsonPath("$.verifications[0].overallResult").value("MISMATCH"))
                .andExpect(jsonPath("$.verifications[0].staffName").isNotEmpty())
                .andExpect(jsonPath("$.verifications[0].note").value("Khách mang thiếu bản gốc"))
                .andExpect(jsonPath("$.verifications[0].details.length()").value(5))
                .andExpect(jsonPath("$.verifications[0].details[0].typeName").value("Căn cước công dân"))
                .andExpect(jsonPath("$.verifications[0].details[1].reason").value(""))
                .andExpect(jsonPath("$.history[-1].status").value("NEED_MORE"))
                .andExpect(jsonPath("$.history[-1].note").value("Căn cước công dân: Ảnh chân dung không khớp bản gốc"))
                .andExpect(jsonPath("$.can.verify").value(false));
        send(post(base + "/verification"), Map.of("details", details, "note", ""), hn).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Hồ sơ không ở trạng thái chờ đối chiếu."));
        assertThat(jdbc.queryForObject("select count(*) from notification n join loan_application a on a.customer_id = n.customer_id "
                + "where a.public_id = ?::uuid and n.title = 'Hồ sơ cần bổ sung giấy tờ' and n.body = 'Căn cước công dân – Ảnh chân dung không khớp bản gốc'",
                Integer.class, appId)).isEqualTo(1);

        // Khách thấy kết quả; nộp lại khi chưa tải lại → bị chặn
        send(get("/applications/" + appId), null, token).andExpect(jsonPath("$.can.resubmit").value(true))
                .andExpect(jsonPath("$.verifications.length()").value(1));
        send(post("/auth/otp"), Map.of(), token).andExpect(status().isOk());
        send(post("/applications/" + appId + "/submit"), Map.of("otp", "123456"), token).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Cần tải lại: Căn cước công dân.")));
        uploadAndConfirm(appId, token, 2, bytes(70, 9));
        send(get("/applications/" + appId), null, token).andExpect(jsonPath(path + ".verifyResult").value(contains((Object) null)));
        submit(appId, token);

        // Khớp toàn bộ → VERIFIED
        send(post(base + "/verification"), Map.of("details", allMatch(appId, token), "note", ""), hn).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VERIFIED"))
                .andExpect(jsonPath("$.verifications.length()").value(2))
                .andExpect(jsonPath("$.verifications[1].overallResult").value("MATCH"))
                .andExpect(jsonPath("$.verifications[1].note").value(""))
                .andExpect(jsonPath("$.history[-1].note").value("Tất cả giấy tờ khớp bản gốc"))
                .andExpect(jsonPath("$.can.decide").value(true));

        // Kết quả xét duyệt: lỗi
        send(post(base + "/decision"), Map.of("resultType", "REJECTED"), hn).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Cần ghi lý do."));
        send(post(base + "/decision"), Map.of("resultType", "MORE_INFO", "reason", " "), hn).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Cần ghi lý do."));
        send(post(base + "/decision"), Map.of("resultType", "APPROVED", "approvedAmount", 140_000_000, "approvedTerm", 36), hn)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Cần ghi số tiền, kỳ hạn, lãi suất được duyệt."));
        send(post(base + "/decision"), Map.of("resultType", "XYZ"), hn).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Dữ liệu không hợp lệ."));
        send(post(base + "/decision"), Map.of("resultType", "APPROVED", "approvedAmount", 140_000_000, "approvedTerm", 36,
                "approvedRate", 12.345), hn).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.approvedRate").exists());

        // Phê duyệt
        send(post(base + "/decision"), Map.of("resultType", "APPROVED", "approvedAmount", 140_000_000, "approvedTerm", 36,
                "approvedRate", 12.5), hn).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.approval.resultType").value("APPROVED"))
                .andExpect(jsonPath("$.approval.approvedAmount").value(140_000_000))
                .andExpect(jsonPath("$.approval.approvedTerm").value(36))
                .andExpect(jsonPath("$.approval.approvedRate").value(12.5))
                .andExpect(jsonPath("$.approval.reason").value((Object) null))
                .andExpect(jsonPath("$.approval.staffName").isNotEmpty())
                .andExpect(jsonPath("$.approval.disbursedAt").value((Object) null))
                .andExpect(jsonPath("$.history[-1].note").value("Duyệt 140.000.000 đ, 36 tháng, 12.5%/năm"))
                .andExpect(jsonPath("$.can.disburse").value(true));

        // Giải ngân: lỗi
        send(post(base + "/disbursement"), Map.of("disbursedAt", "2026-10-25", "disbursedAmount", 0, "beneficiary", "Cửa hàng A"), hn)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Cần đủ ngày, số tiền giải ngân và bên thụ hưởng."));
        send(post(base + "/disbursement"), Map.of("disbursedAt", "2026-10-25", "disbursedAmount", 100), hn)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Cần đủ ngày, số tiền giải ngân và bên thụ hưởng."));
        send(post(base + "/disbursement"), Map.of("disbursedAt", "2026-10-25", "disbursedAmount", 140_000_001, "beneficiary", "Cửa hàng A"), hn)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Số tiền giải ngân vượt số tiền được duyệt."));
        send(post(base + "/disbursement"), Map.of("disbursedAt", "25/10/2026", "disbursedAmount", 100, "beneficiary", "Cửa hàng A"), hn)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.disbursedAt").exists());

        // Giải ngân
        send(post(base + "/disbursement"), Map.of("disbursedAt", "2026-10-25", "disbursedAmount", 140_000_000,
                "beneficiary", "Công ty TNHH Thương mại Hòa Bình"), hn).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISBURSED"))
                .andExpect(jsonPath("$.approval.disbursedAt").value("2026-10-25"))
                .andExpect(jsonPath("$.approval.disbursedAmount").value(140_000_000))
                .andExpect(jsonPath("$.approval.beneficiary").value("Công ty TNHH Thương mại Hòa Bình"))
                .andExpect(jsonPath("$.history[-1].note").value("Giải ngân 140.000.000 đ cho Công ty TNHH Thương mại Hòa Bình"))
                .andExpect(jsonPath("$.can.disburse").value(false));
        send(post(base + "/disbursement"), Map.of("disbursedAt", "2026-10-25", "disbursedAmount", 1, "beneficiary", "X"), hn)
                .andExpect(status().isConflict());

        assertThat(jdbc.queryForObject("select count(*) from notification n join loan_application a on a.customer_id = n.customer_id "
                + "where a.public_id = ?::uuid and n.title = 'Khoản vay đã được giải ngân' "
                + "and n.body = '140.000.000 đ ngày 25/10/2026 vào Công ty TNHH Thương mại Hòa Bình.'", Integer.class, appId)).isEqualTo(1);
        List<String> actions = jdbc.queryForList("select l.action || ' / ' || coalesce(l.detail, '') from activity_log l "
                + "join loan_application a on a.id = l.application_id where a.public_id = ?::uuid and l.actor_type = 'STAFF' and l.action <> 'Xem hồ sơ' order by l.id",
                String.class, appId);
        assertThat(actions).containsExactly("Đối chiếu giấy tờ / Có giấy tờ không khớp", "Đối chiếu giấy tờ / Khớp toàn bộ",
                "Cập nhật kết quả xét duyệt / Được phê duyệt", "Cập nhật giải ngân / 140.000.000 đ");
        assertThat(jdbc.queryForObject("select count(*) from application_status_history h join loan_application a on a.id = h.application_id "
                + "where a.public_id = ?::uuid", Integer.class, appId)).isEqualTo(7); // tạo, nộp, cần bổ sung, nộp lại, đối chiếu, duyệt, giải ngân
    }

    @Test
    void decision_rejectAndMoreInfo() throws Exception {
        String hn = staffHn();
        String[] a = submittedApp();
        String base = "/staff/applications/" + a[1];
        send(post(base + "/verification"), Map.of("details", allMatch(a[1], a[0]), "note", ""), hn).andExpect(status().isOk());
        send(post(base + "/decision"), Map.of("resultType", "MORE_INFO", "reason", "Bổ sung xác nhận thu nhập"), hn).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NEED_MORE"))
                .andExpect(jsonPath("$.approval.resultType").value("MORE_INFO"))
                .andExpect(jsonPath("$.approval.reason").value("Bổ sung xác nhận thu nhập"))
                .andExpect(jsonPath("$.history[-1].note").value("Ngân hàng yêu cầu bổ sung: Bổ sung xác nhận thu nhập"));
        submit(a[1], a[0]);
        send(post(base + "/verification"), Map.of("details", allMatch(a[1], a[0]), "note", ""), hn).andExpect(status().isOk());
        send(post(base + "/decision"), Map.of("resultType", "REJECTED", "reason", "Không đủ điều kiện thẩm định"), hn).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.approval.resultType").value("REJECTED"))
                .andExpect(jsonPath("$.history[-1].note").value("Không đủ điều kiện thẩm định"));
        assertThat(jdbc.queryForObject("select count(*) from approval_result r join loan_application a on a.id = r.application_id "
                + "where a.public_id = ?::uuid", Integer.class, a[1])).isEqualTo(2);
        send(get("/applications/" + a[1]), null, a[0]).andExpect(jsonPath("$.approval.resultType").value("REJECTED"));
    }

    // ---------------------------------------------------------------- fileUrl

    @Test
    void fileUrl_servesOriginalImageAndPdf() throws Exception {
        String token = newCustomer();
        String appId = newApp(token);
        byte[] jpg = bytes(60, 3);
        byte[] pdf = bytes(32, 5);
        uploadAndConfirm(appId, token, 2, jpg);
        uploadAndConfirm(appId, token, 5, pdf);
        ResultActions r = send(get("/applications/" + appId), null, token);
        List<String> cccdUrls = read(r, "$.documents[?(@.documentTypeId == 2)].fileUrl");
        List<String> pdfUrls = read(r, "$.documents[?(@.documentTypeId == 5)].fileUrl");
        assertThat(cccdUrls.get(0)).startsWith("/api/files/local?");
        mvc.perform(get(URI.create(cccdUrls.get(0).substring("/api".length())))).andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG)).andExpect(content().bytes(jpg));
        mvc.perform(get(URI.create(pdfUrls.get(0).substring("/api".length())))).andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF)).andExpect(content().bytes(pdf));
        // Chưa tải thì không có
        send(get("/applications/" + appId), null, token)
                .andExpect(jsonPath("$.documents[?(@.documentTypeId == 3)].fileUrl").value(contains((Object) null)))
                .andExpect(jsonPath("$.documents[?(@.documentTypeId == 5)].previewUrl").value(contains((Object) null)));
        // Nhân viên chi nhánh tiếp nhận cũng nhận được
        send(get("/applications/" + appId), null, staffHn())
                .andExpect(jsonPath("$.documents[?(@.documentTypeId == 2)].fileUrl").value(contains(startsWith("/api/files/local?"))));
    }
}
