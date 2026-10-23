package vn.kltn.tiensanglocvay.document;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Từ kết quả máy đọc ({@link OcrResult}) quyết định nhận hay từ chối giấy tờ (docs/be-huong-dan.md 5.4, quy-tac-tinh-toan mục 9):
 * không đọc được → BLURRY; sai loại → WRONG_TYPE; giấy tờ mẫu → SPECIMEN, trừ khi {@code app.ocr.allow-specimen=true}
 * (môi trường thử nghiệm, profile dev) thì vẫn nhận như giấy tờ thường và ghi nhật ký.
 */
@Component
public class OcrPolicy {

    public static final String SPECIMEN = "SPECIMEN";
    public static final String WRONG_TYPE = "WRONG_TYPE";
    static final String MSG_BLURRY = "Ảnh có độ phân giải thấp hoặc bị mờ, vui lòng chụp lại rõ nét, đủ bốn góc.";
    static final String MSG_SPECIMEN = "Tệp là giấy tờ mẫu, không có giá trị, vui lòng tải giấy tờ thật.";
    /** Hành động ghi vào nhật ký khi nhận giấy tờ mẫu. */
    public static final String LOG_SPECIMEN_ACCEPTED = "Chấp nhận giấy tờ mẫu (môi trường thử nghiệm)";

    /**
     * {@code accepted=false}: giấy tờ bị từ chối ({@code qualityStatus}, {@code message}); {@code true}: lưu {@code fields};
     * {@code specimenAccepted}: giấy tờ mẫu được nhận nhờ cấu hình (cần ghi nhật ký).
     */
    public record Verdict(boolean accepted, String qualityStatus, String message, boolean specimenAccepted) {
    }

    private final boolean allowSpecimen;

    public OcrPolicy(@Value("${app.ocr.allow-specimen:false}") boolean allowSpecimen) {
        this.allowSpecimen = allowSpecimen;
    }

    public Verdict judge(OcrResult r, String typeName) {
        if (!r.readable()) {
            return new Verdict(false, QualityChecker.BLURRY, r.issue() != null ? r.issue() : MSG_BLURRY, false);
        }
        if (!r.documentMatchesType()) {
            return new Verdict(false, WRONG_TYPE, "Tệp không phải " + typeName + ".", false);
        }
        if (r.specimen() && !allowSpecimen) {
            return new Verdict(false, SPECIMEN, MSG_SPECIMEN, false);
        }
        return new Verdict(true, QualityChecker.OK, null, r.specimen());
    }
}
