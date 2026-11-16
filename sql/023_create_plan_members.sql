-- 계획 참여자
CREATE TABLE public.plan_members (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    plan_id UUID NOT NULL,
    user_id UUID NOT NULL,
    role TEXT NOT NULL,
    joined_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_plan_members_user
        UNIQUE (user_id),

    CONSTRAINT uq_plan_members_plan_role
        UNIQUE (plan_id, role),

    CONSTRAINT uq_plan_members_plan_user
        UNIQUE (plan_id, user_id),

    CONSTRAINT fk_plan_members_plan
        FOREIGN KEY (plan_id)
        REFERENCES public.plans (id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_plan_members_user
        FOREIGN KEY (user_id)
        REFERENCES public.users (id)
        ON DELETE RESTRICT,

    CONSTRAINT ck_plan_members_role
        CHECK (role IN ('OWNER', 'PARTNER'))
);