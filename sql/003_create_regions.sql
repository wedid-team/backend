-- 서비스에서 사용하는 14개 지역 권역
CREATE TABLE public.regions (
    id SMALLINT PRIMARY KEY,
    code TEXT NOT NULL,
    display_name TEXT NOT NULL,
    source_names JSONB NOT NULL,
    administrative_scope JSONB,
    sort_order SMALLINT NOT NULL,

    CONSTRAINT uq_regions_code
        UNIQUE (code),

    CONSTRAINT uq_regions_display_name
        UNIQUE (display_name),

    CONSTRAINT ck_regions_source_names_array
        CHECK (jsonb_typeof(source_names) = 'array')
);