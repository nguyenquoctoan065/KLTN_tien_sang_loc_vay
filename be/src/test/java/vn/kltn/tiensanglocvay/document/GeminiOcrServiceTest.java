package vn.kltn.tiensanglocvay.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.genai.errors.ApiException;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Schema;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import vn.kltn.tiensanglocvay.catalog.DocumentType;

/** GeminiOcrService không cần mạng: schema/prompt theo loại giấy tờ, chuẩn hóa kết quả, thử lại 429/503. */
class GeminiOcrServiceTest {

    private static DocumentType type(int id, String name) throws Exception {
        var ctor = DocumentType.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        DocumentType t = ctor.newInstance();
        for (var e : new Object[][] {{"id", id}, {"name", name}}) {
            Field f = DocumentType.class.getDeclaredField((String) e[0]);
            f.setAccessible(true);
            f.set(t, e[1]);
        }
        return t;
    }

    private static GenerateContentResponse response(String json) {
        return GenerateContentResponse.fromJson("{\"candidates\":[{\"content\":{\"role\":\"model\",\"parts\":[{\"text\":"
                + com.fasterxml.jackson.databind.node.TextNode.valueOf(json) + "}]}}],"
                + "\"usageMetadata\":{\"promptTokenCount\":1200,\"candidatesTokenCount\":80}}");
    }

    private static final String OK_JSON = "{\"isReadable\":true,\"documentMatchesType\":true,\"issue\":null,\"fields\":["
            + "{\"key\":\"salary_bank\",\"value\":\"Vietcombank\",\"confidence\":91.6},"
            + "{\"key\":\"avg_income\",\"value\":\"18800000\",\"confidence\":88}]}";

    @Test
    void schemaAndPrompt_followDocumentFields() {
        Schema s = GeminiOcrService.schema(DocumentFields.of(4));
        Schema item = s.properties().orElseThrow().get("fields").items().orElseThrow();
        assertThat(item.properties().orElseThrow().get("key").enum_().orElseThrow()).containsExactly("employer", "salary");
        assertThat(s.required().orElseThrow()).containsExactly("isReadable", "documentMatchesType", "isSpecimen", "issue", "fields");
        assertThat(s.properties().orElseThrow()).containsKey("isSpecimen");
        String p = GeminiOcrService.prompt("Hợp đồng lao động", DocumentFields.of(4));
        assertThat(p).contains("«Hợp đồng lao động»", "- employer (Đơn vị công tác)", "- salary (Mức lương theo hợp đồng)",
                "chỉ gồm chữ số", "value = null");
        // Giấy tờ mẫu báo qua isSpecimen, không dùng isReadable=false
        assertThat(p).contains("isSpecimen", "\"MẪU\"", "không dùng isReadable = false");
        assertThat(GeminiOcrService.prompt("Sao kê", DocumentFields.of(5))).contains("03 tháng", "avg_income", "salary_bank");
        assertThat(GeminiOcrService.prompt("CCCD", DocumentFields.of(2))).contains("dd/MM/yyyy", "in hoa", "\"Nam\" hoặc \"Nữ\"");
    }

    @Test
    void parse_normalizesLikeMock() {
        OcrResult r = GeminiOcrService.parse("{\"isReadable\":true,\"documentMatchesType\":true,\"issue\":\"\",\"fields\":["
                + "{\"key\":\"dob\",\"value\":\"5/3/1992\",\"confidence\":0.93},"
                + "{\"key\":\"full_name\",\"value\":\"  nguyễn  văn minh \",\"confidence\":97},"
                + "{\"key\":\"national_id\",\"value\":\"001 092 012345\",\"confidence\":150},"
                + "{\"key\":\"gender\",\"value\":\"NỮ\",\"confidence\":98},"
                + "{\"key\":\"la\",\"value\":\"x\",\"confidence\":50}]}", 2);
        assertThat(r.readable()).isTrue();
        assertThat(r.issue()).isNull();
        // Đủ trường, đúng thứ tự danh mục; khóa lạ bỏ; thiếu address → rỗng, độ tin cậy 0
        assertThat(r.fields()).extracting(OcrResult.Field::key).containsExactly("full_name", "national_id", "dob", "gender", "address");
        assertThat(r.fields()).extracting(OcrResult.Field::value).containsExactly("NGUYỄN VĂN MINH", "001092012345", "05/03/1992", "Nữ", "");
        assertThat(r.fields()).extracting(OcrResult.Field::confidence).containsExactly(97.0, 100.0, 93.0, 98.0, 0.0);
        assertThat(r.fields().get(0).label()).isEqualTo("Họ và tên");

        OcrResult money = GeminiOcrService.parse("{\"isReadable\":true,\"documentMatchesType\":true,\"issue\":null,\"fields\":["
                + "{\"key\":\"beneficiary\",\"value\":null,\"confidence\":60},"
                + "{\"key\":\"invoice_amount\",\"value\":\"162,000,000.00 VND\",\"confidence\":89}]}", 6);
        assertThat(money.fields()).extracting(OcrResult.Field::value).containsExactly("", "162.000.000");
        assertThat(money.fields()).extracting(OcrResult.Field::confidence).containsExactly(0.0, 89.0);
        assertThat(GeminiOcrService.normalize(DocumentFields.Kind.DATE, "1992-05-15")).isEqualTo("15/05/1992");
        assertThat(GeminiOcrService.normalize(DocumentFields.Kind.DATE, "31/02/1992")).isEqualTo("31/02/1992");
        assertThat(GeminiOcrService.normalize(DocumentFields.Kind.MONEY, "18.800.000")).isEqualTo("18.800.000");
        assertThat(GeminiOcrService.normalize(DocumentFields.Kind.GENDER, "Male")).isEqualTo("Nam");
    }

