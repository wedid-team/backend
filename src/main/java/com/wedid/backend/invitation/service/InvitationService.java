package com.wedid.backend.invitation.service;

import com.wedid.backend.invitation.dto.InvitationAcceptResponse;
import com.wedid.backend.invitation.dto.InvitationCreateResponse;
import com.wedid.backend.invitation.dto.InvitationPreviewResponse;
import com.wedid.backend.invitation.dto.InvitationPreviewResponse.Status;
import com.wedid.backend.invitation.dto.InvitationPreviewResponse.ViewerPlan;
import com.wedid.backend.invitation.entity.PlanInvitation;
import com.wedid.backend.invitation.repository.PlanInvitationRepository;
import com.wedid.backend.plan.entity.Plan;
import com.wedid.backend.plan.entity.PlanMember;
import com.wedid.backend.plan.repository.PlanMemberRepository;
import com.wedid.backend.plan.repository.PlanRepository;
import com.wedid.backend.plan.service.PlanNotFoundException;
import com.wedid.backend.user.entity.User;
import com.wedid.backend.user.repository.UserRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * 짝꿍 초대 링크 발급 · 확인 · 합류(P-05 · P-06 · P-08 · P-09, db.md 4.24 · 5.2).
 * <ul>
 *   <li>링크는 여러 개 발급할 수 있고, 각각 7일 동안 쓸 수 있다. 누군가 합류하면 그 계획의 남은 링크를 모두 폐기한다.</li>
 *   <li>합류는 계획 행을 잠근 상태에서 만료 · 기존 연결 · 자기 초대를 검사하고,
 *       멤버 추가 · 토큰 소모 · 남은 링크 폐기를 한 트랜잭션으로 처리한다.</li>
 *   <li>혼자 쓰던 계획이 있는 사람은 교체에 동의해야 합류한다. 그 계획은 REPLACED로 두고 멤버십만 지운다.
 *       이미 다른 짝꿍과 연결된 사람은 교체하지 않는다.</li>
 *   <li>계획당 최대 2명(OWNER 1 · PARTNER 1)과 1인 1계획은 plan_members UNIQUE 제약이 한 번 더 막는다.</li>
 * </ul>
 */
@Service
public class InvitationService {

