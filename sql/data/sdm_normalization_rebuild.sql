/*
WEDID 스드메 정규화 적재 SQL
대상: source_rows -> agencies / vendors / products / price_records /
      product_price_details / vendor_agencies

실행 순서 (deploy/README.md 4번)
  1) sql/ 스키마 적용          sh deploy/scripts/apply-sql.sh
  2) XLSX 원본 적재            (import-file-metadata 프로필)
  3) 이 스크립트              sh deploy/scripts/apply-sql.sh sql/data/sdm_normalization_rebuild.sql

주의
1) 이 스크립트는 기존 데이터 삭제(TRUNCATE/DELETE)를 하지 않습니다. 모든 INSERT는
   ON CONFLICT로 이미 있는 행을 건너뛰므로 여러 번 실행해도 결과가 같습니다.
   (9단계 vendor_agencies만 같은 원본으로 다시 계산한 보유 여부로 덮어씁니다.)
2) 전체가 하나의 트랜잭션입니다. 사전 점검에 걸리면 아무것도 적재하지 않고 멈춥니다.
3) sdm_options / sdm_penalties의 상세 테이블 적재는 포함하지 않습니다.
   vendor_agencies의 보유 여부(has_option, has_penalty)에만 반영합니다.

2026-11-25 확인 결과 (로컬 원본 18,469행 기준, 기존 적재 결과와 값까지 동일)
- 날짜: 수집일·자료 게시일은 모두 YYYY-MM-DD. 그래도 형식이 다른 값이 들어오면 ::date가
  중간에 알 수 없는 에러로 실패하므로, 사전 점검에서 문제 값을 보여주고 멈추게 했다.
  DateStyle도 고정해서 로컬·Neon 설정 차이로 해석이 달라지지 않게 했다.
- 출처 대행사: 가격·선택품목·위약금의 '출처 대행사'는 한 칸에 대행사 하나 (쉼표 0건).
  price_records.agency_id와 9단계 보유 여부는 이름이 정확히 같아야 연결되므로, 쉼표로
  여러 개가 들어오면 연결이 조용히 빠진다. 사전 점검에서 멈추게 했다.
  업체의 '공개 대행사'는 원래 쉼표로 여러 개이고 1·9단계에서 나눠서 처리한다.
- vendors.region_id: 스드메 업체는 일부러 NULL로 둔다. regions 테이블이 비어 있고,
  스드메 조합 API(SdmBasePriceRepository)는 지역으로 거르지 않아 영향이 없다.
  강남 필터(RecommendationRepository의 regions JOIN)는 예식장 추천에만 있고
  예식장 업체(wedding_venues)는 이 스크립트 대상이 아니다. 원본 시·군·구는 sigungu_raw에 남긴다.
*/

BEGIN;

-- 날짜 해석 고정 (YYYY-MM-DD 외의 모호한 표기는 사전 점검에서 걸러짐)
SET LOCAL DateStyle = 'ISO, YMD';

-- 0. 원본 READY 행을 임시 스테이징
CREATE TEMP TABLE _sdm_src ON COMMIT DROP AS
SELECT
    sf.dataset,
    sr.id AS source_row_id,
    sr.cells
FROM public.source_rows sr
JOIN public.source_files sf ON sf.id = sr.source_file_id
WHERE sf.dataset IN (
    'sdm_vendors', 'sdm_prices', 'sdm_packages',
    'sdm_options', 'sdm_penalties'
)
AND sr.parse_status = 'READY';

-- JSONB cells에서 엑셀 컬럼의 displayedValue를 가져오는 임시 함수
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

-- 빈 값 / Y,N / 한글 표기를 boolean으로 변환하는 임시 함수
CREATE OR REPLACE FUNCTION pg_temp.sdm_bool(p_value text)
RETURNS boolean
LANGUAGE sql
IMMUTABLE
AS $$
    SELECT CASE
        WHEN lower(btrim(coalesce(p_value,''))) IN ('y','yes','true','1','포함','있음') THEN true
        WHEN lower(btrim(coalesce(p_value,''))) IN ('n','no','false','0','미포함','없음') THEN false
        ELSE NULL
    END
