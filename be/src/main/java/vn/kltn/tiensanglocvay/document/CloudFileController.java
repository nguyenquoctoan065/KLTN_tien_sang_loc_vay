package vn.kltn.tiensanglocvay.document;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.kltn.tiensanglocvay.common.ApiException;

/**
 * Phát ảnh xem trước (previewUrl) hoặc tệp gốc (fileUrl, {@code kind=original}) của {@link CloudinaryStorageService} theo URL ký có hạn (AppDocument.previewUrl). Giống {@link LocalFileController}:
 * không dùng Bearer, quyền kiểm tra lúc cấp URL, ở đây kiểm tra chữ ký và hạn rồi tải ảnh từ Cloudinary.
 * Chỉ có khi {@code app.storage.provider=cloudinary}.
 */
@RestController
@ConditionalOnProperty(name = "app.storage.provider", havingValue = "cloudinary")
public class CloudFileController {

    private static final Logger log = LoggerFactory.getLogger(CloudFileController.class);

    private final CloudinaryStorageService storage;

    public CloudFileController(CloudinaryStorageService storage) {
        this.storage = storage;
    }

    @GetMapping("/files/cloud")
    ResponseEntity<byte[]> get(@RequestParam(required = false) String path, @RequestParam(required = false) String rt,
                               @RequestParam(required = false) Long v, @RequestParam(required = false) String fmt,
                               @RequestParam(required = false) String kind,
                               @RequestParam(required = false) Long exp, @RequestParam(required = false) String sig,
                               @RequestHeader(value = HttpHeaders.ACCEPT, required = false) String accept) {
        if (kind != null && !CloudinaryStorageService.KIND_ORIGINAL.equals(kind)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy.");
        }
        boolean original = kind != null;
        StoredFile f = storage.verify(path, rt, v == null ? 0 : v, fmt, original, exp == null ? 0 : exp, sig)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy."));
        CloudinaryStorageService.Preview p;
        try {
            p = (original ? storage.fetchOriginal(f) : storage.fetchPreview(f, accept))
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy."));
        } catch (ApiException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("Không tải được tệp {} từ Cloudinary: {}", f.publicId(), e.toString());
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Không tải được tệp từ kho lưu trữ, vui lòng thử lại sau.");
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(p.contentType()))
                .cacheControl(CacheControl.noStore().cachePrivate())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, original ? attachmentName(f) : "inline")
                .body(p.body());
    }

    /** {@code inline; filename="doc-12-v2.pdf"}: mở trong tab mới, lưu về máy có tên tệp. */
    private static String attachmentName(StoredFile f) {
        String name = f.publicId().substring(f.publicId().lastIndexOf('/') + 1) + (f.format().isEmpty() ? "" : "." + f.format());
        return ContentDisposition.inline().filename(name).build().toString();
    }
}
