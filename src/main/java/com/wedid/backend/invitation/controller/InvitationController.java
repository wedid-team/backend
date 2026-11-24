package com.wedid.backend.invitation.controller;

import com.wedid.backend.auth.web.LoginUser;
import com.wedid.backend.invitation.dto.InvitationAcceptRequest;
import com.wedid.backend.invitation.dto.InvitationAcceptResponse;
import com.wedid.backend.invitation.dto.InvitationCreateResponse;
import com.wedid.backend.invitation.dto.InvitationPreviewResponse;
import com.wedid.backend.invitation.service.InvitationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 짝꿍 초대.
 * <ul>
 *   <li>POST /api/v1/invitations: 초대 링크 발급 (로그인 필요, C-02)</li>
 *   <li>GET /api/v1/invitations/{token}: 링크 상태 확인. 로그인 없이 열 수 있고, 로그인했으면 내 계획 상태도 준다</li>
 *   <li>POST /api/v1/invitations/{token}/accept: 합류 (로그인 필요)</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/invitations")
public class InvitationController {

    private final InvitationService invitationService;

    public InvitationController(InvitationService invitationService) {
        this.invitationService = invitationService;
    }

    @PostMapping
    public ResponseEntity<InvitationCreateResponse> create(@AuthenticationPrincipal LoginUser loginUser) {
        return ResponseEntity.status(HttpStatus.CREATED).body(invitationService.create(loginUser.id()));
    }

    @GetMapping("/{token}")
    public InvitationPreviewResponse preview(
            @PathVariable String token,
            @AuthenticationPrincipal LoginUser loginUser
    ) {
        return invitationService.preview(token, loginUser == null ? null : loginUser.id());
    }

    @PostMapping("/{token}/accept")
    public InvitationAcceptResponse accept(
            @PathVariable String token,
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestBody(required = false) InvitationAcceptRequest request
    ) {
        boolean replaceMyPlan = request != null && request.confirmsReplace();
        return invitationService.accept(token, loginUser.id(), replaceMyPlan);
    }
}
