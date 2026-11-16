-- 부부의 현재 계획
CREATE TABLE public.plans (
    id UUID PRIMARY KEY,
    owner_user_id UUID NOT NULL,
    status TEXT NOT NULL,
    region_id SMALLINT NOT NULL,
    guest_choice TEXT NOT NULL,

    date_precision TEXT NOT NULL,
    wedding_date DATE,
    wedding_month DATE,
    assumption_date DATE NOT NULL,
    effective_wedding_date DATE NOT NULL,

    budget_amount NUMERIC(18, 0),
    preferred_moods TEXT[] NOT NULL DEFAULT '{}',
    selected_band_id BIGINT NOT NULL,
    current_estimate_id UUID,

    conditions_updated_at TIMESTAMPTZ NOT NULL
        DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_plans_owner
        FOREIGN KEY (owner_user_id)
        REFERENCES public.users (id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_plans_region
        FOREIGN KEY (region_id)
        REFERENCES public.regions (id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_plans_selected_band
        FOREIGN KEY (selected_band_id)
        REFERENCES public.sdm_budget_bands (id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_plans_updated_by
        FOREIGN KEY (updated_by)
        REFERENCES public.users (id)
        ON DELETE RESTRICT,

    CONSTRAINT ck_plans_status
        CHECK (status IN ('ACTIVE', 'REPLACED')),

    CONSTRAINT ck_plans_guest_choice
        CHECK (
            guest_choice IN (
                'LT100', 'G100_150', 'G150_200',
                'G200_250', 'G250_300', 'GE300', 'UNKNOWN'
            )
        ),

    CONSTRAINT ck_plans_date_selection
        CHECK (
            (
                date_precision = 'EXACT'
                AND wedding_date IS NOT NULL
                AND wedding_month IS NULL
            )
            OR
            (
                date_precision = 'MONTH'
                AND wedding_date IS NULL
                AND wedding_month IS NOT NULL
                AND EXTRACT(DAY FROM wedding_month) = 1
            )
            OR
            (
                date_precision = 'UNKNOWN'
                AND wedding_date IS NULL
                AND wedding_month IS NULL
            )
        ),

    CONSTRAINT ck_plans_budget
        CHECK (
            budget_amount IS NULL
            OR (
                budget_amount BETWEEN 1000000 AND 999990000
                AND MOD(budget_amount, 10000) = 0
            )
        ),

    CONSTRAINT ck_plans_preferred_moods
        CHECK (
            preferred_moods <@ ARRAY[
                'BRIGHT', 'GRAND', 'CLASSIC', 'GREENERY', 'LUXURY'
            ]::TEXT[]
            AND array_position(preferred_moods, NULL) IS NULL
        )
);

-- 사용자당 활성 소유 계획은 최대 한 개
CREATE UNIQUE INDEX uq_plans_active_owner
    ON public.plans (owner_user_id)
    WHERE status = 'ACTIVE';

CREATE INDEX idx_plans_owner_user_id
    ON public.plans (owner_user_id);

CREATE INDEX idx_plans_region_id
    ON public.plans (region_id);

CREATE INDEX idx_plans_selected_band_id
    ON public.plans (selected_band_id);

CREATE INDEX idx_plans_updated_by
    ON public.plans (updated_by);