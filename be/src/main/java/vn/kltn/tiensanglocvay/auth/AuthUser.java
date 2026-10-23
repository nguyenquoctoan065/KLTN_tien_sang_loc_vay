package vn.kltn.tiensanglocvay.auth;

import vn.kltn.tiensanglocvay.security.Role;

/** Schema AuthUser; {@code username} là số điện thoại (khách hàng) hoặc email (nội bộ). */
public record AuthUser(long id, String name, Role role, String username, String email, String branchName) {
}
