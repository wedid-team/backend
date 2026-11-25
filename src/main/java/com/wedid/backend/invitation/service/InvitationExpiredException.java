package com.wedid.backend.invitation.service;

/** 없는 · 만료된 · 이미 쓴 · 폐기된 초대 링크, 또는 초대한 계획이 없다(P-08). 410으로 응답한다. */
public class InvitationExpiredException extends RuntimeException {

    public static final String CODE = "INVITATION_EXPIRED";

    public InvitationExpiredException() {
        super("초대 링크가 만료됐어요.");
    }
}
