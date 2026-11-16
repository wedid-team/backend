-- 스드메 단품·패키지의 금액과 적용 조건
CREATE TABLE public.product_price_details (
    price_record_id BIGINT PRIMARY KEY,
    amount_min NUMERIC(18, 2),
    amount_max NUMERIC(18, 2),
    time_raw TEXT,
    season_standard TEXT,
    time_standard TEXT,

    CONSTRAINT fk_product_price_details_price_record
        FOREIGN KEY (price_record_id)
        REFERENCES public.price_records (id)
        ON DELETE RESTRICT
);