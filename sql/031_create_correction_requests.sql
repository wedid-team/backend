-- 업체·가격·옵션 정보 정정 요청
CREATE TABLE public.correction_requests (
    id UUID PRIMARY KEY,
    vendor_id BIGINT,
    price_record_id BIGINT,
    option_id BIGINT,
    submitted_by UUID,

    message TEXT NOT NULL,
    evidence_url TEXT,
    status TEXT NOT NULL,
    resolution_note TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_at TIMESTAMPTZ,

    CONSTRAINT fk_correction_requests_vendor
        FOREIGN KEY (vendor_id)
        REFERENCES public.vendors (id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_correction_requests_price_record
        FOREIGN KEY (price_record_id)
        REFERENCES public.price_records (id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_correction_requests_option
        FOREIGN KEY (option_id)
        REFERENCES public.options (id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_correction_requests_submitted_by
        FOREIGN KEY (submitted_by)
        REFERENCES public.users (id)
        ON DELETE RESTRICT,

    CONSTRAINT ck_correction_requests_target
        CHECK (
            vendor_id IS NOT NULL
            OR price_record_id IS NOT NULL
            OR option_id IS NOT NULL
        ),

    CONSTRAINT ck_correction_requests_status
        CHECK (
            status IN ('OPEN', 'IN_REVIEW', 'RESOLVED', 'REJECTED')
        )
);

CREATE INDEX idx_correction_requests_vendor_id
    ON public.correction_requests (vendor_id);

CREATE INDEX idx_correction_requests_price_record_id
    ON public.correction_requests (price_record_id);

CREATE INDEX idx_correction_requests_option_id
    ON public.correction_requests (option_id);

CREATE INDEX idx_correction_requests_submitted_by
    ON public.correction_requests (submitted_by);