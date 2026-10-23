package vn.kltn.tiensanglocvay.catalog;

/** Schema CrossSellProduct trong openapi.yaml. Tiền là số nguyên VND; điểm cộng và mức giảm lãi là số thập phân. */
public record CrossSellProductDto(int id, String name, String productType, long estCost, double scoreBonus, double rateDiscount,
                                  String status) {

    static CrossSellProductDto of(CrossSellProduct c) {
        return new CrossSellProductDto(c.getId(), c.getName(), c.getProductType(),
                c.getEstCost() == null ? 0 : c.getEstCost().longValueExact(),
                c.getScoreBonus() == null ? 0 : c.getScoreBonus().doubleValue(),
                c.getRateDiscount() == null ? 0 : c.getRateDiscount().doubleValue(), c.getStatus());
    }
}
