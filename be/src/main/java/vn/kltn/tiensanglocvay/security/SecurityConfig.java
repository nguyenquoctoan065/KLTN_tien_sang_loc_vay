package vn.kltn.tiensanglocvay.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import vn.kltn.tiensanglocvay.auth.SessionService;
import vn.kltn.tiensanglocvay.common.ErrorBody;

/**
 * Mọi đường dẫn cần đăng nhập, trừ các thao tác {@code security: []} hoặc {@code security: [{}, ...]} trong openapi.yaml.
 * Các đường dẫn dưới đây tính từ context path {@code /api}.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    static final String MSG_UNAUTHENTICATED = "Chưa đăng nhập.";
    static final String MSG_FORBIDDEN = "Không có quyền.";

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, ObjectMapper json, SessionService sessions) throws Exception {
        http
                .addFilterBefore(new TokenAuthFilter(sessions), UsernamePasswordAuthenticationFilter.class)
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) -> write(json, res, HttpServletResponse.SC_UNAUTHORIZED, MSG_UNAUTHENTICATED))
                        .accessDeniedHandler((req, res, ex) -> write(json, res, HttpServletResponse.SC_FORBIDDEN, MSG_FORBIDDEN)))
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs", "/v3/api-docs/**").permitAll()
                        // /auth/otp là [{}, bearer] và /auth/logout luôn trả 200 (openapi): TokenAuthFilter vẫn nạp CurrentUser nếu có token.
                        .requestMatchers(HttpMethod.POST, "/auth/login", "/auth/register", "/auth/otp", "/auth/logout").permitAll()
                        .requestMatchers(HttpMethod.GET, "/products", "/products/*/checklist", "/cross-sell-products", "/branches").permitAll()
                        .requestMatchers(HttpMethod.POST, "/screenings", "/simulations").permitAll()
                        .requestMatchers(HttpMethod.GET, "/screenings/*").permitAll()
                        .anyRequest().authenticated());
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    private static void write(ObjectMapper json, HttpServletResponse res, int status, String message) throws IOException {
        res.setStatus(status);
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.setCharacterEncoding("UTF-8");
        json.writeValue(res.getOutputStream(), ErrorBody.of(message));
    }
}
