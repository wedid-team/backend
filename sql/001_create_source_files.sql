-- 적재한 원본 XLSX 파일의 버전 기록
CREATE TABLE public.source_files (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    dataset TEXT NOT NULL,
    original_filename TEXT NOT NULL,
    sha256 CHAR(64) NOT NULL,
    imported_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_source_files_dataset_sha256
        UNIQUE (dataset, sha256)
);