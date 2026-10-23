package vn.kltn.tiensanglocvay.document;

import java.util.Arrays;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Kiểm tra chất lượng tệp tải lên (docs/quy-tac-tinh-toan.md mục 9), chạy trên bytes nhận được, trước khi lưu kho và đọc OCR.
 * Thứ tự và câu chữ giống handler upload trong fe/src/mocks/handlers/applications.ts.
 */
@Component
public class QualityChecker {

    public static final String OK = "OK";
    public static final String WRONG_FORMAT = "WRONG_FORMAT";
    public static final String TOO_LARGE = "TOO_LARGE";
    public static final String BLURRY = "BLURRY";

    static final int MAX_KB = 10 * 1024;
    static final int MIN_IMAGE_KB = 25;

    /** {@code ext}: đuôi in hoa (JPEG coi là JPG); {@code kb} = max(1, round(bytes/1024)) như mock. */
    public record Result(String ext, int kb, String qualityStatus, String message) {

        public boolean ok() {
            return OK.equals(qualityStatus);
        }
    }

    public Result check(String fileName, long size, String allowedFormats) {
        String ext = extension(fileName);
        int kb = (int) Math.max(1, Math.round(size / 1024.0));
        if (Arrays.stream(allowedFormats.split(",")).noneMatch(ext::equals)) {
            return new Result(ext, kb, WRONG_FORMAT, "Định dạng " + ext + " không được chấp nhận (cho phép " + allowedFormats + ").");
        }
        if (kb > MAX_KB) {
            return new Result(ext, kb, TOO_LARGE, "Tệp lớn hơn 10 MB.");
        }
        if (!"PDF".equals(ext) && kb < MIN_IMAGE_KB) {
            return new Result(ext, kb, BLURRY, "Ảnh có độ phân giải thấp hoặc bị mờ, vui lòng chụp lại rõ nét, đủ bốn góc.");
        }
        return new Result(ext, kb, OK, null);
    }

    /** {@code (file.name.split('.').pop() || '').toUpperCase().replace('JPEG', 'JPG')} của mock. */
    static String extension(String fileName) {
        String name = fileName == null ? "" : fileName;
        return name.substring(name.lastIndexOf('.') + 1).toUpperCase(Locale.ROOT).replaceFirst("JPEG", "JPG");
    }
}