$$;

-- 숫자 문자열에서 쉼표 등을 제거해 numeric으로 변환
CREATE OR REPLACE FUNCTION pg_temp.sdm_num(p_value text)
RETURNS numeric
LANGUAGE sql
IMMUTABLE
AS $$
    SELECT CASE
        WHEN NULLIF(regexp_replace(coalesce(p_value,''), '[^0-9.-]', '', 'g'), '') IS NULL THEN NULL
        ELSE regexp_replace(p_value, '[^0-9.-]', '', 'g')::numeric
    END
$$;

-- 사전 점검: 문제가 있으면 아무것도 적재하지 않고 이유와 예시 값을 보여주며 멈춘다
DO $$
DECLARE
    v_source_count integer;
    v_bad_dates text;
    v_multi_agencies text;
BEGIN
    -- (1) 원본 적재 여부
    SELECT count(*) INTO v_source_count
    FROM _sdm_src
    WHERE dataset IN ('sdm_vendors', 'sdm_prices');
    IF v_source_count = 0 THEN
        RAISE EXCEPTION 'sdm_vendors/sdm_prices 원본 행(READY)이 없습니다. XLSX 원본 적재를 먼저 실행하세요.';
    END IF;

    -- (2) 날짜로 바꿀 칸이 날짜로 읽히는지 (5·6단계의 ::date)
    SELECT string_agg(sample, ', ') INTO v_bad_dates
    FROM (
        SELECT DISTINCT format('%s.%s="%s"', s.dataset, k.col, c.v) AS sample
        FROM _sdm_src s
        JOIN (VALUES ('sdm_prices', '수집일'),
                     ('sdm_packages', '수집일'),
                     ('sdm_packages', '자료 게시일')) AS k(dataset, col)
          ON k.dataset = s.dataset
        CROSS JOIN LATERAL (SELECT pg_temp.sdm_cell(s.cells, k.col)) AS c(v)
        WHERE c.v IS NOT NULL
          AND NOT pg_input_is_valid(c.v, 'date')
        LIMIT 10
    ) bad;
    IF v_bad_dates IS NOT NULL THEN
        RAISE EXCEPTION '날짜로 읽을 수 없는 값이 있습니다 (YYYY-MM-DD 필요, 최대 10개): %', v_bad_dates;
    END IF;

    -- (3) '출처 대행사'는 한 칸에 하나여야 함 (5단계 agency_id, 9단계 보유 여부가 정확히 같은 이름으로 연결)
    SELECT string_agg(sample, ', ') INTO v_multi_agencies
    FROM (
        SELECT DISTINCT format('%s="%s"', s.dataset, pg_temp.sdm_cell(s.cells, '출처 대행사')) AS sample
        FROM _sdm_src s
        WHERE s.dataset IN ('sdm_prices', 'sdm_options', 'sdm_penalties')
          AND pg_temp.sdm_cell(s.cells, '출처 대행사') LIKE '%,%'
        LIMIT 10
    ) multi;
    IF v_multi_agencies IS NOT NULL THEN
        RAISE EXCEPTION '출처 대행사가 쉼표로 여러 개인 행이 있습니다. 대행사 연결이 빠지므로 원본을 행별로 나눠주세요 (최대 10개): %', v_multi_agencies;
    END IF;
END
$$;

-- 1. 대행사: sdm_prices의 '출처 대행사'를 쉼표 기준 분리
WITH agency_rows AS (
    SELECT
        btrim(x.agency_name) AS source_name,
        s.source_row_id
    FROM _sdm_src s
    CROSS JOIN LATERAL regexp_split_to_table(
        coalesce(pg_temp.sdm_cell(s.cells, '출처 대행사'), ''),
        '\s*,\s*'
    ) AS x(agency_name)
    WHERE s.dataset = 'sdm_prices'
      AND btrim(x.agency_name) <> ''
),
agency_first AS (
    SELECT DISTINCT ON (source_name)
        source_name, source_row_id
    FROM agency_rows
    ORDER BY source_name, source_row_id
)
INSERT INTO public.agencies (source_name, source_row_id)
SELECT source_name, source_row_id
FROM agency_first
ON CONFLICT (source_name) DO NOTHING;

