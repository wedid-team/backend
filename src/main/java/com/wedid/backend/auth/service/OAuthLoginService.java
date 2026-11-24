package com.wedid.backend.auth.service;

import com.wedid.backend.auth.oauth.OAuthUserProfile;
import com.wedid.backend.user.entity.OAuthAccount;
import com.wedid.backend.user.entity.User;
import com.wedid.backend.user.repository.OAuthAccountRepository;
import com.wedid.backend.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;

/**
 * 소셜 로그인 성공 시 회원을 조회하거나, 처음이면 별도 가입 단계 없이 바로 만든다(C-09).
 * 회원 식별은 (provider, provider_subject)로만 하며 이메일로 계정을 합치지 않는다.
 */
@Service
public class OAuthLoginService {

    private final UserRepository userRepository;
    private final OAuthAccountRepository oauthAccountRepository;
    private final Clock clock;

    public OAuthLoginService(
            UserRepository userRepository,
            OAuthAccountRepository oauthAccountRepository,
            Clock clock
    ) {
        this.userRepository = userRepository;
        this.oauthAccountRepository = oauthAccountRepository;
        this.clock = clock;
    }

    @Transactional
    public User loginOrRegister(OAuthUserProfile profile) {
        OffsetDateTime now = OffsetDateTime.now(clock);

        return oauthAccountRepository
                .findByProviderAndProviderSubject(profile.provider(), profile.subject())
                .map(account -> {
                    User user = userRepository.findById(account.getUserId())
                            .orElseThrow(() -> new IllegalStateException(
                                    "소셜 계정에 연결된 회원이 없습니다. userId=" + account.getUserId()));
                    user.updateProfile(profile.displayName(), profile.email(), now);
                    return user;
                })
                .orElseGet(() -> register(profile, now));
    }

    private User register(OAuthUserProfile profile, OffsetDateTime now) {
        User user = userRepository.save(User.create(profile.displayName(), profile.email(), now));
        // 같은 계정으로 동시에 첫 로그인하면 UNIQUE(provider, provider_subject) 위반으로 이 트랜잭션은 롤백된다.
        // 호출하는 쪽(OAuthLoginSuccessHandler)에서 한 번 더 시도하면 먼저 만들어진 회원으로 로그인된다.
        oauthAccountRepository.saveAndFlush(
                new OAuthAccount(user.getId(), profile.provider(), profile.subject()));
        return user;
    }
}
