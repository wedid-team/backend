-- 스드메 예산대 경계 확정 (Q-05, 2026-11-25): 360만 · 420만 · 530만 원
--
-- 근거: 서울 공개 가격(sdm_prices.xlsx, 2026-01 기준)으로 만든 스튜디오 · 드레스 · 메이크업 조합 3,660,438개의
-- 합계 사분위수(Q1 360만 · 중앙값 423만 · Q3 528.5만 원)를 반올림. 구간별 조합 비율은 약 25%씩.
-- 업체 기본가 = 대표 구성(스튜디오 촬영, 드레스 · 메이크업 본식+촬영) 공개 가격 행의 (최저+최고)/2 중앙값.
--
-- 034의 DRAFT 정책(2026-11-draft-1)은 그대로 두고 새 버전을 ACTIVE로 추가한다.
-- 계획 · 스드메 API는 ACTIVE 정책이 있으면 그것을 쓴다. 경계를 바꿀 때는 코드를 고치지 않고 시드만 고친다
-- (이미 쓴 정책 버전은 계산 결과가 참조하므로, 새 버전 행을 추가하고 이 버전을 RETIRED로 바꾸는 방식을 권장).
--
-- 구간은 [하한, 상한): 하한 포함 · 상한 미포함. 034 DRAFT 시드와 같은 규칙이다.
-- BASIC은 하한 없음, LUXURY는 상한 없음(NULL).

INSERT INTO public.calculation_policies (version, status, settings, effective_at)
VALUES (
    '2026-11-active-1',
    'ACTIVE',
    '{
        "defaultSdmBandCode": "STANDARD",
        "sdmBandBoundaries": "DECIDED",
        "notes": ["Q-05 스드메 예산대 경계 확정 2026-11-25 (360·420·530만 원)", "Q-03 범위 금액 반올림 단위 미정"]
    }'::JSONB,
    CURRENT_TIMESTAMP
)
ON CONFLICT (version) DO NOTHING;

INSERT INTO public.sdm_budget_bands (
    policy_id, code, label,
    lower_amount, upper_amount, lower_inclusive, upper_inclusive, sort_order
)
SELECT p.id, b.code, b.label, b.lower_amount, b.upper_amount, TRUE, FALSE, b.sort_order
FROM public.calculation_policies p
CROSS JOIN (
    VALUES
        ('BASIC', '베이직', NULL::NUMERIC, 3600000::NUMERIC, 1),
        ('STANDARD', '스탠다드', 3600000, 4200000, 2),
        ('PREMIUM', '프리미엄', 4200000, 5300000, 3),
        ('LUXURY', '럭셔리', 5300000, NULL, 4)
) AS b (code, label, lower_amount, upper_amount, sort_order)
WHERE p.version = '2026-11-active-1'
ON CONFLICT (policy_id, code) DO NOTHING;
