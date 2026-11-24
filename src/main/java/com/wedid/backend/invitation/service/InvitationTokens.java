package com.wedid.backend.invitation.service;

import org.apache.commons.codec.digest.DigestUtils;

import java.security.SecureRandom;
import java.util.Base64;

/** 초대 토큰. 평문은 링크로만 전달하고 DB에는 SHA-256 해시만 저장한다(db.md 4.24). */
final class InvitationTokens {

    private static final int TOKEN_BYTES = 32;
    private static final SecureRandom RANDOM = new SecureRandom();

    private InvitationTokens() {
    }

    static String generate() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hash(String rawToken) {
        return DigestUtils.sha256Hex(rawToken);
    }
}
