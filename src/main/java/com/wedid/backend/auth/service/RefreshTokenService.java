package com.wedid.backend.auth.service;

import com.wedid.backend.auth.config.AuthProperties;
import com.wedid.backend.auth.entity.RefreshToken;
import com.wedid.backend.auth.repository.RefreshTokenRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.UUID;

/**
 * 리프레시 토큰 발급·교체·폐기.
 * 갱신할 때마다 기존 토큰을 폐기하고 새 토큰을 발급한다(rotation).
 * 이미 폐기된 토큰이 다시 들어오면 탈취로 보고 해당 회원의 모든 리프레시 토큰을 폐기한다.
 */
@Slf4j
@Service
public class RefreshTokenService {

    private static final int TOKEN_BYTES = 32;

    private final RefreshTokenRepository refreshTokenRepository;
    private final Duration ttl;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    public RefreshTokenService(
            RefreshTokenRepository refreshTokenRepository,
            AuthProperties properties,
            Clock clock
    ) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.ttl = properties.refreshTokenTtl();
        this.clock = clock;
    }

    /** 새 리프레시 토큰을 발급하고 평문을 돌려준다. */
    @Transactional
    public String issue(UUID userId) {
        String rawToken = generateRawToken();
        OffsetDateTime expiresAt = now().plus(ttl);
        refreshTokenRepository.save(new RefreshToken(userId, hash(rawToken), expiresAt));
        return rawToken;
    }

    /**
     * 기존 토큰을 폐기하고 새 토큰을 발급한다.
     * 재사용 감지로 전체 폐기한 경우에도 그 변경은 커밋되어야 하므로 예외에서 롤백하지 않는다.
     */
    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public RotatedRefreshToken rotate(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new InvalidRefreshTokenException();
        }

        RefreshToken current = refreshTokenRepository.findByTokenHashForUpdate(hash(rawToken))
                .orElseThrow(InvalidRefreshTokenException::new);
        OffsetDateTime now = now();

        if (current.isRevoked()) {
            log.warn("폐기된 리프레시 토큰이 재사용되어 회원의 모든 리프레시 토큰을 폐기합니다. userId={}",
                    current.getUserId());
            refreshTokenRepository.revokeAllActiveByUserId(current.getUserId(), now);
            throw new InvalidRefreshTokenException();
        }
        if (current.isExpired(now)) {
            throw new InvalidRefreshTokenException();
        }

        current.revoke(now);
        String newRawToken = issue(current.getUserId());
        return new RotatedRefreshToken(current.getUserId(), newRawToken);
    }

    /** 로그아웃: 전달된 토큰만 폐기한다. 없는 토큰이면 아무 일도 하지 않는다. */
    @Transactional
    public void revoke(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        refreshTokenRepository.findByTokenHash(hash(rawToken))
                .ifPresent(token -> token.revoke(now()));
    }

    private String generateRawToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hash(String rawToken) {
        return DigestUtils.sha256Hex(rawToken);
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock);
    }

    public record RotatedRefreshToken(UUID userId, String rawToken) {
    }
}
