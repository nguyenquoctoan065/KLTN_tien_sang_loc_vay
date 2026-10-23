package vn.kltn.tiensanglocvay.engine;

import java.util.Map;

/**
 * Trọng số MB12 theo gói ("TD", "TC") → tiêu chí → %. Map bên trong phải giữ thứ tự (như {@code Object.entries} trong engine.ts)
 * để phép cộng số thực cho cùng kết quả.
 */
public record WeightSet(Map<String, Map<String, Double>> byProduct) {

    public Map<String, Double> of(ProductCode code) {
        return byProduct.getOrDefault(code.name(), Map.of());
    }
}
