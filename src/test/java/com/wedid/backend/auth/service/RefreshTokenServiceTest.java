package com.wedid.backend.auth.service;

import com.wedid.backend.auth.config.AuthProperties;
import com.wedid.backend.auth.entity.RefreshToken;
import com.wedid.backend.auth.repository.RefreshTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RefreshTokenServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-18T00:00:00Z");
    private static final OffsetDateTime NOW_ODT = OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC);
    private static final UUID USER_ID = UUID.randomUUID();

    private RefreshTokenRepository repository;
    private RefreshTokenService service;

    @BeforeEach
    void setUp() {
        repository = mock(RefreshTokenRepository.class);
        AuthProperties properties = new AuthProperties("http://localhost:3000", "a".repeat(32),
                Duration.ofMinutes(30), Duration.ofDays(14), false);
        service = new RefreshTokenService(repository, properties, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void 발급하면_평문이_아닌_해시를_저장한다() {
        String raw = service.issue(USER_ID);

        ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
        verify(repository).save(saved.capture());
        assertThat(raw).hasSizeGreaterThanOrEqualTo(43);
        assertThat(saved.getValue().getUserId()).isEqualTo(USER_ID);
        assertThat(saved.getValue().isExpired(NOW_ODT.plusDays(14).minusSeconds(1))).isFalse();
        assertThat(saved.getValue().isExpired(NOW_ODT.plusDays(14))).isTrue();
    }

    @Test
    void 갱신하면_기존_토큰을_폐기하고_새_토큰을_발급한다() {
        RefreshToken current = new RefreshToken(USER_ID, RefreshTokenService.hash("old"), NOW_ODT.plusDays(1));
        when(repository.findByTokenHashForUpdate(RefreshTokenService.hash("old"))).thenReturn(Optional.of(current));

        RefreshTokenService.RotatedRefreshToken rotated = service.rotate("old");

        assertThat(current.isRevoked()).isTrue();
        assertThat(rotated.userId()).isEqualTo(USER_ID);
        assertThat(rotated.rawToken()).isNotEqualTo("old");
        verify(repository).save(any(RefreshToken.class));
    }

    @Test
    void 이미_폐기된_토큰을_다시_쓰면_회원의_모든_토큰을_폐기한다() {
        RefreshToken revoked = new RefreshToken(USER_ID, RefreshTokenService.hash("stolen"), NOW_ODT.plusDays(1));
        revoked.revoke(NOW_ODT.minusMinutes(5));
        when(repository.findByTokenHashForUpdate(anyString())).thenReturn(Optional.of(revoked));

        assertThatThrownBy(() -> service.rotate("stolen")).isInstanceOf(InvalidRefreshTokenException.class);
        verify(repository).revokeAllActiveByUserId(USER_ID, NOW_ODT);
        verify(repository, never()).save(any());
    }

    @Test
    void 만료되었거나_없는_토큰은_거절한다() {
        RefreshToken expired = new RefreshToken(USER_ID, RefreshTokenService.hash("expired"), NOW_ODT);
        when(repository.findByTokenHashForUpdate(RefreshTokenService.hash("expired"))).thenReturn(Optional.of(expired));
        when(repository.findByTokenHashForUpdate(RefreshTokenService.hash("unknown"))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.rotate("expired")).isInstanceOf(InvalidRefreshTokenException.class);
        assertThatThrownBy(() -> service.rotate("unknown")).isInstanceOf(InvalidRefreshTokenException.class);
        assertThatThrownBy(() -> service.rotate(" ")).isInstanceOf(InvalidRefreshTokenException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void 로그아웃하면_해당_토큰만_폐기한다() {
        RefreshToken token = new RefreshToken(USER_ID, RefreshTokenService.hash("mine"), NOW_ODT.plusDays(1));
        when(repository.findByTokenHash(RefreshTokenService.hash("mine"))).thenReturn(Optional.of(token));

        service.revoke("mine");

        assertThat(token.isRevoked()).isTrue();
        verify(repository, never()).revokeAllActiveByUserId(any(), any());
    }
}
