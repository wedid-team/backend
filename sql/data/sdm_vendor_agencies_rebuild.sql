/*
WEDID 스드메 업체-대행사 연결(vendor_agencies) 적재
= sdm_normalization_rebuild.sql의 9단계만 따로 실행하는 스크립트

언제 쓰나
- agencies / vendors / source_rows는 이미 있는데 vendor_agencies만 비어 있을 때
  (예: 2026-11-26 가연 님 DB 전체 덤프는 vendor_agencies가 0건)
- 정리 적재 전체(sdm_normalization_rebuild.sql)를 돌렸다면 따로 실행할 필요 없음

실행
  sh deploy/scripts/apply-sql.sh sql/data/sdm_vendor_agencies_rebuild.sql

동작
- 업체의 '공개 대행사'(쉼표로 여러 개)를 나눠 agencies와 이름으로 연결한다.
- 원본(sdm_prices / sdm_options / sdm_penalties)에 같은 업체 ID + 출처 대행사 행이 있는지로
  has_base_price / has_option / has_penalty를 채운다.
- 이미 있는 연결은 보유 여부만 다시 계산해 덮어쓴다 (여러 번 실행해도 결과 같음).
*/

BEGIN;

CREATE TEMP TABLE _sdm_src ON COMMIT DROP AS
SELECT
    sf.dataset,
    sr.id AS source_row_id,
    sr.cells
FROM public.source_rows sr
JOIN public.source_files sf ON sf.id = sr.source_file_id
WHERE sf.dataset IN ('sdm_prices', 'sdm_options', 'sdm_penalties')
AND sr.parse_status = 'READY';

CREATE OR REPLACE FUNCTION pg_temp.sdm_cell(p_cells jsonb, p_column text)
RETURNS text
LANGUAGE sql
IMMUTABLE
AS $$
    SELECT NULLIF(BTRIM(c->>'displayedValue'), '')
    FROM jsonb_array_elements(p_cells) AS c
    WHERE c->>'columnName' = p_column
    LIMIT 1
$$;

-- 사전 점검
DO $$
DECLARE
    v_vendors integer;
    v_agencies integer;
    v_missing_datasets text;
    v_multi_agencies text;
BEGIN
    -- 보유 여부를 계산할 원본(가격·선택품목·위약금)마다 READY 행이 있어야 함.
    -- 하나라도 없으면 그 보유 여부가 전부 false로 계산되고, ON CONFLICT DO UPDATE가
    -- 기존 vendor_agencies 값을 false로 덮어쓰므로 아무것도 바꾸지 않고 멈춘다.
    SELECT string_agg(required.dataset, ', ' ORDER BY required.dataset) INTO v_missing_datasets
    FROM (VALUES ('sdm_prices'), ('sdm_options'), ('sdm_penalties')) AS required(dataset)
    WHERE NOT EXISTS (SELECT 1 FROM _sdm_src s WHERE s.dataset = required.dataset);
    IF v_missing_datasets IS NOT NULL THEN
        RAISE EXCEPTION '보유 여부를 계산할 원본 행(READY)이 없는 데이터셋이 있습니다: %. 기존 연결을 false로 덮어쓰지 않도록 멈춥니다.', v_missing_datasets;
    END IF;

    SELECT count(*) INTO v_vendors FROM public.vendors WHERE source_dataset = 'sdm_vendors';
    SELECT count(*) INTO v_agencies FROM public.agencies;
    IF v_vendors = 0 OR v_agencies = 0 THEN
        RAISE EXCEPTION '스드메 업체(%)나 대행사(%)가 없습니다. 정리 적재 전체(sdm_normalization_rebuild.sql)를 실행하세요.',
            v_vendors, v_agencies;
    END IF;

    -- 보유 여부는 '출처 대행사' 이름이 정확히 같아야 잡히므로 한 칸에 하나여야 함
    SELECT string_agg(sample, ', ') INTO v_multi_agencies
    FROM (
        SELECT DISTINCT format('%s="%s"', s.dataset, pg_temp.sdm_cell(s.cells, '출처 대행사')) AS sample
        FROM _sdm_src s
        WHERE pg_temp.sdm_cell(s.cells, '출처 대행사') LIKE '%,%'
        LIMIT 10
    ) multi;
    IF v_multi_agencies IS NOT NULL THEN
        RAISE EXCEPTION '출처 대행사가 쉼표로 여러 개인 행이 있습니다 (최대 10개): %', v_multi_agencies;
    END IF;
