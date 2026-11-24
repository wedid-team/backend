package com.wedid.backend.roadmap.controller;

import com.wedid.backend.auth.web.LoginUser;
import com.wedid.backend.roadmap.dto.BudgetStatusResponse;
import com.wedid.backend.roadmap.dto.ContractAmountRequest;
import com.wedid.backend.roadmap.dto.RoadmapStepResponse;
import com.wedid.backend.roadmap.dto.StepCompletionRequest;
import com.wedid.backend.roadmap.dto.TodoResponse;
import com.wedid.backend.roadmap.dto.TodoUpdateRequest;
import com.wedid.backend.roadmap.service.RoadmapService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * 로드맵 진행. 모두 로그인 필요(C-02, SecurityConfig의 나머지 API 인증 규칙).
 * <ul>
 *   <li>PATCH /api/v1/plans/me/tasks/{taskId}: 할 일 완료 · 미완료, 담당 변경</li>
 *   <li>PATCH /api/v1/plans/me/roadmap/steps/{stepId}: 단계 완료 · 완료 취소</li>
 *   <li>PUT /api/v1/plans/me/contracts/{stepId}: 예식장 · 스드메 실제 계약 금액 입력 · 수정</li>
 *   <li>GET /api/v1/plans/me/budget-status: 남은 예산 판정</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/plans/me")
public class RoadmapController {

    private final RoadmapService roadmapService;

    public RoadmapController(RoadmapService roadmapService) {
        this.roadmapService = roadmapService;
    }

    @PatchMapping("/tasks/{taskId}")
    public TodoResponse updateTodo(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID taskId,
            @Valid @RequestBody TodoUpdateRequest request
    ) {
        return roadmapService.updateTodo(loginUser.id(), taskId, request);
    }

    @PatchMapping("/roadmap/steps/{stepId}")
    public RoadmapStepResponse updateStepCompletion(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID stepId,
            @Valid @RequestBody StepCompletionRequest request
    ) {
        return roadmapService.updateStepCompletion(loginUser.id(), stepId, request.completed());
    }

    @PutMapping("/contracts/{stepId}")
    public RoadmapStepResponse recordContractAmount(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID stepId,
            @Valid @RequestBody ContractAmountRequest request
    ) {
        return roadmapService.recordContractAmount(loginUser.id(), stepId, request.actualAmount());
    }

    @GetMapping("/budget-status")
    public BudgetStatusResponse getBudgetStatus(@AuthenticationPrincipal LoginUser loginUser) {
        return roadmapService.getBudgetStatus(loginUser.id());
    }
}
