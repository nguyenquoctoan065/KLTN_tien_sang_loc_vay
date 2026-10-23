package vn.kltn.tiensanglocvay.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
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
import vn.kltn.tiensanglocvay.admin.AdminTestHelper;

/** B8: db/dev/V1000__dev_history.sql chạy được và cho số liệu báo cáo (container riêng; các test khác không bật db/dev). */
@SpringBootTest(properties = "spring.flyway.locations=classpath:db/migration,classpath:db/dev")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class DevHistoryMigrationTest {

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
    void historyCoversStatusesBranchesAndProducts() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM loan_application", Integer.class)).isEqualTo(90);
        assertThat(jdbc.queryForList("SELECT DISTINCT status FROM loan_application", String.class)).containsExactlyInAnyOrder(
                "DRAFTING", "WAITING_VERIFY", "NEED_MORE", "VERIFIED", "APPROVED", "DISBURSED", "REJECTED", "CANCELLED", "EXPIRED");
        assertThat(jdbc.queryForObject("SELECT count(DISTINCT branch_id) FROM loan_application", Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("""
                SELECT count(DISTINCT p.product_id) FROM loan_application a JOIN loan_plan p ON p.id = a.plan_id
                """, Integer.class)).isEqualTo(2);
        // hồ sơ trải ít nhất 6 tháng; mọi hồ sơ có lịch sử bắt đầu bằng DRAFTING và kết thúc ở trạng thái hiện tại
        assertThat(jdbc.queryForObject("""
                SELECT count(DISTINCT to_char(created_at AT TIME ZONE 'Asia/Ho_Chi_Minh', 'YYYY-MM')) FROM loan_application
                """, Integer.class)).isGreaterThanOrEqualTo(6);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM loan_application a WHERE a.status <> (SELECT h.to_status FROM application_status_history h
                    WHERE h.application_id = a.id ORDER BY h.created_at DESC, h.id DESC LIMIT 1)
                """, Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM approval_result WHERE result_type = 'REJECTED' AND reason IS NULL",
                Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM screening", Integer.class)).isGreaterThan(90 * 2 + 120 - 1);
    }

    @Test
    void reportsHaveNumbers() throws Exception {
        String admin = h.admin();
        JsonNode s = json.readTree(AdminTestHelper.body(h.send(get("/admin/stats"), null, admin).andExpect(status().isOk())));
        assertThat(s.at("/totals/applications").asInt()).isGreaterThan(80);
        assertThat(s.at("/totals/screenings").asInt()).isGreaterThan(200);
        assertThat(s.at("/totals/disbursed").asInt()).isPositive();
        assertThat(s.at("/disbursedAmount").asLong()).isPositive();
        assertThat(s.at("/avgProcessingDays").asDouble()).isPositive();
        assertThat(s.get("byBranch")).hasSize(3);

        h.send(get("/admin/model-accuracy"), null, admin).andExpect(status().isOk())
                .andExpect(jsonPath("$.sample").value(org.hamcrest.Matchers.greaterThan(30)));

        YearMonth last = YearMonth.from(LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"))).minusMonths(1);
        JsonNode r = json.readTree(AdminTestHelper.body(h.send(get("/admin/reports/mb14?month=" + last), null, admin)
                .andExpect(status().isOk())));
        assertThat(r.at("/stats/totals/applications").asInt()).isPositive();
        assertThat(r.get("proposals")).isNotEmpty();
    }

    @Test
    void historicalApplicationOpensForBranchStaff() throws Exception {
        String staff = h.staffToken("nhanvien@tiensangloc.local");
        List<String> ids = jdbc.queryForList("""
                SELECT a.public_id::text FROM loan_application a JOIN branch b ON b.id = a.branch_id
                WHERE b.code = 'HN01' ORDER BY a.status, a.id
                """, String.class);
        assertThat(ids).isNotEmpty();
        for (String id : ids) {
            h.send(get("/applications/" + id), null, staff).andExpect(status().isOk());
        }
        h.send(get("/staff/applications"), null, staff).andExpect(status().isOk());
    }
}
