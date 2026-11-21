package com.wedid.backend.auth.oauth;

import com.wedid.backend.user.entity.OAuthProvider;

import java.util.Map;

/**
 * 소셜 서비스가 돌려준 사용자 정보 중 저장 대상만 추린 값 (C-11: 계정 ID · 이름 · 이메일만 수집).
 * 이메일은 공급자가 인증된 이메일이라고 알려준 경우에만 담는다.
 */
public record OAuthUserProfile(
        OAuthProvider provider,
        String subject,
        String displayName,
        String email
) {

    public OAuthUserProfile {
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("소셜 계정 고유 ID가 없습니다.");
        }
        displayName = blankToNull(displayName);
        email = blankToNull(email);
    }

    public static OAuthUserProfile of(String registrationId, Map<String, Object> attributes) {
        OAuthProvider provider = OAuthProvider.fromRegistrationId(registrationId);
        return switch (provider) {
            case KAKAO -> fromKakao(attributes);
            case GOOGLE -> fromGoogle(attributes);
        };
    }

    /**
     * 카카오 /v2/user/me 응답.
     * { "id": 123, "kakao_account": { "profile": { "nickname": "..." }, "email": "...",
     *   "is_email_valid": true, "is_email_verified": true } }
     */
    private static OAuthUserProfile fromKakao(Map<String, Object> attributes) {
        Object id = attributes.get("id");
        Map<?, ?> account = asMap(attributes.get("kakao_account"));
        Map<?, ?> profile = asMap(account.get("profile"));

        String email = Boolean.TRUE.equals(account.get("is_email_valid"))
                && Boolean.TRUE.equals(account.get("is_email_verified"))
                ? asString(account.get("email"))
                : null;

        return new OAuthUserProfile(
                OAuthProvider.KAKAO,
                id == null ? null : String.valueOf(id),
                asString(profile.get("nickname")),
                email
        );
    }

    /** 구글 OpenID Connect 사용자 정보. sub · name · email · email_verified */
    private static OAuthUserProfile fromGoogle(Map<String, Object> attributes) {
        String email = Boolean.TRUE.equals(attributes.get("email_verified"))
                ? asString(attributes.get("email"))
                : null;

        return new OAuthUserProfile(
                OAuthProvider.GOOGLE,
                asString(attributes.get("sub")),
                asString(attributes.get("name")),
                email
        );
    }

    private static Map<?, ?> asMap(Object value) {
        return value instanceof Map<?, ?> map ? map : Map.of();
    }

    private static String asString(Object value) {
        return value instanceof String string ? string : null;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
