package vn.kltn.tiensanglocvay.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Vai trò được gọi endpoint (x-roles trong openapi.yaml). Đặt trên controller hoặc từng phương thức (phương thức ưu tiên hơn lớp).
 * {@link RequireRoleInterceptor} kiểm tra trước khi Spring đọc body: sai vai trò → 403 "Không có quyền." như mock;
 * chưa đăng nhập vẫn là 401 "Chưa đăng nhập." của SecurityConfig.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface RequireRole {

    Role[] value();
}
