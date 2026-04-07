package com.lianyutian.xhs.user.config;

import com.lianyutian.xhs.user.controller.response.ApiResponse;
import com.lianyutian.xhs.user.service.auth.DefaultAuthService;
import com.lianyutian.xhs.user.service.auth.CurrentUserView;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

@Configuration
public class WebSecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
        HttpSecurity http,
        DefaultAuthService authService,
        ObjectMapper objectMapper
    ) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .httpBasic(httpBasic -> httpBasic.disable())
            .formLogin(form -> form.disable())
            .exceptionHandling(ex -> ex.authenticationEntryPoint((request, response, authException) -> {
                String reason = (String) request.getAttribute(JwtContextFilter.AUTH_FAILURE_REASON);
                String message = JwtContextFilter.INVALID_ACCESS_TOKEN.equals(reason) ? "invalid access token" : "unauthorized";
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.setCharacterEncoding("UTF-8");
                objectMapper.writeValue(response.getWriter(), new ApiResponse<>("UNAUTHORIZED", message, null));
            }))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/register", "/api/v1/auth/login", "/api/v1/auth/refresh").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/verification/image-captcha").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/verification/email-code").permitAll()
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                .requestMatchers("/api/v1/auth/me", "/api/v1/auth/logout").authenticated()
                .requestMatchers("/api/v1/protected/**").authenticated()
                .anyRequest().denyAll())
            .addFilterBefore(new JwtContextFilter(authService), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    private static final class JwtContextFilter extends OncePerRequestFilter {

        private static final String AUTH_FAILURE_REASON = "auth_failure_reason";
        private static final String INVALID_ACCESS_TOKEN = "INVALID_ACCESS_TOKEN";

        private final DefaultAuthService authService;

        private JwtContextFilter(DefaultAuthService authService) {
            this.authService = authService;
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
            String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
            if (authorization != null && authorization.startsWith("Bearer ")) {
                String token = authorization.substring(7);
                try {
                    CurrentUserView user = authService.currentUser(token);
                    UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        user.email(),
                        null,
                        java.util.List.of(new SimpleGrantedAuthority("ROLE_USER"))
                    );
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                } catch (IllegalArgumentException ignored) {
                    SecurityContextHolder.clearContext();
                    request.setAttribute(AUTH_FAILURE_REASON, INVALID_ACCESS_TOKEN);
                }
            }
            filterChain.doFilter(request, response);
        }
    }
}
