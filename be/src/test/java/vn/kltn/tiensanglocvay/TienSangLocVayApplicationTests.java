package vn.kltn.tiensanglocvay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class TienSangLocVayApplicationTests {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    TestRestTemplate rest;

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void healthTraLoiUpTaiContextPathApi() {
        // TestRestTemplate đã tự gắn context path vào địa chỉ gốc nên đường dẫn thực tế là /api/actuator/health.
        assertThat(rest.getRootUri()).endsWith("/api");
        var res = rest.getForEntity("/actuator/health", String.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).contains("\"status\":\"UP\"");
    }

    @Test
    void flywayDaChayV1VaV2() {
        List<String> versions = jdbc.queryForList(
                "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank", String.class);
        Integer tables = jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name <> 'flyway_schema_history'",
                Integer.class);
        Integer products = jdbc.queryForObject("SELECT count(*) FROM loan_product", Integer.class);

        List<String> publicIdTables = jdbc.queryForList("SELECT table_name FROM information_schema.columns WHERE table_schema = 'public' "
                + "AND column_name = 'public_id' AND data_type = 'uuid' AND is_nullable = 'NO' ORDER BY table_name", String.class);

        assertThat(versions).containsExactly("1", "2", "3", "4", "5", "6", "7", "8");
        assertThat(tables).isEqualTo(28); // 24 bảng V1 + auth_session, otp_code (V3) + application_status_history, notification (V6)
        assertThat(products).isPositive();
        assertThat(publicIdTables).containsExactly("loan_application", "loan_plan", "screening"); // V5
    }

    @Test
    void duongDanCanDangNhapTraVe401TiengViet() throws Exception {
        mvc.perform(get("/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Chưa đăng nhập."))
                .andExpect(jsonPath("$.fields").doesNotExist());
    }

    @Test
    void tokenSaiVanLa401() throws Exception {
        mvc.perform(get("/me/screenings").header("Authorization", "Bearer khong-ton-tai"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Chưa đăng nhập."));
    }

    @Test
    void endpointCongKhaiKhongBiChan() throws Exception {
        // Danh mục (B2) đã có controller: không token vẫn trả 200.
        mvc.perform(get("/products")).andExpect(status().isOk());
        mvc.perform(get("/products/TD/checklist")).andExpect(status().isOk());
        mvc.perform(get("/cross-sell-products")).andExpect(status().isOk());
        mvc.perform(get("/branches")).andExpect(status().isOk());
        // Sàng lọc (B3): bộ lọc cho qua, controller trả lỗi nghiệp vụ (không phải 401).
        mvc.perform(get("/screenings/999999")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Không tìm thấy kết quả sàng lọc."));
        // /auth/**, /screenings, /simulations: bộ lọc cho qua, controller trả 400 vì thiếu dữ liệu.
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());
        mvc.perform(post("/screenings").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());
        mvc.perform(post("/simulations").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());
    }

    @Test
    void endpointCanTokenBiChanKhiChuaDangNhap() throws Exception {
        mvc.perform(get("/me/applications")).andExpect(status().isUnauthorized());
        mvc.perform(post("/screenings/1/save")).andExpect(status().isUnauthorized());
        mvc.perform(post("/plans").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
        mvc.perform(get("/admin/param-versions")).andExpect(status().isUnauthorized());
        mvc.perform(get("/staff/applications")).andExpect(status().isUnauthorized());
    }
}
