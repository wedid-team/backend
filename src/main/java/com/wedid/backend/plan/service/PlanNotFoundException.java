package com.wedid.backend.plan.service;

/** 로그인한 회원에게 저장된 계획이 없다. 404로 응답한다. */
public class PlanNotFoundException extends RuntimeException {

    public PlanNotFoundException() {
        super("저장된 계획이 없어요.");
    }
}
