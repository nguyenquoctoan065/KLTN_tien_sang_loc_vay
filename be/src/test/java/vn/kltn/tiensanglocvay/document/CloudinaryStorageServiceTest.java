package vn.kltn.tiensanglocvay.document;

import static org.assertj.core.api.Assertions.assertThat;

import com.cloudinary.Cloudinary;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** CloudinaryStorageService không cần mạng: URL giao tệp đã ký của Cloudinary và URL proxy ký có hạn. */
class CloudinaryStorageServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-23T03:00:00Z");

    private static CloudinaryStorageService service(Instant now) {
        Cloudinary c = new Cloudinary("cloudinary://123456:bi-mat@demo");
        c.config.secure = true;
        return new CloudinaryStorageService(c, new UrlSigner("khoa-thu", Clock.fixed(now, ZoneOffset.UTC)), "/api");
    }

    private static final StoredFile JPG = new StoredFile("tien-sang-loc-vay/test/app-5/doc-12-v2", "image", "jpg", 1761188400L, 0);
    private static final StoredFile PDF = new StoredFile("tien-sang-loc-vay/test/app-5/doc-14-v1", "image", "pdf", 1761188401L, 0);

    private static Map<String, String> query(String url) {
        Map<String, String> q = new LinkedHashMap<>();
        for (String p : URI.create(url).getRawQuery().split("&")) {
            String[] kv = p.split("=", 2);
            q.put(kv[0], java.net.URLDecoder.decode(kv[1], java.nio.charset.StandardCharsets.UTF_8));
        }
        return q;
    }

    @Test
    void deliveryUrl_isSignedAuthenticatedWithPreviewTransformation() {
        CloudinaryStorageService s = service(NOW);
        String img = s.previewDeliveryUrl(JPG);
        assertThat(img).startsWith("https://res.cloudinary.com/demo/image/authenticated/s--")
                .contains("/c_limit,f_auto,q_auto,w_480/v1761188400/tien-sang-loc-vay/test/app-5/doc-12-v2.jpg");
        // PDF: ảnh trang đầu, đuôi jpg
        assertThat(s.previewDeliveryUrl(PDF)).contains("/c_limit,f_auto,pg_1,q_auto,w_480/v1761188401/")
                .contains("/doc-14-v1.jpg");
    }

    @Test
    void proxyUrl_signedWithExpiry() {
        CloudinaryStorageService s = service(NOW);
        String url = s.signedUrl(JPG, Duration.ofMinutes(10));
        assertThat(url).startsWith("/api/files/cloud?path=");
        Map<String, String> q = query(url);
        assertThat(q).containsEntry("path", JPG.publicId()).containsEntry("rt", "image").containsEntry("v", "1761188400")
                .containsEntry("fmt", "jpg").containsEntry("exp", String.valueOf(NOW.getEpochSecond() + 600));
        long exp = Long.parseLong(q.get("exp"));
        assertThat(s.verify(q.get("path"), q.get("rt"), 1761188400L, q.get("fmt"), exp, q.get("sig"))).contains(JPG);
        // Sửa bất kỳ tham số nào → chữ ký sai
        assertThat(s.verify(q.get("path") + "x", "image", 1761188400L, "jpg", exp, q.get("sig"))).isEmpty();
        assertThat(s.verify(q.get("path"), "raw", 1761188400L, "jpg", exp, q.get("sig"))).isEmpty();
        assertThat(s.verify(q.get("path"), "image", 1L, "jpg", exp, q.get("sig"))).isEmpty();
        assertThat(s.verify(q.get("path"), "image", 1761188400L, "png", exp, q.get("sig"))).isEmpty();
        assertThat(s.verify(q.get("path"), "image", 1761188400L, "jpg", exp + 1, q.get("sig"))).isEmpty();
        assertThat(s.verify(null, "image", 1761188400L, "jpg", exp, q.get("sig"))).isEmpty();
        // Hết hạn sau 10 phút
        CloudinaryStorageService later = service(NOW.plusSeconds(601));
        assertThat(later.verify(q.get("path"), "image", 1761188400L, "jpg", exp, q.get("sig"))).isEmpty();
    }

    @Test
    void fileUrl_originalKindIsPartOfSignature() {
        CloudinaryStorageService s = service(NOW);
        // URL giao tệp gốc: không biến đổi, giữ định dạng gốc (PDF vẫn là .pdf)
        assertThat(s.originalDeliveryUrl(PDF)).startsWith("https://res.cloudinary.com/demo/image/authenticated/s--")
                .contains("/s--").contains("--/v1761188401/tien-sang-loc-vay/test/app-5/doc-14-v1.pdf").doesNotContain("w_480");
        assertThat(CloudinaryStorageService.contentType("pdf")).isEqualTo("application/pdf");
        assertThat(CloudinaryStorageService.contentType("PNG")).isEqualTo("image/png");

        Map<String, String> q = query(s.fileUrl(PDF, Duration.ofMinutes(10)));
        assertThat(q).containsEntry("kind", "original").containsEntry("fmt", "pdf");
        long exp = Long.parseLong(q.get("exp"));
        assertThat(s.verify(q.get("path"), "image", 1761188401L, "pdf", true, exp, q.get("sig"))).contains(PDF);
        // Chữ ký của tệp gốc không dùng được cho ảnh xem trước và ngược lại
        assertThat(s.verify(q.get("path"), "image", 1761188401L, "pdf", false, exp, q.get("sig"))).isEmpty();
        Map<String, String> p = query(s.signedUrl(PDF, Duration.ofMinutes(10)));
        assertThat(s.verify(p.get("path"), "image", 1761188401L, "pdf", true, exp, p.get("sig"))).isEmpty();
    }
}
