-- 예식장·스드메 추가 옵션
CREATE TABLE public.options (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    source_dataset TEXT NOT NULL,
    source_option_key TEXT NOT NULL,
    source_row_id BIGINT NOT NULL,
    vendor_id BIGINT NOT NULL,
    agency_id BIGINT,
    target_price_record_id BIGINT,

    source_numeric_id BIGINT,
    source_numeric_vendor_id BIGINT,
    source_numeric_price_id BIGINT,

    category_raw TEXT,
    option_type_raw TEXT,
    name_raw TEXT,
    unit_raw TEXT,
    unit_standard TEXT,
    domain_raw TEXT,
    cost_classification_raw TEXT,
    condition_raw TEXT,
    charge_condition_raw TEXT,
    notes_raw TEXT,

    CONSTRAINT uq_options_dataset_option_key
        UNIQUE (source_dataset, source_option_key),

    CONSTRAINT fk_options_source_row
        FOREIGN KEY (source_row_id)
        REFERENCES public.source_rows (id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_options_vendor
        FOREIGN KEY (vendor_id)
        REFERENCES public.vendors (id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_options_agency
        FOREIGN KEY (agency_id)
        REFERENCES public.agencies (id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_options_target_price_record
        FOREIGN KEY (target_price_record_id)
        REFERENCES public.price_records (id)
        ON DELETE RESTRICT,

    CONSTRAINT ck_options_source_dataset
        CHECK (
            source_dataset IN ('wedding_options', 'sdm_options')
        )
);

CREATE INDEX idx_options_source_row_id
    ON public.options (source_row_id);

CREATE INDEX idx_options_vendor_id
    ON public.options (vendor_id);

CREATE INDEX idx_options_agency_id
    ON public.options (agency_id);

CREATE INDEX idx_options_target_price_record_id
    ON public.options (target_price_record_id);