-- 2. 스드메 업체
INSERT INTO public.vendors (
    source_dataset, source_vendor_id, source_numeric_id, source_row_id,
    name, category, sido_raw, sigungu_raw, dong_raw, address,
    region_id, region_raw, alternate_names_raw, source_agencies_raw,
    source_agency_count, source_price_row_count, notes
)
SELECT
    'sdm_vendors',
    pg_temp.sdm_cell(s.cells, '업체 ID'),
    NULL,
    s.source_row_id,
    pg_temp.sdm_cell(s.cells, '업체명'),
    CASE pg_temp.sdm_cell(s.cells, '카테고리(스튜디오/드레스/메이크업)')
        WHEN '스튜디오' THEN 'STUDIO'
        WHEN '드레스' THEN 'DRESS'
        WHEN '메이크업' THEN 'MAKEUP'
        ELSE upper(pg_temp.sdm_cell(s.cells, '카테고리(스튜디오/드레스/메이크업)'))
    END,
    pg_temp.sdm_cell(s.cells, '시·도'),
    pg_temp.sdm_cell(s.cells, '시·군·구'),
    pg_temp.sdm_cell(s.cells, '읍·면·동'),
    pg_temp.sdm_cell(s.cells, '주소(카카오맵 기준)'),
    NULL,
    NULL,
    pg_temp.sdm_cell(s.cells, '다른 표기'),
    pg_temp.sdm_cell(s.cells, '공개 대행사'),
    pg_temp.sdm_num(pg_temp.sdm_cell(s.cells, '공개 대행사 수'))::integer,
    pg_temp.sdm_num(pg_temp.sdm_cell(s.cells, '가격 행 수'))::integer,
    pg_temp.sdm_cell(s.cells, '비고')
FROM _sdm_src s
WHERE s.dataset = 'sdm_vendors'
  AND pg_temp.sdm_cell(s.cells, '업체 ID') IS NOT NULL
ON CONFLICT (source_dataset, source_vendor_id) DO NOTHING;

-- 3. 기본 스드메 상품 (sdm_prices)
INSERT INTO public.products (
    product_kind, source_dataset, source_product_key, source_row_id,
    vendor_id, seller_agency_id, name, category, region_id, region_raw,
    product_type_raw, product_type_standard, composition_raw, session_count,
    branch_line_raw, staff_grade_raw, staff_grade_standard, staff_rank,
    includes_studio, includes_dress, includes_makeup,
    shooting_composition_raw, ceremony_composition_raw, choice_composition_raw,
    extra_costs_raw, domain_raw, cost_classification_raw, notes
)
SELECT
    'SDM_BASE',
    'sdm_prices',
    pg_temp.sdm_cell(s.cells, '가격 ID'),
    s.source_row_id,
    v.id,
    NULL,
    NULL,
    CASE pg_temp.sdm_cell(s.cells, '카테고리')
        WHEN '스튜디오' THEN 'STUDIO'
        WHEN '드레스' THEN 'DRESS'
        WHEN '메이크업' THEN 'MAKEUP'
        ELSE upper(pg_temp.sdm_cell(s.cells, '카테고리'))
    END,
    NULL,
    NULL,
    pg_temp.sdm_cell(s.cells, '상품 구분(촬영/본식/본식+촬영/토탈/비토탈 등)'),
    pg_temp.sdm_cell(s.cells, '표준 상품 구분'),
    pg_temp.sdm_cell(s.cells, '구성·포함 내용'),
    pg_temp.sdm_num(pg_temp.sdm_cell(s.cells, '횟수'))::integer,
    pg_temp.sdm_cell(s.cells, '지점·라인'),
    pg_temp.sdm_cell(s.cells, '직급'),
    pg_temp.sdm_cell(s.cells, '표준 직급'),
    NULL, -- 기존 적재 결과에서 staff_rank는 전체 NULL
    NULL, NULL, NULL,
    NULL, NULL, NULL,
    NULL, NULL,
    pg_temp.sdm_cell(s.cells, '비용 구분'),
    pg_temp.sdm_cell(s.cells, '조건·비고')
