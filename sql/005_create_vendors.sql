-- 예식장·스드메 업체
CREATE TABLE public.vendors (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    source_dataset TEXT NOT NULL,
    source_vendor_id TEXT NOT NULL,
    source_numeric_id BIGINT,
    source_row_id BIGINT NOT NULL,
    name TEXT NOT NULL,
    category TEXT NOT NULL,

    sido_raw TEXT,
    sigungu_raw TEXT,
    dong_raw TEXT,
    address TEXT,
    phone TEXT,
    region_id SMALLINT,
    region_raw TEXT,

    official_url TEXT,
    operating_status_raw TEXT,
    alternate_names_raw TEXT,
    meal_type_raw TEXT,
    parking_status_raw TEXT,
    station_access_raw TEXT,
    concept_tags_raw TEXT,
    mood_tags_raw TEXT,

    source_agency_count INTEGER,
    source_agencies_raw TEXT,
    source_price_row_count INTEGER,
    source_url TEXT,
    published_at DATE,
    collected_at DATE,
    notes TEXT,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_vendors_dataset_vendor_id
        UNIQUE (source_dataset, source_vendor_id),

    CONSTRAINT fk_vendors_source_row
        FOREIGN KEY (source_row_id)
        REFERENCES public.source_rows (id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_vendors_region
        FOREIGN KEY (region_id)
        REFERENCES public.regions (id)
        ON DELETE RESTRICT,

    CONSTRAINT ck_vendors_source_dataset
        CHECK (source_dataset IN ('wedding_venues', 'sdm_vendors')),

    CONSTRAINT ck_vendors_category
        CHECK (category IN ('WEDDING_HALL', 'STUDIO', 'DRESS', 'MAKEUP'))
);

CREATE INDEX idx_vendors_source_row_id
    ON public.vendors (source_row_id);

CREATE INDEX idx_vendors_region_id
    ON public.vendors (region_id);