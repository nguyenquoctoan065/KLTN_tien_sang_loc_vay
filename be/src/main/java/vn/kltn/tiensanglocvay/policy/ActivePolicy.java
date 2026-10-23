package vn.kltn.tiensanglocvay.policy;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import vn.kltn.tiensanglocvay.catalog.LoanProduct;
import vn.kltn.tiensanglocvay.catalog.LoanProductRepository;

/**
 * Phiên bản tham số đang hiệu lực ({@code param_version.status = 'ACTIVE'}), nạp một lần rồi giữ trong bộ nhớ.
 * Kích hoạt phiên bản mới (B7) phải gọi {@link #evict()} sau khi giao dịch đã commit.
 * {@link #version(int)} đọc một phiên bản bất kỳ (lượt sàng lọc cũ được tính lại theo phiên bản lúc tạo); phiên bản ACTIVE/EXPIRED
 * không sửa được nên giữ trong bộ nhớ là an toàn.
 */
@Component
public class ActivePolicy {

    private final ParamVersionRepository versions;
    private final PolicyParamRepository params;
    private final ScoringWeightRepository weights;
    private final LoanProductRepository products;

    private volatile PolicySnapshot cached;
    private final Map<Integer, PolicySnapshot> byId = new ConcurrentHashMap<>();

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
            s = load(versions.findByStatus(ParamVersion.ACTIVE)
                    .orElseThrow(() -> new IllegalStateException("Chưa có phiên bản tham số đang áp dụng.")));
            cached = s;
        }
        return s;
    }

    public PolicySnapshot version(int id) {
        PolicySnapshot s = cached;
        if (s != null && s.versionId() == id) {
            return s;
        }
        return byId.computeIfAbsent(id, k -> load(versions.findById(k)
                .orElseThrow(() -> new IllegalStateException("Không có phiên bản tham số #" + k + "."))));
    }

    public void evict() {
        cached = null;
        byId.clear();
    }

    private PolicySnapshot load(ParamVersion v) {
        Map<Integer, String> codeById = new HashMap<>();
        for (LoanProduct p : products.findAll()) {
            codeById.put(p.getId(), p.getCode());
        }
        Map<String, String> common = new LinkedHashMap<>();
        Map<String, Map<String, String>> byProduct = new LinkedHashMap<>();
        for (PolicyParam p : params.findByVersionIdOrderById(v.getId())) {
            if (p.getProductId() == null) {
                common.put(p.getKey(), p.getValue());
            } else {
                byProduct.computeIfAbsent(codeById.get(p.getProductId()), k -> new LinkedHashMap<>()).put(p.getKey(), p.getValue());
            }
        }
        // Thứ tự tiêu chí theo id (như mảng trọng số của mock) để tổng điểm cộng cùng thứ tự với engine.ts.
        Map<String, Map<String, BigDecimal>> byWeight = new LinkedHashMap<>();
        for (ScoringWeight w : weights.findByVersionIdOrderById(v.getId())) {
            byWeight.computeIfAbsent(codeById.get(w.getProductId()), k -> new LinkedHashMap<>()).put(w.getCriterion(), w.getWeight());
        }
        return new PolicySnapshot(v.getId(), v.getVersionNo(), Collections.unmodifiableMap(common), freeze(byProduct), freeze(byWeight));
    }

    private static <T> Map<String, Map<String, T>> freeze(Map<String, Map<String, T>> in) {
        Map<String, Map<String, T>> out = new LinkedHashMap<>();
        in.forEach((k, m) -> out.put(k, Collections.unmodifiableMap(m)));
        return Collections.unmodifiableMap(out);
    }
}
