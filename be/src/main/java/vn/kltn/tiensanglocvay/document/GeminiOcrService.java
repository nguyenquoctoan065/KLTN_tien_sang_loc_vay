package vn.kltn.tiensanglocvay.document;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.Client;
import com.google.genai.errors.ApiException;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.GenerateContentResponseUsageMetadata;
import com.google.genai.types.Part;
import com.google.genai.types.Schema;
import com.google.genai.types.Type;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import vn.kltn.tiensanglocvay.catalog.DocumentType;
import vn.kltn.tiensanglocvay.engine.JsFormat;

/**
 * Đọc giấy tờ bằng Gemini API (docs/be-huong-dan.md 5.4): một lần {@code generateContent} với tệp + prompt tiếng Việt, trả JSON theo
 * {@code responseSchema} sinh từ {@link DocumentFields}, {@code temperature 0}. Model đọc từ {@code app.ocr.model}.
 * Thử lại 2 lần khi 429/503 (chờ 1 s rồi 3 s). Lỗi khác hoặc JSON sai schema ném ngoại lệ (người gọi lưu UPLOADED để khách nhập tay).
 * Chỉ ghi log model, thời gian, số token, mã lỗi; không ghi nội dung tệp hay giá trị trích xuất.
 */
@Service
@ConditionalOnProperty(name = "app.ocr.provider", havingValue = "gemini")
public class GeminiOcrService implements OcrService {

