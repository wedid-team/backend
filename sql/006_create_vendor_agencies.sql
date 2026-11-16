-- 업체와 가격 공개 대행사 연결
CREATE TABLE public.vendor_agencies (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    vendor_id BIGINT NOT NULL,
    agency_id BIGINT NOT NULL,
    has_base_price BOOLEAN NOT NULL,
    has_option BOOLEAN NOT NULL,
    has_penalty BOOLEAN NOT NULL,

    CONSTRAINT uq_vendor_agencies_vendor_agency
        UNIQUE (vendor_id, agency_id),

    CONSTRAINT fk_vendor_agencies_vendor
        FOREIGN KEY (vendor_id)
        REFERENCES public.vendors (id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_vendor_agencies_agency
        FOREIGN KEY (agency_id)
        REFERENCES public.agencies (id)
        ON DELETE RESTRICT
);

CREATE INDEX idx_vendor_agencies_agency_id
    ON public.vendor_agencies (agency_id);