    private final PlanInvitationRepository invitationRepository;
    private final PlanRepository planRepository;
    private final PlanMemberRepository planMemberRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    public InvitationService(
            PlanInvitationRepository invitationRepository,
            PlanRepository planRepository,
            PlanMemberRepository planMemberRepository,
            UserRepository userRepository,
            Clock clock
    ) {
        this.invitationRepository = invitationRepository;
        this.planRepository = planRepository;
        this.planMemberRepository = planMemberRepository;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    /** 초대 링크 발급. 내 계획에 이미 짝꿍이 있으면 409. */
    @Transactional
    public InvitationCreateResponse create(UUID userId) {
        PlanMember member = planMemberRepository.findByUserId(userId)
                .orElseThrow(PlanNotFoundException::new);
        // 합류와 같은 계획 잠금: 발급과 합류가 엇갈려도 "짝꿍 있음" 검사가 어긋나지 않는다
        Plan plan = planRepository.findByIdAndStatusForUpdate(member.getPlanId(), Plan.Status.ACTIVE)
                .orElseThrow(PlanNotFoundException::new);
        if (hasPartner(planMemberRepository.findByPlanId(plan.getId()))) {
            throw InvitationConflictException.planAlreadyConnected();
        }

        // 이전에 발급한 링크는 그대로 둔다(만료 전이면 계속 쓸 수 있다)
        OffsetDateTime now = now();
        String rawToken = InvitationTokens.generate();
        PlanInvitation invitation = invitationRepository.save(
                PlanInvitation.create(plan.getId(), userId, InvitationTokens.hash(rawToken), now));
        return new InvitationCreateResponse(rawToken, invitation.getExpiresAt());
    }

    /**
     * 초대 링크 확인(로그인 없이도 열 수 있다). 없는 토큰은 410,
     * 그 외에는 상태와 로그인한 사람의 계획 상태를 돌려준다.
     */
    @Transactional(readOnly = true)
    public InvitationPreviewResponse preview(String rawToken, UUID viewerId) {
        PlanInvitation invitation = findByToken(rawToken);
        OffsetDateTime now = now();
        Optional<Plan> plan = planRepository.findByIdAndStatus(invitation.getPlanId(), Plan.Status.ACTIVE);
        List<PlanMember> members = plan.map(p -> planMemberRepository.findByPlanId(p.getId())).orElse(List.of());

        Status status = statusOf(invitation, plan.isPresent(), members, viewerId, now);
        boolean showInviter = status == Status.VALID || status == Status.ALREADY_MEMBER;
        String inviterName = showInviter
                ? userRepository.findById(invitation.getInvitedBy()).map(User::getDisplayName).orElse(null)
                : null;
        ViewerPlan viewerPlan = viewerId == null || status == Status.ALREADY_MEMBER ? null : viewerPlanOf(viewerId);

        return new InvitationPreviewResponse(status, inviterName, invitation.getExpiresAt(), viewerPlan);
    }

    /** 초대 합류. 성공하면 초대한 계획의 PARTNER가 된다. */
    @Transactional
    public InvitationAcceptResponse accept(String rawToken, UUID userId, boolean replaceMyPlan) {
        UUID targetPlanId = invitationRepository.findPlanIdByTokenHash(hashOf(rawToken))
                .orElseThrow(InvitationExpiredException::new);
        Optional<PlanMember> myMembership = planMemberRepository.findByUserId(userId);

        // 두 사람이 서로의 링크로 동시에 합류해도 교착되지 않도록 계획 ID 순서로 잠근다
        Optional<UUID> myPlanId = myMembership.map(PlanMember::getPlanId).filter(id -> !id.equals(targetPlanId));
        Plan target = null;
        Plan mine = null;
        if (myPlanId.isPresent() && myPlanId.get().compareTo(targetPlanId) < 0) {
            mine = lockActive(myPlanId.get()).orElse(null);
            target = lockActive(targetPlanId).orElse(null);
        } else {
            target = lockActive(targetPlanId).orElse(null);
            mine = myPlanId.flatMap(this::lockActive).orElse(null);
        }

        // 발급 · 합류는 모두 계획 잠금 아래에서 링크를 바꾸므로, 잠근 뒤에 읽어야 최신 상태다
        PlanInvitation invitation = findByToken(rawToken);
        OffsetDateTime now = now();
        List<PlanMember> targetMembers = target == null ? List.of() : planMemberRepository.findByPlanId(target.getId());
        switch (statusOf(invitation, target != null, targetMembers, userId, now)) {
            case EXPIRED -> throw new InvitationExpiredException();
            case ALREADY_MEMBER -> throw InvitationConflictException.alreadyMember();
            case ALREADY_CONNECTED -> throw InvitationConflictException.planAlreadyConnected();
            case VALID -> {
            }
        }

        UUID replacedPlanId = null;
        if (mine != null) {
            List<PlanMember> myPlanMembers = planMemberRepository.findByPlanId(mine.getId());
            if (hasPartner(myPlanMembers)) {
                throw InvitationConflictException.viewerAlreadyConnected();
            }
            if (!replaceMyPlan) {
                throw InvitationConflictException.replaceConfirmationRequired();
            }
            replacedPlanId = replace(mine, myPlanMembers, now);
        } else if (myMembership.isPresent() && !myMembership.get().getPlanId().equals(targetPlanId)) {
            // 멤버십은 있는데 활성 계획이 아니다(잠그기 전에 바뀜). UNIQUE 위반으로 실패하기 전에 막는다
            throw InvitationConflictException.viewerAlreadyConnected();
        }

        PlanMember partner;
        try {
            partner = planMemberRepository.saveAndFlush(PlanMember.partner(target.getId(), userId, now));
        } catch (DataIntegrityViolationException e) {
            throw toConflict(e);
        }
        invitation.accept(userId, now);
        // 합류하면 이 계획의 남은 링크를 모두 즉시 쓸 수 없게 한다(P-09)
        revokePending(target.getId(), now);

        return new InvitationAcceptResponse(
                target.getId(), PlanMember.Role.PARTNER.name(), partner.getJoinedAt(), replacedPlanId);
    }

    /**
     * 링크 상태. 순서: 이미 멤버 → 계획에 짝꿍 있음 → 만료 · 폐기 · 사용됨 · 계획 없음 → 사용 가능.
     * (이미 수락된 링크라도 계획에 짝꿍이 있으면 "이미 연결된 계획"으로 안내한다)
     */
    private static Status statusOf(
            PlanInvitation invitation,
            boolean planActive,
            List<PlanMember> members,
            UUID viewerId,
            OffsetDateTime now
    ) {
        if (!planActive) {
            return Status.EXPIRED;
        }
        if (viewerId != null && members.stream().anyMatch(m -> m.getUserId().equals(viewerId))) {
            return Status.ALREADY_MEMBER;
        }
        if (hasPartner(members)) {
            return Status.ALREADY_CONNECTED;
        }
        return invitation.isUsable(now) ? Status.VALID : Status.EXPIRED;
    }

    private ViewerPlan viewerPlanOf(UUID viewerId) {
        Optional<PlanMember> member = planMemberRepository.findByUserId(viewerId);
        if (member.isEmpty()
                || planRepository.findByIdAndStatus(member.get().getPlanId(), Plan.Status.ACTIVE).isEmpty()) {
            return ViewerPlan.NONE;
        }
        return hasPartner(planMemberRepository.findByPlanId(member.get().getPlanId()))
                ? ViewerPlan.HAS_PARTNER
                : ViewerPlan.HAS_PLAN;
    }

    /** 혼자 쓰던 계획을 REPLACED로 두고 내 멤버십을 지운다. 그 계획의 남은 링크도 폐기한다. */
    private UUID replace(Plan plan, List<PlanMember> members, OffsetDateTime now) {
        plan.replace(now);
        revokePending(plan.getId(), now);
        planMemberRepository.deleteAll(members);
        // 새 멤버십을 넣기 전에 지워야 plan_members(user_id) UNIQUE에 걸리지 않는다
        planMemberRepository.flush();
        return plan.getId();
    }

    private void revokePending(UUID planId, OffsetDateTime now) {
        invitationRepository.findPendingByPlanId(planId).forEach(invitation -> invitation.revoke(now));
    }

    private PlanInvitation findByToken(String rawToken) {
        return invitationRepository.findByTokenHash(hashOf(rawToken))
                .orElseThrow(InvitationExpiredException::new);
    }

    private static String hashOf(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new InvitationExpiredException();
        }
        return InvitationTokens.hash(rawToken);
    }

    private Optional<Plan> lockActive(UUID planId) {
        return planRepository.findByIdAndStatusForUpdate(planId, Plan.Status.ACTIVE);
    }

    private static boolean hasPartner(List<PlanMember> members) {
        return members.stream().anyMatch(m -> m.getRole() == PlanMember.Role.PARTNER);
    }

    /** plan_members UNIQUE 위반을 409로 바꾼다. 다른 무결성 오류는 그대로 던진다. */
    private static RuntimeException toConflict(DataIntegrityViolationException e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation && violation.getConstraintName() != null) {
                return switch (violation.getConstraintName().toLowerCase(Locale.ROOT)) {
                    case "uq_plan_members_plan_role" -> InvitationConflictException.planAlreadyConnected();
                    case "uq_plan_members_user", "uq_plan_members_plan_user" ->
                            InvitationConflictException.viewerAlreadyConnected();
                    default -> e;
                };
            }
        }
        return e;
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock);
    }
}
