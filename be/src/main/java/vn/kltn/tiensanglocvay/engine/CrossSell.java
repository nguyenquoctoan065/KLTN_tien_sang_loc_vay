package vn.kltn.tiensanglocvay.engine;

/** Sản phẩm bán chéo như bộ máy cần (tên trường giống CrossSellProduct trong openapi.yaml). */
public record CrossSell(int id, String name, long estCost, double scoreBonus, double rateDiscount, String status) {

    public boolean active() {
        return "ACTIVE".equals(status);
    }
}
