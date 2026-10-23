package vn.kltn.tiensanglocvay.policy;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import vn.kltn.tiensanglocvay.catalog.LoanProduct;
import vn.kltn.tiensanglocvay.catalog.LoanProductRepository;

/**
 * Phiên bản tham số đang hiệu lực ({@code param_version.status = 'ACTIVE'}), nạp một lần rồi giữ trong bộ nhớ.
 * Kích hoạt phiên bản mới (B7) phải gọi {@link #evict()} sau khi giao dịch đã commit.
 */
@Component
public class ActivePolicy {

    private final ParamVersionRepository versions;
    private final PolicyParamRepository params;
    private final ScoringWeightRepository weights;
    private final LoanProductRepository products;

    private volatile PolicySnapshot cached;

    public ActivePolicy(ParamVersionRepository versions, PolicyParamRepository params, ScoringWeightRepository weights,
                        LoanProductRepository products) {
        this.versions = versions;
        this.params = params;
        this.weights = weights;
        this.products = products;
    }

    public PolicySnapshot current() {
        PolicySnapshot s = cached;
        if (s == null) {
            s = load();
            cached = s;
        }
        return s;
    }

    public void evict() {
        cached = null;
    }

    private PolicySnapshot load() {
        ParamVersion v = versions.findByStatus(ParamVersion.ACTIVE)
                .orElseThrow(() -> new IllegalStateException("Chưa có phiên bản tham số đang áp dụng."));
        Map<Integer, String> codeById = new HashMap<>();
        for (LoanProduct p : products.findAll()) {
            codeById.put(p.getId(), p.getCode());
        }
        Map<String, String> common = new HashMap<>();
        Map<String, Map<String, String>> byProduct = new HashMap<>();
        for (PolicyParam p : params.findByVersionId(v.getId())) {
            if (p.getProductId() == null) {
                common.put(p.getKey(), p.getValue());
            } else {
                byProduct.computeIfAbsent(codeById.get(p.getProductId()), k -> new HashMap<>()).put(p.getKey(), p.getValue());
            }
        }
        Map<String, Map<String, BigDecimal>> byWeight = new HashMap<>();
        for (ScoringWeight w : weights.findByVersionId(v.getId())) {
            byWeight.computeIfAbsent(codeById.get(w.getProductId()), k -> new HashMap<>()).put(w.getCriterion(), w.getWeight());
        }
        return new PolicySnapshot(v.getId(), v.getVersionNo(), Map.copyOf(common), copy(byProduct), copy(byWeight));
    }

    private static <T> Map<String, Map<String, T>> copy(Map<String, Map<String, T>> in) {
        Map<String, Map<String, T>> out = new HashMap<>();
        in.forEach((k, m) -> out.put(k, Map.copyOf(m)));
        return Map.copyOf(out);
    }
}
