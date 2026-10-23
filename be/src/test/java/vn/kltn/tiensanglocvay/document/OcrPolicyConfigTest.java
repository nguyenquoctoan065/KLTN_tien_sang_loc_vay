package vn.kltn.tiensanglocvay.document;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/** {@link OcrPolicy} đọc đúng cấu hình {@code app.ocr.allow-specimen} từ môi trường Spring (context nhỏ, không cần cơ sở dữ liệu). */
class OcrPolicyConfigTest {

    private static final OcrResult SPECIMEN = new OcrResult(true, true, true, null, List.of());

    @SpringBootTest(classes = OcrPolicy.class)
    @TestPropertySource(properties = "app.ocr.allow-specimen=true")
    @Nested
    class Allowed {

        @Autowired
        OcrPolicy policy;

        @Test
        void specimenAccepted() {
            assertThat(policy.judge(SPECIMEN, "CCCD")).isEqualTo(new OcrPolicy.Verdict(true, "OK", null, true));
        }
    }

    @SpringBootTest(classes = OcrPolicy.class)
    @Nested
    class DefaultValue {

        @Autowired
        OcrPolicy policy;

        @Test
        void specimenRejectedWhenNotConfigured() {
            assertThat(policy.judge(SPECIMEN, "CCCD").qualityStatus()).isEqualTo("SPECIMEN");
            assertThat(policy.judge(SPECIMEN, "CCCD").accepted()).isFalse();
        }
    }
}
