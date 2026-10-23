package vn.kltn.tiensanglocvay.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static vn.kltn.tiensanglocvay.admin.AdminTestHelper.read;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
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

/** B7: 7.3 danh mục (/admin/catalog, products, document-types, product-documents, cross-sell-products) và /admin/logs. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class AdminCatalogLogApiTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    JdbcTemplate jdbc;

    AdminTestHelper h;

    @BeforeEach
    void setUp() {
        h = new AdminTestHelper(mvc, json);
    }

    @Test
    void roles_onlyAdmin() throws Exception {
        String staff = h.staffToken("nhanvien@tiensangloc.local");
        for (String path : new String[] {"/admin/catalog", "/admin/logs"}) {
            h.send(get(path), null, staff).andExpect(status().isForbidden()).andExpect(jsonPath("$.message").value(AdminTestHelper.FORBIDDEN));
        }
        h.send(put("/admin/product-documents"), List.of(), staff).andExpect(status().isForbidden());
        h.send(post("/admin/cross-sell-products"), Map.of(), h.newCustomer()).andExpect(status().isForbidden());
    }

    @Test
    void catalog_productsDocTypesRulesCrossSells() throws Exception {
        String admin = h.admin();
        h.send(get("/admin/catalog"), null, admin).andExpect(status().isOk())
                .andExpect(jsonPath("$.products[*].code").value(org.hamcrest.Matchers.contains("TD", "TC")))
                .andExpect(jsonPath("$.documentTypes[0].id").value(1))
                .andExpect(jsonPath("$.documentTypes[0].name").value(not(org.hamcrest.Matchers.containsString("hệ thống tự lập"))))
                .andExpect(jsonPath("$.productDocuments[?(@.productCode == 'TD' && @.documentTypeId == 1)].required").value(true))
                .andExpect(jsonPath("$.crossSells.length()").value(org.hamcrest.Matchers.greaterThan(0)));

        // Gói vay: chỉ đổi trường được gửi; INACTIVE ẩn khỏi danh mục công khai
        String description = read(h.send(get("/admin/catalog"), null, admin), "$.products[1].description");
        h.send(put("/admin/products/999"), Map.of("name", "X"), admin).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Không tìm thấy gói vay."));
        h.send(put("/admin/products/2"), Map.of("status", "TAM_DUNG"), admin).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.status").exists());
        h.send(put("/admin/products/2"), Map.of("name", "Thấu chi mới", "status", "INACTIVE", "code", "XX"), admin).andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Thấu chi mới")).andExpect(jsonPath("$.code").value("TC"))
                .andExpect(jsonPath("$.description").value(description));
        h.send(get("/products"), null, null).andExpect(jsonPath("$[*].code").value(not(hasItem("TC"))));
        h.send(put("/admin/products/2"), Map.of("status", "ACTIVE"), admin).andExpect(status().isOk());
        h.send(get("/products"), null, null).andExpect(jsonPath("$[*].code").value(hasItem("TC")));

        // Loại giấy tờ
        int typeId = read(h.send(post("/admin/document-types"), docType("Giấy xác nhận thu nhập", "jpg, png", 30, "ACTIVE"), admin)
                .andExpect(status().isCreated()).andExpect(jsonPath("$.allowedFormats").value("JPG,PNG")), "$.id");
        h.send(post("/admin/document-types"), docType("Giấy X", "PDF", 0, "ACTIVE"), admin).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.validityDays").exists());
        h.send(put("/admin/document-types/999"), docType("X", "PDF", null, "ACTIVE"), admin).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Không tìm thấy loại giấy tờ."));
        h.send(put("/admin/document-types/1"), docType("Giấy đề nghị vay vốn", "PDF", null, "INACTIVE"), admin).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Không ngừng áp dụng giấy đề nghị vay vốn."));
        h.send(put("/admin/document-types/" + typeId), docType("Giấy xác nhận thu nhập (mẫu mới)", "PDF", null, "ACTIVE"), admin)
                .andExpect(status().isOk()).andExpect(jsonPath("$.validityDays").isEmpty());

        // Giấy tờ theo gói: thiếu giấy đề nghị, loại không tồn tại, trùng; thay toàn bộ
        List<Map<String, Object>> rules = read(h.send(get("/admin/catalog"), null, admin), "$.productDocuments");
        List<Map<String, Object>> noSystem = rules.stream().filter(r -> !("TC".equals(r.get("productCode")) && ((Number) r.get("documentTypeId")).intValue() == 1)).toList();
        h.send(put("/admin/product-documents"), noSystem, admin).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Mỗi gói phải có giấy đề nghị vay vốn."));
        List<Map<String, Object>> bad = new ArrayList<>(rules);
        bad.add(Map.of("productCode", "TD", "documentTypeId", 999, "required", true));
        h.send(put("/admin/product-documents"), bad, admin).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields").exists());
        List<Map<String, Object>> dup = new ArrayList<>(rules);
        dup.add(rules.get(0));
        h.send(put("/admin/product-documents"), dup, admin).andExpect(status().isBadRequest());
        List<Map<String, Object>> added = new ArrayList<>(rules);
        added.add(Map.of("productCode", "TD", "documentTypeId", typeId, "required", false));
        h.send(put("/admin/product-documents"), added, admin).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(rules.size() + 1))
                .andExpect(jsonPath("$[?(@.documentTypeId == " + typeId + ")].productCode").value("TD"));
        h.send(get("/products/TD/checklist"), null, null).andExpect(jsonPath("$[*].name").value(hasItem("Giấy xác nhận thu nhập (mẫu mới)")));

        // Bán chéo
        int cs = read(h.send(post("/admin/cross-sell-products"), crossSell("Bảo hiểm sức khỏe", 1_500_000, 3, 0.5), admin)
                .andExpect(status().isCreated()).andExpect(jsonPath("$.estCost").value(1_500_000)), "$.id");
        h.send(post("/admin/cross-sell-products"), crossSell("X", 1_000, 1000, 0.5), admin).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.scoreBonus").exists());
        h.send(put("/admin/cross-sell-products/999"), crossSell("X", 1_000, 1, 0.5), admin).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Không tìm thấy sản phẩm."));
        Map<String, Object> off = crossSell("Bảo hiểm sức khỏe", 1_500_000, 3, 0.5);
        off.put("status", "INACTIVE");
        h.send(put("/admin/cross-sell-products/" + cs), off, admin).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("INACTIVE"));
        h.send(get("/cross-sell-products"), null, null).andExpect(jsonPath("$[*].id").value(not(hasItem(cs))));

        assertThat(jdbc.queryForList("select action from activity_log where actor_id = 102 and actor_type = 'STAFF'", String.class))
                .contains("Cập nhật gói vay", "Thêm loại giấy tờ", "Cập nhật loại giấy tờ", "Cập nhật giấy tờ theo gói vay",
                        "Thêm sản phẩm bán chéo", "Cập nhật sản phẩm bán chéo");
    }

    @Test
    void logs_filterKeywordLimit() throws Exception {
        String admin = h.admin();
        String customer = h.newCustomer();
        h.send(get("/auth/me"), null, customer).andExpect(status().isOk());
        String phone = jdbc.queryForObject("select phone from customer order by id desc limit 1", String.class);
        h.send(put("/admin/products/1"), Map.of("description", "Mô tả mới"), admin).andExpect(status().isOk());
        jdbc.update("insert into activity_log (actor_type, action, detail) values ('SYSTEM', 'Thử ký tự', 'Giảm 5%'), ('SYSTEM', 'Thử ký tự', 'Giảm 50')");

        h.send(get("/admin/logs"), null, admin).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.action == 'Đăng ký tài khoản')].actorName").value(hasItem("Khách hàng " + phone.substring(6))))
                .andExpect(jsonPath("$[?(@.action == 'Cập nhật gói vay')].actorName").value(hasItem("Trần Minh Đức")))
                .andExpect(jsonPath("$[?(@.action == 'Cập nhật gói vay')].actorType").value(hasItem("STAFF")));
        h.send(get("/admin/logs").param("actorType", "CUSTOMER"), null, admin)
                .andExpect(jsonPath("$[*].actorType").value(everyItem(is("CUSTOMER"))));
        h.send(get("/admin/logs").param("actorType", "ADMIN"), null, admin).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.actorType").exists());
        // Từ khóa: không phân biệt hoa thường, tìm cả tên người thực hiện; % và _ là ký tự thường
        h.send(get("/admin/logs").param("keyword", "  CẬP NHẬT GÓI  "), null, admin)
                .andExpect(jsonPath("$[*].action").value(everyItem(is("Cập nhật gói vay"))))
                .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThan(0)));
        h.send(get("/admin/logs").param("keyword", "trần minh"), null, admin)
                .andExpect(jsonPath("$[*].actorName").value(everyItem(is("Trần Minh Đức"))));
        h.send(get("/admin/logs").param("keyword", "5%"), null, admin).andExpect(jsonPath("$[*].detail").value(org.hamcrest.Matchers.contains("Giảm 5%")));
        h.send(get("/admin/logs").param("keyword", "giảm 5_"), null, admin).andExpect(jsonPath("$.length()").value(0));

        // Tối đa 500 dòng, mới nhất trước
        jdbc.update("insert into activity_log (actor_type, action, detail, created_at) "
                + "select 'SYSTEM', 'Thử', 'dòng ' || g, now() - make_interval(secs => g) from generate_series(1, 600) g");
        List<String> times = read(h.send(get("/admin/logs"), null, admin).andExpect(jsonPath("$.length()").value(500)), "$[*].createdAt");
        List<String> sorted = new ArrayList<>(times);
        sorted.sort((a, b) -> OffsetDateTime.parse(b).compareTo(OffsetDateTime.parse(a)));
        assertThat(times).isEqualTo(sorted);
        h.send(get("/admin/logs").param("actorType", "SYSTEM"), null, admin)
                .andExpect(jsonPath("$[0].actorName").value("Hệ thống")).andExpect(jsonPath("$[0].applicationCode").isEmpty());
    }

    private static Map<String, Object> docType(String name, String formats, Integer validityDays, String status) {
        Map<String, Object> m = new HashMap<>();
        m.put("name", name);
        m.put("group", "Thu nhập");
        m.put("allowedFormats", formats);
        m.put("validityDays", validityDays);
        m.put("status", status);
        return m;
    }

    private static Map<String, Object> crossSell(String name, long cost, double bonus, double discount) {
        Map<String, Object> m = new HashMap<>();
        m.put("name", name);
        m.put("productType", "Bảo hiểm");
        m.put("estCost", cost);
        m.put("scoreBonus", bonus);
        m.put("rateDiscount", discount);
        m.put("status", "ACTIVE");
        return m;
    }
}
