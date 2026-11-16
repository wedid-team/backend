-- 계산·정렬 규칙의 버전 관리
CREATE TABLE public.calculation_policies (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    version TEXT NOT NULL,
    status TEXT NOT NULL,
    settings JSONB NOT NULL,
    effective_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_calculation_policies_version
        UNIQUE (version),

    CONSTRAINT ck_calculation_policies_status
        CHECK (status IN ('DRAFT', 'ACTIVE', 'RETIRED'))
);

-- 활성 정책은 최대 한 개만 허용
CREATE UNIQUE INDEX uq_calculation_policies_active
    ON public.calculation_policies (status)
    WHERE status = 'ACTIVE';