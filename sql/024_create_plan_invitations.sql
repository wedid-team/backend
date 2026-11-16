-- 짝꿍 초대 링크
CREATE TABLE public.plan_invitations (
    id UUID PRIMARY KEY,
    plan_id UUID NOT NULL,
    invited_by UUID NOT NULL,
    token_hash TEXT NOT NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ NOT NULL,
    accepted_by UUID,
    accepted_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,

    CONSTRAINT uq_plan_invitations_token_hash
        UNIQUE (token_hash),

    CONSTRAINT fk_plan_invitations_plan
        FOREIGN KEY (plan_id)
        REFERENCES public.plans (id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_plan_invitations_invited_by
        FOREIGN KEY (invited_by)
        REFERENCES public.users (id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_plan_invitations_accepted_by
        FOREIGN KEY (accepted_by)
        REFERENCES public.users (id)
        ON DELETE RESTRICT,

    CONSTRAINT ck_plan_invitations_expiry
        CHECK (expires_at > created_at),

    CONSTRAINT ck_plan_invitations_acceptance
        CHECK (
            (accepted_by IS NULL AND accepted_at IS NULL)
            OR
            (accepted_by IS NOT NULL AND accepted_at IS NOT NULL)
        )
);

CREATE INDEX idx_plan_invitations_plan_id
    ON public.plan_invitations (plan_id);

CREATE INDEX idx_plan_invitations_invited_by
    ON public.plan_invitations (invited_by);

CREATE INDEX idx_plan_invitations_accepted_by
    ON public.plan_invitations (accepted_by);