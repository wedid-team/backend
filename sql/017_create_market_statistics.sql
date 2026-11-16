-- 참가격 지역별 통계
CREATE TABLE public.market_statistics (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    source_stat_id TEXT NOT NULL,
    source_row_id BIGINT NOT NULL,
    reference_month DATE NOT NULL,
    scope_type TEXT NOT NULL,
    region_id SMALLINT,

    region_raw TEXT NOT NULL,
    category_raw TEXT NOT NULL,
    item_raw TEXT NOT NULL,
    unit_raw TEXT NOT NULL,
    season_raw TEXT NOT NULL,

    p10 NUMERIC(18, 2),
    p25 NUMERIC(18, 2),
    median NUMERIC(18, 2),
    p75 NUMERIC(18, 2),
    p90 NUMERIC(18, 2),
    mean NUMERIC(18, 2),
    sample_count INTEGER,

    representative_basis_raw TEXT NOT NULL,
    conditions_raw TEXT,
    source_url TEXT,
    collected_at DATE,
    notes_raw TEXT,

    CONSTRAINT uq_market_statistics_source_stat_id
        UNIQUE (source_stat_id),

    CONSTRAINT fk_market_statistics_source_row
        FOREIGN KEY (source_row_id)
        REFERENCES public.source_rows (id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_market_statistics_region
        FOREIGN KEY (region_id)
        REFERENCES public.regions (id)
        ON DELETE RESTRICT,

    CONSTRAINT ck_market_statistics_scope
        CHECK (
            (
                scope_type = 'REGION'
                AND region_id IS NOT NULL
            )
            OR
            (
                scope_type IN ('NATIONAL', 'CAPITAL_AREA', 'OTHER')
                AND region_id IS NULL
            )
        ),

    CONSTRAINT ck_market_statistics_reference_month
        CHECK (EXTRACT(DAY FROM reference_month) = 1)
);

CREATE INDEX idx_market_statistics_source_row_id
    ON public.market_statistics (source_row_id);

CREATE INDEX idx_market_statistics_region_id
    ON public.market_statistics (region_id);