package vn.kltn.tiensanglocvay.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
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
import vn.kltn.tiensanglocvay.policy.ActivePolicy;
import vn.kltn.tiensanglocvay.policy.PolicySnapshot;

/**
 * B2: danh mục công khai và ActivePolicy. Giá trị mong đợi chép từ dữ liệu mẫu và handlers/catalog.ts trong fe/src/mocks;
 * các endpoint này không cần đăng nhập nên không gửi token.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class CatalogApiTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ActivePolicy activePolicy;

    // ---------------------------------------------------------------- /products

    @Test
    void products_returnsActiveProductsWithAllFields() throws Exception {
        mvc.perform(get("/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].code").value("TD"))
                .andExpect(jsonPath("$[0].name").value("Vay tiêu dùng không tài sản bảo đảm"))
                .andExpect(jsonPath("$[0].productType").value("CONSUMER"))
                .andExpect(jsonPath("$[0].repaymentMethod").value("Gốc trả đều hằng tháng, lãi trên dư nợ giảm dần"))
                .andExpect(jsonPath("$[0].description")
                        .value("Phục vụ nhu cầu tiêu dùng hợp pháp; giải ngân vào tài khoản bên thụ hưởng theo hóa đơn, hợp đồng."))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$[1].id").value(2))
                .andExpect(jsonPath("$[1].code").value("TC"))
                .andExpect(jsonPath("$[1].name").value("Vay thấu chi không tài sản bảo đảm"))
                .andExpect(jsonPath("$[1].productType").value("OVERDRAFT"))
                .andExpect(jsonPath("$[1].repaymentMethod").value("Lãi trả hằng tháng, gốc trả cuối kỳ hoặc trả dần"));
    }

    @Test
    void products_hidesInactive() throws Exception {
        jdbc.update("insert into loan_product (code, name, product_type, repayment_method, status) values ('ZZ', 'Gói tạm ngưng', 'CONSUMER', 'x', 'INACTIVE')");
        try {
            mvc.perform(get("/products"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[?(@.code=='ZZ')]").isEmpty());
        } finally {
            jdbc.update("delete from loan_product where code = 'ZZ'");
        }
    }

    // ---------------------------------------------------------------- /products/{code}/checklist

    @Test
    void checklist_consumer_listsSixDocumentsAllRequired() throws Exception {
        mvc.perform(get("/products/TD/checklist"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(6))
                .andExpect(jsonPath("$[0].name").value("Giấy đề nghị vay vốn (mẫu ngân hàng) – hệ thống tự lập"))
                .andExpect(jsonPath("$[0].group").value("Khác"))
                .andExpect(jsonPath("$[0].required").value(true))
                .andExpect(jsonPath("$[1].name").value("Căn cước công dân"))
                .andExpect(jsonPath("$[1].group").value("Pháp lý"))
                .andExpect(jsonPath("$[2].name").value("Xác nhận cư trú"))
                .andExpect(jsonPath("$[3].name").value("Hợp đồng lao động hoặc xác nhận nhân sự"))
                .andExpect(jsonPath("$[3].group").value("Thu nhập"))
                .andExpect(jsonPath("$[4].name").value("Sao kê tài khoản nhận lương 03 tháng"))
                .andExpect(jsonPath("$[5].name").value("Hóa đơn, hợp đồng mua bán hoặc hợp đồng 3 bên"))
                .andExpect(jsonPath("$[5].group").value("Mục đích"))
                .andExpect(jsonPath("$[?(@.required==false)]").isEmpty());
    }

    @Test
    void checklist_overdraft_putsOptionalLastLikeMock() throws Exception {
        mvc.perform(get("/products/TC/checklist"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].name").value("Giấy đề nghị vay vốn (mẫu ngân hàng) – hệ thống tự lập"))
                .andExpect(jsonPath("$[1].name").value("Căn cước công dân"))
                .andExpect(jsonPath("$[2].name").value("Sao kê tài khoản nhận lương 03 tháng"))
                .andExpect(jsonPath("$[2].required").value(true))
                .andExpect(jsonPath("$[3].name").value("Hợp đồng lao động hoặc xác nhận nhân sự"))
                .andExpect(jsonPath("$[3].required").value(false));
    }

    @Test
    void checklist_unknownCode_isEmptyList() throws Exception {
        mvc.perform(get("/products/XX/checklist"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void migrationV4_statementAcceptsImagesLikeMock() {
        String formats = jdbc.queryForObject(
                "select allowed_formats from document_type where name = 'Sao kê tài khoản nhận lương 03 tháng'", String.class);
        assertThat(formats).isEqualTo("PDF,JPG,PNG");
    }

    // ---------------------------------------------------------------- /cross-sell-products

    @Test
    void crossSells_returnsFourActiveWithNumbers() throws Exception {
        mvc.perform(get("/cross-sell-products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("Bảo hiểm nhân thọ"))
                .andExpect(jsonPath("$[0].productType").value("INSURANCE"))
                .andExpect(jsonPath("$[0].estCost").value(8_000_000))
                .andExpect(jsonPath("$[0].scoreBonus").value(5.0))
                .andExpect(jsonPath("$[0].rateDiscount").value(1.0))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$[1].name").value("Thẻ tín dụng"))
                .andExpect(jsonPath("$[1].estCost").value(0))
                .andExpect(jsonPath("$[1].rateDiscount").value(0.5))
                .andExpect(jsonPath("$[2].name").value("Chứng chỉ tiền gửi"))
                .andExpect(jsonPath("$[2].scoreBonus").value(4.0))
                .andExpect(jsonPath("$[2].rateDiscount").value(0.7))
                .andExpect(jsonPath("$[3].name").value("Tài khoản số đẹp"))
                .andExpect(jsonPath("$[3].estCost").value(1_000_000))
                .andExpect(jsonPath("$[3].rateDiscount").value(0.3));
    }

    @Test
    void crossSells_hidesInactive() throws Exception {
        jdbc.update("insert into cross_sell_product (name, product_type, est_cost, score_bonus, rate_discount, status) values ('Tạm ngưng', 'CARD', 0, 0, 0, 'INACTIVE')");
        try {
            mvc.perform(get("/cross-sell-products"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(4))
                    .andExpect(jsonPath("$[?(@.name=='Tạm ngưng')]").isEmpty());
        } finally {
            jdbc.update("delete from cross_sell_product where name = 'Tạm ngưng'");
        }
    }

    // ---------------------------------------------------------------- /branches

    @Test
    void branches_returnsThreeActiveWithoutOpenApplications() throws Exception {
        mvc.perform(get("/branches"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].code").value("HN01"))
                .andExpect(jsonPath("$[0].name").value("Chi nhánh Hà Nội"))
                .andExpect(jsonPath("$[0].address").value("Hà Nội"))
                .andExpect(jsonPath("$[0].phone").value("02400000001"))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$[0].openApplications").doesNotExist())
                .andExpect(jsonPath("$[1].code").value("HCM01"))
                .andExpect(jsonPath("$[1].name").value("Chi nhánh Hồ Chí Minh"))
                .andExpect(jsonPath("$[2].code").value("DN01"))
                .andExpect(jsonPath("$[2].name").value("Chi nhánh Đà Nẵng"));
    }

    @Test
    void branches_hidesInactive() throws Exception {
        jdbc.update("insert into branch (code, name, address, status) values ('ZZ01', 'Chi nhánh đóng', 'x', 'INACTIVE')");
        try {
            mvc.perform(get("/branches"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(3))
                    .andExpect(jsonPath("$[?(@.code=='ZZ01')]").isEmpty());
        } finally {
            jdbc.update("delete from branch where code = 'ZZ01'");
        }
    }

    // ---------------------------------------------------------------- ActivePolicy

    @Test
    void activePolicy_loadsSeededVersion() {
        PolicySnapshot p = activePolicy.current();

        assertThat(p.versionNo()).isEqualTo("2026.10");
        assertThat(p.common("DTI_MAX")).isEqualByComparingTo("70");
        assertThat(p.common("LIVING_COST_BASE")).isEqualByComparingTo("4000000");
        assertThat(p.common("RETIRE_AGE_FEMALE")).isEqualByComparingTo("60");
        assertThat(p.product("TD", "MAX_AMOUNT")).isEqualByComparingTo("500000000");
        assertThat(p.product("TD", "REF_RATE")).isEqualByComparingTo("12.5");
        assertThat(p.product("TC", "MIN_AGE")).isEqualByComparingTo("19");
        assertThat(p.product("TC", "REF_RATE")).isEqualByComparingTo("18");
        assertThat(p.weight("TD", "DTI")).isEqualByComparingTo("35");
        assertThat(p.weight("TC", "SALARY_BANK")).isEqualByComparingTo("25");
        assertThat(p.weight("TC", "KHONG_CO")).isEqualTo(BigDecimal.ZERO);
    }

    @Test
    void activePolicy_weightsPerProductSumTo100() {
        PolicySnapshot p = activePolicy.current();
        for (String code : new String[] {"TD", "TC"}) {
            BigDecimal sum = p.weights().get(code).values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            assertThat(sum).as("tổng trọng số " + code).isEqualByComparingTo("100");
        }
    }

    @Test
    void activePolicy_missingParamIsReportedClearly() {
        PolicySnapshot p = activePolicy.current();

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> p.common("KHONG_CO"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("KHONG_CO");
    }

    @Test
    void activePolicy_evictReloadsNewActiveVersion() {
        PolicySnapshot before = activePolicy.current();
        assertThat(activePolicy.current()).isSameAs(before);

        jdbc.update("update policy_param set param_value = '65' where version_id = 1 and param_key = 'DTI_MAX'");
        try {
            assertThat(activePolicy.current().common("DTI_MAX")).as("còn dùng bản đã nạp").isEqualByComparingTo("70");
            activePolicy.evict();
            assertThat(activePolicy.current().common("DTI_MAX")).isEqualByComparingTo("65");
        } finally {
            jdbc.update("update policy_param set param_value = '70' where version_id = 1 and param_key = 'DTI_MAX'");
            activePolicy.evict();
        }
    }
}
