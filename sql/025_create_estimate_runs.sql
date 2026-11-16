-- 계산 결과의 고정 기록
CREATE TABLE public.estimate_runs (
    id UUID PRIMARY KEY,
    plan_id UUID,
    created_by UUID,
    policy_id BIGINT NOT NULL,
    band_id BIGINT NOT NULL,
    region_id SMALLINT NOT NULL,

    guest_access_token_hash TEXT,
    guest_expires_at TIMESTAMPTZ,

    input_snapshot JSONB NOT NULL,
    result_snapshot JSONB NOT NULL,
    source_snapshot JSONB NOT NULL,

    venue_min NUMERIC(18, 2),
    venue_max NUMERIC(18, 2),
    sdm_min NUMERIC(18, 2),
    sdm_max NUMERIC(18, 2),
    total_min NUMERIC(18, 2),
    total_max NUMERIC(18, 2),

    calculation_status TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_estimate_runs_plan
        FOREIGN KEY (plan_id)
        REFERENCES public.plans (id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_estimate_runs_created_by
        FOREIGN KEY (created_by)
        REFERENCES public.users (id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_estimate_runs_policy
        FOREIGN KEY (policy_id)
        REFERENCES public.calculation_policies (id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_estimate_runs_band
        FOREIGN KEY (band_id)
        REFERENCES public.sdm_budget_bands (id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_estimate_runs_region
        FOREIGN KEY (region_id)
        REFERENCES public.regions (id)
        ON DELETE RESTRICT,

    CONSTRAINT ck_estimate_runs_status
        CHECK (
            calculation_status IN ('COMPLETE', 'PARTIAL', 'UNAVAILABLE')
        )
);

CREATE INDEX idx_estimate_runs_plan_id
    ON public.estimate_runs (plan_id);

CREATE INDEX idx_estimate_runs_created_by
    ON public.estimate_runs (created_by);

CREATE INDEX idx_estimate_runs_policy_id
    ON public.estimate_runs (policy_id);

CREATE INDEX idx_estimate_runs_band_id
    ON public.estimate_runs (band_id);

CREATE INDEX idx_estimate_runs_region_id
    ON public.estimate_runs (region_id);

-- 앞서 남겨둔 계획 → 현재 계산 결과 연결
ALTER TABLE public.plans
    ADD CONSTRAINT fk_plans_current_estimate
    FOREIGN KEY (current_estimate_id)
    REFERENCES public.estimate_runs (id)
    ON DELETE RESTRICT;

CREATE UNIQUE INDEX uq_plans_current_estimate_id
    ON public.plans (current_estimate_id);