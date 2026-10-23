package vn.kltn.tiensanglocvay.admin.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static vn.kltn.tiensanglocvay.admin.AdminTestHelper.read;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import vn.kltn.tiensanglocvay.admin.AdminTestHelper;
import vn.kltn.tiensanglocvay.document.StorageService;

/** B7: 1.7 tác vụ định kỳ (docs/quy-tac-tinh-toan.md mục 13), chạy tay qua POST /admin/jobs/{code}/run. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class JobApiTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    JdbcTemplate jdbc;

    @SpyBean
    StorageService storage;

    @Value("${app.storage.local-dir}")
    String localDir;

    AdminTestHelper h;

    @BeforeEach
    void setUp() {
        h = new AdminTestHelper(mvc, json);
    }

    @Test
    void describeCron() {
        assertThat(JobService.describe("0 0 1 * * *")).isEqualTo("Hằng ngày lúc 01:00");
        assertThat(JobService.describe("0 30 9 * * *")).isEqualTo("Hằng ngày lúc 09:30");
        assertThat(JobService.describe("0 0 2 1 * *")).isEqualTo("Ngày 1 hằng tháng lúc 02:00");
        assertThat(JobService.describe("-")).isEqualTo("Không chạy tự động");
        assertThat(JobService.describe("0 */5 * * * *")).isEqualTo("Theo lịch 0 */5 * * * *");
    }

    @Test
    void listRolesUnknown() throws Exception {
        String admin = h.admin();
        h.send(get("/admin/jobs"), null, admin).andExpect(status().isOk())
                .andExpect(jsonPath("$[*].code").value(org.hamcrest.Matchers.contains("EXPIRE_DOCUMENTS", "PURGE_RETENTION", "REMIND_DRAFTS")))
                .andExpect(jsonPath("$[0].name").value("Kiểm tra giấy tờ quá thời hạn hiệu lực"))
                .andExpect(jsonPath("$[0].schedule").value("Không chạy tự động")); // profile test tắt cron
        h.send(post("/admin/jobs/KHONG_CO/run"), null, admin).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Không có tác vụ."));
        String staff = h.staffToken("nhanvien@tiensangloc.local");
        h.send(get("/admin/jobs"), null, staff).andExpect(status().isForbidden());
        h.send(post("/admin/jobs/REMIND_DRAFTS/run"), null, staff).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(AdminTestHelper.FORBIDDEN));
    }

    @Test
    void expireDocuments() throws Exception {
        String admin = h.admin();
        String[] a = h.submittedApp(1);
        String[] fresh = h.submittedApp(1); // còn hạn: giữ nguyên
        jdbc.update("update loan_application set expires_at = now() - interval '1 minute' where public_id = ?::uuid", a[1]);

        h.send(post("/admin/jobs/EXPIRE_DOCUMENTS/run"), null, admin).andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("EXPIRE_DOCUMENTS"))
                .andExpect(jsonPath("$.lastResult").value("1 hồ sơ chuyển “Hết hạn”."))
                .andExpect(jsonPath("$.lastRunAt").isNotEmpty());
        h.send(get("/applications/" + a[1]), null, a[0]).andExpect(jsonPath("$.status").value("EXPIRED"))
                .andExpect(jsonPath("$.history[-1].note").value("Giấy tờ quá thời hạn hiệu lực"))
                .andExpect(jsonPath("$.history[-1].actor").value("Hệ thống"));
        h.send(get("/applications/" + fresh[1]), null, fresh[0]).andExpect(jsonPath("$.status").value("WAITING_VERIFY"));
        String code = read(h.send(get("/applications/" + a[1]), null, a[0]), "$.code");
        h.send(get("/notifications"), null, a[0]).andExpect(jsonPath("$[*].body").value(hasItem(
                "Giấy tờ của hồ sơ " + code + " đã quá thời hạn hiệu lực. Cập nhật giấy tờ để nộp lại.")));
        assertThat(jdbc.queryForObject("select count(*) from activity_log l join loan_application a on a.id = l.application_id "
                + "where a.public_id = ?::uuid and l.action = 'Chuyển hết hạn' and l.actor_type = 'SYSTEM'", Integer.class, a[1])).isEqualTo(1);
        assertThat(jdbc.queryForObject("select detail from activity_log where action = 'Chạy tác vụ định kỳ' and actor_id = 102 order by id desc limit 1",
                String.class)).isEqualTo("Kiểm tra giấy tờ quá thời hạn hiệu lực: 1 hồ sơ chuyển “Hết hạn”.");

        // Chạy lại: không còn hồ sơ nào
        h.send(post("/admin/jobs/EXPIRE_DOCUMENTS/run"), null, admin).andExpect(jsonPath("$.lastResult").value("0 hồ sơ chuyển “Hết hạn”."));
        h.send(get("/admin/jobs"), null, admin).andExpect(jsonPath("$[0].lastResult").value("0 hồ sơ chuyển “Hết hạn”."));
    }

    @Test
    void remindDrafts() throws Exception {
        String admin = h.admin();
        String token = h.newCustomer();
        String old = h.newApp(token, 1);
        String recent = h.newApp(h.newCustomer(), 1);
        jdbc.update("update loan_application set updated_at = now() - interval '4 days' where public_id = ?::uuid", old);
        String result = read(h.send(post("/admin/jobs/REMIND_DRAFTS/run"), null, admin).andExpect(status().isOk()), "$.lastResult");
        assertThat(result).matches("Gửi nhắc \\d+ khách hàng\\.");
        String code = read(h.send(get("/applications/" + old), null, token), "$.code");
        h.send(get("/notifications"), null, token)
                .andExpect(jsonPath("$[*].title").value(hasItem("Nhắc hoàn tất hồ sơ")))
                .andExpect(jsonPath("$[*].body").value(hasItem("Hồ sơ " + code + " còn đang lập. Hoàn tất giấy tờ để nộp hồ sơ.")));
        assertThat(jdbc.queryForObject("select count(*) from notification n join loan_application a on a.customer_id = n.customer_id "
                + "where a.public_id = ?::uuid and n.title = 'Nhắc hoàn tất hồ sơ'", Integer.class, recent)).isZero();
    }

    @Test
    void purgeRetention_deletesFilesAndIdentity_failureKeepsApplication() throws Exception {
        String admin = h.admin();
        String[] ok = h.submittedApp(1);
        String[] failing = h.submittedApp(1);
        String[] recent = h.submittedApp(1);
        long okId = id(ok[1]);
        long failingId = id(failing[1]);
        for (String app : new String[] {ok[1], failing[1]}) {
            jdbc.update("update loan_application set status = 'CANCELLED', updated_at = now() - interval '400 days' where public_id = ?::uuid", app);
        }
        jdbc.update("update loan_application set status = 'CANCELLED' where public_id = ?::uuid", recent[1]); // mới đóng: giữ
        List<Path> okFiles = files(okId);
        assertThat(okFiles).hasSize(5).allMatch(Files::exists);
        List<Path> failingFiles = files(failingId);

        doThrow(new UncheckedIOException(new java.io.IOException("Cloudinary lỗi")))
                .when(storage).delete(argThat(f -> f != null && f.publicId().contains("/app-" + failingId + "/")));
        try {
            h.send(post("/admin/jobs/PURGE_RETENTION/run"), null, admin).andExpect(status().isOk())
                    .andExpect(jsonPath("$.lastResult").value(
                            "Xóa 5 tài liệu của hồ sơ đã đóng quá 12 tháng. 1 hồ sơ chưa xóa được tệp, sẽ thử lại lần sau."));
        } finally {
            reset(storage);
        }

        // Hồ sơ xóa được: tệp mất khỏi kho, giấy tờ về MISSING, không còn trường trích xuất, giấy đề nghị, CCCD, địa chỉ
        assertThat(okFiles).noneMatch(Files::exists);
        assertThat(jdbc.queryForObject("select count(*) from application_document where application_id = ? and (file_name is not null "
                + "or storage_path is not null or status <> 'MISSING')", Integer.class, okId)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from extracted_field f join application_document d on d.id = f.document_id "
                + "where d.application_id = ?", Integer.class, okId)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from loan_request_form where application_id = ?", Integer.class, okId)).isZero();
        Map<String, Object> row = jdbc.queryForMap("select national_id, address, full_name, status from loan_application where id = ?", okId);
        assertThat(row.get("national_id")).isNull();
        assertThat(row.get("address")).isNull();
        assertThat(row.get("full_name")).isEqualTo("Trần Thị Lan");
        assertThat(row.get("status")).isEqualTo("CANCELLED");

        // Hồ sơ lỗi xóa tệp: hoàn tác toàn bộ, lần sau thử lại
        assertThat(failingFiles).allMatch(Files::exists);
        assertThat(jdbc.queryForObject("select national_id from loan_application where id = ?", String.class, failingId)).isEqualTo("001092012345");
        assertThat(files(failingId)).hasSize(5);
        // Hồ sơ mới đóng không bị đụng
        assertThat(files(id(recent[1]))).hasSize(5);

        h.send(post("/admin/jobs/PURGE_RETENTION/run"), null, admin)
                .andExpect(jsonPath("$.lastResult").value("Xóa 5 tài liệu của hồ sơ đã đóng quá 12 tháng."));
        assertThat(failingFiles).noneMatch(Files::exists);
        h.send(post("/admin/jobs/PURGE_RETENTION/run"), null, admin)
                .andExpect(jsonPath("$.lastResult").value("Xóa 0 tài liệu của hồ sơ đã đóng quá 12 tháng."));
    }

    private long id(String publicId) {
        return jdbc.queryForObject("select id from loan_application where public_id = ?::uuid", Long.class, publicId);
    }

    /** Tệp trong kho cục bộ của các giấy tờ đã lưu (trừ giấy đề nghị hệ thống không có tệp). */
    private List<Path> files(long appId) {
        return jdbc.queryForList("select storage_path, file_format from application_document where application_id = ? and storage_path is not null",
                appId).stream().map(r -> Path.of(localDir).toAbsolutePath().normalize()
                .resolve(r.get("storage_path") + "." + r.get("file_format"))).toList();
    }
}
