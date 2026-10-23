package vn.kltn.tiensanglocvay.document;

import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.HttpRetryOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Bean {@link Client} của Gemini API khi {@code app.ocr.provider=gemini}. Khóa {@code GEMINI_API_KEY}: biến môi trường hoặc be/.env
 * (profile dev import). Timeout {@code app.ocr.timeout-seconds}; SDK không tự thử lại ({@link GeminiOcrService} tự thử lại 429/503).
 */
@Configuration
@ConditionalOnProperty(name = "app.ocr.provider", havingValue = "gemini")
public class GeminiConfig {

    @Bean(destroyMethod = "close")
    Client geminiClient(Environment env, @Value("${app.ocr.timeout-seconds:30}") int timeoutSeconds) {
        String key = env.getProperty("GEMINI_API_KEY");
        if (key == null || key.isBlank()) {
            throw new IllegalStateException("Thiếu GEMINI_API_KEY (đặt trong be/.env hoặc biến môi trường), "
                    + "hoặc chạy profile local để dùng máy đọc giả lập.");
        }
        return Client.builder()
                .apiKey(key.trim())
                .httpOptions(HttpOptions.builder()
                        .timeout(timeoutSeconds * 1000)
                        .retryOptions(HttpRetryOptions.builder().attempts(1).build())
                        .build())
                .build();
    }
}
