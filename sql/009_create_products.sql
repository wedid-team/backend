-- 스드메 기본 상품·패키지
CREATE TABLE public.products (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    product_kind TEXT NOT NULL,
    source_dataset TEXT NOT NULL,
    source_product_key TEXT NOT NULL,
    source_row_id BIGINT NOT NULL,

    vendor_id BIGINT,
    seller_agency_id BIGINT,
    name TEXT,
    category TEXT NOT NULL,
    region_id SMALLINT,
    region_raw TEXT,

    product_type_raw TEXT,
    product_type_standard TEXT,
    composition_raw TEXT,
    session_count INTEGER,
    branch_line_raw TEXT,
    staff_grade_raw TEXT,
    staff_grade_standard TEXT,
    staff_rank INTEGER,

    includes_studio BOOLEAN,
    includes_dress BOOLEAN,
    includes_makeup BOOLEAN,
    shooting_composition_raw TEXT,
    ceremony_composition_raw TEXT,
    choice_composition_raw TEXT,
    extra_costs_raw TEXT,
    domain_raw TEXT,
    cost_classification_raw TEXT,
    notes TEXT,

    CONSTRAINT uq_products_dataset_product_key
        UNIQUE (source_dataset, source_product_key),

    CONSTRAINT fk_products_source_row
        FOREIGN KEY (source_row_id)
        REFERENCES public.source_rows (id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_products_vendor
        FOREIGN KEY (vendor_id)
        REFERENCES public.vendors (id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_products_seller_agency
        FOREIGN KEY (seller_agency_id)
        REFERENCES public.agencies (id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_products_region
        FOREIGN KEY (region_id)
        REFERENCES public.regions (id)
        ON DELETE RESTRICT,

    -- 기본 상품은 업체에, 패키지는 판매 대행사에 연결
    CONSTRAINT ck_products_kind_and_owner
        CHECK (
            (
                product_kind = 'SDM_BASE'
                AND source_dataset = 'sdm_prices'
                AND category IN ('STUDIO', 'DRESS', 'MAKEUP')
                AND vendor_id IS NOT NULL
                AND seller_agency_id IS NULL
            )
            OR
            (
                product_kind = 'SDM_PACKAGE'
                AND source_dataset = 'sdm_packages'
                AND category = 'PACKAGE'
                AND vendor_id IS NULL
                AND seller_agency_id IS NOT NULL
            )
        )
);

CREATE INDEX idx_products_source_row_id
    ON public.products (source_row_id);

CREATE INDEX idx_products_vendor_id
    ON public.products (vendor_id);

CREATE INDEX idx_products_seller_agency_id
    ON public.products (seller_agency_id);

CREATE INDEX idx_products_region_id
    ON public.products (region_id);