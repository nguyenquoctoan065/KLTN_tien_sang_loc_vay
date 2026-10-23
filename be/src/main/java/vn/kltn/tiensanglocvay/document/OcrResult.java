package vn.kltn.tiensanglocvay.document;

import java.util.List;

/**
 * Kết quả đọc giấy tờ (docs/be-huong-dan.md 5.4): {@code readable=false} → BLURRY, {@code documentMatchesType=false} → WRONG_TYPE
 * (B4b), còn lại lưu {@code fields}.
 */
public record OcrResult(boolean readable, boolean documentMatchesType, String issue, List<Field> fields) {

    /** {@code key} là field_key (quy-tac-tinh-toan mục 9), {@code confidence} 0–100. */
    public record Field(String key, String label, String value, double confidence) {
    }

    public static OcrResult of(List<Field> fields) {
        return new OcrResult(true, true, null, fields);
    }
}
