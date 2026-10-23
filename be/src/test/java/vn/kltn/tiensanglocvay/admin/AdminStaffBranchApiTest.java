package vn.kltn.tiensanglocvay.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static vn.kltn.tiensanglocvay.admin.AdminTestHelper.read;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
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

/** B7: /admin/staff/** (1.5) và /admin/branches/** (1.6). Thông báo lỗi đối chiếu với fe/src/mocks/handlers/admin.ts. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class AdminStaffBranchApiTest {

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
        String staff = h.staffToken("nv.hcm@tiensangloc.local");
        String customer = h.newCustomer();
        for (String token : new String[] {staff, customer}) {
            h.send(get("/admin/staff"), null, token).andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.message").value(AdminTestHelper.FORBIDDEN));
            h.send(post("/admin/staff/101/lock"), null, token).andExpect(status().isForbidden());
            h.send(get("/admin/branches"), null, token).andExpect(status().isForbidden());
            mvc.perform(post("/admin/branches/1/deactivate").header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON).content("{sai")).andExpect(status().isForbidden());
        }
        h.send(get("/admin/branches"), null, null).andExpect(status().isUnauthorized());
    }

    @Test
    void staff_createUpdateLockResetPassword() throws Exception {
        String admin = h.admin();
        h.send(get("/admin/staff"), null, admin).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.email == 'nhanvien@tiensangloc.local')].branchName").value("Chi nhánh Hà Nội"))
                .andExpect(jsonPath("$[?(@.email == 'quantri@tiensangloc.local')].branchId").value(org.hamcrest.Matchers.contains((Object) null)));

        // Tạo: lỗi theo thứ tự của mock rồi kiểm tra chặt hơn
        h.send(post("/admin/staff"), staff("Nguyễn Văn A", "khong-hop-le", "BRANCH_STAFF", 1L), admin)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Email không hợp lệ."));
        h.send(post("/admin/staff"), staff("Nguyễn Văn A", "nhanvien@tiensangloc.local", "BRANCH_STAFF", 1L), admin)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("Email đã được dùng."));
        h.send(post("/admin/staff"), staff("Nguyễn Văn A", "a@tiensangloc.local", "BRANCH_STAFF", null), admin)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Nhân viên chi nhánh cần chọn chi nhánh."));
        h.send(post("/admin/staff"), staff("Nguyễn Văn A", "a@tiensangloc.local", "BRANCH_STAFF", 999L), admin)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.branchId").exists());
        h.send(post("/admin/staff"), staff(" ", "a@tiensangloc.local", "BRANCH_STAFF", 1L), admin)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.fullName").exists());

        String created = AdminTestHelper.body(h.send(post("/admin/staff"), staff("Nguyễn Văn A", "a@tiensangloc.local", "BRANCH_STAFF", 1L), admin)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.branchName").value("Chi nhánh Hà Nội")));
        int id = com.jayway.jsonpath.JsonPath.read(created, "$.id");
        String temp = com.jayway.jsonpath.JsonPath.read(created, "$.tempPassword");
        assertThat(temp).hasSize(10).isNotEqualTo("123456");
        String token = read(h.login("STAFF", "a@tiensangloc.local", temp).andExpect(status().isOk()), "$.token");
        h.send(get("/staff/summary"), null, token).andExpect(status().isOk());

        // Quản trị viên luôn không có chi nhánh
        h.send(post("/admin/staff"), staff("Quản trị B", "b@tiensangloc.local", "ADMIN", 2L), admin).andExpect(status().isCreated())
                .andExpect(jsonPath("$.branchId").isEmpty());

        // Sửa
        h.send(put("/admin/staff/9999"), Map.of("fullName", "X", "role", "ADMIN"), admin).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Không tìm thấy tài khoản."));
        h.send(put("/admin/staff/" + id), staff("Nguyễn Văn A", null, "BRANCH_STAFF", null), admin).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Nhân viên chi nhánh cần chọn chi nhánh."));
        h.send(put("/admin/staff/102"), staff("Trần Minh Đức", null, "BRANCH_STAFF", 1L), admin).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Không tự hạ quyền của chính mình."));
        h.send(put("/admin/staff/" + id), staff("Nguyễn Văn An", null, "BRANCH_STAFF", 2L), admin).andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Nguyễn Văn An")).andExpect(jsonPath("$.branchName").value("Chi nhánh Hồ Chí Minh"))
                .andExpect(jsonPath("$.tempPassword").isEmpty());

        // Khóa: tự khóa 409; khóa thu hồi phiên, đăng nhập báo bị khóa
        h.send(post("/admin/staff/102/lock"), null, admin).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Không tự khóa tài khoản của mình."));
        h.send(post("/admin/staff/" + id + "/lock"), null, admin).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("LOCKED"));
        h.send(get("/auth/me"), null, token).andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("select count(*) from auth_session where account_kind = 'STAFF' and account_id = ? and revoked_at is null",
                Integer.class, id)).isZero();
        h.login("STAFF", "a@tiensangloc.local", temp).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Tài khoản đang bị khóa, liên hệ quản trị viên."));
        h.send(post("/admin/staff/" + id + "/unlock"), null, admin).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"));
        String token2 = read(h.login("STAFF", "a@tiensangloc.local", temp).andExpect(status().isOk()), "$.token");

        // Đặt lại mật khẩu: mật khẩu tạm mới, thu hồi phiên, mật khẩu cũ hết dùng
        String temp2 = read(h.send(post("/admin/staff/" + id + "/reset-password"), null, admin).andExpect(status().isOk()), "$.tempPassword");
        assertThat(temp2).hasSize(10).isNotEqualTo(temp);
        h.send(get("/auth/me"), null, token2).andExpect(status().isUnauthorized());
        h.login("STAFF", "a@tiensangloc.local", temp).andExpect(status().isUnauthorized());
        h.login("STAFF", "a@tiensangloc.local", temp2).andExpect(status().isOk());

        h.send(post("/admin/staff/" + id + "/xoa"), null, admin).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Thao tác không hợp lệ."));
        h.send(post("/admin/staff/9999/lock"), null, admin).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Không tìm thấy tài khoản."));

        assertThat(jdbc.queryForList("select detail from activity_log where action = 'Quản lý tài khoản nhân viên' order by id", String.class))
                .contains("Khóa a@tiensangloc.local", "Mở khóa a@tiensangloc.local", "Đặt lại mật khẩu a@tiensangloc.local");
        assertThat(jdbc.queryForList("select action from activity_log where actor_id = 102 and actor_type = 'STAFF'", String.class))
                .contains("Tạo tài khoản nhân viên", "Cập nhật tài khoản nhân viên");
    }

    @Test
    void branches_crudDeactivateTransfersApplicationsAndStaff() throws Exception {
        String admin = h.admin();
        String customer = h.newCustomer();
        String app = h.newApp(customer, 3); // DRAFTING tại Đà Nẵng: tính là đang xử lý
        String code = read(h.send(get("/applications/" + app), null, customer), "$.code");

        h.send(get("/admin/branches"), null, admin).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[2].code").value("DN01"))
                .andExpect(jsonPath("$[2].openApplications").value(1));

        // Thêm, sửa
        h.send(post("/admin/branches"), branch("HN01", "Trùng"), admin).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Mã chi nhánh đã tồn tại."));
        h.send(post("/admin/branches"), Map.of("code", "CT01", "name", "Chi nhánh Cần Thơ", "address", ""), admin)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.address").exists());
        int ct = read(h.send(post("/admin/branches"), branch("CT01", "Chi nhánh Cần Thơ"), admin).andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE")), "$.id");
        h.send(put("/admin/branches/9999"), branch("X", "X"), admin).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Không tìm thấy chi nhánh."));
        h.send(put("/admin/branches/" + ct), branch("HCM01", "Cần Thơ"), admin).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Mã chi nhánh đã tồn tại."));
        h.send(put("/admin/branches/" + ct), branch("CT01", "Chi nhánh Cần Thơ mới"), admin).andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Chi nhánh Cần Thơ mới"));

        // Chi nhánh chỉ có nhân viên: cần chi nhánh nhận, nhân viên được chuyển
        int ctStaff = read(h.send(post("/admin/staff"), staff("NV Cần Thơ", "ct@tiensangloc.local", "BRANCH_STAFF", (long) ct), admin)
                .andExpect(status().isCreated()), "$.id");
        h.send(post("/admin/branches/" + ct + "/deactivate"), null, admin).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Chi nhánh còn 1 nhân viên; chọn chi nhánh nhận chuyển."));
        h.send(post("/admin/branches/" + ct + "/deactivate"), Map.of("transferToBranchId", 1), admin).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"))
                .andExpect(jsonPath("$.transferred").value(0)).andExpect(jsonPath("$.transferredStaff").value(1));
        assertThat(jdbc.queryForObject("select branch_id from staff where id = ?", Integer.class, ctStaff)).isEqualTo(1);

        // Đà Nẵng: còn hồ sơ; chi nhánh nhận thiếu / chính nó / đã ngừng → 409
        String msg = "Chi nhánh còn 1 hồ sơ đang xử lý; chọn chi nhánh nhận chuyển.";
        h.send(post("/admin/branches/3/deactivate"), Map.of(), admin).andExpect(status().isConflict()).andExpect(jsonPath("$.message").value(msg));
        h.send(post("/admin/branches/3/deactivate"), Map.of("transferToBranchId", 3), admin).andExpect(status().isConflict());
        h.send(post("/admin/branches/3/deactivate"), Map.of("transferToBranchId", ct), admin).andExpect(status().isConflict());
        h.send(post("/admin/branches/9999/deactivate"), Map.of(), admin).andExpect(status().isNotFound());

        h.send(post("/admin/branches/3/deactivate"), Map.of("transferToBranchId", 2), admin).andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("DN01")).andExpect(jsonPath("$.status").value("INACTIVE"))
                .andExpect(jsonPath("$.transferred").value(1)).andExpect(jsonPath("$.transferredStaff").value(1));
        h.send(get("/applications/" + app), null, customer).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFTING"))
                .andExpect(jsonPath("$.branchId").value(2))
                .andExpect(jsonPath("$.history[-1].note").value("Chuyển từ Chi nhánh Đà Nẵng sang Chi nhánh Hồ Chí Minh"))
                .andExpect(jsonPath("$.history[-1].actor").value("Trần Minh Đức"));
        h.send(get("/notifications"), null, customer)
                .andExpect(jsonPath("$[*].title").value(hasItem("Hồ sơ được chuyển chi nhánh")))
                .andExpect(jsonPath("$[*].body").value(hasItem("Hồ sơ " + code + " được chuyển sang Chi nhánh Hồ Chí Minh (TP. Hồ Chí Minh).")));
        String hcm = h.staffToken("nv.hcm@tiensangloc.local");
        h.send(get("/notifications"), null, hcm).andExpect(jsonPath("$[*].body").value(hasItem(code + " từ Chi nhánh Đà Nẵng")));
        // Nhân viên Đà Nẵng chuyển sang HCM: thấy thông báo của chi nhánh mới
        String dn = h.staffToken("nv.danang@tiensangloc.local");
        h.send(get("/notifications"), null, dn).andExpect(jsonPath("$[*].body").value(hasItem(code + " từ Chi nhánh Đà Nẵng")));
        h.send(get("/admin/staff"), null, admin)
                .andExpect(jsonPath("$[?(@.email == 'nv.danang@tiensangloc.local')].branchName").value("Chi nhánh Hồ Chí Minh"));
        h.send(get("/branches"), null, null).andExpect(jsonPath("$[*].code").value(not(hasItem("DN01"))));
        h.send(get("/admin/branches"), null, admin).andExpect(jsonPath("$[?(@.code == 'DN01')].openApplications").value(0))
                .andExpect(jsonPath("$[?(@.code == 'HCM01')].openApplications").value(1));
        assertThat(jdbc.queryForObject("select detail from activity_log where action = 'Ngừng hoạt động chi nhánh' order by id desc limit 1",
                String.class)).isEqualTo("Chi nhánh Đà Nẵng, chuyển 1 hồ sơ, chuyển 1 nhân viên");

        // Mở lại
        h.send(post("/admin/branches/3/activate"), null, admin).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"));
        h.send(post("/admin/branches/9999/activate"), null, admin).andExpect(status().isNotFound());
        h.send(get("/branches"), null, null).andExpect(jsonPath("$[*].code").value(hasItem("DN01")));
        assertThat(jdbc.queryForList("select action from activity_log where actor_id = 102 and actor_type = 'STAFF'", String.class))
                .contains("Thêm chi nhánh", "Cập nhật chi nhánh", "Ngừng hoạt động chi nhánh", "Mở lại chi nhánh");
    }

    private static Map<String, Object> staff(String fullName, String email, String role, Long branchId) {
        Map<String, Object> m = new HashMap<>();
        m.put("fullName", fullName);
        if (email != null) {
            m.put("email", email);
        }
        m.put("role", role);
        m.put("branchId", branchId);
        return m;
    }

    private static Map<String, Object> branch(String code, String name) {
        return Map.of("code", code, "name", name, "address", "Cần Thơ", "phone", "02920000001");
    }
}
