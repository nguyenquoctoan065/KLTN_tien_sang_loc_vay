package vn.kltn.tiensanglocvay.engine;

import java.util.List;

/** Schema Suggestion; {@code type}: REDUCE_AMOUNT, EXTEND_TERM, SWITCH_PRODUCT, CROSS_SELL. */
public record Suggestion(String type, String title, String detail, ProductCode productCode, long amount, int termMonths,
                         List<Integer> crossSellIds, double score, long extraCost) {
}
