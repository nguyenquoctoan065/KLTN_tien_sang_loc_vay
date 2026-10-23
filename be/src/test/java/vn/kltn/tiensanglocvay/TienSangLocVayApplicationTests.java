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

        assertThat(versions).containsExactly("1", "2");
        assertThat(tables).isEqualTo(24);
        assertThat(products).isPositive();
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
        // Chưa có controller ở B0 nên 404 (không phải 401): bộ lọc bảo mật đã cho đi qua.
        mvc.perform(get("/products")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Không tìm thấy."));
        mvc.perform(get("/products/TD/checklist")).andExpect(status().isNotFound());
        mvc.perform(get("/cross-sell-products")).andExpect(status().isNotFound());
        mvc.perform(get("/branches")).andExpect(status().isNotFound());
        mvc.perform(get("/screenings/1")).andExpect(status().isNotFound());
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isNotFound());
        mvc.perform(post("/screenings").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isNotFound());
        mvc.perform(post("/simulations").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isNotFound());
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
