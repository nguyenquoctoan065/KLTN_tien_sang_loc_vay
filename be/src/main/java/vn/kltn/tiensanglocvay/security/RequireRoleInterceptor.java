package vn.kltn.tiensanglocvay.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Arrays;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import vn.kltn.tiensanglocvay.common.ApiException;

/**
 * Thực thi {@link RequireRole}. Chạy sau khi chọn handler nhưng trước khi đọc tham số/body, nên thứ tự lỗi giống mock
 * (kiểm tra vai trò trước, dữ liệu sau). Ném {@link ApiException} để GlobalExceptionHandler trả JSON tiếng Việt.
 */
public class RequireRoleInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) {
        if (!(handler instanceof HandlerMethod m)) {
            return true;
        }
        RequireRole r = AnnotatedElementUtils.findMergedAnnotation(m.getMethod(), RequireRole.class);
        if (r == null) {
            r = AnnotatedElementUtils.findMergedAnnotation(m.getBeanType(), RequireRole.class);
        }
        if (r == null) {
            return true;
        }
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a != null && a.getPrincipal() instanceof CurrentUser u && Arrays.asList(r.value()).contains(u.role())) {
            return true;
        }
        throw new ApiException(HttpStatus.FORBIDDEN, SecurityConfig.MSG_FORBIDDEN);
    }
}
