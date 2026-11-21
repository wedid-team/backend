package com.wedid.backend.auth.oauth;

import com.wedid.backend.user.entity.OAuthProvider;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OAuthUserProfileTest {

    @Test
    void 카카오_응답에서_ID_닉네임_인증된_이메일을_꺼낸다() {
        Map<String, Object> attributes = Map.of(
                "id", 4321L,
                "kakao_account", Map.of(
                        "profile", Map.of("nickname", "  신랑  "),
                        "email", "groom@kakao.com",
                        "is_email_valid", true,
                        "is_email_verified", true
                )
        );

        OAuthUserProfile profile = OAuthUserProfile.of("kakao", attributes);

        assertThat(profile.provider()).isEqualTo(OAuthProvider.KAKAO);
        assertThat(profile.subject()).isEqualTo("4321");
        assertThat(profile.displayName()).isEqualTo("신랑");
        assertThat(profile.email()).isEqualTo("groom@kakao.com");
    }

    @Test
    void 카카오_이메일이_인증되지_않았거나_동의하지_않으면_저장하지_않는다() {
        Map<String, Object> unverified = Map.of(
                "id", 1L,
                "kakao_account", Map.of(
                        "email", "x@kakao.com",
                        "is_email_valid", true,
                        "is_email_verified", false
                )
        );
        Map<String, Object> noConsent = Map.of("id", 2L);

        assertThat(OAuthUserProfile.of("kakao", unverified).email()).isNull();
        OAuthUserProfile profile = OAuthUserProfile.of("kakao", noConsent);
        assertThat(profile.email()).isNull();
        assertThat(profile.displayName()).isNull();
    }

    @Test
    void 구글_응답에서_sub_이름_인증된_이메일을_꺼낸다() {
        Map<String, Object> attributes = Map.of(
                "sub", "109876",
                "name", "신부",
                "email", "bride@gmail.com",
                "email_verified", true
        );

        OAuthUserProfile profile = OAuthUserProfile.of("google", attributes);

        assertThat(profile.provider()).isEqualTo(OAuthProvider.GOOGLE);
        assertThat(profile.subject()).isEqualTo("109876");
        assertThat(profile.displayName()).isEqualTo("신부");
        assertThat(profile.email()).isEqualTo("bride@gmail.com");
    }

    @Test
    void 고유_ID가_없으면_거절한다() {
        assertThatThrownBy(() -> OAuthUserProfile.of("google", Map.of("name", "이름")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 지원하지_않는_공급자는_거절한다() {
        assertThatThrownBy(() -> OAuthUserProfile.of("naver", Map.of("id", "1")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
