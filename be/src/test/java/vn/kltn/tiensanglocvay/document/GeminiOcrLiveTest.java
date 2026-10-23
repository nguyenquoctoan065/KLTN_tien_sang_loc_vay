package vn.kltn.tiensanglocvay.document;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import vn.kltn.tiensanglocvay.catalog.DocumentType;

/**
 * Gọi Gemini API thật với 5 tệp mẫu trong tests/e2e/files (ảnh giả, không phải giấy tờ thật – xem be-huong-dan 5.4 về dữ liệu cá nhân).
 * Chỉ chạy khi shell có biến môi trường GEMINI_API_KEY (không đọc be/.env). Chỉ kiểm tra cấu trúc kết quả, không kiểm tra giá trị.
 * Model: biến môi trường GEMINI_MODEL, mặc định như app.ocr.model.
 */
@EnabledIfEnvironmentVariable(named = "GEMINI_API_KEY", matches = ".+")
class GeminiOcrLiveTest {

    private static final Path FILES = Path.of("..", "tests", "e2e", "files");
    private static Client client;

    @BeforeAll
    static void setUp() {
        client = Client.builder().apiKey(System.getenv("GEMINI_API_KEY")).httpOptions(HttpOptions.builder().timeout(30_000).build()).build();
    }

    @AfterAll
    static void tearDown() {
        client.close();
    }

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

    @ParameterizedTest
    @CsvSource({
            "cccd.jpg, 2, Căn cước công dân, image/jpeg",
            "cutru.jpg, 3, Xác nhận cư trú, image/jpeg",
            "hdld.jpg, 4, Hợp đồng lao động hoặc xác nhận nhân sự, image/jpeg",
            "saoke.pdf, 5, Sao kê tài khoản nhận lương 03 tháng, application/pdf",
            "hoadon.jpg, 6, 'Hóa đơn, hợp đồng mua bán hoặc hợp đồng 3 bên', image/jpeg"})
    void readsSampleFile(String file, int typeId, String typeName, String mime) throws Exception {
        String model = System.getenv().getOrDefault("GEMINI_MODEL", "gemini-3.5-flash-lite");
        GeminiOcrService s = new GeminiOcrService(client, model);
        OcrResult r = s.extract(type(typeId, typeName), Files.readAllBytes(FILES.resolve(file)), mime, null);
        assertThat(r).isNotNull();
        assertThat(r.fields()).extracting(OcrResult.Field::key)
                .containsExactlyElementsOf(DocumentFields.of(typeId).stream().map(DocumentFields.Def::key).toList());
        for (OcrResult.Field f : r.fields()) {
            assertThat(f.value()).isNotNull();
            assertThat(f.confidence()).isBetween(0.0, 100.0);
            assertThat(f.label()).isNotBlank();
        }
        if (!r.readable()) {
            assertThat(r.issue()).isNotBlank();
        }
    }
}
