-- 가격 공개·판매 대행사
CREATE TABLE public.agencies (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    source_name TEXT NOT NULL,
    official_url TEXT,
    source_row_id BIGINT,

    CONSTRAINT uq_agencies_source_name
        UNIQUE (source_name),

    CONSTRAINT fk_agencies_source_row
        FOREIGN KEY (source_row_id)
        REFERENCES public.source_rows (id)
        ON DELETE RESTRICT
);

-- 원본 행과의 연결 조회에 사용하는 인덱스
CREATE INDEX idx_agencies_source_row_id
    ON public.agencies (source_row_id);