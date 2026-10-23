package vn.kltn.tiensanglocvay.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import vn.kltn.tiensanglocvay.common.ErrorBody;

/**
 * Các thao tác chỉ dành cho khách hàng mà mock trả thông báo 401 riêng (không phải "Chưa đăng nhập."): chặn ngay ở bộ lọc, trước khi
 * Spring đọc body, nên chưa đăng nhập hoặc không phải khách hàng luôn nhận 401 đúng chữ của mock dù body sai định dạng.
 * Các đường dẫn này được mở ở SecurityConfig, bộ lọc này thay cho bước kiểm tra đăng nhập.
 */
public class CustomerOnlyFilter extends OncePerRequestFilter {

    private static final AntPathMatcher PATHS = new AntPathMatcher();

    private final ObjectMapper json;

    public CustomerOnlyFilter(ObjectMapper json) {
        this.json = json;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String message = messageFor(req);
        if (message != null && !isCustomer()) {
            res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            res.setContentType(MediaType.APPLICATION_JSON_VALUE);
            res.setCharacterEncoding("UTF-8");
            json.writeValue(res.getOutputStream(), ErrorBody.of(message));
            return;
        }
        chain.doFilter(req, res);
    }

    /** Thông báo 401 của thao tác (theo fe/src/mocks/handlers/screening.ts), hoặc null nếu không thuộc nhóm này. */
    static String messageFor(HttpServletRequest req) {
        if (!HttpMethod.POST.matches(req.getMethod())) {
            return null;
        }
        String path = req.getRequestURI().substring(req.getContextPath().length());
        if (PATHS.match("/plans", path)) {
            return "Cần đăng nhập tài khoản khách hàng để lưu phương án.";
        }
        if (PATHS.match("/screenings/*/save", path)) {
            return "Cần đăng nhập tài khoản khách hàng.";
        }
        return null;
    }

    private static boolean isCustomer() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return a != null && a.getPrincipal() instanceof CurrentUser u && u.role() == Role.CUSTOMER;
    }
}
