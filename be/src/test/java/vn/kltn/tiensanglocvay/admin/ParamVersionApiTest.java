package vn.kltn.tiensanglocvay.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static vn.kltn.tiensanglocvay.admin.AdminTestHelper.body;
import static vn.kltn.tiensanglocvay.admin.AdminTestHelper.read;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import vn.kltn.tiensanglocvay.engine.Engine;
import vn.kltn.tiensanglocvay.engine.ParamSet;
import vn.kltn.tiensanglocvay.engine.ProductCode;
import vn.kltn.tiensanglocvay.engine.WeightSet;
import vn.kltn.tiensanglocvay.policy.ActivePolicy;
import vn.kltn.tiensanglocvay.policy.EngineParams;

/** B7: /admin/param-versions/** (QT07). Thông báo lỗi đối chiếu với fe/src/mocks/handlers/admin.ts. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class ParamVersionApiTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ActivePolicy policy;

    @Autowired
    Clock clock;

    AdminTestHelper h;

    @BeforeEach
    void setUp() {
        h = new AdminTestHelper(mvc, json);
    }

    @Test
    void roles_onlyAdmin() throws Exception {
        String customer = h.newCustomer();
        String staff = h.staffToken("nhanvien@tiensangloc.local");
        for (String token : new String[] {customer, staff}) {
            h.send(get("/admin/param-versions"), null, token).andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.message").value(AdminTestHelper.FORBIDDEN));
            mvc.perform(post("/admin/param-versions/1/clone").header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON).content("{sai")).andExpect(status().isForbidden());
            h.send(post("/admin/param-versions/1/activate"), null, token).andExpect(status().isForbidden());
        }
        h.send(get("/admin/param-versions"), null, null).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Chưa đăng nhập."));
    }

    @Test
    void fullFlow_cloneEditTestActivate() throws Exception {
        String admin = h.admin();
        // Lượt sàng lọc theo phiên bản cũ: sau khi kích hoạt bản mới vẫn tính lại y hệt
        String screening = body(h.send(post("/screenings"), AdminTestHelper.screening(), null).andExpect(status().isCreated()));
        String screeningId = com.jayway.jsonpath.JsonPath.read(screening, "$.id");

        h.send(get("/admin/param-versions"), null, admin).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].versionNo").value("2026.10"))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$[0].createdBy").value("Trần Minh Đức"));
        h.send(get("/admin/param-versions/999"), null, admin).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Không tìm thấy phiên bản."));

        // Tạo bản nháp: nguồn không có, trùng số, dữ liệu sai, thành công kèm changes
        h.send(post("/admin/param-versions/999/clone"), Map.of("versionNo", "2026.11", "legalBasis", "BB"), admin)
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.message").value("Không tìm thấy phiên bản."));
        h.send(post("/admin/param-versions/1/clone"), Map.of("versionNo", "2026.10", "legalBasis", "BB"), admin)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("Số phiên bản đã tồn tại."));
        h.send(post("/admin/param-versions/1/clone"), Map.of("versionNo", "2026.11", "legalBasis", " "), admin)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.legalBasis").exists());
        Map<String, Object> change = new HashMap<>();
        change.put("key", "DTI_MAX");
        change.put("productCode", null);
        change.put("value", "75");
        int draftId = read(h.send(post("/admin/param-versions/1/clone"),
                Map.of("versionNo", "2026.11", "legalBasis", "Biên bản 02/2026", "changes", List.of(change)), admin)
                .andExpect(status().isCreated()), "$.id");
        h.send(post("/admin/param-versions/1/clone"), Map.of("versionNo", "2026.12", "legalBasis", "BB"), admin)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Đang có một phiên bản nháp, hãy hoàn tất hoặc xóa trước."));

        String detail = body(h.send(get("/admin/param-versions/" + draftId), null, admin).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.testResult").isEmpty())
                .andExpect(jsonPath("$.params[?(@.key == 'DTI_MAX')].value").value("75"))
                .andExpect(jsonPath("$.params[?(@.key == 'DTI_MAX')].label").value("Tỷ lệ DTI tối đa"))
                .andExpect(jsonPath("$.weights[?(@.criterion == 'DTI' && @.productCode == 'TD')].label").value("Tỷ lệ DTI sau vay")));
        int sourceParams = jdbc.queryForObject("select count(*) from policy_param where version_id = 1", Integer.class);
        assertThat(com.jayway.jsonpath.JsonPath.<List<?>>read(detail, "$.params")).hasSize(sourceParams);

        // Kích hoạt trước khi chạy thử
        h.send(post("/admin/param-versions/" + draftId + "/activate"), null, admin).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Cần chạy thử trên bộ hồ sơ mẫu trước khi kích hoạt."));

        // Sửa bản nháp: tổng trọng số, DTI, khóa lạ
        JsonNode d = json.readTree(detail);
        h.send(put("/admin/param-versions/" + draftId), putBody(d, Map.of("TD|DTI", 40.0), Map.of()), admin)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Tổng trọng số của gói TD phải bằng 100% (hiện 105%)."));
        h.send(put("/admin/param-versions/" + draftId), putBody(d, Map.of(), Map.of("|DTI_MAX", "90")), admin)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("DTI tối đa phải nằm trong khoảng 65%–80%."));
        h.send(put("/admin/param-versions/" + draftId), putBody(d, Map.of(), Map.of("|LIVING_COST_BASE", "abc")), admin)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Dữ liệu không hợp lệ."));
        Map<String, Object> extra = putBody(d, Map.of(), Map.of());
        @SuppressWarnings("unchecked")
        List<Object> ps = new ArrayList<>((List<Object>) extra.get("params"));
        ps.add(Map.of("productCode", "TD", "key", "KHONG_CO", "value", "1"));
        extra.put("params", ps);
        h.send(put("/admin/param-versions/" + draftId), extra, admin).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields").exists());

        Map<String, Object> ok = putBody(d, Map.of("TD|DTI", 30.5, "TD|AMOUNT_RATIO", 29.5), Map.of("|DTI_MAX", "72"));
        ok.put("legalBasis", "Biên bản 02/2026 (sửa)");
        h.send(put("/admin/param-versions/" + draftId), ok, admin).andExpect(status().isOk()).andExpect(jsonPath("$.ok").value(true));
        h.send(get("/admin/param-versions/" + draftId), null, admin)
                .andExpect(jsonPath("$.legalBasis").value("Biên bản 02/2026 (sửa)"))
                .andExpect(jsonPath("$.params[?(@.key == 'DTI_MAX')].value").value("72"))
                .andExpect(jsonPath("$.weights[?(@.criterion == 'DTI' && @.productCode == 'TD')].weight").value(30.5));

        // Chạy thử: 6 hồ sơ × 2 gói, khớp bộ máy Java với hai bộ tham số
        String test = body(h.send(post("/admin/param-versions/" + draftId + "/test"), null, admin).andExpect(status().isOk()));
        JsonNode t = json.readTree(test);
        assertThat(t.get("rows")).hasSize(12);
        ParamSet psA = EngineParams.paramSet(policy.current());
        WeightSet wsA = EngineParams.weightSet(policy.current());
        ParamSet psD = EngineParams.paramSet(policy.load(draftId));
        WeightSet wsD = EngineParams.weightSet(policy.load(draftId));
        LocalDate today = LocalDate.now(clock);
        int changed = 0;
        for (int i = 0; i < 12; i++) {
            JsonNode r = t.get("rows").get(i);
            SampleProfiles.Profile pr = SampleProfiles.ALL.get(i / 2);
            ProductCode code = ProductCode.values()[i % 2];
            assertThat(r.get("profile").asText()).isEqualTo(pr.name());
            assertThat(r.get("productCode").asText()).isEqualTo(code.name());
            Engine.Exclusion ea = Engine.exclusion(pr.input(), psA, code, today);
            Engine.Exclusion ed = Engine.exclusion(pr.input(), psD, code, today);
            assertThat(r.get("activeEligible").asBoolean()).isEqualTo(ea.eligible());
            if (ed.eligible()) {
                assertThat(r.get("draftMaxSafe").asLong())
                        .isEqualTo(Engine.maxSafeAmount(pr.input(), psD, code, ed.term(), psD.product(code).refRate()));
                assertThat(r.get("draftScore").asDouble())
                        .isEqualTo(Engine.scoreOf(pr.input(), psD, wsD, code, pr.input().desiredAmount(), ed.term(), List.of(), today));
            } else {
                assertThat(r.get("draftMaxSafe").isNull()).isTrue();
            }
            if (r.get("activeEligible").asBoolean() != r.get("draftEligible").asBoolean()
                    || !Objects.equals(r.get("activeMaxSafe").asText(), r.get("draftMaxSafe").asText())
                    || !Objects.equals(r.get("activeScore").asText(), r.get("draftScore").asText())) {
                changed++;
            }
        }
        assertThat(t.get("changed").asInt()).isEqualTo(changed).isPositive();
        assertThat(wsA).isNotEqualTo(wsD);
        h.send(get("/admin/param-versions/" + draftId), null, admin).andExpect(jsonPath("$.testResult.changed").value(changed))
                .andExpect(jsonPath("$.testResult.rows.length()").value(12));

        // Sửa lại thì kết quả chạy thử bị xóa, phải chạy lại mới kích hoạt được
        h.send(put("/admin/param-versions/" + draftId), ok, admin).andExpect(status().isOk());
        h.send(get("/admin/param-versions/" + draftId), null, admin).andExpect(jsonPath("$.testResult").isEmpty());
        h.send(post("/admin/param-versions/" + draftId + "/activate"), null, admin).andExpect(status().isConflict());
        h.send(post("/admin/param-versions/" + draftId + "/test"), null, admin).andExpect(status().isOk());

        assertThat(policy.current().versionNo()).isEqualTo("2026.10");
        h.send(post("/admin/param-versions/" + draftId + "/activate"), null, admin).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE")).andExpect(jsonPath("$.activatedAt").isNotEmpty());
        assertThat(policy.current().versionNo()).isEqualTo("2026.11"); // evict sau commit
        assertThat(EngineParams.paramSet(policy.current()).dtiMax()).isEqualTo(72);
        h.send(get("/admin/param-versions"), null, admin)
                .andExpect(jsonPath("$[0].versionNo").value("2026.11")).andExpect(jsonPath("$[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$[1].versionNo").value("2026.10")).andExpect(jsonPath("$[1].status").value("EXPIRED"));

        // Lượt sàng lọc cũ vẫn tính theo phiên bản 2026.10; lượt mới theo 2026.11
        String again = body(h.send(get("/screenings/" + screeningId), null, null).andExpect(status().isOk()));
        assertThat(json.readTree(again)).isEqualTo(json.readTree(screening));
        String newScreening = read(h.send(post("/screenings"), AdminTestHelper.screening(), null), "$.id");
        int newVersion = jdbc.queryForObject("select version_id from screening where public_id = ?::uuid", Integer.class, newScreening);
        assertThat(newVersion).isEqualTo(draftId);

        // Bản đã kích hoạt / hết hiệu lực không sửa, xóa, chạy thử, kích hoạt lại được
        for (int id : new int[] {draftId, 1}) {
            h.send(put("/admin/param-versions/" + id), ok, admin).andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value("Chỉ sửa được phiên bản nháp."));
            h.send(delete("/admin/param-versions/" + id), null, admin).andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value("Chỉ xóa được phiên bản nháp."));
            h.send(post("/admin/param-versions/" + id + "/test"), null, admin).andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value("Chỉ chạy thử phiên bản nháp."));
            h.send(post("/admin/param-versions/" + id + "/activate"), null, admin).andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value("Chỉ kích hoạt phiên bản nháp."));
        }

        // Xóa bản nháp
        int second = read(h.send(post("/admin/param-versions/" + draftId + "/clone"), Map.of("versionNo", "2026.12", "legalBasis", "BB"), admin)
                .andExpect(status().isCreated()), "$.id");
        h.send(delete("/admin/param-versions/" + second), null, admin).andExpect(status().isOk());
        h.send(get("/admin/param-versions/" + second), null, admin).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("select count(*) from policy_param where version_id = ?", Integer.class, second)).isZero();

        List<String> actions = jdbc.queryForList("select action from activity_log where actor_type = 'STAFF' and actor_id = 102 order by id",
                String.class);
        assertThat(actions).contains("Tạo bản nháp tham số", "Cập nhật bản nháp tham số", "Chạy thử phiên bản tham số",
                "Kích hoạt phiên bản tham số", "Xóa bản nháp tham số");
    }

    /**
     * Body PUT từ chi tiết phiên bản: {@code weights} đổi theo khóa "TD|DTI", {@code params} đổi theo khóa "|DTI_MAX" (mã gói rỗng = dùng chung).
     */
    private Map<String, Object> putBody(JsonNode detail, Map<String, Double> weights, Map<String, String> params) {
        List<Map<String, Object>> ps = new ArrayList<>();
        for (JsonNode p : detail.get("params")) {
            String pc = p.get("productCode").isNull() ? null : p.get("productCode").asText();
            Map<String, Object> m = new HashMap<>();
            m.put("id", p.get("id").asLong());
            m.put("productCode", pc);
            m.put("key", p.get("key").asText());
            m.put("label", p.get("label").asText());
            m.put("value", params.getOrDefault((pc == null ? "" : pc) + "|" + p.get("key").asText(), p.get("value").asText()));
            m.put("unit", p.get("unit").asText());
            ps.add(m);
        }
        List<Map<String, Object>> ws = new ArrayList<>();
        for (JsonNode w : detail.get("weights")) {
            String k = w.get("productCode").asText() + "|" + w.get("criterion").asText();
            ws.add(Map.of("id", w.get("id").asLong(), "productCode", w.get("productCode").asText(), "criterion", w.get("criterion").asText(),
                    "label", w.get("label").asText(), "weight", weights.getOrDefault(k, w.get("weight").asDouble())));
        }
        Map<String, Object> b = new HashMap<>();
        b.put("params", ps);
        b.put("weights", ws);
        return b;
    }
}
