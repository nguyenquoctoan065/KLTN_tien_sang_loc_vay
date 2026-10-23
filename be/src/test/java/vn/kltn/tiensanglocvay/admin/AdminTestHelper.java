package vn.kltn.tiensanglocvay.admin;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Tiện ích chung cho test B7 (mỗi lớp test vẫn có container PostgreSQL riêng). */
public final class AdminTestHelper {

    public static final String FORBIDDEN = "Không có quyền.";
    private static final AtomicInteger SEQ = new AtomicInteger(70_000_000);

    private final MockMvc mvc;
    private final ObjectMapper json;

    public AdminTestHelper(MockMvc mvc, ObjectMapper json) {
        this.mvc = mvc;
        this.json = json;
    }

    public ResultActions send(MockHttpServletRequestBuilder req, Object body, String token) throws Exception {
        if (body != null) {
            req = req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        }
        if (token != null) {
            req = req.header("Authorization", "Bearer " + token);
        }
        return mvc.perform(req);
    }

    public static String body(ResultActions r) throws Exception {
        return r.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    public static <T> T read(ResultActions r, String path) throws Exception {
        return JsonPath.read(body(r), path);
    }

    public ResultActions login(String kind, String username, String password) throws Exception {
        return send(post("/auth/login"), Map.of("kind", kind, "username", username, "password", password), null);
    }

    public String staffToken(String email) throws Exception {
        return read(login("STAFF", email, "123456").andExpect(status().isOk()), "$.token");
    }

    public String admin() throws Exception {
        return staffToken("quantri@tiensangloc.local");
    }

    public String newCustomer() throws Exception {
        String phone = "09" + SEQ.incrementAndGet();
        send(post("/auth/otp"), Map.of("phone", phone), null).andExpect(status().isOk());
        return read(send(post("/auth/register"), Map.of("phone", phone, "password", "123456", "otp", "123456"), null)
                .andExpect(status().isOk()), "$.token");
    }

    public static Map<String, Object> screening() {
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

    /** Phương án gói TD đã xác nhận của khách (chưa lập hồ sơ); trả public id. */
    public String confirmedPlan(String token) throws Exception {
        String screeningId = read(send(post("/screenings"), screening(), null).andExpect(status().isCreated()), "$.id");
        String planId = read(send(post("/plans"), Map.of("screeningId", screeningId, "productCode", "TD", "amount", 150_000_000,
                "termMonths", 36, "crossSellIds", List.of()), token).andExpect(status().isCreated()), "$.id");
        send(post("/plans/" + planId + "/confirm"), null, token).andExpect(status().isOk());
        return planId;
    }

    /** POST /applications (chưa kiểm tra kết quả). */
    public ResultActions createApp(String token, String planId, int branchId) throws Exception {
        return send(post("/applications"), Map.of("planId", planId, "branchId", branchId,
                "consentB", Map.of("agreed", true, "docVersion", "DK-1.0")), token);
    }

    /** Hồ sơ DRAFTING gói TD tại chi nhánh {@code branchId}; trả public id. */
    public String newApp(String token, int branchId) throws Exception {
        return read(createApp(token, confirmedPlan(token), branchId).andExpect(status().isCreated()), "$.id");
    }

    /** Khách mới nộp hồ sơ (5 giấy tờ đã tải, xác nhận) tại chi nhánh {@code branchId}; trả {token, appId}. */
    public String[] submittedApp(int branchId) throws Exception {
        String token = newCustomer();
        String appId = newApp(token, branchId);
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
        send(post("/auth/otp"), Map.of(), token).andExpect(status().isOk());
        send(post("/applications/" + appId + "/submit"), Map.of("otp", "123456"), token).andExpect(status().isOk());
        return new String[] {token, appId};
    }

    private void uploadAndConfirm(String appId, String token, int typeId, byte[] data) throws Exception {
        List<Number> ids = read(send(get("/applications/" + appId), null, token), "$.documents[?(@.documentTypeId == " + typeId + ")].id");
        long id = ids.get(0).longValue();
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
}
