package vn.kltn.tiensanglocvay.document;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Lưu tệp vào thư mục cục bộ {@code app.storage.local-dir} (test, phát triển khi chưa có khóa Cloudinary).
 * URL xem tệp mô phỏng URL ký của Cloudinary: {@code /api/files/local?path=…&exp=…&sig=…}, chữ ký HMAC-SHA256 trên path và hạn.
 * Khóa lấy từ {@code app.storage.local-secret}; để trống thì sinh ngẫu nhiên mỗi lần khởi động (URL cũ hết hiệu lực khi khởi động lại).
 */
@Service
@ConditionalOnProperty(name = "app.storage.provider", havingValue = "local")
public class LocalStorageService implements StorageService {

    /** Mỗi đoạn chỉ gồm chữ, số, gạch, gạch dưới, dấu chấm và không bắt đầu bằng dấu chấm (chặn "." và ".."). */
    private static final Pattern SAFE_PATH = Pattern.compile("(?:[A-Za-z0-9_-][A-Za-z0-9._-]*/)*[A-Za-z0-9_-][A-Za-z0-9._-]*");

    private final Path root;
    private final byte[] secret;
    private final String contextPath;
    private final Clock clock;

    public LocalStorageService(@Value("${app.storage.local-dir:./data/uploads}") String dir,
                               @Value("${app.storage.local-secret:}") String secret,
                               @Value("${server.servlet.context-path:}") String contextPath, Clock clock) {
        this.root = Path.of(dir).toAbsolutePath().normalize();
        if (secret == null || secret.isBlank()) {
            this.secret = new byte[32];
            new SecureRandom().nextBytes(this.secret);
        } else {
            this.secret = secret.getBytes(StandardCharsets.UTF_8);
        }
        this.contextPath = contextPath == null ? "" : contextPath;
        this.clock = clock;
    }

    @Override
    public StoredFile upload(byte[] data, String fileName, String folder) {
        int dot = fileName.lastIndexOf('.');
        String format = dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
        String publicId = folder + "/" + (dot < 0 ? fileName : fileName.substring(0, dot));
        Path target = resolve(relative(publicId, format))
                .orElseThrow(() -> new IllegalArgumentException("Đường dẫn lưu tệp không hợp lệ: " + publicId));
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, data);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        String type = switch (format) {
            case "jpg", "jpeg", "png" -> StoredFile.IMAGE;
            default -> StoredFile.RAW;
        };
        return new StoredFile(publicId, type, format, clock.millis() / 1000, data.length);
    }

    @Override
    public String signedUrl(StoredFile f, Duration ttl) {
        String path = relative(f.publicId(), f.format());
        long exp = clock.millis() / 1000 + ttl.toSeconds();
        return contextPath + "/files/local?path=" + URLEncoder.encode(path, StandardCharsets.UTF_8) + "&exp=" + exp
                + "&sig=" + sign(path, exp);
    }

    @Override
    public void delete(StoredFile f) {
        resolve(relative(f.publicId(), f.format())).ifPresent(p -> {
            try {
                Files.deleteIfExists(p);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    /** Tệp ứng với URL ký; rỗng nếu chữ ký sai, hết hạn, đường dẫn không hợp lệ hoặc tệp không còn. */
    public Optional<Path> open(String path, long exp, String sig) {
        if (path == null || sig == null || exp < clock.millis() / 1000) {
            return Optional.empty();
        }
        byte[] expected = sign(path, exp).getBytes(StandardCharsets.US_ASCII);
        if (!MessageDigest.isEqual(expected, sig.getBytes(StandardCharsets.US_ASCII))) {
            return Optional.empty();
        }
        return resolve(path).filter(Files::isRegularFile);
    }

    private static String relative(String publicId, String format) {
        return format.isEmpty() ? publicId : publicId + "." + format;
    }

    private Optional<Path> resolve(String relative) {
        if (relative == null || !SAFE_PATH.matcher(relative).matches()) {
            return Optional.empty();
        }
        Path p = root.resolve(relative).normalize();
        return p.startsWith(root) ? Optional.of(p) : Optional.empty();
    }

    private String sign(String path, long exp) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            byte[] h = mac.doFinal((path + "|" + exp).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(h);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
