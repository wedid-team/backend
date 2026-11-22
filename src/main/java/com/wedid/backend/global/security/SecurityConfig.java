
package com.wedid.backend.global.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http)
            throws Exception {

        http
                .cors(Customizer.withDefaults())

                // 비회원 예상 비용 계산 API의 CSRF 검사 제외
                .csrf(csrf -> csrf
                        .ignoringRequestMatchers(
                                "/api/v1/estimates/hall"
                        )
                )

                .authorizeHttpRequests(auth -> auth
                        // 비회원 조회 가능 API
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/regions",
                                "/api/v1/guest-ranges",
                                "/api/v1/moods",
                                "/api/v1/halls",
                                "/api/v1/halls/*"
                        ).permitAll()

                        // 비회원 예식장 예상 비용 계산
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/estimates/hall"
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

                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable());

        return http.build();
    }
}
