package com.wedid.backend.global.security;

import com.wedid.backend.auth.config.AuthProperties;
import com.wedid.backend.auth.oauth.LoginRedirectAuthorizationRequestResolver;
import com.wedid.backend.auth.oauth.OAuthLoginFailureHandler;
import com.wedid.backend.auth.oauth.OAuthLoginSuccessHandler;
import com.wedid.backend.auth.token.AccessTokenProvider;
import com.wedid.backend.auth.web.AuthCookieManager;
import com.wedid.backend.auth.web.JwtAuthenticationFilter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizedClientRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.savedrequest.NullRequestCache;

/**
 * 인증 구조
 * - 로그인: GET /api/v1/auth/login/{kakao|google}?redirect=/돌아갈경로 → 소셜 인증 → /api/v1/auth/callback/{provider}
 *   → 회원 조회·생성 후 액세스·리프레시 토큰을 HttpOnly 쿠키로 발급하고 프론트엔드로 리다이렉트
 * - 이후 요청: 액세스 토큰 쿠키(JwtAuthenticationFilter)로 인증. 서버 세션에 로그인 상태를 저장하지 않는다.
 *   (OAuth 진행 중 state 보관에만 세션을 잠깐 쓰고, 로그인이 끝나면 무효화한다)
 * - CSRF: 인증 쿠키가 모두 SameSite=Lax라 다른 사이트에서 보낸 POST·PATCH 등에는 쿠키가 실리지 않는다.
 *   상태를 바꾸는 GET API를 만들지 않는 것을 전제로 CSRF 토큰 검사는 끈다.
 */
@Configuration
@EnableConfigurationProperties(AuthProperties.class)
public class SecurityConfig {

    static final String LOGIN_BASE_URI = "/api/v1/auth/login";
    static final String CALLBACK_BASE_URI = "/api/v1/auth/callback/*";

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            ClientRegistrationRepository clientRegistrationRepository,
            OAuthLoginSuccessHandler successHandler,
            OAuthLoginFailureHandler failureHandler,
            AccessTokenProvider accessTokenProvider,
            AuthCookieManager cookieManager
    ) throws Exception {

        http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())

                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .securityContext(context -> context
                        .securityContextRepository(new RequestAttributeSecurityContextRepository()))
                .requestCache(cache -> cache.requestCache(new NullRequestCache()))

                .authorizeHttpRequests(auth -> auth
                        // 비회원 조회 가능 API
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/regions",
                                "/api/v1/guest-ranges",
                                "/api/v1/moods",
                                "/api/v1/auth/me"
                                // 예식장 후보 목록·상세(/api/v1/halls/**)는 로그인 필요 (C-01 · C-02 · R-12)
                        ).permitAll()

                        // 비회원 예식장 예상 비용 계산 · 토큰 갱신 · 로그아웃
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/estimates/hall",
                                "/api/v1/auth/refresh",
                                "/api/v1/auth/logout"
                        ).permitAll()

                        // 소셜 로그인 시작 · 콜백
                        .requestMatchers(
                                LOGIN_BASE_URI + "/**",
                                "/api/v1/auth/callback/**"
                        ).permitAll()

                        // Swagger
                        .requestMatchers(
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/v3/api-docs/**",
                                "/v3/api-docs"
                        ).permitAll()

                        .requestMatchers("/error").permitAll()

                        // 나머지 API는 인증 필요
                        .anyRequest().authenticated()
                )

                .oauth2Login(oauth2 -> oauth2
                        .loginPage(LOGIN_BASE_URI)
                        .authorizationEndpoint(endpoint -> endpoint
                                .baseUri(LOGIN_BASE_URI)
                                .authorizationRequestResolver(new LoginRedirectAuthorizationRequestResolver(
                                        clientRegistrationRepository, LOGIN_BASE_URI)))
                        .redirectionEndpoint(endpoint -> endpoint.baseUri(CALLBACK_BASE_URI))
                        // 소셜 서비스의 액세스 토큰은 로그인 후 쓰지 않는다.
                        // 기본값(메모리 저장소)은 회원별로 계속 쌓이므로, 로그인 직후 무효화되는 세션에 두고 버린다.
                        .authorizedClientRepository(new HttpSessionOAuth2AuthorizedClientRepository())
                        .successHandler(successHandler)
                        .failureHandler(failureHandler)
                )
                .addFilterBefore(
                        new JwtAuthenticationFilter(accessTokenProvider, cookieManager),
                        UsernamePasswordAuthenticationFilter.class
                )

                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(JsonAuthenticationHandlers.unauthorized())
                        .accessDeniedHandler(JsonAuthenticationHandlers.forbidden())
                )

                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable());

        return http.build();
    }
}
