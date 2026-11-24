package com.wedid.backend.roadmap.entity;

import com.wedid.backend.global.entity.AssignedIdEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/** 계획별 기본 할 일 상태. 계획 생성 때 템플릿에서 만들고, 조건이 바뀌어도 그대로 둔다(P-02). */
@Entity
@Table(name = "plan_todos", schema = "public")
public class PlanTodo extends AssignedIdEntity {

    @Id
    private UUID id;

    @Column(name = "plan_step_id", nullable = false, updatable = false)
    private UUID planStepId;

    @Column(name = "todo_template_id", nullable = false, updatable = false)
    private Long todoTemplateId;

    @Column(name = "title_snapshot", nullable = false, updatable = false)
    private String titleSnapshot;

    @Column(name = "assignment_kind", nullable = false)
    private String assignmentKind;

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
        todo.assignmentKind = "UNASSIGNED";
        todo.completed = false;
        todo.updatedBy = userId;
        todo.updatedAt = now;
        return todo;
    }

    public UUID getId() {
        return id;
    }

    public UUID getPlanStepId() {
        return planStepId;
    }

    public boolean isCompleted() {
        return completed;
    }

    public OffsetDateTime getCompletedAt() {
        return completedAt;
    }
}
