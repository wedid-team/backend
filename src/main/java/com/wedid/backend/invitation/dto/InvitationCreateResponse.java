package com.wedid.backend.invitation.dto;

import java.time.OffsetDateTime;

/** 새 초대 링크. 토큰 평문은 이 응답에서만 받을 수 있다(DB에는 해시만 저장). */
public record InvitationCreateResponse(
        String token,
        OffsetDateTime expiresAt
) {
}
