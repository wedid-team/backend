-- 공개 가격의 대상·가격 유형·VAT·출처
CREATE TABLE public.price_records (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    source_dataset TEXT NOT NULL,
    source_price_id TEXT NOT NULL,
    source_row_id BIGINT NOT NULL,

    hall_id BIGINT,
    product_id BIGINT,
    agency_id BIGINT,

    price_type TEXT NOT NULL,
    price_type_basis TEXT,
    vat_status TEXT NOT NULL,

    source_url TEXT,
    source_detail_raw TEXT,
    published_at DATE,
    file_reference_raw TEXT,
    collected_at DATE,
    conditions_raw TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_price_records_dataset_price_id
        UNIQUE (source_dataset, source_price_id),

    CONSTRAINT fk_price_records_source_row
        FOREIGN KEY (source_row_id)
        REFERENCES public.source_rows (id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_price_records_hall
        FOREIGN KEY (hall_id)
        REFERENCES public.halls (id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_price_records_product
        FOREIGN KEY (product_id)
        REFERENCES public.products (id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_price_records_agency
        FOREIGN KEY (agency_id)
        REFERENCES public.agencies (id)
        ON DELETE RESTRICT,

    CONSTRAINT ck_price_records_source_dataset
        CHECK (
            source_dataset IN (
                'wedding_hall_prices',
                'sdm_prices',
                'sdm_packages'
            )
        ),

    -- 홀 또는 상품 중 정확히 하나에 연결
    CONSTRAINT ck_price_records_target
        CHECK (
            (hall_id IS NOT NULL AND product_id IS NULL)
            OR
            (hall_id IS NULL AND product_id IS NOT NULL)
        ),

    CONSTRAINT ck_price_records_price_type
        CHECK (
            price_type IN (
                'DIRECT', 'PLANNER', 'ONLINE_PUBLIC', 'UNKNOWN'
            )
        ),

    CONSTRAINT ck_price_records_vat_status
        CHECK (
            vat_status IN ('INCLUDED', 'EXCLUDED', 'UNKNOWN')
        )
);

CREATE INDEX idx_price_records_source_row_id
    ON public.price_records (source_row_id);

CREATE INDEX idx_price_records_hall_id
    ON public.price_records (hall_id);

CREATE INDEX idx_price_records_product_id
    ON public.price_records (product_id);

CREATE INDEX idx_price_records_agency_id
    ON public.price_records (agency_id);