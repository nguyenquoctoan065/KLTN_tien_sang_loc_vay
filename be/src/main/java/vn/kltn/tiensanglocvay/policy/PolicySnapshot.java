package vn.kltn.tiensanglocvay.policy;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Ảnh chụp bất biến của một phiên bản tham số: tham số dùng chung, tham số theo mã gói ("TD", "TC") và trọng số chấm điểm
 * theo mã gói. Khóa tham số và tiêu chí là các mã trong docs/quy-tac-tinh-toan.md mục 1 (DTI_MAX, MAX_AMOUNT, DTI…).
 */
public record PolicySnapshot(int versionId, String versionNo, Map<String, String> common,
                             Map<String, Map<String, String>> byProduct, Map<String, Map<String, BigDecimal>> weights) {

    /** Tham số dùng chung; ném {@link IllegalStateException} nếu phiên bản thiếu tham số (dữ liệu cấu hình sai). */
    public BigDecimal common(String key) {
        return number(common.get(key), "dùng chung", key);
    }

    public BigDecimal product(String productCode, String key) {
        return number(byProduct.getOrDefault(productCode, Map.of()).get(key), productCode, key);
    }

    /** Trọng số (%) của tiêu chí; 0 nếu phiên bản không khai tiêu chí đó cho gói. */
    public BigDecimal weight(String productCode, String criterion) {
        return weights.getOrDefault(productCode, Map.of()).getOrDefault(criterion, BigDecimal.ZERO);
    }

    private BigDecimal number(String raw, String scope, String key) {
        if (raw == null) {
            throw new IllegalStateException("Phiên bản tham số " + versionNo + " thiếu tham số " + key + " (" + scope + ").");
        }
        return new BigDecimal(raw);
    }
}