    @Test
    void parse_unreadableAndWrongType() {
        OcrResult r = GeminiOcrService.parse("{\"isReadable\":false,\"documentMatchesType\":true,"
                + "\"issue\":\"Ảnh bị lóa, vui lòng chụp lại.\",\"fields\":[]}", 3);
        assertThat(r.readable()).isFalse();
        assertThat(r.issue()).isEqualTo("Ảnh bị lóa, vui lòng chụp lại.");
        assertThat(GeminiOcrService.parse("{\"isReadable\":true,\"documentMatchesType\":false,\"issue\":null,\"fields\":[]}", 3)
                .documentMatchesType()).isFalse();
    }

    @Test
    void parse_specimen() {
        OcrResult r = GeminiOcrService.parse("{\"isReadable\":true,\"documentMatchesType\":true,\"isSpecimen\":true,\"issue\":null,"
                + "\"fields\":[{\"key\":\"address\",\"value\":\"12 Láng Hạ\",\"confidence\":95}]}", 3);
        assertThat(r.specimen()).isTrue();
        assertThat(r.readable()).isTrue();
        assertThat(r.fields()).extracting(OcrResult.Field::value).containsExactly("12 Láng Hạ");
        // Thiếu isSpecimen (model bỏ sót) → giấy tờ thật, không từ chối nhầm
        assertThat(GeminiOcrService.parse("{\"isReadable\":true,\"documentMatchesType\":true,\"issue\":null,\"fields\":[]}", 3).specimen())
                .isFalse();
    }

    @Test
    void parse_badJsonThrows() {
        assertThatThrownBy(() -> GeminiOcrService.parse("không phải json", 2)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> GeminiOcrService.parse("{\"isReadable\":true,\"fields\":[]}", 2)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> GeminiOcrService.parse(null, 2)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void extract_retries429And503ThenSucceeds() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        List<Long> slept = new ArrayList<>();
        GeminiOcrService s = new GeminiOcrService((m, c, cfg) -> {
            assertThat(m).isEqualTo("gemini-test");
            assertThat(cfg.temperature()).contains(0f);
            assertThat(cfg.responseMimeType()).contains("application/json");
            return switch (calls.incrementAndGet()) {
                case 1 -> throw new ApiException(429, "RESOURCE_EXHAUSTED", "quota");
                case 2 -> throw new ApiException(503, "UNAVAILABLE", "busy");
                default -> response(OK_JSON);
            };
        }, "gemini-test", slept::add);
        OcrResult r = s.extract(type(5, "Sao kê"), new byte[] {1}, "application/pdf", null);
        assertThat(calls).hasValue(3);
        assertThat(slept).containsExactly(1000L, 3000L);
        assertThat(r.fields()).extracting(OcrResult.Field::value).containsExactly("Vietcombank", "18.800.000");
        assertThat(r.fields()).extracting(OcrResult.Field::confidence).containsExactly(92.0, 88.0);
    }

    @Test
    void extract_givesUpAfterTwoRetriesAndDoesNotRetry400() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        GeminiOcrService busy = new GeminiOcrService((m, c, cfg) -> {
            calls.incrementAndGet();
            throw new ApiException(503, "UNAVAILABLE", "busy");
        }, "m", ms -> { });
        assertThatThrownBy(() -> busy.extract(type(5, "Sao kê"), new byte[] {1}, "image/jpeg", null)).isInstanceOf(ApiException.class);
        assertThat(calls).hasValue(3);

        calls.set(0);
        GeminiOcrService bad = new GeminiOcrService((m, c, cfg) -> {
            calls.incrementAndGet();
            throw new ApiException(400, "INVALID_ARGUMENT", "bad");
        }, "m", ms -> { });
        assertThatThrownBy(() -> bad.extract(type(5, "Sao kê"), new byte[] {1}, "image/jpeg", null)).isInstanceOf(ApiException.class);
        assertThat(calls).hasValue(1);
    }

    @Test
    void extract_typeWithoutFieldsSkipsCall() throws Exception {
        GeminiOcrService s = new GeminiOcrService((m, c, cfg) -> {
            throw new AssertionError("không được gọi");
        }, "m", ms -> { });
        assertThat(s.extract(type(1, "Giấy đề nghị"), new byte[] {1}, "application/pdf", null).fields()).isEmpty();
    }

    @Test
    void blankFields_forManualEntry() {
        assertThat(DocumentFields.blank(4)).containsExactly(new OcrResult.Field("employer", "Đơn vị công tác", "", 0),
                new OcrResult.Field("salary", "Mức lương theo hợp đồng", "", 0));
        assertThat(DocumentFields.blank(1)).isEmpty();
    }
}
