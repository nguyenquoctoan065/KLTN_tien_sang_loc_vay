package vn.kltn.tiensanglocvay.catalog;

/**
 * Schema Branch trong openapi.yaml, không có {@code openApplications} (chỉ có ở GET /admin/branches, giai đoạn B7).
 */
public record BranchDto(long id, String code, String name, String address, String phone, String status) {

    static BranchDto of(Branch b) {
        return new BranchDto(b.getId(), b.getCode(), b.getName(), b.getAddress(), b.getPhone(), b.getStatus());
    }
}
