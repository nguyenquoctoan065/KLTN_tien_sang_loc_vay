package vn.kltn.tiensanglocvay.engine;

import java.util.Map;

/** Bộ tham số MB11 đã đọc thành số (tương ứng {@code ParamSet} của engine.ts). {@code products} khóa theo "TD", "TC". */
public record ParamSet(double dtiMax, double livingCostBase, double livingCostPerDependent, double retireAgeMale,
                       double retireAgeFemale, double roomFactor, double maxRateDiscount, Map<String, ProductParams> products) {

    /** {@code incomeMultiplier} chỉ có ở vay tiêu dùng; rỗng hoặc 0 nghĩa là không giới hạn theo thu nhập. */
    public record ProductParams(double minAge, Double incomeMultiplier, double maxAmount, double minAmount, int maxTerm, int minTerm,
                                double refRate) {
    }

    public ProductParams product(ProductCode code) {
        return products.get(code.name());
    }
}
