package com.wedid.backend.auth.controller;

import com.wedid.backend.auth.dto.AuthMeResponse;
import com.wedid.backend.auth.service.InvalidRefreshTokenException;
import com.wedid.backend.auth.service.RefreshTokenService;
import com.wedid.backend.auth.service.RefreshTokenService.RotatedRefreshToken;
import com.wedid.backend.auth.token.AccessTokenProvider;
import com.wedid.backend.auth.web.AuthCookieManager;
import com.wedid.backend.auth.web.LoginUser;
import com.wedid.backend.global.exception.ErrorResponse;
import com.wedid.backend.user.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 로그인 상태 확인 · 토큰 갱신 · 로그아웃.
 * 로그인 시작(GET /api/v1/auth/login/{provider})과 콜백(/api/v1/auth/callback/{provider})은
 * Spring Security OAuth2 필터가 처리한다(SecurityConfig 참고).
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final UserRepository userRepository;
    private final AccessTokenProvider accessTokenProvider;
    private final RefreshTokenService refreshTokenService;
    private final AuthCookieManager cookieManager;

    public AuthController(
            UserRepository userRepository,
            AccessTokenProvider accessTokenProvider,
            RefreshTokenService refreshTokenService,
            AuthCookieManager cookieManager
    ) {
        this.userRepository = userRepository;
        this.accessTokenProvider = accessTokenProvider;
        this.refreshTokenService = refreshTokenService;
        this.cookieManager = cookieManager;
    }

    /** 비로그인이어도 401이 아니라 authenticated=false로 응답한다. */
    @GetMapping("/me")
    public AuthMeResponse me(@AuthenticationPrincipal LoginUser loginUser) {
        if (loginUser == null) {
            return AuthMeResponse.anonymous();
        }
        return userRepository.findById(loginUser.id())
                .map(user -> AuthMeResponse.of(user.getId(), user.getDisplayName(), user.getEmail()))
                .orElseGet(AuthMeResponse::anonymous);
    }

    /** 리프레시 토큰 쿠키로 새 액세스·리프레시 토큰을 발급한다. 실패하면 쿠키를 지우고 401. */
    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(HttpServletRequest request, HttpServletResponse response) {
        try {
            String rawToken = cookieManager.readRefreshToken(request)
                    .orElseThrow(InvalidRefreshTokenException::new);
            RotatedRefreshToken rotated = refreshTokenService.rotate(rawToken);

            cookieManager.writeTokens(
                    response,
                    accessTokenProvider.issue(rotated.userId()),
                    rotated.rawToken()
            );
            return ResponseEntity.noContent().build();
        } catch (InvalidRefreshTokenException e) {
            cookieManager.clearTokens(response);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse(e.getMessage()));
        }
    }

    /** 리프레시 토큰을 폐기하고 쿠키를 지운다. 로그인 상태가 아니어도 204. 계획은 계정에 유지된다(U-08). */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        cookieManager.readRefreshToken(request).ifPresent(refreshTokenService::revoke);
        cookieManager.clearTokens(response);
        return ResponseEntity.noContent().build();
    }
}
