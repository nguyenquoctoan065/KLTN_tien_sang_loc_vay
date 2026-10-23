package vn.kltn.tiensanglocvay.common;

import java.util.Map;
import org.springframework.http.HttpStatus;

/** Lỗi nghiệp vụ trả về {@code { "message": "...", "fields": {...} }} (xem GlobalExceptionHandler). */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final Map<String, String> fields;

    public ApiException(HttpStatus status, String message) {
        this(status, message, null);
    }

    public ApiException(HttpStatus status, String message, Map<String, String> fields) {
        super(message);
        this.status = status;
        this.fields = fields;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public Map<String, String> getFields() {
        return fields;
    }
}