END
$$;

-- 9. 업체-대행사 연결 (sdm_normalization_rebuild.sql 9단계와 같음)
WITH vendor_agency_pairs AS (
    SELECT
        v.id AS vendor_id,
        a.id AS agency_id,
        v.source_vendor_id,
        a.source_name
    FROM public.vendors v
    CROSS JOIN LATERAL regexp_split_to_table(
        coalesce(v.source_agencies_raw, ''),
        '\s*,\s*'
    ) AS x(agency_name)
    JOIN public.agencies a
      ON a.source_name = btrim(x.agency_name)
    WHERE v.source_dataset = 'sdm_vendors'
      AND btrim(x.agency_name) <> ''
),
flags AS (
    SELECT
        vap.vendor_id,
        vap.agency_id,
        EXISTS (
            SELECT 1 FROM _sdm_src s
            WHERE s.dataset = 'sdm_prices'
              AND pg_temp.sdm_cell(s.cells, '업체 ID') = vap.source_vendor_id
              AND pg_temp.sdm_cell(s.cells, '출처 대행사') = vap.source_name
        ) AS has_base_price,
        EXISTS (
            SELECT 1 FROM _sdm_src s
            WHERE s.dataset = 'sdm_options'
              AND pg_temp.sdm_cell(s.cells, '업체 ID') = vap.source_vendor_id
              AND pg_temp.sdm_cell(s.cells, '출처 대행사') = vap.source_name
        ) AS has_option,
        EXISTS (
            SELECT 1 FROM _sdm_src s
            WHERE s.dataset = 'sdm_penalties'
              AND pg_temp.sdm_cell(s.cells, '업체 ID') = vap.source_vendor_id
              AND pg_temp.sdm_cell(s.cells, '출처 대행사') = vap.source_name
        ) AS has_penalty
    FROM vendor_agency_pairs vap
)
INSERT INTO public.vendor_agencies (
    vendor_id, agency_id, has_base_price, has_option, has_penalty
)
SELECT vendor_id, agency_id, has_base_price, has_option, has_penalty
FROM flags
ON CONFLICT (vendor_id, agency_id)
DO UPDATE SET
    has_base_price = EXCLUDED.has_base_price,
    has_option = EXCLUDED.has_option,
    has_penalty = EXCLUDED.has_penalty;

-- 공개 대행사 이름이 agencies에 없어 연결되지 않은 경우 알림 (멈추지는 않음)
DO $$
DECLARE
    v_unmatched integer;
    v_count_mismatch integer;
BEGIN
    SELECT count(*) INTO v_unmatched
    FROM public.vendors v
    CROSS JOIN LATERAL regexp_split_to_table(coalesce(v.source_agencies_raw, ''), '\s*,\s*') AS x(agency_name)
    WHERE v.source_dataset = 'sdm_vendors'
      AND btrim(x.agency_name) <> ''
      AND NOT EXISTS (SELECT 1 FROM public.agencies a WHERE a.source_name = btrim(x.agency_name));

    SELECT count(*) INTO v_count_mismatch
    FROM public.vendors v
    WHERE v.source_dataset = 'sdm_vendors'
      AND v.source_agency_count IS DISTINCT FROM
          (SELECT count(*) FROM public.vendor_agencies va WHERE va.vendor_id = v.id);

    RAISE NOTICE '연결 안 된 공개 대행사 이름 %건, 공개 대행사 수와 연결 수가 다른 업체 %곳',
        v_unmatched, v_count_mismatch;
END
$$;

-- 결과
SELECT has_base_price, has_option, has_penalty, COUNT(*) AS row_count
FROM public.vendor_agencies
GROUP BY 1, 2, 3
UNION ALL
SELECT NULL, NULL, NULL, COUNT(*) FROM public.vendor_agencies
ORDER BY 4 DESC;

COMMIT;
