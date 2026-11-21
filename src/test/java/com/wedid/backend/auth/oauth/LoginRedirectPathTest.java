package com.wedid.backend.auth.oauth;

import com.wedid.backend.auth.config.AuthProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class LoginRedirectPathTest {

    @Test
    void 사이트_내부_상대_경로는_그대로_허용한다() {
        assertThat(LoginRedirectPath.sanitize("/roadmap?resume=save-plan"))
                .isEqualTo("/roadmap?resume=save-plan");
        assertThat(LoginRedirectPath.sanitize("/vendors/halls#top")).isEqualTo("/vendors/halls#top");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            "https://evil.com",
            "//evil.com",
            "/\\evil.com",
            "evil.com",
            "@evil.com",
            "/path with space",
            "/line\nbreak",
            "javascript:alert(1)"
    })
    void 외부_주소나_이상한_값은_첫_화면으로_바꾼다(String rawPath) {
        assertThat(LoginRedirectPath.sanitize(rawPath)).isEqualTo("/");
    }

    @Test
    void 프론트엔드_주소에_경로와_오류_코드를_붙인다() {
        AuthProperties properties = new AuthProperties(
                "http://localhost:3000/", "x".repeat(32),
                Duration.ofMinutes(30), Duration.ofDays(14), false);

        assertThat(LoginRedirectPath.toFrontendUrl(properties, "/roadmap?resume=save-plan", null))
                .isEqualTo("http://localhost:3000/roadmap?resume=save-plan");
        assertThat(LoginRedirectPath.toFrontendUrl(properties, "/roadmap?resume=save-plan", "cancelled"))
                .isEqualTo("http://localhost:3000/roadmap?resume=save-plan&loginError=cancelled");
        assertThat(LoginRedirectPath.toFrontendUrl(properties, "/", "failed"))
                .isEqualTo("http://localhost:3000/?loginError=failed");
    }
}
