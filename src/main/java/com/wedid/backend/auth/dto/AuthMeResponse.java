package com.wedid.backend.auth.dto;

import java.util.UUID;

/**
 * 로그인 상태 확인 응답. 비로그인이면 authenticated=false, user=null.
 * displayName이 null이면 화면에서 이름 없이 "다시 오셨네요!"만 표시한다(C-06).
 */
public record AuthMeResponse(boolean authenticated, UserInfo user) {

    public static AuthMeResponse anonymous() {
        return new AuthMeResponse(false, null);
    }

    public static AuthMeResponse of(UUID id, String displayName, String email) {
        return new AuthMeResponse(true, new UserInfo(id, displayName, email));
    }

    public record UserInfo(UUID id, String displayName, String email) {
    }
}
