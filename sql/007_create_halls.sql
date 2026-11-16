-- 예식장 업체 내 개별 홀
CREATE TABLE public.halls (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    vendor_id BIGINT NOT NULL,
    source_hall_name TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_halls_vendor_hall_name
        UNIQUE (vendor_id, source_hall_name),

    CONSTRAINT fk_halls_vendor
        FOREIGN KEY (vendor_id)
        REFERENCES public.vendors (id)
        ON DELETE RESTRICT
);