package com.wedid.backend.invitation.service;

/**
 * 지금 상태로는 초대 · 합류할 수 없다(이미 연결됨 · 내 계획 교체 확인 필요 등). 409로 응답한다.
 * 프론트엔드는 메시지 대신 {@link #getCode()}로 분기한다.
 */
public class InvitationConflictException extends RuntimeException {

    private final String code;

    private InvitationConflictException(String code, String message) {
        super(message);
        this.code = code;
    }

    /** 초대한 계획에 이미 짝꿍이 있다(P-08). */
    public static InvitationConflictException planAlreadyConnected() {
        return new InvitationConflictException("PLAN_ALREADY_CONNECTED", "이미 짝꿍과 연결된 계획이에요.");
    }

    /** 초대한 사람 본인이거나 이미 합류한 짝꿍이 다시 열었다(P-08 자기 링크). */
    public static InvitationConflictException alreadyMember() {
        return new InvitationConflictException("ALREADY_MEMBER", "이미 함께하고 있는 계획이에요.");
    }

    /** 합류하려는 사람이 이미 다른 짝꿍과 연결돼 있다. 기존 연결은 교체하지 않는다(db.md 5.2). */
    public static InvitationConflictException viewerAlreadyConnected() {
        return new InvitationConflictException("VIEWER_ALREADY_CONNECTED",
                "이미 짝꿍과 연결되어 있어요. 연결을 해제한 뒤 합류할 수 있어요.");
    }

    /** 합류하려는 사람에게 계획이 있는데 교체 확인을 받지 않았다(P-05). */
    public static InvitationConflictException replaceConfirmationRequired() {
        return new InvitationConflictException("REPLACE_CONFIRMATION_REQUIRED", "합류하면 내 계획은 사라져요.");
    }

    public String getCode() {
        return code;
    }
}
