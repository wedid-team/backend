-- 계획별 로드맵 단계와 실제 계약금액
CREATE TABLE public.plan_steps (
    id UUID PRIMARY KEY,
    plan_id UUID NOT NULL,
    step_template_id SMALLINT NOT NULL,
    recommended_start_date DATE NOT NULL,
    recommended_end_date DATE NOT NULL,

    estimated_min NUMERIC(18, 2),
    estimated_max NUMERIC(18, 2),
    estimate_run_id UUID,
    actual_amount NUMERIC(18, 0),

    completed_at TIMESTAMPTZ,
    completed_by UUID,
    overdue_reviewed_for TIMESTAMPTZ,
    updated_by UUID NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_plan_steps_plan_template
        UNIQUE (plan_id, step_template_id),

    CONSTRAINT fk_plan_steps_plan
        FOREIGN KEY (plan_id)
        REFERENCES public.plans (id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_plan_steps_template
        FOREIGN KEY (step_template_id)
        REFERENCES public.roadmap_step_templates (id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_plan_steps_estimate_run
        FOREIGN KEY (estimate_run_id)
        REFERENCES public.estimate_runs (id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_plan_steps_completed_by
        FOREIGN KEY (completed_by)
        REFERENCES public.users (id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_plan_steps_updated_by
        FOREIGN KEY (updated_by)
        REFERENCES public.users (id)
        ON DELETE RESTRICT,

    CONSTRAINT ck_plan_steps_dates
        CHECK (recommended_start_date <= recommended_end_date),

    CONSTRAINT ck_plan_steps_actual_amount
        CHECK (actual_amount IS NULL OR actual_amount >= 0),

    CONSTRAINT ck_plan_steps_completion
        CHECK (
            (completed_at IS NULL AND completed_by IS NULL)
            OR
            (completed_at IS NOT NULL AND completed_by IS NOT NULL)
        )
);

CREATE INDEX idx_plan_steps_step_template_id
    ON public.plan_steps (step_template_id);

CREATE INDEX idx_plan_steps_estimate_run_id
    ON public.plan_steps (estimate_run_id);

CREATE INDEX idx_plan_steps_completed_by
    ON public.plan_steps (completed_by);

CREATE INDEX idx_plan_steps_updated_by
    ON public.plan_steps (updated_by);