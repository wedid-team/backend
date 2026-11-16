-- 홀 가격의 세부 금액과 적용 조건
CREATE TABLE public.hall_price_details (
    price_record_id BIGINT PRIMARY KEY,
    source_numeric_price_id BIGINT,
    source_numeric_vendor_id BIGINT,
    hall_type_raw TEXT,
    is_exclusive BOOLEAN,

    rental_min NUMERIC(18, 2),
    rental_max NUMERIC(18, 2),
    guaranteed_guests INTEGER,
    meal_min NUMERIC(18, 2),
    meal_max NUMERIC(18, 2),
    decoration_min NUMERIC(18, 2),
    decoration_max NUMERIC(18, 2),
    decoration_in_rental BOOLEAN,
    capacity_min INTEGER,
    capacity_max INTEGER,

    weekday_raw TEXT,
    time_raw TEXT,
    season_raw TEXT,
    weekday_standard TEXT,
    time_standard TEXT,
    season_standard TEXT,
    start_month SMALLINT,
    end_month SMALLINT,
    applicable_year INTEGER,

    included_services_raw TEXT,
    deposit_raw TEXT,
    penalty_raw TEXT,

    beverage_in_meal BOOLEAN,
    beverage_per_person NUMERIC(18, 2),
    extra_guest_meal_min NUMERIC(18, 2),
    extra_guest_meal_max NUMERIC(18, 2),
    rental_list_price NUMERIC(18, 2),
    meal_list_price NUMERIC(18, 2),
    production_fee NUMERIC(18, 2),
    other_required_fee NUMERIC(18, 2),
    normalization_notes_raw TEXT,

    CONSTRAINT fk_hall_price_details_price_record
        FOREIGN KEY (price_record_id)
        REFERENCES public.price_records (id)
        ON DELETE RESTRICT
);