package vn.kltn.tiensanglocvay.document;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.util.List;
import org.junit.jupiter.api.Test;
import vn.kltn.tiensanglocvay.catalog.DocumentType;

/** QualityChecker (quy-tac-tinh-toan mục 9) và FakeOcrService (chép ocrFields của fe/src/mocks/ctx.ts). */
class DocumentUnitTest {

    private final QualityChecker quality = new QualityChecker();
    private final FakeOcrService ocr = new FakeOcrService();

    private static DocumentType type(int id) throws Exception {
        var ctor = DocumentType.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        DocumentType t = ctor.newInstance();
        Field f = DocumentType.class.getDeclaredField("id");
        f.setAccessible(true);
        f.set(t, id);
        return t;
    }

    private static OcrContext ctx(int seed) {
        return new OcrContext("Nguyễn Văn Minh", "001092012345", "", "", "12 Láng Hạ", "1992-05-15", "FEMALE", 20_000_000L,
                "Vietcombank", 150_000_000, seed);
    }

    @Test
    void ocrPolicy_specimenDependsOnConfig() {
        OcrResult specimen = new OcrResult(true, true, true, null, List.of());
        OcrResult real = OcrResult.of(List.of());
        // allow-specimen=false (mặc định): giấy tờ mẫu bị từ chối
        OcrPolicy strict = new OcrPolicy(false);
        assertThat(strict.judge(specimen, "CCCD")).isEqualTo(new OcrPolicy.Verdict(false, "SPECIMEN",
                "Tệp là giấy tờ mẫu, không có giá trị, vui lòng tải giấy tờ thật.", false));
        assertThat(strict.judge(real, "CCCD")).isEqualTo(new OcrPolicy.Verdict(true, "OK", null, false));
        // allow-specimen=true: nhận như thường, đánh dấu để ghi nhật ký; giấy tờ thật không đổi
        OcrPolicy lenient = new OcrPolicy(true);
        assertThat(lenient.judge(specimen, "CCCD")).isEqualTo(new OcrPolicy.Verdict(true, "OK", null, true));
        assertThat(lenient.judge(real, "CCCD")).isEqualTo(new OcrPolicy.Verdict(true, "OK", null, false));
        // Không đọc được / sai loại vẫn bị từ chối ở cả hai cấu hình, và xét trước giấy tờ mẫu
        for (OcrPolicy p : List.of(strict, lenient)) {
            assertThat(p.judge(new OcrResult(false, true, true, "Ảnh bị lóa.", List.of()), "CCCD"))
                    .isEqualTo(new OcrPolicy.Verdict(false, "BLURRY", "Ảnh bị lóa.", false));
            assertThat(p.judge(new OcrResult(true, false, true, null, List.of()), "CCCD"))
                    .isEqualTo(new OcrPolicy.Verdict(false, "WRONG_TYPE", "Tệp không phải CCCD.", false));
        }
    }

    @Test
    void quality_orderAndMessages() {
        assertThat(quality.check("a.gif", 60_000, "JPG,PNG,PDF")).isEqualTo(new QualityChecker.Result("GIF", 59, "WRONG_FORMAT",
                "Định dạng GIF không được chấp nhận (cho phép JPG,PNG,PDF)."));
        // Sai định dạng xét trước dung lượng
        assertThat(quality.check("a.gif", 20 * 1024 * 1024, "JPG").qualityStatus()).isEqualTo("WRONG_FORMAT");
        assertThat(quality.check("a.JPEG", 11L * 1024 * 1024, "JPG,PNG,PDF").qualityStatus()).isEqualTo("TOO_LARGE");
        assertThat(quality.check("a.jpeg", 24 * 1024, "JPG").qualityStatus()).isEqualTo("BLURRY");
        assertThat(quality.check("a.jpg", 25 * 1024, "JPG").ok()).isTrue();
        // PDF nhỏ không bị coi là mờ; dung lượng tối thiểu 1 KB
        QualityChecker.Result pdf = quality.check("sao.ke.pdf", 100, "PDF,JPG,PNG");
        assertThat(pdf.ok()).isTrue();
        assertThat(pdf.ext()).isEqualTo("PDF");
        assertThat(pdf.kb()).isEqualTo(1);
        assertThat(quality.check("khongduoi", 60_000, "JPG").ext()).isEqualTo("KHONGDUOI");
        assertThat(quality.check("a.png", 10 * 1024 * 1024, "PNG").ok()).isTrue();
    }

    @Test
    void fakeOcr_matchesMock() throws Exception {
        List<OcrResult.Field> cccd = ocr.extract(type(2), new byte[0], "image/jpeg", ctx(60)).fields();
        assertThat(cccd).extracting(OcrResult.Field::key).containsExactly("full_name", "national_id", "dob", "gender", "address");
        assertThat(cccd).extracting(OcrResult.Field::value)
                .containsExactly("NGUYỄN VĂN MINH", "001092012345", "15/05/1992", "Nữ", "12 Láng Hạ");
        assertThat(cccd).extracting(OcrResult.Field::confidence).containsExactly(97.0, 95.0, 93.0, 98.0, 81.0);
        assertThat(ocr.extract(type(3), null, null, ctx(1)).fields()).containsExactly(new OcrResult.Field("address", "Địa chỉ cư trú", "12 Láng Hạ", 84));
        assertThat(ocr.extract(type(4), null, null, ctx(1)).fields()).extracting(OcrResult.Field::value)
                .containsExactly("Công ty CP Công nghệ Sao Mai", "21.000.000");
        // seed % 13: 6 → 0%, 1 → -5%, 12 → +6%
        assertThat(ocr.extract(type(5), null, null, ctx(32)).fields().get(1).value()).isEqualTo("20.000.000");
        assertThat(ocr.extract(type(5), null, null, ctx(40)).fields().get(1).value()).isEqualTo("19.000.000");
        assertThat(ocr.extract(type(5), null, null, ctx(12)).fields().get(1).value()).isEqualTo("21.200.000");
        assertThat(ocr.extract(type(5), null, null, ctx(12)).fields().get(0).value()).isEqualTo("Vietcombank");
        assertThat(ocr.extract(type(6), null, null, ctx(1)).fields()).extracting(OcrResult.Field::value)
                .containsExactly("Công ty TNHH Thương mại Hòa Bình", "162.000.000");
        assertThat(ocr.extract(type(1), null, null, ctx(1)).fields()).isEmpty();
        // Không có lượt sàng lọc gốc: thu nhập mặc định 15 triệu, ngân hàng rỗng
        OcrContext none = new OcrContext("", "", "", "", "", null, null, null, null, 10_000_000, 6);
        assertThat(ocr.extract(type(5), null, null, none).fields()).extracting(OcrResult.Field::value).containsExactly("", "15.000.000");
        assertThat(ocr.extract(type(2), null, null, none).fields()).extracting(OcrResult.Field::value).containsExactly("", "", "", "Nam", "");
        assertThat(FakeOcrService.reverseDate("2026-10-23")).isEqualTo("23/10/2026");
    }
}
