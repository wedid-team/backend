-- 홀별 비용 항목 원문
CREATE TABLE public.hall_cost_items (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    source_item_key TEXT NOT NULL,
    source_row_id BIGINT NOT NULL,
    price_record_id BIGINT NOT NULL,
    vendor_id BIGINT NOT NULL,

    hall_name_raw TEXT,
    domain_raw TEXT,
    cost_classification_raw TEXT,
    item_name TEXT NOT NULL,
    amount_min NUMERIC(18, 2),
    amount_max NUMERIC(18, 2),
    unit_raw TEXT,
    reference_list_price NUMERIC(18, 2),
    charge_condition_raw TEXT,

    CONSTRAINT uq_hall_cost_items_source_item_key
        UNIQUE (source_item_key),

    CONSTRAINT fk_hall_cost_items_source_row
        FOREIGN KEY (source_row_id)
        REFERENCES public.source_rows (id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_hall_cost_items_price_record
        FOREIGN KEY (price_record_id)
        REFERENCES public.price_records (id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_hall_cost_items_vendor
        FOREIGN KEY (vendor_id)
        REFERENCES public.vendors (id)
        ON DELETE RESTRICT
);

CREATE INDEX idx_hall_cost_items_source_row_id
    ON public.hall_cost_items (source_row_id);

CREATE INDEX idx_hall_cost_items_price_record_id
    ON public.hall_cost_items (price_record_id);

CREATE INDEX idx_hall_cost_items_vendor_id
    ON public.hall_cost_items (vendor_id);