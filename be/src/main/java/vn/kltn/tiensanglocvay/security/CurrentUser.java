package vn.kltn.tiensanglocvay.security;

/** Người đang gọi API, nạp từ phiên đăng nhập. {@code branchId} chỉ có với nhân viên chi nhánh. */
public record CurrentUser(AccountKind kind, long id, Role role, Long branchId) {
}
