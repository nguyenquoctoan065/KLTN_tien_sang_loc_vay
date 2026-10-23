package vn.kltn.tiensanglocvay.document;

/**
 * Kho lưu tệp từ chối tệp vì hỏng hoặc không mở được (ví dụ Cloudinary trả "Invalid PDF file", kho cục bộ nhận tệp rỗng).
 * {@code DocumentUploadService} đổi thành giấy tờ REJECTED_QUALITY / CORRUPTED thay vì lỗi 500; lỗi khác của kho (khóa sai, mạng, hết
 * hạn mức) vẫn là lỗi hệ thống.
 */
public class CorruptedFileException extends RuntimeException {

    public CorruptedFileException(String message) {
        super(message);
    }

    public CorruptedFileException(String message, Throwable cause) {
        super(message, cause);
    }
}
