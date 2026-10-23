package vn.kltn.tiensanglocvay.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import vn.kltn.tiensanglocvay.common.Hashing;

/** B1: /auth/**. Thông báo lỗi đối chiếu từng chữ với fe/src/mocks/handlers/auth.ts. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class AuthApiTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    private static final String OTP = "123456";
    private static final AtomicInteger SEQ = new AtomicInteger(10_000_000);

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PasswordEncoder encoder;

    // ---------------------------------------------------------------- tiện ích

    private static String newPhone() {
        return "09" + SEQ.incrementAndGet();
    }

    private ResultActions postJson(String path, String body, String token) throws Exception {
        var req = post(path).contentType(MediaType.APPLICATION_JSON).content(body);
        if (token != null) {
            req = req.header("Authorization", "Bearer " + token);
        }
        return mvc.perform(req);
    }

    private ResultActions login(String kind, String username, String password) throws Exception {
        return postJson("/auth/login", "{\"kind\":\"%s\",\"username\":\"%s\",\"password\":\"%s\"}".formatted(kind, username, password), null);
    }

    private String loginToken(String kind, String username, String password) throws Exception {
        return read(login(kind, username, password).andExpect(status().isOk()), "$.token");
    }

    private ResultActions me(String token) throws Exception {
        return mvc.perform(get("/auth/me").header("Authorization", "Bearer " + token));
    }

    private static String read(ResultActions r, String path) throws Exception {
        return JsonPath.read(r.andReturn().getResponse().getContentAsString(), path);
    }

    private ResultActions sendOtp(String phone) throws Exception {
        return postJson("/auth/otp", "{\"phone\":\"%s\"}".formatted(phone), null);
    }

    private ResultActions register(String phone, String email, String password, String otp) throws Exception {
        String emailJson = email == null ? "" : ",\"email\":\"%s\"".formatted(email);
        return postJson("/auth/register",
                "{\"phone\":\"%s\",\"password\":\"%s\",\"otp\":\"%s\"%s}".formatted(phone, password, otp, emailJson), null);
    }

    /** Gửi OTP rồi đăng ký, trả token. */
    private String registerOk(String phone, String email, String password) throws Exception {
        sendOtp(phone).andExpect(status().isOk());
        return read(register(phone, email, password, OTP).andExpect(status().isOk()), "$.token");
    }

    // ---------------------------------------------------------------- đăng nhập

    @Test
    void dangNhapKhachBangSoDienThoai() throws Exception {
        login("CUSTOMER", "0901234567", "123456")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.user.id").value(1))
                .andExpect(jsonPath("$.user.name").value("Khách hàng mẫu"))
                .andExpect(jsonPath("$.user.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.user.username").value("0901234567"))
                .andExpect(jsonPath("$.user.email").value("khachhang@tiensangloc.local"))
                .andExpect(jsonPath("$.user.branchName").value((Object) null));
    }

    @Test
    void dangNhapKhachBangEmailKhongPhanBietHoaThuong() throws Exception {
        login("CUSTOMER", " KhachHang@TienSangLoc.local ", "123456")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.id").value(1))
                .andExpect(jsonPath("$.user.username").value("0901234567"));
    }

    @Test
    void dangNhapNhanVienVaQuanTri() throws Exception {
        login("STAFF", "nhanvien@tiensangloc.local", "123456")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.id").value(101))
                .andExpect(jsonPath("$.user.name").value("Nguyễn Thu Hà"))
                .andExpect(jsonPath("$.user.role").value("BRANCH_STAFF"))
                .andExpect(jsonPath("$.user.username").value("nhanvien@tiensangloc.local"))
                .andExpect(jsonPath("$.user.email").value((Object) null))
                .andExpect(jsonPath("$.user.branchName").value("Chi nhánh Hà Nội"));
        login("STAFF", "nv.hcm@tiensangloc.local", "123456")
                .andExpect(jsonPath("$.user.branchName").value("Chi nhánh Hồ Chí Minh"));
        login("STAFF", "nv.danang@tiensangloc.local", "123456")
                .andExpect(jsonPath("$.user.branchName").value("Chi nhánh Đà Nẵng"));
        login("STAFF", "quantri@tiensangloc.local", "123456")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.role").value("ADMIN"))
                .andExpect(jsonPath("$.user.branchName").value((Object) null));
    }

    @Test
    void dangNhapSaiMatKhauHoacKhongTonTaiHoacSaiLoaiTaiKhoan() throws Exception {
        String msg = "Sai tài khoản hoặc mật khẩu.";
        login("CUSTOMER", "0901234567", "sai-mat-khau").andExpect(status().isUnauthorized()).andExpect(jsonPath("$.message").value(msg));
        login("CUSTOMER", "0999999999", "123456").andExpect(status().isUnauthorized()).andExpect(jsonPath("$.message").value(msg));
        login("CUSTOMER", "nobody@tiensangloc.local", "123456").andExpect(status().isUnauthorized()).andExpect(jsonPath("$.message").value(msg));
        login("STAFF", "0901234567", "123456").andExpect(status().isUnauthorized()).andExpect(jsonPath("$.message").value(msg));
        login("CUSTOMER", "quantri@tiensangloc.local", "123456").andExpect(status().isUnauthorized()).andExpect(jsonPath("$.message").value(msg));
        // Mật khẩu sai của nhân viên.
        login("STAFF", "nhanvien@tiensangloc.local", "sai").andExpect(status().isUnauthorized());
    }

    @Test
    void dangNhapThieuTruongTra400() throws Exception {
        postJson("/auth/login", "{}", null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Dữ liệu không hợp lệ."))
                .andExpect(jsonPath("$.fields.username").exists());
        postJson("/auth/login", "{\"kind\":\"ADMIN\",\"username\":\"a\",\"password\":\"b\"}", null).andExpect(status().isBadRequest());
    }

    @Test
    void taiKhoanBiKhoaTra403SauKhiDungMatKhauVaPhienCuMatHieuLuc() throws Exception {
        String phone = newPhone();
        String token = registerOk(phone, null, "matkhau1");
        me(token).andExpect(status().isOk());

        jdbc.update("UPDATE customer SET status = 'LOCKED' WHERE phone = ?", phone);

        login("CUSTOMER", phone, "matkhau1").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Tài khoản đang bị khóa, liên hệ quản trị viên."));
        // Như mock: kiểm tra mật khẩu trước, sai mật khẩu vẫn là 401.
        login("CUSTOMER", phone, "sai-mat-khau").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Sai tài khoản hoặc mật khẩu."));
        // Tài khoản khóa coi như chưa đăng nhập.
        me(token).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.message").value("Chưa đăng nhập."));
    }

    @Test
    void dangNhapGhiNhatKyVaTokenKhongLuuNguyenVan() throws Exception {
        String token = loginToken("STAFF", "nv.hcm@tiensangloc.local", "123456");

        Integer logs = jdbc.queryForObject(
                "SELECT count(*) FROM activity_log WHERE actor_type = 'STAFF' AND actor_id = 103 AND action = 'Đăng nhập' AND detail = 'Nhân viên đăng nhập'",
                Integer.class);
        assertThat(logs).isPositive();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM auth_session WHERE token_hash = ?", Integer.class, token)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM auth_session WHERE token_hash = ?", Integer.class, Hashing.sha256Hex(token))).isEqualTo(1);
        assertThat(token).hasSize(43).matches("[A-Za-z0-9_-]+");
    }

    // ---------------------------------------------------------------- phiên, /me, đăng xuất

    @Test
    void meTraThongTinVaTokenSaiLa401() throws Exception {
        String token = loginToken("CUSTOMER", "0901234567", "123456");
        me(token).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.username").value("0901234567"))
                .andExpect(jsonPath("$.role").value("CUSTOMER"));
        me("khong-ton-tai").andExpect(status().isUnauthorized()).andExpect(jsonPath("$.message").value("Chưa đăng nhập."));
    }

    @Test
    void phienHetHanLa401() throws Exception {
        String token = loginToken("CUSTOMER", "0901234567", "123456");
        jdbc.update("UPDATE auth_session SET expires_at = now() - interval '1 minute' WHERE token_hash = ?", Hashing.sha256Hex(token));
        me(token).andExpect(status().isUnauthorized());
    }

    @Test
    void moiLanDangNhapCoTokenRieng() throws Exception {
        String t1 = loginToken("CUSTOMER", "0901234567", "123456");
        String t2 = loginToken("CUSTOMER", "0901234567", "123456");
        assertThat(t1).isNotEqualTo(t2);
        me(t1).andExpect(status().isOk());
        me(t2).andExpect(status().isOk());
    }

    @Test
    void dangXuatThuHoiDungPhienHienTaiVaLuonTra200() throws Exception {
        String t1 = loginToken("CUSTOMER", "0901234567", "123456");
        String t2 = loginToken("CUSTOMER", "0901234567", "123456");

        postJson("/auth/logout", "", t1).andExpect(status().isOk()).andExpect(jsonPath("$.ok").value(true));

        me(t1).andExpect(status().isUnauthorized());
        me(t2).andExpect(status().isOk());
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM activity_log WHERE actor_type = 'CUSTOMER' AND actor_id = 1 AND action = 'Đăng xuất'", Integer.class)).isPositive();

        // Token đã thu hồi, token rác, không có token: vẫn 200.
        postJson("/auth/logout", "", t1).andExpect(status().isOk()).andExpect(jsonPath("$.ok").value(true));
        postJson("/auth/logout", "", "rac").andExpect(status().isOk());
        postJson("/auth/logout", "", null).andExpect(status().isOk()).andExpect(jsonPath("$.ok").value(true));
    }

    // ---------------------------------------------------------------- OTP

    @Test
    void otpSoDienThoaiKhongHopLe() throws Exception {
        String msg = "Số điện thoại không hợp lệ.";
        postJson("/auth/otp", "{}", null).andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(msg));
        postJson("/auth/otp", "{\"phone\":\"123\"}", null).andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(msg));
        postJson("/auth/otp", "{\"phone\":\"09012345678\"}", null).andExpect(status().isBadRequest());
        postJson("/auth/otp", "{\"phone\":\"1901234567\"}", null).andExpect(status().isBadRequest());
        // Nhân viên đăng nhập không có số điện thoại để gửi tới.
        String staff = loginToken("STAFF", "nhanvien@tiensangloc.local", "123456");
        postJson("/auth/otp", "{}", staff).andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(msg));
    }

    @Test
    void otpGuiChoDangKyVaChoKhachDangDangNhap() throws Exception {
        postJson("/auth/otp", "{\"phone\":\"0912345678\"}", null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.sent").value(true)).andExpect(jsonPath("$.devOtp").value(OTP));
        String token = loginToken("CUSTOMER", "0901234567", "123456");
        postJson("/auth/otp", "{}", token)
                .andExpect(status().isOk()).andExpect(jsonPath("$.sent").value(true)).andExpect(jsonPath("$.devOtp").value(OTP));
        // Token hết hiệu lực trên endpoint [{}, bearer] chỉ bị bỏ qua.
        postJson("/auth/otp", "{\"phone\":\"0912345678\"}", "token-cu").andExpect(status().isOk());
    }

    @Test
    void otpKhongLuuMaGoc() throws Exception {
        String phone = newPhone();
        sendOtp(phone).andExpect(status().isOk());
        String hash = jdbc.queryForObject("SELECT code_hash FROM otp_code WHERE target = ? AND used_at IS NULL", String.class, phone);
        assertThat(hash).hasSize(64).isNotEqualTo(OTP);
    }

    @Test
    void guiLaiOtpVoHieuMaCu() throws Exception {
        String phone = newPhone();
        sendOtp(phone).andExpect(status().isOk());
        sendOtp(phone).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM otp_code WHERE target = ? AND used_at IS NULL", Integer.class, phone)).isEqualTo(1);
    }

    // ---------------------------------------------------------------- đăng ký

    @Test
    void dangKyThanhCongRoiDangNhapDuoc() throws Exception {
        String phone = newPhone();
        sendOtp(phone).andExpect(status().isOk());
        register(phone, " Moi.Dung@Example.com ", "matkhau1", OTP)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.user.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.user.name").value("Khách hàng " + phone.substring(6)))
                .andExpect(jsonPath("$.user.username").value(phone))
                .andExpect(jsonPath("$.user.email").value("Moi.Dung@Example.com"));

        String hash = jdbc.queryForObject("SELECT password_hash FROM customer WHERE phone = ?", String.class, phone);
        assertThat(hash).startsWith("$2").isNotEqualTo("matkhau1");
        login("CUSTOMER", phone, "matkhau1").andExpect(status().isOk());
        login("CUSTOMER", "moi.dung@example.com", "matkhau1").andExpect(status().isOk());
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM activity_log WHERE action = 'Đăng ký tài khoản' AND actor_type = 'CUSTOMER' AND actor_id = (SELECT id FROM customer WHERE phone = ?)",
                Integer.class, phone)).isEqualTo(1);
    }

    @Test
    void dangKyKhongEmailThiEmailLaNull() throws Exception {
        String phone = newPhone();
        sendOtp(phone).andExpect(status().isOk());
        postJson("/auth/register", "{\"phone\":\"%s\",\"email\":\"\",\"password\":\"matkhau1\",\"otp\":\"%s\"}".formatted(phone, OTP), null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.user.email").value((Object) null));
    }

    @Test
    void dangKyOtpSaiHoacChuaGui() throws Exception {
        String phone = newPhone();
        register(phone, null, "matkhau1", OTP).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Mã OTP không đúng."));
        sendOtp(phone).andExpect(status().isOk());
        register(phone, null, "matkhau1", "000000").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Mã OTP không đúng."));
        postJson("/auth/register", "{\"phone\":\"%s\",\"password\":\"matkhau1\"}".formatted(phone), null)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Mã OTP không đúng."));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM customer WHERE phone = ?", Integer.class, phone)).isZero();
        // Nhập sai không làm mất mã đúng.
        register(phone, null, "matkhau1", OTP).andExpect(status().isOk());
    }

    @Test
    void otpSaiQuaNamLanThiMaHetHieuLuc() throws Exception {
        String phone = newPhone();
        sendOtp(phone).andExpect(status().isOk());
        for (int i = 0; i < 5; i++) {
            register(phone, null, "matkhau1", "000000").andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Mã OTP không đúng."));
        }
        // Từ lần thứ 6, ngay cả mã đúng cũng không dùng được; phải gửi lại.
        register(phone, null, "matkhau1", OTP).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Mã OTP đã hết hạn hoặc nhập sai quá nhiều lần, vui lòng gửi lại mã mới."));
        sendOtp(phone).andExpect(status().isOk());
        register(phone, null, "matkhau1", OTP).andExpect(status().isOk());
    }

    @Test
    void otpHetHanSauNamPhut() throws Exception {
        String phone = newPhone();
        sendOtp(phone).andExpect(status().isOk());
        jdbc.update("UPDATE otp_code SET expires_at = now() - interval '1 second' WHERE target = ?", phone);
        register(phone, null, "matkhau1", OTP).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Mã OTP đã hết hạn hoặc nhập sai quá nhiều lần, vui lòng gửi lại mã mới."));
    }

    @Test
    void otpChiDungMotLan() throws Exception {
        String phone = newPhone();
        registerOk(phone, null, "matkhau1");
        register(phone, null, "matkhau1", OTP).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Mã OTP không đúng."));
    }

    @Test
    void dangKyTrungSoDienThoai() throws Exception {
        sendOtp("0901234567").andExpect(status().isOk());
        register("0901234567", null, "matkhau1", OTP).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Số điện thoại đã được đăng ký."));
    }

    @Test
    void dangKyTrungEmailKhongPhanBietHoaThuongVaGiuLaiOtp() throws Exception {
        String phone = newPhone();
        sendOtp(phone).andExpect(status().isOk());
        register(phone, "KhachHang@TienSangLoc.local", "matkhau1", OTP).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Email đã được sử dụng."));
        // Lỗi nghiệp vụ không làm cháy OTP: đổi email rồi đăng ký lại bằng chính mã đó.
        register(phone, "khac@example.com", "matkhau1", OTP).andExpect(status().isOk());
        // Email của nhân viên không tính (mock chỉ xét tài khoản khách hàng).
        String phone2 = newPhone();
        registerOk(phone2, "quantri@tiensangloc.local", "matkhau1");
    }

    @Test
    void dangKyDuLieuKhongHopLe() throws Exception {
        postJson("/auth/register", "{\"phone\":\"123\",\"password\":\"abc\",\"otp\":\"123456\"}", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Dữ liệu không hợp lệ."))
                .andExpect(jsonPath("$.fields.phone").value("Số điện thoại không hợp lệ."))
                .andExpect(jsonPath("$.fields.password").value("Mật khẩu tối thiểu 6 ký tự."));
        postJson("/auth/register", "{\"phone\":\"0901234560\",\"password\":\"matkhau1\",\"otp\":\"123456\",\"email\":\"khong-phai-email\"}", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Dữ liệu không hợp lệ."))
                .andExpect(jsonPath("$.fields.email").value("Email không hợp lệ."));
    }

    // ---------------------------------------------------------------- đổi mật khẩu

    @Test
    void doiMatKhauCanDangNhap() throws Exception {
        postJson("/auth/change-password", "{\"oldPassword\":\"a\",\"newPassword\":\"bbbbbb\"}", null)
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.message").value("Chưa đăng nhập."));
    }

    @Test
    void doiMatKhauBaLoiTheoThuTuMock() throws Exception {
        String phone = newPhone();
        String token = registerOk(phone, null, "matkhau1");

        postJson("/auth/change-password", "{\"oldPassword\":\"sai\",\"newPassword\":\"matkhau2\"}", token)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Mật khẩu hiện tại không đúng."));
        postJson("/auth/change-password", "{\"newPassword\":\"matkhau2\"}", token)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Mật khẩu hiện tại không đúng."));
        postJson("/auth/change-password", "{\"oldPassword\":\"matkhau1\",\"newPassword\":\"12345\"}", token)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Mật khẩu mới tối thiểu 6 ký tự."));
        postJson("/auth/change-password", "{\"oldPassword\":\"matkhau1\"}", token)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Mật khẩu mới tối thiểu 6 ký tự."));
        postJson("/auth/change-password", "{\"oldPassword\":\"matkhau1\",\"newPassword\":\"matkhau1\"}", token)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Mật khẩu mới phải khác mật khẩu hiện tại."));
        // Mật khẩu hiện tại sai được báo trước lỗi mật khẩu mới.
        postJson("/auth/change-password", "{\"oldPassword\":\"sai\",\"newPassword\":\"1\"}", token)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Mật khẩu hiện tại không đúng."));
        login("CUSTOMER", phone, "matkhau1").andExpect(status().isOk());
    }

    @Test
    void doiMatKhauThuHoiPhienKhacVaGiuPhienHienTai() throws Exception {
        String phone = newPhone();
        String current = registerOk(phone, null, "matkhau1");
        String other = loginToken("CUSTOMER", phone, "matkhau1");
        String otherAccount = registerOk(newPhone(), null, "matkhau1");

        postJson("/auth/change-password", "{\"oldPassword\":\"matkhau1\",\"newPassword\":\"matkhau2\"}", current)
                .andExpect(status().isOk()).andExpect(jsonPath("$.ok").value(true));

        me(current).andExpect(status().isOk());
        me(other).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.message").value("Chưa đăng nhập."));
        me(otherAccount).andExpect(status().isOk());
        login("CUSTOMER", phone, "matkhau1").andExpect(status().isUnauthorized());
        login("CUSTOMER", phone, "matkhau2").andExpect(status().isOk());
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM activity_log WHERE action = 'Đổi mật khẩu' AND actor_id = (SELECT id FROM customer WHERE phone = ?)",
                Integer.class, phone)).isEqualTo(1);
    }

    @Test
    void doiMatKhauNhanVien() throws Exception {
        jdbc.update("INSERT INTO staff (id, branch_id, full_name, email, password_hash, role, status) VALUES (9001, 1, 'Nhân viên thử', 'thu.doimk@tiensangloc.local', ?, 'BRANCH_STAFF', 'ACTIVE')",
                encoder.encode("matkhau1"));
        String current = loginToken("STAFF", "thu.doimk@tiensangloc.local", "matkhau1");
        String other = loginToken("STAFF", "thu.doimk@tiensangloc.local", "matkhau1");

        postJson("/auth/change-password", "{\"oldPassword\":\"matkhau1\",\"newPassword\":\"matkhau2\"}", current).andExpect(status().isOk());

        me(current).andExpect(status().isOk());
        me(other).andExpect(status().isUnauthorized());
        login("STAFF", "thu.doimk@tiensangloc.local", "matkhau2").andExpect(status().isOk());
        login("STAFF", "thu.doimk@tiensangloc.local", "matkhau1").andExpect(status().isUnauthorized());
    }
}