    private static final Logger log = LoggerFactory.getLogger(GeminiOcrService.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    static final long[] BACKOFF_MS = {1000, 3000};
    static final int MAX_ISSUE = 200;

    /** Một lần gọi API (tách ra để test thử lại mà không cần mạng). */
    interface Caller {
        GenerateContentResponse call(String model, Content content, GenerateContentConfig config);
    }

    interface Sleeper {
        void sleep(long ms) throws InterruptedException;
    }

    /** Gợi ý thêm cho từng trường (ngoài định dạng theo kiểu). */
    private static final Map<String, String> HINTS = Map.of(
            "address", "địa chỉ đầy đủ như ghi trên giấy tờ",
            "employer", "tên công ty hoặc đơn vị sử dụng lao động",
            "salary", "mức lương hằng tháng ghi trong hợp đồng",
            "salary_bank", "tên ngân hàng phát hành sao kê",
            "avg_income", "tự tính: trung bình mỗi tháng của các khoản ghi có có nội dung lương (LUONG, TRA LUONG, SALARY…) "
                    + "trong 03 tháng gần nhất của sao kê; không thấy giao dịch lương thì để null",
            "beneficiary", "bên bán hoặc bên nhận tiền",
            "invoice_amount", "tổng giá trị thanh toán (đã gồm thuế)");

    private final Caller caller;
    private final String model;
    private final Sleeper sleeper;

    @Autowired
    public GeminiOcrService(Client client, @Value("${app.ocr.model:gemini-3.5-flash-lite}") String model) {
        this((m, c, cfg) -> client.models.generateContent(m, c, cfg), model, Thread::sleep);
    }

    GeminiOcrService(Caller caller, String model, Sleeper sleeper) {
        this.caller = caller;
        this.model = model;
        this.sleeper = sleeper;
    }

    @Override
    public OcrResult extract(DocumentType type, byte[] data, String mimeType, OcrContext ctx) {
        List<DocumentFields.Def> defs = DocumentFields.of(type.getId());
        if (defs.isEmpty()) {
            return OcrResult.of(List.of());
        }
        Content content = Content.fromParts(Part.fromBytes(data, mimeType), Part.fromText(prompt(type.getName(), defs)));
        GenerateContentConfig config = GenerateContentConfig.builder()
                .responseMimeType("application/json")
                .responseSchema(schema(defs))
                .temperature(0f)
                .build();
        long t0 = System.nanoTime();
        GenerateContentResponse res = callWithRetry(content, config, type.getId());
        long ms = (System.nanoTime() - t0) / 1_000_000;
        GenerateContentResponseUsageMetadata u = res.usageMetadata().orElse(null);
        log.info("Gemini đọc giấy tờ loại {}: model {}, {} ms, token vào {}, ra {}", type.getId(), model, ms,
                u == null ? "?" : u.promptTokenCount().orElse(0), u == null ? "?" : u.candidatesTokenCount().orElse(0));
        return parse(res.text(), type.getId());
    }

    private GenerateContentResponse callWithRetry(Content content, GenerateContentConfig config, int typeId) {
        for (int attempt = 0; ; attempt++) {
            try {
                return caller.call(model, content, config);
            } catch (ApiException e) {
                boolean retryable = e.code() == 429 || e.code() == 503;
                if (!retryable || attempt >= BACKOFF_MS.length) {
                    log.warn("Gemini lỗi khi đọc giấy tờ loại {}: mã {} {}", typeId, e.code(), e.status());
                    throw e;
                }
                log.info("Gemini trả mã {} (loại {}), thử lại sau {} ms", e.code(), typeId, BACKOFF_MS[attempt]);
                try {
                    sleeper.sleep(BACKOFF_MS[attempt]);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
            }
        }
    }

    // ---------------------------------------------------------------- prompt, schema

    static String prompt(String typeName, List<DocumentFields.Def> defs) {
        StringBuilder b = new StringBuilder();
        b.append("Bạn là hệ thống đọc giấy tờ cho hồ sơ vay tín chấp của một ngân hàng Việt Nam. ")
                .append("Khách hàng tải tệp đính kèm lên với tư cách là: «").append(typeName).append("».\n")
                .append("1. isReadable: false nếu ảnh mờ, lóa, bị che hoặc bị cắt mất phần chính nên không đọc được; khi đó ghi issue là ")
                .append("một câu nhắc ngắn bằng tiếng Việt để khách chụp lại (ví dụ: \"Ảnh bị lóa ở phần số CCCD, vui lòng chụp lại.\").\n")
                .append("2. documentMatchesType: false nếu tệp rõ ràng không phải «").append(typeName).append("».\n")
                .append("3. fields: mỗi trường dưới đây đúng một phần tử, giữ nguyên key:\n");
        for (DocumentFields.Def d : defs) {
            b.append("- ").append(d.key()).append(" (").append(d.label()).append("): ").append(format(d.kind()));
            String hint = HINTS.get(d.key());
            if (hint != null) {
                b.append("; ").append(hint);
            }
            b.append('\n');
        }
        b.append("Không đoán: trường không có hoặc không đọc được thì value = null, confidence = 0. ")
                .append("confidence là mức chắc chắn từ 0 đến 100. issue = null khi isReadable = true. Chỉ trả JSON theo schema.");
        return b.toString();
    }

    private static String format(DocumentFields.Kind kind) {
        return switch (kind) {
            case NAME -> "chữ in hoa, giữ dấu tiếng Việt";
            case ID -> "chỉ gồm chữ số, không khoảng trắng";
            case DATE -> "định dạng dd/MM/yyyy";
            case GENDER -> "\"Nam\" hoặc \"Nữ\"";
            case MONEY -> "số tiền VND chỉ gồm chữ số, không dấu chấm, dấu phẩy hay đơn vị";
            case TEXT -> "ghi đúng như trên giấy tờ";
        };
    }

    static Schema schema(List<DocumentFields.Def> defs) {
        Schema field = Schema.builder()
                .type(Type.Known.OBJECT)
                .properties(Map.of(
                        "key", Schema.builder().type(Type.Known.STRING).enum_(defs.stream().map(DocumentFields.Def::key).toList()).build(),
                        "value", Schema.builder().type(Type.Known.STRING).nullable(true).build(),
                        "confidence", Schema.builder().type(Type.Known.NUMBER).minimum(0.0).maximum(100.0).build()))
                .required("key", "value", "confidence")
                .propertyOrdering("key", "value", "confidence")
                .build();
        return Schema.builder()
                .type(Type.Known.OBJECT)
                .properties(Map.of(
                        "isReadable", Schema.builder().type(Type.Known.BOOLEAN).build(),
                        "documentMatchesType", Schema.builder().type(Type.Known.BOOLEAN).build(),
                        "issue", Schema.builder().type(Type.Known.STRING).nullable(true).build(),
                        "fields", Schema.builder().type(Type.Known.ARRAY).items(field).build()))
                .required("isReadable", "documentMatchesType", "issue", "fields")
                .propertyOrdering("isReadable", "documentMatchesType", "issue", "fields")
                .build();
    }

    // ---------------------------------------------------------------- đọc kết quả

    /**
     * JSON của Gemini → {@link OcrResult}: đủ các trường của loại giấy tờ theo đúng thứ tự danh mục (thiếu thì rỗng, độ tin cậy 0;
     * khóa lạ bỏ qua), giá trị chuẩn hóa như mock (tiền {@code 18.800.000}, ngày dd/MM/yyyy, giới tính Nam/Nữ).
     * JSON hỏng hoặc thiếu {@code isReadable}/{@code documentMatchesType}/{@code fields} → {@link IllegalStateException}.
     */
    static OcrResult parse(String text, int typeId) {
        JsonNode root;
        try {
            root = text == null ? null : JSON.readTree(text);
        } catch (Exception e) {
            throw new IllegalStateException("Gemini trả JSON không hợp lệ");
        }
        if (root == null || !root.path("isReadable").isBoolean() || !root.path("documentMatchesType").isBoolean()
                || !root.path("fields").isArray()) {
            throw new IllegalStateException("Gemini trả JSON không đúng schema");
        }
        Map<String, JsonNode> byKey = new HashMap<>();
        for (JsonNode f : root.get("fields")) {
            if (f.path("key").isTextual()) {
                byKey.putIfAbsent(f.get("key").asText(), f);
            }
        }
        List<OcrResult.Field> fields = new ArrayList<>();
        for (DocumentFields.Def d : DocumentFields.of(typeId)) {
            JsonNode f = byKey.get(d.key());
            String raw = f == null || !f.path("value").isTextual() ? "" : f.get("value").asText();
            String value = normalize(d.kind(), raw);
            double confidence = value.isEmpty() || f == null ? 0 : confidence(f.path("confidence").asDouble(0));
            fields.add(new OcrResult.Field(d.key(), d.label(), value, confidence));
        }
        String issue = root.path("issue").isTextual() ? root.get("issue").asText().trim() : null;
        if (issue != null && issue.isEmpty()) {
            issue = null;
        }
        if (issue != null && issue.length() > MAX_ISSUE) {
            issue = issue.substring(0, MAX_ISSUE);
        }
        return new OcrResult(root.get("isReadable").asBoolean(), root.get("documentMatchesType").asBoolean(), issue, fields);
    }

    private static double confidence(double c) {
        if (c > 0 && c <= 1) {
            c *= 100; // một số lần model trả thang 0–1
        }
        return Math.round(Math.max(0, Math.min(100, c)));
    }

    private static final Pattern DECIMAL_TAIL = Pattern.compile("[.,]\\d{1,2}$");
    private static final Pattern DMY = Pattern.compile("(\\d{1,2})[/.-](\\d{1,2})[/.-](\\d{4})");
    private static final Pattern YMD = Pattern.compile("(\\d{4})-(\\d{1,2})-(\\d{1,2})");
    private static final DateTimeFormatter OUT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    static String normalize(DocumentFields.Kind kind, String raw) {
        String v = raw == null ? "" : raw.trim().replaceAll("\\s+", " ");
        if (v.isEmpty() || "null".equalsIgnoreCase(v)) {
            return "";
        }
        return switch (kind) {
            case NAME -> v.toUpperCase(Locale.ROOT);
            case ID -> v.replaceAll("[\\s.]", "");
            case DATE -> date(v);
            case GENDER -> gender(v);
            case MONEY -> money(v);
            case TEXT -> v;
        };
    }

    private static String money(String v) {
        String digits = DECIMAL_TAIL.matcher(v.replaceAll("[^0-9.,]", "")).replaceFirst("").replaceAll("\\D", "");
        if (digits.isEmpty() || digits.length() > 15) {
            return "";
        }
        return JsFormat.vnd(Long.parseLong(digits));
    }

    private static String date(String v) {
        try {
            Matcher m = DMY.matcher(v);
            if (m.matches()) {
                return LocalDate.of(Integer.parseInt(m.group(3)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(1))).format(OUT);
            }
            m = YMD.matcher(v);
            if (m.matches()) {
                return LocalDate.of(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3))).format(OUT);
            }
        } catch (DateTimeException e) {
            // ngày không tồn tại: giữ nguyên để khách sửa
        }
        return v;
    }

    private static String gender(String v) {
        String s = v.toLowerCase(Locale.ROOT);
        if (s.equals("nữ") || s.equals("nu") || s.equals("female") || s.equals("f")) {
            return "Nữ";
        }
        if (s.equals("nam") || s.equals("male") || s.equals("m")) {
            return "Nam";
        }
        return v;
    }
}
