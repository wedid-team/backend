-- 계획별 기본 할 일 상태
CREATE TABLE public.plan_todos (
    id UUID PRIMARY KEY,
    plan_step_id UUID NOT NULL,
    todo_template_id BIGINT NOT NULL,
    title_snapshot TEXT NOT NULL,
    assignment_kind TEXT NOT NULL,
    assigned_user_id UUID,
    is_completed BOOLEAN NOT NULL,
    completed_at TIMESTAMPTZ,
    updated_by UUID NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_plan_todos_step_template
        UNIQUE (plan_step_id, todo_template_id),

    CONSTRAINT fk_plan_todos_plan_step
        FOREIGN KEY (plan_step_id)
        REFERENCES public.plan_steps (id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_plan_todos_todo_template
        FOREIGN KEY (todo_template_id)
        REFERENCES public.todo_templates (id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_plan_todos_assigned_user
        FOREIGN KEY (assigned_user_id)
        REFERENCES public.users (id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_plan_todos_updated_by
        FOREIGN KEY (updated_by)
        REFERENCES public.users (id)
        ON DELETE RESTRICT,

    CONSTRAINT ck_plan_todos_assignment
        CHECK (
            (
                assignment_kind = 'USER'
                AND assigned_user_id IS NOT NULL
            )
            OR
            (
                assignment_kind IN ('UNASSIGNED', 'BOTH')
                AND assigned_user_id IS NULL
            )
        ),

    CONSTRAINT ck_plan_todos_completion
        CHECK (
            (is_completed = TRUE AND completed_at IS NOT NULL)
            OR
            (is_completed = FALSE AND completed_at IS NULL)
        )
);

CREATE INDEX idx_plan_todos_todo_template_id
    ON public.plan_todos (todo_template_id);

CREATE INDEX idx_plan_todos_assigned_user_id
    ON public.plan_todos (assigned_user_id);

CREATE INDEX idx_plan_todos_updated_by
    ON public.plan_todos (updated_by);