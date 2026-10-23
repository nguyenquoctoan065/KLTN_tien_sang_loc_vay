package vn.kltn.tiensanglocvay.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import vn.kltn.tiensanglocvay.auth.SessionService;

/**
 * Đọc {@code Authorization: Bearer ...} và nạp {@link CurrentUser}. Token sai hoặc hết hạn chỉ bị bỏ qua (không lỗi),
 * để các endpoint công khai và endpoint {@code [{}, bearer]} vẫn chạy; endpoint cần đăng nhập sẽ trả 401 "Chưa đăng nhập.".
 */
public class TokenAuthFilter extends OncePerRequestFilter {

    private static final String PREFIX = "bearer ";

    private final SessionService sessions;

    public TokenAuthFilter(SessionService sessions) {
        this.sessions = sessions;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String token = extractToken(req);
        if (token != null) {
            sessions.authenticate(token).ifPresent(user -> SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(user, null,
                            List.of(new SimpleGrantedAuthority("ROLE_" + user.role().name())))));
        }
        chain.doFilter(req, res);
    }

    /** Token trong header Authorization, hoặc null nếu không có. */
    public static String extractToken(HttpServletRequest req) {
        String h = req.getHeader("Authorization");
        if (h == null || h.length() <= PREFIX.length() || !h.regionMatches(true, 0, PREFIX, 0, PREFIX.length())) {
            return null;
        }
        String token = h.substring(PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }
}
