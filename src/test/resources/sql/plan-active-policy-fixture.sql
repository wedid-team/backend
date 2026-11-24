-- 예산대 경계가 확정된 뒤의 동작을 확인하기 위한 테스트 전용 ACTIVE 정책.
-- 250 · 350 · 450만 원은 Q-05의 예시값이며 마이그레이션에는 넣지 않는다.

-- ACTIVE는 하나만 둘 수 있어(부분 UNIQUE) 로컬 DB의 ACTIVE 정책(sql/035)은 이 트랜잭션 안에서만 물러난다.
UPDATE public.calculation_policies SET status = 'RETIRED' WHERE status = 'ACTIVE';

INSERT INTO public.calculation_policies (version, status, settings)
VALUES ('plan-test-active', 'ACTIVE', '{"defaultSdmBandCode": "STANDARD"}');

INSERT INTO public.sdm_budget_bands (
    policy_id, code, label,
    lower_amount, upper_amount, lower_inclusive, upper_inclusive, sort_order
)
SELECT p.id, b.code, b.label, b.lower_amount, b.upper_amount, b.lower_inclusive, b.upper_inclusive, b.sort_order
FROM public.calculation_policies p
CROSS JOIN (
    VALUES
        ('BASIC', '베이직', NULL::NUMERIC, 2500000::NUMERIC, TRUE, TRUE, 1),
        ('STANDARD', '스탠다드', 2500000, 3500000, FALSE, TRUE, 2),
        ('PREMIUM', '프리미엄', 3500000, 4500000, FALSE, TRUE, 3),
        ('LUXURY', '럭셔리', 4500000, NULL, FALSE, FALSE, 4)
) AS b (code, label, lower_amount, upper_amount, lower_inclusive, upper_inclusive, sort_order)
WHERE p.version = 'plan-test-active';
