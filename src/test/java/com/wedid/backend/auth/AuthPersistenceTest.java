package com.wedid.backend.auth;

import com.wedid.backend.auth.config.AuthProperties;
import com.wedid.backend.auth.oauth.OAuthUserProfile;
import com.wedid.backend.auth.repository.RefreshTokenRepository;
import com.wedid.backend.auth.service.InvalidRefreshTokenException;
import com.wedid.backend.auth.service.OAuthLoginService;
import com.wedid.backend.auth.service.RefreshTokenService;
import com.wedid.backend.global.config.ClockConfig;
import com.wedid.backend.user.entity.OAuthProvider;
import com.wedid.backend.user.entity.User;
import com.wedid.backend.user.repository.OAuthAccountRepository;
import com.wedid.backend.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 로컬 PostgreSQL(docker compose)에 대해 회원·소셜 계정·리프레시 토큰 저장을 검증한다.
 * 스키마 검증(ddl-auto=validate)도 함께 이뤄진다. 각 테스트는 끝나면 롤백된다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({OAuthLoginService.class, RefreshTokenService.class, ClockConfig.class})
@EnableConfigurationProperties(AuthProperties.class)
@TestPropertySource(properties = {
        "wedid.auth.frontend-base-url=http://localhost:3000",
        "wedid.auth.jwt-secret=test-secret-test-secret-test-secret-0123",
        "wedid.auth.access-token-ttl=30m",
        "wedid.auth.refresh-token-ttl=14d"
})
class AuthPersistenceTest {

    @Autowired
    private OAuthLoginService oauthLoginService;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OAuthAccountRepository oauthAccountRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Test
    void 첫_로그인은_회원과_소셜_계정을_만들고_다음_로그인은_같은_회원을_찾는다() {
        OAuthUserProfile first = new OAuthUserProfile(OAuthProvider.KAKAO, "persist-test-1", "신랑", null);

        User created = oauthLoginService.loginOrRegister(first);
        User again = oauthLoginService.loginOrRegister(
                new OAuthUserProfile(OAuthProvider.KAKAO, "persist-test-1", "새 닉네임", "groom@kakao.com"));

        assertThat(again.getId()).isEqualTo(created.getId());
        assertThat(oauthAccountRepository.findByProviderAndProviderSubject(OAuthProvider.KAKAO, "persist-test-1"))
                .hasValueSatisfying(account -> assertThat(account.getUserId()).isEqualTo(created.getId()));

        userRepository.flush();
        User stored = userRepository.findById(created.getId()).orElseThrow();
        assertThat(stored.getDisplayName()).isEqualTo("새 닉네임");
        assertThat(stored.getEmail()).isEqualTo("groom@kakao.com");
    }

    @Test
    void 같은_고유_ID라도_공급자가_다르면_다른_회원이다() {
        User kakao = oauthLoginService.loginOrRegister(
                new OAuthUserProfile(OAuthProvider.KAKAO, "persist-test-2", null, null));
        User google = oauthLoginService.loginOrRegister(
                new OAuthUserProfile(OAuthProvider.GOOGLE, "persist-test-2", null, null));

        assertThat(kakao.getId()).isNotEqualTo(google.getId());
    }

    @Test
    void 리프레시_토큰_교체와_재사용_감지가_DB에서_동작한다() {
        User user = oauthLoginService.loginOrRegister(
                new OAuthUserProfile(OAuthProvider.GOOGLE, "persist-test-3", "신부", null));
        String first = refreshTokenService.issue(user.getId());

        RefreshTokenService.RotatedRefreshToken rotated = refreshTokenService.rotate(first);
        assertThat(rotated.userId()).isEqualTo(user.getId());

        // 이미 교체된 첫 토큰을 다시 쓰면 거절되고, 새로 받은 토큰까지 모두 폐기된다.
        assertThatThrownBy(() -> refreshTokenService.rotate(first))
                .isInstanceOf(InvalidRefreshTokenException.class);
        assertThatThrownBy(() -> refreshTokenService.rotate(rotated.rawToken()))
                .isInstanceOf(InvalidRefreshTokenException.class);
        assertThat(refreshTokenRepository.count()).isGreaterThanOrEqualTo(2);
    }
}
