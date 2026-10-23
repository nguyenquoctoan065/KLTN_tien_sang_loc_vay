package vn.kltn.tiensanglocvay.document;

import java.time.Duration;

/**
 * Kho lưu tệp giấy tờ. Chọn bằng {@code app.storage.provider}: {@code local} ({@link LocalStorageService}) hoặc
 * {@code cloudinary} (B4b, CloudinaryStorageService – type authenticated, URL ký có hạn).
 */
public interface StorageService {

    /**
     * @param fileName tên tệp trong kho, gồm đuôi (ví dụ {@code doc-12-v2.jpg}); phần trước đuôi là public_id trong {@code folder}
     * @param folder   thư mục, ví dụ {@code tien-sang-loc-vay/dev/app-5}
     */
    StoredFile upload(byte[] data, String fileName, String folder);

    /** Đường dẫn xem/tải tệp có chữ ký và hết hạn sau {@code ttl}; frontend dùng thẳng trong thẻ img. */
    String signedUrl(StoredFile f, Duration ttl);

    /**
     * Đường dẫn mở tệp gốc cỡ đầy đủ (ảnh gốc hoặc PDF) có chữ ký và hết hạn sau {@code ttl} (AppDocument.fileUrl, nút “Mở tệp”).
     */
    String fileUrl(StoredFile f, Duration ttl);

    /** Xóa tệp; không có thì bỏ qua. */
    void delete(StoredFile f);
}
