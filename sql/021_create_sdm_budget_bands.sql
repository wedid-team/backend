-- 정책 버전별 스드메 예산대
CREATE TABLE public.sdm_budget_bands (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    policy_id BIGINT NOT NULL,
    code TEXT NOT NULL,
    label TEXT NOT NULL,
    lower_amount NUMERIC(18, 2),
    upper_amount NUMERIC(18, 2),
    lower_inclusive BOOLEAN NOT NULL,
    upper_inclusive BOOLEAN NOT NULL,
    sort_order SMALLINT NOT NULL,

    CONSTRAINT uq_sdm_budget_bands_policy_code
        UNIQUE (policy_id, code),

    CONSTRAINT uq_sdm_budget_bands_policy_sort_order
        UNIQUE (policy_id, sort_order),

    CONSTRAINT fk_sdm_budget_bands_policy
        FOREIGN KEY (policy_id)
        REFERENCES public.calculation_policies (id)
        ON DELETE RESTRICT,

    CONSTRAINT ck_sdm_budget_bands_code
        CHECK (
            code IN ('BASIC', 'STANDARD', 'PREMIUM', 'LUXURY')
        ),

    CONSTRAINT ck_sdm_budget_bands_sort_order
        CHECK (sort_order BETWEEN 1 AND 4),

    CONSTRAINT ck_sdm_budget_bands_amount_order
        CHECK (
            lower_amount IS NULL
            OR upper_amount IS NULL
            OR lower_amount <= upper_amount
        )
);