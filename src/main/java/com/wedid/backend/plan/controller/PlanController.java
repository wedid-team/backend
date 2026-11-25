package com.wedid.backend.plan.controller;

import com.wedid.backend.auth.web.LoginUser;
import com.wedid.backend.plan.dto.PlanRequest;
import com.wedid.backend.plan.dto.PlanResponse;
import com.wedid.backend.plan.service.PlanService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * 계획 저장 · 조회 · 덮어쓰기. 모두 로그인 필요(C-02, SecurityConfig의 나머지 API 인증 규칙).
 * <ul>
 *   <li>POST /api/v1/plans: 첫 계획 저장 (로그인 후 비회원 입력값 이전 포함, C-05). 이미 있으면 409 (C-10)</li>
 *   <li>GET /api/v1/plans/me: 내 계획 조회. 없으면 404</li>
 *   <li>PUT /api/v1/plans/me: 조건 덮어쓰기 · 스드메 예산대 변경</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/plans")
public class PlanController {

    private final PlanService planService;

    public PlanController(PlanService planService) {
        this.planService = planService;
    }

    @PostMapping
    public ResponseEntity<PlanResponse> create(
            @AuthenticationPrincipal LoginUser loginUser,
            @Valid @RequestBody PlanRequest request
    ) {
        PlanResponse response = planService.create(loginUser.id(), request);
        return ResponseEntity.created(URI.create("/api/v1/plans/me")).body(response);
    }

    @GetMapping("/me")
    public PlanResponse getMine(@AuthenticationPrincipal LoginUser loginUser) {
        return planService.getMine(loginUser.id());
    }

    @PutMapping("/me")
    public PlanResponse overwriteMine(
            @AuthenticationPrincipal LoginUser loginUser,
            @Valid @RequestBody PlanRequest request
    ) {
        return planService.overwriteMine(loginUser.id(), request);
    }
}
