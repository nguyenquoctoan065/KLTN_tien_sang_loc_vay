package vn.kltn.tiensanglocvay.engine;

import java.util.List;

/** Schema ScreeningResultItem (một dòng MB03). */
public record ScreeningResultItem(ProductCode productCode, String productName, boolean eligible, List<String> reasons, List<String> hints,
                                  Double dti, Long maxSafeAmount, double refRate, int maxTerm, Double score, Integer rankNo) {

    ScreeningResultItem withRank(Integer rank) {
        return new ScreeningResultItem(productCode, productName, eligible, reasons, hints, dti, maxSafeAmount, refRate, maxTerm, score, rank);
    }
}
