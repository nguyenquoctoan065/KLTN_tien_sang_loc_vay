package vn.kltn.tiensanglocvay.admin;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import vn.kltn.tiensanglocvay.common.ApiException;

/**
 * Gom lỗi kiểm tra dữ liệu (chặt hơn mock) thành một 400 "Dữ liệu không hợp lệ." kèm {@code fields}. Chạy sau các kiểm tra có câu chữ
 * riêng của mock, để những trường hợp mock xử lý vẫn trả đúng thông báo của mock.
 */
final class FieldErrors {

    static final String INVALID = "Dữ liệu không hợp lệ.";
    static final Set<String> STATUSES = Set.of("ACTIVE", "INACTIVE");

    private final Map<String, String> fields = new LinkedHashMap<>();

    void add(String field, String message) {
        fields.putIfAbsent(field, message);
    }

    /** Chuỗi bắt buộc (không rỗng sau khi trim), tối đa {@code max} ký tự. */
    void text(String field, String value, int max) {
        if (value == null || value.isBlank()) {
            add(field, "Không được để trống.");
        } else {
            optionalText(field, value, max);
        }
    }

    void optionalText(String field, String value, int max) {
        if (value != null && value.trim().length() > max) {
            add(field, "Tối đa " + max + " ký tự.");
        }
    }

    void oneOf(String field, String value, Set<String> allowed) {
        if (value == null || !allowed.contains(value)) {
            add(field, "Phải là một trong: " + String.join(", ", allowed.stream().sorted().toList()) + ".");
        }
    }

    /** Số trong khoảng [min, max] với tối đa {@code scale} chữ số thập phân (cột decimal). */
    void decimal(String field, BigDecimal value, BigDecimal min, BigDecimal max, int scale) {
        if (value == null) {
            add(field, "Không được để trống.");
        } else if (value.compareTo(min) < 0 || value.compareTo(max) > 0) {
            add(field, "Phải nằm trong khoảng " + min.toPlainString() + "–" + max.toPlainString() + ".");
        } else if (value.stripTrailingZeros().scale() > scale) {
            add(field, scale == 0 ? "Phải là số nguyên." : "Tối đa " + scale + " chữ số thập phân.");
        }
    }

    boolean isEmpty() {
        return fields.isEmpty();
    }

    void throwIfAny() {
        if (!fields.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, INVALID, fields);
        }
    }

    static String trim(String s) {
        return s == null ? null : s.trim();
    }
}
