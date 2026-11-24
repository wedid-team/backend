package com.wedid.backend.invitation.dto;

/**
 * 초대 합류 요청. 본문은 생략할 수 있다.
 *
 * @param replaceMyPlan 내 계획이 있을 때 "합류하면 내 계획은 사라져요"에 동의했는지(P-05). 계획이 없으면 무시한다
 */
public record InvitationAcceptRequest(
        Boolean replaceMyPlan
) {

    public boolean confirmsReplace() {
        return Boolean.TRUE.equals(replaceMyPlan);
    }
}
