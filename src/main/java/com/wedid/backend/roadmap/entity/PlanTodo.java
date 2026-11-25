package com.wedid.backend.roadmap.entity;

import com.wedid.backend.global.entity.AssignedIdEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/** 계획별 기본 할 일 상태. 계획 생성 때 템플릿에서 만들고, 조건이 바뀌어도 그대로 둔다(P-02). */
@Entity
@Table(name = "plan_todos", schema = "public")
public class PlanTodo extends AssignedIdEntity {

    /**
     * 담당 저장 방식(P-07 · db.md 5.3). "나 / 짝꿍"은 보는 사람에 따라 달라지므로 회원 ID로 저장한다.
     * USER이면 assigned_user_id가 있고, UNASSIGNED · BOTH이면 없다(sql/030 ck_plan_todos_assignment).
     */
    public enum AssignmentKind {
        UNASSIGNED, USER, BOTH
    }

    @Id
    private UUID id;

    @Column(name = "plan_step_id", nullable = false, updatable = false)
    private UUID planStepId;

    @Column(name = "todo_template_id", nullable = false, updatable = false)
    private Long todoTemplateId;

    @Column(name = "title_snapshot", nullable = false, updatable = false)
    private String titleSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(name = "assignment_kind", nullable = false)
    private AssignmentKind assignmentKind;

    @Column(name = "assigned_user_id")
    private UUID assignedUserId;

    @Column(name = "is_completed", nullable = false)
    private boolean completed;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "updated_by", nullable = false)
    private UUID updatedBy;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected PlanTodo() {
    }

    public static PlanTodo create(UUID planStepId, TodoTemplate template, UUID userId, OffsetDateTime now) {
        PlanTodo todo = new PlanTodo();
        todo.id = UUID.randomUUID();
        todo.planStepId = planStepId;
        todo.todoTemplateId = template.getId();
        todo.titleSnapshot = template.getTitle();
        todo.assignmentKind = AssignmentKind.UNASSIGNED;
        todo.completed = false;
        todo.updatedBy = userId;
        todo.updatedAt = now;
        return todo;
    }

    /** 담당을 바꾼다. assignedUserId는 USER일 때만 쓴다. 같은 담당이면 수정 기록을 남기지 않는다. */
    public void assign(AssignmentKind kind, UUID assignedUserId, UUID userId, OffsetDateTime now) {
        UUID assignee = kind == AssignmentKind.USER ? assignedUserId : null;
        if (kind == AssignmentKind.USER && assignee == null) {
            throw new IllegalArgumentException("담당 회원이 필요합니다.");
        }
        if (this.assignmentKind == kind && Objects.equals(this.assignedUserId, assignee)) {
            return;
        }
        this.assignmentKind = kind;
        this.assignedUserId = assignee;
        touch(userId, now);
    }

    /** 완료 · 미완료를 바꾼다. 같은 상태면 처음 체크한 시각과 수정 기록을 그대로 둔다. */
    public void markCompleted(boolean completed, UUID userId, OffsetDateTime now) {
        if (this.completed == completed) {
            return;
        }
        this.completed = completed;
        this.completedAt = completed ? now : null;
        touch(userId, now);
    }

    private void touch(UUID userId, OffsetDateTime now) {
        this.updatedBy = userId;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getPlanStepId() {
        return planStepId;
    }

    public Long getTodoTemplateId() {
        return todoTemplateId;
    }

    public boolean isCompleted() {
        return completed;
    }

    public OffsetDateTime getCompletedAt() {
        return completedAt;
    }

    public String getTitleSnapshot() {
        return titleSnapshot;
    }

    public AssignmentKind getAssignmentKind() {
        return assignmentKind;
    }

    public UUID getAssignedUserId() {
        return assignedUserId;
    }

    public UUID getUpdatedBy() {
        return updatedBy;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
