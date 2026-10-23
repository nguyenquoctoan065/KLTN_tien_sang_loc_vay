package vn.kltn.tiensanglocvay.common;

import java.util.UUID;
import org.springframework.http.HttpStatus;

/**
 * Mã công khai (UUID) của lượt sàng lọc, phương án, hồ sơ. Đường dẫn có id không đúng định dạng UUID (kể cả id số) được coi là
 * không tìm thấy, trả 404 với thông báo của tài nguyên đó.
 */
public final class PublicIds {

    private PublicIds() {
    }

    public static UUID parse(String raw, String notFoundMessage) {
        try {
            UUID id = UUID.fromString(raw);
            // UUID.fromString chấp nhận cả dạng rút gọn ("1-1-1-1-1"); chỉ nhận dạng chuẩn 36 ký tự.
            if (raw.length() == 36 && id.toString().equalsIgnoreCase(raw)) {
                return id;
            }
        } catch (IllegalArgumentException | NullPointerException e) {
            // rơi xuống 404
        }
        throw new ApiException(HttpStatus.NOT_FOUND, notFoundMessage);
    }

    /** Mã ngắn dùng trong chi tiết nhật ký: 8 ký tự đầu của UUID. */
    public static String shortCode(UUID id) {
        return id.toString().substring(0, 8);
    }
}
