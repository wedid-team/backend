package com.wedid.backend.auth.token;

import com.wedid.backend.auth.config.AuthProperties;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AccessTokenProviderTest {

    private static final Instant NOW = Instant.parse("2026-11-18T00:00:00Z");
    private static final UUID USER_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");

    private static AuthProperties properties(String secret) {
        return new AuthProperties("http://localhost:3000", secret,
                Duration.ofMinutes(30), Duration.ofDays(14), false);
    }

    private static AccessTokenProvider provider(String secret, Instant now) {
        return new AccessTokenProvider(properties(secret), Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    void 발급한_토큰에서_회원_ID를_꺼낸다() {
        AccessTokenProvider provider = provider("a".repeat(32), NOW);

        String token = provider.issue(USER_ID);

        assertThat(provider.verify(token)).contains(USER_ID);
    }

    @Test
    void 만료된_토큰은_거절한다() {
        String token = provider("a".repeat(32), NOW).issue(USER_ID);
        AccessTokenProvider later = provider("a".repeat(32), NOW.plus(Duration.ofMinutes(31)));

        assertThat(later.verify(token)).isEmpty();
    }

    @Test
    void 다른_키로_서명한_토큰과_변조된_토큰은_거절한다() {
        String token = provider("a".repeat(32), NOW).issue(USER_ID);
        AccessTokenProvider otherKey = provider("b".repeat(32), NOW);

        assertThat(otherKey.verify(token)).isEmpty();
        assertThat(provider("a".repeat(32), NOW).verify(token + "x")).isEmpty();
        assertThat(provider("a".repeat(32), NOW).verify("not-a-jwt")).isEmpty();
    }
}
