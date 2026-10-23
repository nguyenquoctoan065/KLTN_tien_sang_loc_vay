package vn.kltn.tiensanglocvay.document;

import com.cloudinary.Cloudinary;
import com.cloudinary.Transformation;
import com.cloudinary.utils.ObjectUtils;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Lưu tệp giấy tờ trên Cloudinary (docs/be-huong-dan.md 5.3): {@code type=authenticated} (không công khai), thư mục
 * {@code app.storage.folder/app-<id>}, public_id {@code doc-<docId>-v<phiên bản>}; JPG/PNG/PDF đều có resource_type {@code image}.
 * <p>
 * Gói miễn phí không có URL giao tệp tự hết hạn (token theo thời gian chỉ từ gói Advanced), nên {@code previewUrl} trỏ về backend:
 * {@code /api/files/cloud?…&exp&sig} ký bằng {@link UrlSigner}, hạn {@code app.storage.signed-url-minutes}. {@link CloudFileController}
 * kiểm tra chữ ký rồi tải ảnh xem trước ({@code w_480,c_limit,q_auto,f_auto}; PDF lấy trang đầu {@code pg_1}) bằng URL ký của
 * Cloudinary và trả về; URL Cloudinary không lộ ra trình duyệt.
 */
@Service
@ConditionalOnProperty(name = "app.storage.provider", havingValue = "cloudinary")
public class CloudinaryStorageService implements StorageService {

    static final String AUTHENTICATED = "authenticated";

    private final Cloudinary cloudinary;
    private final UrlSigner signer;
    private final String contextPath;
    private final HttpClient http;

    public CloudinaryStorageService(Cloudinary cloudinary, UrlSigner signer, @Value("${server.servlet.context-path:}") String contextPath) {
        this.cloudinary = cloudinary;
        this.signer = signer;
        this.contextPath = contextPath == null ? "" : contextPath;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NORMAL).build();
    }

    /** Ảnh xem trước tải từ Cloudinary. */
    public record Preview(byte[] body, String contentType) {
    }

    @Override
    public StoredFile upload(byte[] data, String fileName, String folder) {
        int dot = fileName.lastIndexOf('.');
        String name = dot < 0 ? fileName : fileName.substring(0, dot);
        try {
            Map<?, ?> r = cloudinary.uploader().upload(data, ObjectUtils.asMap("type", AUTHENTICATED, "resource_type", "auto",
                    "folder", folder, "public_id", name, "overwrite", true));
            return new StoredFile((String) r.get("public_id"), (String) r.get("resource_type"), str(r.get("format")),
                    ((Number) r.get("version")).longValue(), ((Number) r.get("bytes")).longValue());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public String signedUrl(StoredFile f, Duration ttl) {
        long exp = signer.expiry(ttl);
        return contextPath + "/files/cloud?path=" + enc(f.publicId()) + "&rt=" + enc(f.resourceType()) + "&v=" + f.version()
                + "&fmt=" + enc(f.format()) + "&exp=" + exp + "&sig=" + signer.sign(payload(f.publicId(), f.resourceType(), f.version(), f.format()), exp);
    }

    @Override
    public void delete(StoredFile f) {
        try {
            cloudinary.uploader().destroy(f.publicId(), ObjectUtils.asMap("type", AUTHENTICATED, "resource_type",
                    f.resourceType() == null ? "image" : f.resourceType(), "invalidate", true));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Tệp ứng với URL ký của {@link #signedUrl}; rỗng nếu chữ ký sai hoặc hết hạn. */
    public Optional<StoredFile> verify(String path, String rt, long v, String fmt, long exp, String sig) {
        if (path == null || rt == null || fmt == null || !signer.verify(payload(path, rt, v, fmt), exp, sig)) {
            return Optional.empty();
        }
        return Optional.of(new StoredFile(path, rt, fmt, v, 0));
    }

    /**
     * Tải ảnh xem trước từ Cloudinary. {@code accept} là header Accept của trình duyệt (để f_auto chọn định dạng).
     * Rỗng nếu Cloudinary không còn tệp; lỗi mạng hoặc lỗi khác của Cloudinary ném ngoại lệ.
     */
    public Optional<Preview> fetchPreview(StoredFile f, String accept) {
        HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(previewDeliveryUrl(f))).timeout(Duration.ofSeconds(15)).GET();
        if (accept != null && !accept.isBlank()) {
            req.header("Accept", accept);
        }
        try {
            HttpResponse<byte[]> res = http.send(req.build(), HttpResponse.BodyHandlers.ofByteArray());
            if (res.statusCode() == 404) {
                return Optional.empty();
            }
            if (res.statusCode() != 200) {
                throw new IllegalStateException("Cloudinary trả mã " + res.statusCode());
            }
            return Optional.of(new Preview(res.body(), res.headers().firstValue("Content-Type").orElse("image/jpeg")));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    /** URL giao tệp đã ký của Cloudinary cho ảnh xem trước (chỉ dùng phía máy chủ). PDF: trang đầu, đuôi jpg. */
    @SuppressWarnings("rawtypes")
    String previewDeliveryUrl(StoredFile f) {
        boolean pdf = "pdf".equals(f.format());
        Transformation t = new Transformation().width(480).crop("limit").quality("auto").fetchFormat("auto");
        if (pdf) {
            t.page(1);
        }
        return cloudinary.url().secure(true).type(AUTHENTICATED).resourceType(f.resourceType()).signed(true).version(f.version())
                .transformation(t).format(pdf || f.format().isEmpty() ? "jpg" : f.format()).generate(f.publicId());
    }

    private static String payload(String publicId, String rt, long v, String fmt) {
        return "cloud|" + publicId + "|" + rt + "|" + v + "|" + fmt;
    }

    private static String enc(String s) {
        return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
    }

    private static String str(Object o) {
        return o == null ? "" : o.toString();
    }
}