FROM _sdm_src s
JOIN public.vendors v
  ON v.source_dataset = 'sdm_vendors'
 AND v.source_vendor_id = pg_temp.sdm_cell(s.cells, '업체 ID')
WHERE s.dataset = 'sdm_prices'
  AND pg_temp.sdm_cell(s.cells, '가격 ID') IS NOT NULL
ON CONFLICT (source_dataset, source_product_key) DO NOTHING;

-- 4. 패키지 상품 (sdm_packages)
INSERT INTO public.products (
    product_kind, source_dataset, source_product_key, source_row_id,
    vendor_id, seller_agency_id, name, category, region_id, region_raw,
    product_type_raw, product_type_standard, composition_raw, session_count,
    branch_line_raw, staff_grade_raw, staff_grade_standard, staff_rank,
    includes_studio, includes_dress, includes_makeup,
    shooting_composition_raw, ceremony_composition_raw, choice_composition_raw,
    extra_costs_raw, domain_raw, cost_classification_raw, notes
)
SELECT
    'SDM_PACKAGE',
    'sdm_packages',
    pg_temp.sdm_cell(s.cells, '패키지 ID'),
    s.source_row_id,
    NULL,
    a.id,
    pg_temp.sdm_cell(s.cells, '패키지명'),
    'PACKAGE',
    NULL,
    pg_temp.sdm_cell(s.cells, '참가격 지역'),
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    pg_temp.sdm_bool(pg_temp.sdm_cell(s.cells, '스튜디오 포함')),
    pg_temp.sdm_bool(pg_temp.sdm_cell(s.cells, '드레스 포함')),
    pg_temp.sdm_bool(pg_temp.sdm_cell(s.cells, '메이크업 포함')),
    pg_temp.sdm_cell(s.cells, '촬영 구성'),
    pg_temp.sdm_cell(s.cells, '본식 구성'),
    pg_temp.sdm_cell(s.cells, '선택 구성(택N)'),
    pg_temp.sdm_cell(s.cells, '별도 비용'),
    NULL,
    pg_temp.sdm_cell(s.cells, '비용 구분'),
    pg_temp.sdm_cell(s.cells, '비고')
FROM _sdm_src s
JOIN public.agencies a
  ON a.source_name = pg_temp.sdm_cell(s.cells, '업체명')
WHERE s.dataset = 'sdm_packages'
  AND pg_temp.sdm_cell(s.cells, '패키지 ID') IS NOT NULL
ON CONFLICT (source_dataset, source_product_key) DO NOTHING;

-- 5. 기본 상품 가격 기록
INSERT INTO public.price_records (
    source_dataset, source_price_id, source_row_id, hall_id, product_id,
    agency_id, price_type, price_type_basis, vat_status, source_url,
    source_detail_raw, published_at, file_reference_raw, collected_at, conditions_raw
)
SELECT
    'sdm_prices',
    pg_temp.sdm_cell(s.cells, '가격 ID'),
    s.source_row_id,
    NULL,
    p.id,
    a.id,
    'UNKNOWN',
    NULL,
    CASE
        WHEN pg_temp.sdm_bool(pg_temp.sdm_cell(s.cells, 'VAT 포함')) IS TRUE THEN 'INCLUDED'
        WHEN pg_temp.sdm_bool(pg_temp.sdm_cell(s.cells, 'VAT 포함')) IS FALSE THEN 'EXCLUDED'
        ELSE 'UNKNOWN'
    END,
    pg_temp.sdm_cell(s.cells, '출처 URL'),
    pg_temp.sdm_cell(s.cells, '출처 상세'),
    NULL,
    pg_temp.sdm_cell(s.cells, '파일 기준일'),
    NULLIF(pg_temp.sdm_cell(s.cells, '수집일'), '')::date,
    pg_temp.sdm_cell(s.cells, '조건·비고')
