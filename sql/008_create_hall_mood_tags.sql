-- 홀 단위 분위기 태그와 근거
CREATE TABLE public.hall_mood_tags (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    hall_id BIGINT NOT NULL,
    tag TEXT NOT NULL,
    source_label TEXT,
    source_row_id BIGINT,
    source_url TEXT,
    mapping_basis TEXT NOT NULL,
    verified_at TIMESTAMPTZ,

    CONSTRAINT uq_hall_mood_tags_hall_tag
        UNIQUE (hall_id, tag),

    CONSTRAINT fk_hall_mood_tags_hall
        FOREIGN KEY (hall_id)
        REFERENCES public.halls (id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_hall_mood_tags_source_row
        FOREIGN KEY (source_row_id)
        REFERENCES public.source_rows (id)
        ON DELETE RESTRICT,

    CONSTRAINT ck_hall_mood_tags_tag
        CHECK (
            tag IN (
                'BRIGHT',
                'GRAND',
                'CLASSIC',
                'GREENERY',
                'LUXURY'
            )
        )
);

CREATE INDEX idx_hall_mood_tags_source_row_id
    ON public.hall_mood_tags (source_row_id);