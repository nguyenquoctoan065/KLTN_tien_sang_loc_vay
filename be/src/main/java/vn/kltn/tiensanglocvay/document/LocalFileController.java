package vn.kltn.tiensanglocvay.document;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.kltn.tiensanglocvay.common.ApiException;

/**
 * Phát tệp của {@link LocalStorageService} theo URL ký (AppDocument.previewUrl). Không dùng Bearer vì thẻ img không gửi được;
 * quyền được kiểm tra khi cấp URL (chỉ người xem được hồ sơ mới nhận previewUrl), ở đây kiểm tra chữ ký và hạn.
 * Chỉ có khi {@code app.storage.provider=local}; với Cloudinary, frontend tải thẳng từ URL ký của Cloudinary.
 */
@RestController
@ConditionalOnProperty(name = "app.storage.provider", havingValue = "local")
public class LocalFileController {

    private final LocalStorageService storage;

    public LocalFileController(LocalStorageService storage) {
        this.storage = storage;
    }

    @GetMapping("/files/local")
    ResponseEntity<byte[]> get(@RequestParam(required = false) String path, @RequestParam(required = false) Long exp,
                               @RequestParam(required = false) String sig) throws IOException {
        Path file = storage.open(path, exp == null ? 0 : exp, sig)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy."));
        return ResponseEntity.ok()
                .contentType(mediaType(file))
                .cacheControl(CacheControl.noStore().cachePrivate())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .body(Files.readAllBytes(file));
    }

    private static MediaType mediaType(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) {
            return MediaType.IMAGE_JPEG;
        }
        if (name.endsWith(".png")) {
            return MediaType.IMAGE_PNG;
        }
        if (name.endsWith(".pdf")) {
            return MediaType.APPLICATION_PDF;
        }
        return MediaType.APPLICATION_OCTET_STREAM;
    }
}