FROM _sdm_src s
JOIN public.products p
  ON p.source_dataset = 'sdm_prices'
 AND p.source_product_key = pg_temp.sdm_cell(s.cells, '가격 ID')
LEFT JOIN public.agencies a
  ON a.source_name = pg_temp.sdm_cell(s.cells, '출처 대행사')
WHERE s.dataset = 'sdm_prices'
  AND pg_temp.sdm_cell(s.cells, '가격 ID') IS NOT NULL
ON CONFLICT (source_dataset, source_price_id) DO NOTHING;

-- 6. 패키지 가격 기록
INSERT INTO public.price_records (
    source_dataset, source_price_id, source_row_id, hall_id, product_id,
    agency_id, price_type, price_type_basis, vat_status, source_url,
    source_detail_raw, published_at, file_reference_raw, collected_at, conditions_raw
)
SELECT
    'sdm_packages',
    pg_temp.sdm_cell(s.cells, '패키지 ID'),
    s.source_row_id,
    NULL,
    p.id,
    a.id,
    'UNKNOWN',
    NULL,
    CASE
        WHEN pg_temp.sdm_bool(pg_temp.sdm_cell(s.cells, 'VAT 포함 여부')) IS TRUE THEN 'INCLUDED'
        WHEN pg_temp.sdm_bool(pg_temp.sdm_cell(s.cells, 'VAT 포함 여부')) IS FALSE THEN 'EXCLUDED'
        ELSE 'UNKNOWN'
    END,
    pg_temp.sdm_cell(s.cells, '출처 URL'),
    pg_temp.sdm_cell(s.cells, '출처 상세'),
    NULLIF(pg_temp.sdm_cell(s.cells, '자료 게시일'), '')::date,
    pg_temp.sdm_cell(s.cells, '파일 기준일'),
    NULLIF(pg_temp.sdm_cell(s.cells, '수집일'), '')::date,
    pg_temp.sdm_cell(s.cells, '비고')
FROM _sdm_src s
JOIN public.products p
  ON p.source_dataset = 'sdm_packages'
 AND p.source_product_key = pg_temp.sdm_cell(s.cells, '패키지 ID')
LEFT JOIN public.agencies a
  ON a.source_name = pg_temp.sdm_cell(s.cells, '업체명')
WHERE s.dataset = 'sdm_packages'
  AND pg_temp.sdm_cell(s.cells, '패키지 ID') IS NOT NULL
ON CONFLICT (source_dataset, source_price_id) DO NOTHING;

-- 7. 기본 상품 가격 상세
INSERT INTO public.product_price_details (
    price_record_id, amount_min, amount_max, time_raw, season_standard, time_standard
)
SELECT
    pr.id,
    pg_temp.sdm_num(pg_temp.sdm_cell(s.cells, '가격 최저(원)')),
    pg_temp.sdm_num(pg_temp.sdm_cell(s.cells, '가격 최고(원)')),
    pg_temp.sdm_cell(s.cells, '시간대'),
    pg_temp.sdm_cell(s.cells, '시즌 구분'),
    pg_temp.sdm_cell(s.cells, '시간대(표준)')
FROM _sdm_src s
JOIN public.price_records pr
  ON pr.source_dataset = 'sdm_prices'
 AND pr.source_price_id = pg_temp.sdm_cell(s.cells, '가격 ID')
WHERE s.dataset = 'sdm_prices'
ON CONFLICT (price_record_id) DO NOTHING;

