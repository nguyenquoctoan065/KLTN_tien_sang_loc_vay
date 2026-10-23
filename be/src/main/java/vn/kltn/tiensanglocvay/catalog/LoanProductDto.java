package vn.kltn.tiensanglocvay.catalog;

/** Schema LoanProduct trong openapi.yaml. */
public record LoanProductDto(int id, String code, String name, String productType, String repaymentMethod, String description,
                             String status) {

    public static LoanProductDto of(LoanProduct p) {
        return new LoanProductDto(p.getId(), p.getCode(), p.getName(), p.getProductType(), p.getRepaymentMethod(),
                p.getDescription(), p.getStatus());
    }
}
