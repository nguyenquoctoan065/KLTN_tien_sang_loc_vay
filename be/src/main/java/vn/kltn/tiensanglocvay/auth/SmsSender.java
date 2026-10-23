package vn.kltn.tiensanglocvay.auth;

/** Cổng gửi SMS; gắn nhà cung cấp thật bằng cách thêm một bean thay {@link LoggingSmsSender}. */
public interface SmsSender {

    void send(String phone, String code);
}
