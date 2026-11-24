package com.wedid.backend.user.entity;

import java.util.Arrays;
import java.util.Locale;

/** oauth_accounts.provider 값. Spring Security registrationId(kakao, google)와 1:1로 대응한다. */
public enum OAuthProvider {
    KAKAO,
    GOOGLE;

    public static OAuthProvider fromRegistrationId(String registrationId) {
        return Arrays.stream(values())
                .filter(provider -> provider.registrationId().equals(registrationId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "지원하지 않는 로그인 방식입니다: " + registrationId));
    }

    public String registrationId() {
        return name().toLowerCase(Locale.ROOT);
    }
}
