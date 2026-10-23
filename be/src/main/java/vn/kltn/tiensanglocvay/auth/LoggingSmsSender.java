package vn.kltn.tiensanglocvay.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Chưa có nhà cung cấp SMS: chỉ ghi log việc gửi, không ghi mã OTP. Thay bằng bean {@link SmsSender} thật khi có. */
@Component
public class LoggingSmsSender implements SmsSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingSmsSender.class);

    @Override
    public void send(String phone, String code) {
        String masked = phone.length() > 6 ? phone.substring(0, 3) + "****" + phone.substring(phone.length() - 3) : "****";
        log.info("OTP: chưa cấu hình nhà cung cấp SMS, không gửi tin nhắn tới {}", masked);
    }
}
