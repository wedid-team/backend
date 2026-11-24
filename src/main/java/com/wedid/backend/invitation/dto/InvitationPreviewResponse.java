package com.wedid.backend.invitation.dto;

import java.time.OffsetDateTime;

/**
 * 초대 링크를 연 화면(SCR-11-B)에서 보여줄 상태.
 *
 * @param status      링크 상태
 * @param inviterName 초대한 사람 이름. 링크를 쓸 수 있을 때(VALID · ALREADY_MEMBER)만 준다
 * @param expiresAt   링크 만료 시각
 * @param viewerPlan  로그인한 사람의 현재 계획 상태. 로그인하지 않았거나 이미 이 계획의 멤버면 null
 */
public record InvitationPreviewResponse(
        Status status,
        String inviterName,
        OffsetDateTime expiresAt,
        ViewerPlan viewerPlan
) {

    public enum Status {
        /** 합류할 수 있다. */
        VALID,
        /** 만료 · 폐기 · 이미 사용됨 · 초대한 계획 없음 → "초대 링크가 만료됐어요" */
        EXPIRED,
        /** 초대한 계획에 이미 짝꿍이 있다 → "이미 짝꿍과 연결된 계획이에요" */
        ALREADY_CONNECTED,
        /** 연 사람이 이미 이 계획의 멤버(자기 링크 · 이미 합류) → 안내 후 로드맵 */
        ALREADY_MEMBER
    }

    public enum ViewerPlan {
        /** 계획 없음 → [함께하기] */
        NONE,
        /** 혼자 쓰는 계획 있음 → "합류하면 내 계획은 사라져요" 확인 후 합류(P-05) */
        HAS_PLAN,
        /** 이미 다른 짝꿍과 연결됨 → 합류 불가 */
        HAS_PARTNER
    }
}
