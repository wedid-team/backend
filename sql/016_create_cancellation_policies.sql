-- 위약금 원문과 적용 범위
CREATE TABLE public.cancellation_policies (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    source_dataset TEXT NOT NULL,
    source_policy_key TEXT NOT NULL,
    source_row_id BIGINT NOT NULL,
    scope TEXT NOT NULL,

    vendor_id BIGINT,
    agency_id BIGINT,
    product_id BIGINT,
    vendor_name_raw TEXT,
    policy_text TEXT NOT NULL,
    source_url TEXT,
    published_at DATE,
    collected_at DATE,

    CONSTRAINT uq_cancellation_policies_dataset_key
        UNIQUE (source_dataset, source_policy_key),

    CONSTRAINT fk_cancellation_policies_source_row
        FOREIGN KEY (source_row_id)
        REFERENCES public.source_rows (id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_cancellation_policies_vendor
        FOREIGN KEY (vendor_id)
        REFERENCES public.vendors (id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_cancellation_policies_agency
        FOREIGN KEY (agency_id)
        REFERENCES public.agencies (id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_cancellation_policies_product
        FOREIGN KEY (product_id)
        REFERENCES public.products (id)
        ON DELETE RESTRICT,

    CONSTRAINT ck_cancellation_policies_dataset
        CHECK (
            source_dataset IN ('sdm_penalties', 'sdm_packages')
        ),

    CONSTRAINT ck_cancellation_policies_scope
        CHECK (
            (
                scope = 'AGENCY'
                AND agency_id IS NOT NULL
                AND vendor_id IS NULL
                AND product_id IS NULL
            )
            OR
            (
                scope = 'VENDOR_AGENCY'
                AND vendor_id IS NOT NULL
                AND agency_id IS NOT NULL
                AND product_id IS NULL
            )
            OR
            (
                scope = 'PRODUCT'
                AND product_id IS NOT NULL
                AND agency_id IS NOT NULL
                AND vendor_id IS NULL
            )
        )
);

CREATE INDEX idx_cancellation_policies_source_row_id
    ON public.cancellation_policies (source_row_id);

CREATE INDEX idx_cancellation_policies_vendor_id
    ON public.cancellation_policies (vendor_id);

CREATE INDEX idx_cancellation_policies_agency_id
    ON public.cancellation_policies (agency_id);

CREATE INDEX idx_cancellation_policies_product_id
    ON public.cancellation_policies (product_id);