-- 8. 패키지 가격 상세
INSERT INTO public.product_price_details (
    price_record_id, amount_min, amount_max, time_raw, season_standard, time_standard
)
SELECT
    pr.id,
    pg_temp.sdm_num(pg_temp.sdm_cell(s.cells, '패키지 가격(원)')),
    pg_temp.sdm_num(pg_temp.sdm_cell(s.cells, '패키지 가격(원)')),
    NULL, NULL, NULL
FROM _sdm_src s
JOIN public.price_records pr
  ON pr.source_dataset = 'sdm_packages'
 AND pr.source_price_id = pg_temp.sdm_cell(s.cells, '패키지 ID')
WHERE s.dataset = 'sdm_packages'
ON CONFLICT (price_record_id) DO NOTHING;

-- 9. 업체-대행사 연결
-- 업체의 '공개 대행사' 목록을 분리하고, 원본 가격/선택품목/위약금 존재 여부를 계산
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

-- 10. 원본은 있는데 적재되지 않은 행 알림 (업체 ID·대행사 이름이 연결되지 않은 경우, 멈추지는 않음)
DO $$
DECLARE
    v_missing_prices integer;
    v_missing_packages integer;
BEGIN
    SELECT count(*) INTO v_missing_prices
    FROM _sdm_src s
    WHERE s.dataset = 'sdm_prices'
      AND pg_temp.sdm_cell(s.cells, '가격 ID') IS NOT NULL
      AND NOT EXISTS (
          SELECT 1 FROM public.products p
          WHERE p.source_dataset = 'sdm_prices'
            AND p.source_product_key = pg_temp.sdm_cell(s.cells, '가격 ID'));

    SELECT count(*) INTO v_missing_packages
    FROM _sdm_src s
    WHERE s.dataset = 'sdm_packages'
      AND pg_temp.sdm_cell(s.cells, '패키지 ID') IS NOT NULL
      AND NOT EXISTS (
          SELECT 1 FROM public.products p
          WHERE p.source_dataset = 'sdm_packages'
            AND p.source_product_key = pg_temp.sdm_cell(s.cells, '패키지 ID'));

    RAISE NOTICE '적재되지 않은 원본 행: sdm_prices %건(업체 ID 불일치), sdm_packages %건(판매 대행사 이름 불일치)',
        v_missing_prices, v_missing_packages;
END
$$;

-- 11. 적재 결과 검증용 결과셋
SELECT 'agencies' AS table_name, COUNT(*) AS row_count FROM public.agencies
UNION ALL
SELECT 'vendors_sdm', COUNT(*) FROM public.vendors WHERE source_dataset = 'sdm_vendors'
UNION ALL
SELECT 'products_sdm_prices', COUNT(*) FROM public.products WHERE source_dataset = 'sdm_prices'
UNION ALL
SELECT 'products_sdm_packages', COUNT(*) FROM public.products WHERE source_dataset = 'sdm_packages'
UNION ALL
SELECT 'price_records_sdm_prices', COUNT(*) FROM public.price_records WHERE source_dataset = 'sdm_prices'
UNION ALL
SELECT 'price_records_sdm_packages', COUNT(*) FROM public.price_records WHERE source_dataset = 'sdm_packages'
UNION ALL
SELECT 'product_price_details_sdm', COUNT(*)
FROM public.product_price_details ppd
JOIN public.price_records pr ON pr.id = ppd.price_record_id
WHERE pr.source_dataset IN ('sdm_prices','sdm_packages')
UNION ALL
SELECT 'vendor_agencies', COUNT(*) FROM public.vendor_agencies
UNION ALL
SELECT 'price_records_sdm_agency_null', COUNT(*) FROM public.price_records
WHERE source_dataset IN ('sdm_prices','sdm_packages') AND agency_id IS NULL
UNION ALL
SELECT 'vendors_sdm_region_id_null', COUNT(*) FROM public.vendors
WHERE source_dataset = 'sdm_vendors' AND region_id IS NULL
ORDER BY table_name;

COMMIT;
