package vn.kltn.tiensanglocvay;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

/** Đăng nhập dùng token phiên lưu bảng (xem security/), nên tắt tài khoản mặc định "user" của Spring Security. */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class TienSangLocVayApplication {

    public static void main(String[] args) {
        SpringApplication.run(TienSangLocVayApplication.class, args);
    }
}
