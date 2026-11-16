-- 옵션 금액과 출처
CREATE TABLE public.option_prices (
    option_id BIGINT PRIMARY KEY,
    price_status_raw TEXT,
    amount_min NUMERIC(18, 2),
    amount_max NUMERIC(18, 2),
    vat_status TEXT NOT NULL,
    price_type TEXT NOT NULL,
    classification_basis TEXT,
    source_url TEXT,
    published_at DATE,
    collected_at DATE,

    CONSTRAINT fk_option_prices_option
        FOREIGN KEY (option_id)
        REFERENCES public.options (id)
        ON DELETE RESTRICT,

    CONSTRAINT ck_option_prices_vat_status
        CHECK (
            vat_status IN ('INCLUDED', 'EXCLUDED', 'UNKNOWN')
        ),

    CONSTRAINT ck_option_prices_price_type
        CHECK (
            price_type IN (
                'DIRECT', 'PLANNER', 'ONLINE_PUBLIC', 'UNKNOWN'
            )
        )
);