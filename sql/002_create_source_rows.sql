-- 원본 XLSX의 시트별 행 기록
CREATE TABLE public.source_rows (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    source_file_id BIGINT NOT NULL,
    sheet_name TEXT NOT NULL,
    excel_row_number INTEGER NOT NULL,
    cells JSONB NOT NULL,
    parse_status TEXT NOT NULL,
    parse_notes JSONB,

    CONSTRAINT fk_source_rows_source_file
        FOREIGN KEY (source_file_id)
        REFERENCES public.source_files (id)
        ON DELETE RESTRICT,

    CONSTRAINT uq_source_rows_file_sheet_row
        UNIQUE (source_file_id, sheet_name, excel_row_number),

    CONSTRAINT ck_source_rows_excel_row_number
        CHECK (excel_row_number >= 1),

    CONSTRAINT ck_source_rows_parse_status
        CHECK (parse_status IN ('READY', 'REVIEW', 'REFERENCE')),

    CONSTRAINT ck_source_rows_cells_array
        CHECK (jsonb_typeof(cells) = 'array')
);