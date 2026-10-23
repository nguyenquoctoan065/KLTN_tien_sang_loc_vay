package vn.kltn.tiensanglocvay.document;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Chữ ký HMAC-SHA256 có hạn cho URL xem tệp (AppDocument.previewUrl) của {@code /files/local} và {@code /files/cloud}.
 * Thẻ img không gửi được Bearer nên quyền được kiểm tra lúc cấp URL; khi phát tệp chỉ kiểm tra chữ ký và hạn.
 * Khóa {@code app.storage.url-secret} (tên cũ {@code app.storage.local-secret}); để trống thì sinh ngẫu nhiên mỗi lần khởi động
 * (URL cũ hết hiệu lực khi khởi động lại).
 */
@Component
public class UrlSigner {

    private final byte[] secret;
    private final Clock clock;

    public UrlSigner(@Value("${app.storage.url-secret:${app.storage.local-secret:}}") String secret, Clock clock) {
        if (secret == null || secret.isBlank()) {
            this.secret = new byte[32];
            new SecureRandom().nextBytes(this.secret);
        } else {
            this.secret = secret.getBytes(StandardCharsets.UTF_8);
        }
        this.clock = clock;
    }

    /** Hạn (epoch giây) sau {@code ttl} kể từ bây giờ. */
    public long expiry(Duration ttl) {
        return clock.millis() / 1000 + ttl.toSeconds();
    }

    public String sign(String payload, long exp) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            byte[] h = mac.doFinal((payload + "|" + exp).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(h);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Chữ ký đúng và chưa hết hạn. */
    public boolean verify(String payload, long exp, String sig) {
        if (payload == null || sig == null || exp < clock.millis() / 1000) {
            return false;
        }
        byte[] expected = sign(payload, exp).getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(expected, sig.getBytes(StandardCharsets.US_ASCII));
    }
}
