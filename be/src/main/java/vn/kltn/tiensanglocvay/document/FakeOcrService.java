package vn.kltn.tiensanglocvay.document;

import java.util.List;
import java.util.Locale;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import vn.kltn.tiensanglocvay.catalog.DocumentType;
import vn.kltn.tiensanglocvay.engine.JsFormat;

/**
 * Máy đọc giả lập: chép đúng hàm {@code ocrFields} trong fe/src/mocks/ctx.ts (giá trị lấy từ thông tin đã khai, có sai lệch nhỏ
 * theo {@code seed} để minh họa bước xác nhận và đối chiếu). Không đọc nội dung tệp.
 */
@Service
@ConditionalOnProperty(name = "app.ocr.provider", havingValue = "fake")
public class FakeOcrService implements OcrService {

    @Override
    public OcrResult extract(DocumentType type, byte[] data, String mimeType, OcrContext c) {
        double jitter = ((c.seed() % 13) - 6) / 100.0; // -6%..+6%
        double income = c.avgIncome() != null ? c.avgIncome() : 15_000_000;
        int t = type.getId();
        List<OcrResult.Field> fields = switch (t) {
            case 2 -> List.of(
                    f(t, "full_name", c.fullName().toUpperCase(Locale.ROOT), 97),
                    f(t, "national_id", c.nationalId(), 95),
                    f(t, "dob", reverseDate(or(c.dob(), c.screeningDob())), 93),
                    f(t, "gender", "FEMALE".equals(or(c.gender(), c.screeningGender())) ? "Nữ" : "Nam", 98),
                    f(t, "address", c.address(), 81));
            case 3 -> List.of(f(t, "address", c.address(), 84));
            case 4 -> List.of(
                    f(t, "employer", "Công ty CP Công nghệ Sao Mai", 88),
                    f(t, "salary", JsFormat.vnd(Math.round(income * 1.05 / 1e5) * 1e5), 86));
            case 5 -> List.of(
                    f(t, "salary_bank", c.salaryBank() == null ? "" : c.salaryBank(), 92),
                    f(t, "avg_income", JsFormat.vnd(Math.round(income * (1 + jitter) / 1e4) * 1e4), 90));
            case 6 -> List.of(
                    f(t, "beneficiary", "Công ty TNHH Thương mại Hòa Bình", 87),
                    f(t, "invoice_amount", JsFormat.vnd(Math.round(c.planAmount() * 1.08 / 1e6) * 1e6), 89));
            default -> List.of();
        };
        return OcrResult.of(fields);
    }

    private static OcrResult.Field f(int typeId, String key, String value, double confidence) {
        return new OcrResult.Field(key, DocumentFields.label(typeId, key), value, confidence);
    }

    /** {@code a || b || ''} của JS với chuỗi. */
    private static String or(String a, String b) {
        if (a != null && !a.isEmpty()) {
            return a;
        }
        return b == null ? "" : b;
    }

    /** {@code s.split('-').reverse().join('/')}: yyyy-MM-dd → dd/MM/yyyy. */
    static String reverseDate(String s) {
        String[] p = s.split("-", -1);
        StringBuilder b = new StringBuilder();
        for (int i = p.length - 1; i >= 0; i--) {
            b.append(p[i]);
            if (i > 0) {
                b.append('/');
            }
        }
        return b.toString();
    }
}
