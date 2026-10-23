package vn.kltn.tiensanglocvay.policy;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import vn.kltn.tiensanglocvay.engine.ParamSet;
import vn.kltn.tiensanglocvay.engine.ProductCode;
import vn.kltn.tiensanglocvay.engine.WeightSet;

/** Đọc PolicySnapshot thành ParamSet/WeightSet của bộ máy; giá trị mặc định khi thiếu giống {@code toParamSet()} trong engine.ts. */
public final class EngineParams {

    private EngineParams() {
    }

    public static ParamSet paramSet(PolicySnapshot p) {
        Map<String, ParamSet.ProductParams> products = new LinkedHashMap<>();
        for (ProductCode c : ProductCode.values()) {
            boolean td = c == ProductCode.TD;
            Map<String, String> m = p.byProduct().getOrDefault(c.name(), Map.of());
            products.put(c.name(), new ParamSet.ProductParams(
                    num(m, "MIN_AGE", td ? 22 : 19),
                    td ? num(m, "INCOME_MULTIPLIER", 15) : null,
                    num(m, "MAX_AMOUNT", td ? 500e6 : 200e6),
                    num(m, "MIN_AMOUNT", 10e6),
                    (int) num(m, "MAX_TERM", td ? 60 : 12),
                    (int) num(m, "MIN_TERM", td ? 6 : 12),
                    num(m, "REF_RATE", td ? 12.5 : 18)));
        }
        Map<String, String> g = p.common();
        return new ParamSet(num(g, "DTI_MAX", 70), num(g, "LIVING_COST_BASE", 4_000_000), num(g, "LIVING_COST_PER_DEPENDENT", 2_000_000),
                num(g, "RETIRE_AGE_MALE", 62), num(g, "RETIRE_AGE_FEMALE", 60), num(g, "ROOM_FACTOR", 100), num(g, "MAX_RATE_DISCOUNT", 2),
                products);
    }

    public static WeightSet weightSet(PolicySnapshot p) {
        Map<String, Map<String, Double>> out = new LinkedHashMap<>();
        for (ProductCode c : ProductCode.values()) {
            Map<String, Double> w = new LinkedHashMap<>();
            p.weights().getOrDefault(c.name(), Map.of()).forEach((k, v) -> w.put(k, v.doubleValue()));
            out.put(c.name(), w);
        }
        return new WeightSet(out);
    }

    private static double num(Map<String, String> m, String key, double dflt) {
        String v = m.get(key);
        return v == null ? dflt : new BigDecimal(v.trim()).doubleValue();
    }
}
