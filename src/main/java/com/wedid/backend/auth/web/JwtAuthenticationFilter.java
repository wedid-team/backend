package com.wedid.backend.auth.web;

import com.wedid.backend.auth.token.AccessTokenProvider;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * 액세스 토큰 쿠키가 유효하면 요청을 인증 상태로 만든다.
 * 토큰이 없거나 만료·위조된 경우에는 거절하지 않고 비로그인 요청으로 넘긴다.
 * (비회원도 쓰는 API가 오래된 쿠키 때문에 401이 나지 않도록. 인증이 필요한 API는 인가 단계에서 401 처리)
 *
 * <p>서블릿 필터로 자동 등록되지 않도록 빈이 아니라 SecurityConfig에서 직접 생성한다.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final List<SimpleGrantedAuthority> USER_AUTHORITIES =
            List.of(new SimpleGrantedAuthority("ROLE_USER"));

    private final AccessTokenProvider accessTokenProvider;
    private final AuthCookieManager cookieManager;

    public JwtAuthenticationFilter(AccessTokenProvider accessTokenProvider, AuthCookieManager cookieManager) {
        this.accessTokenProvider = accessTokenProvider;
        this.cookieManager = cookieManager;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        cookieManager.readAccessToken(request)
                .flatMap(accessTokenProvider::verify)
                .ifPresent(userId -> {
                    SecurityContext context = SecurityContextHolder.createEmptyContext();
                    context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                            new LoginUser(userId), null, USER_AUTHORITIES));
                    SecurityContextHolder.setContext(context);
                });

        filterChain.doFilter(request, response);
    }
}
