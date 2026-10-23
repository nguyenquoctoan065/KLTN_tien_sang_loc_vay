package vn.kltn.tiensanglocvay.document;

import com.cloudinary.Cloudinary;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Bean {@link Cloudinary} khi {@code app.storage.provider=cloudinary}. Khóa lấy từ {@code CLOUDINARY_URL}
 * ({@code cloudinary://<api_key>:<api_secret>@<cloud_name>}): biến môi trường hoặc be/.env (profile dev import). Không in giá trị.
 */
@Configuration
@ConditionalOnProperty(name = "app.storage.provider", havingValue = "cloudinary")
public class CloudinaryConfig {

    @Bean
    Cloudinary cloudinary(Environment env) {
        String url = env.getProperty("CLOUDINARY_URL");
        if (url == null || url.isBlank()) {
            throw new IllegalStateException("Thiếu CLOUDINARY_URL (đặt trong be/.env hoặc biến môi trường), "
                    + "hoặc chạy profile local để lưu tệp cục bộ.");
        }
        Cloudinary c = new Cloudinary(url.trim());
        c.config.secure = true;
        return c;
    }
}
