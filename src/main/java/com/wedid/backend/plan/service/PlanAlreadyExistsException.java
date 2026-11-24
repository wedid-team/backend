package com.wedid.backend.plan.service;

/**
 * 이미 계획이 있는 회원이 새 계획을 만들려고 했다(P-01 부부당 계획 1개). 409로 응답한다.
 * 프론트엔드는 계획 선택 모달(C-10)을 띄우고, [방금 만든 계획으로 바꾸기]면 PUT /api/v1/plans/me로 덮어쓴다.
 */
public class PlanAlreadyExistsException extends RuntimeException {

    public PlanAlreadyExistsException() {
        super("이미 저장된 계획이 있어요.");
    }
}
