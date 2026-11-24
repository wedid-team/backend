-- 계획 생성에 필요한 기준 데이터: 계산 정책 · 스드메 예산대 · 로드맵 6단계
--
-- 스드메 예산대 경계(Q-05)는 아직 미정이므로 금액 없이 코드만 둔 DRAFT 정책으로 시작한다.
-- (db.md 4.21 · 10: 예시 숫자 250·350·450만 원으로 활성화하지 않는다)
-- 경계가 확정되면 이 행을 고치지 않고 새 버전을 추가해 ACTIVE로 전환한다.
-- 계획은 ACTIVE 정책이 있으면 그것을, 없으면 가장 최근 DRAFT 정책을 사용한다.

INSERT INTO public.calculation_policies (version, status, settings)
VALUES (
    '2026-11-draft-1',
    'DRAFT',
    '{
        "defaultSdmBandCode": "STANDARD",
        "sdmBandBoundaries": "UNDECIDED",
        "notes": ["Q-05 스드메 예산대 경계 미정", "Q-03 범위 금액 반올림 단위 미정"]
    }'::JSONB
)
ON CONFLICT (version) DO NOTHING;

INSERT INTO public.sdm_budget_bands (
    policy_id, code, label,
    lower_amount, upper_amount, lower_inclusive, upper_inclusive, sort_order
)
SELECT p.id, b.code, b.label, NULL, NULL, TRUE, FALSE, b.sort_order
FROM public.calculation_policies p
CROSS JOIN (
    VALUES
        ('BASIC', '베이직', 1),
        ('STANDARD', '스탠다드', 2),
        ('PREMIUM', '프리미엄', 3),
        ('LUXURY', '럭셔리', 4)
) AS b (code, label, sort_order)
WHERE p.version = '2026-11-draft-1'
ON CONFLICT (policy_id, code) DO NOTHING;

-- 로드맵 6단계 (db.md 6.3 · R-01). 정해야 할 것 · 유의 사항 문구는 콘텐츠 확정 후 채운다.
INSERT INTO public.roadmap_step_templates (
    id, code, title, sort_order,
    days_before_start, days_before_end, cost_kind,
    decision_items, guidance_items
)
VALUES
    (1, 'BUDGET_AGREEMENT', '예산 일정 합의, 상견례', 1, 365, 300, 'NONE', '[]', '[]'),
    (2, 'VENUE_CONTRACT', '예식장 계약', 2, 300, 240, 'VENUE', '[]', '[]'),
    (3, 'SDM_CONTRACT', '스드메 계약', 3, 240, 180, 'SDM', '[]', '[]'),
    (4, 'SHOOTING_TRAVEL', '촬영, 신혼여행 예약', 4, 180, 120, 'NONE', '[]', '[]'),
    (5, 'INVITATIONS_GUESTS', '청첩장 전달, 하객 수 확정', 5, 120, 30, 'NONE', '[]', '[]'),
    (6, 'FINAL_CHECK', '최종 점검', 6, 30, 0, 'NONE', '[]', '[]')
ON CONFLICT (id) DO NOTHING;

-- 단계별 기본 할 일(todo_templates)은 콘텐츠가 확정되면 별도 마이그레이션으로 추가한다.
