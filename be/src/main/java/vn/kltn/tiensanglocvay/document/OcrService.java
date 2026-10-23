package vn.kltn.tiensanglocvay.document;

import vn.kltn.tiensanglocvay.catalog.DocumentType;

/**
 * Đọc thông tin giấy tờ (MB06). Chọn bằng {@code app.ocr.provider}: {@code fake} ({@link FakeOcrService}) hoặc
 * {@code gemini} (B4b, GeminiOcrService). Lỗi gọi dịch vụ ném ngoại lệ; người gọi vẫn lưu giấy tờ ở UPLOADED để khách nhập tay.
 */
public interface OcrService {

    OcrResult extract(DocumentType type, byte[] data, String mimeType, OcrContext ctx);
}
