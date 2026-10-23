package vn.kltn.tiensanglocvay.document;

import static org.assertj.core.api.Assertions.assertThat;

import com.cloudinary.Cloudinary;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.UUID;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Tải tệp mẫu lên Cloudinary thật (thư mục tien-sang-loc-vay/test/live-…), lấy ảnh xem trước qua URL ký rồi xóa.
 * Chỉ chạy khi shell có biến môi trường CLOUDINARY_URL (không đọc be/.env). PDF cần bật "Allow delivery of PDF and ZIP files".
 */
@EnabledIfEnvironmentVariable(named = "CLOUDINARY_URL", matches = ".+")
class CloudinaryLiveTest {

    @ParameterizedTest
    @CsvSource({"cccd.jpg, jpg", "saoke.pdf, pdf"})
    void uploadPreviewDelete(String file, String format) throws Exception {
        Cloudinary c = new Cloudinary(System.getenv("CLOUDINARY_URL").trim());
        c.config.secure = true;
        CloudinaryStorageService s = new CloudinaryStorageService(c, new UrlSigner("", Clock.systemUTC()), "/api");
        byte[] data = Files.readAllBytes(Path.of("..", "tests", "e2e", "files", file));
        StoredFile f = s.upload(data, "doc-1-v1." + format, "tien-sang-loc-vay/test/live-" + UUID.randomUUID());
        try {
            assertThat(f.publicId()).startsWith("tien-sang-loc-vay/test/live-").endsWith("/doc-1-v1");
            assertThat(f.resourceType()).isEqualTo("image");
            assertThat(f.format()).isEqualTo(format);
            assertThat(f.version()).isPositive();
            assertThat(f.bytes()).isEqualTo(data.length);
            CloudinaryStorageService.Preview p = s.fetchPreview(f, "image/webp,image/*").orElseThrow();
            assertThat(p.contentType()).startsWith("image/");
            assertThat(p.body()).isNotEmpty();
            // Tệp authenticated: URL không ký thì không xem được
            String unsigned = c.url().secure(true).type("authenticated").resourceType("image").version(f.version()).format(format)
                    .generate(f.publicId());
            int code = java.net.http.HttpClient.newHttpClient().send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(unsigned)).build(),
                    java.net.http.HttpResponse.BodyHandlers.discarding()).statusCode();
            assertThat(code).isIn(401, 403, 404);
        } finally {
            s.delete(f);
        }
    }
}
