-- 로드맵 단계 문구 · 기본 할 일 (UT용 목업)
--
-- 콘텐츠가 확정되지 않아 프론트 목업(plannerMock.ts) 문구를 그대로 넣는다.
-- 확정 문구가 나오면 새 마이그레이션으로 교체한다.
-- 기본 할 일은 계획을 만들 때 복사되므로, 이미 만든 계획에는 반영되지 않는다.
-- 목업에 할 일이 없는 4~6단계는 비워 둔다.

-- 단계별 "정해야 할 것" · "알아두면 좋은 점"
UPDATE public.roadmap_step_templates AS t
SET decision_items = v.decision_items::JSONB,
    guidance_items = v.guidance_items::JSONB
FROM (
    VALUES
        (1,
         '["전체 예산 상한", "예식 시기", "상견례 날짜, 장소"]',
         '["양가 의견을 미리 나눠 듣기", "예산은 범위로 먼저 합의하기"]'),
        (2,
         '["예식 날짜, 시간대", "보증 인원", "식사 형태 (뷔페 / 코스)"]',
         '["보증 인원은 실제 예상보다 보수적으로", "계약금 환급 기준 확인", "박람회 당일 계약은 서두르지 않기"]'),
        (3,
         '["촬영 스타일 (스튜디오 · 야외)", "드레스 투어 샵 수", "원본 · 수정본 포함 여부"]',
         '["추가금(원본 · 드레스 업그레이드 · 헬퍼비) 미리 확인", "같은 업체도 대행사마다 가격이 달라요", "당일 계약 할인은 서두르지 않기"]'),
        (4,
         '["본식 스냅 · 영상 여부", "신혼여행지와 일정"]',
         '["성수기 항공권은 일찍 확인하기"]'),
        (5,
         '["청첩장 디자인 · 수량", "최종 하객 수"]',
         '["보증 인원 변경 마감일 확인하기"]'),
        (6,
         '["당일 동선 · 담당자", "잔금 정산 일정"]',
         '["업체별 연락처를 한 곳에 모아두기"]')
) AS v (id, decision_items, guidance_items)
WHERE t.id = v.id;

-- 단계별 기본 할 일
INSERT INTO public.todo_templates (step_template_id, code, title, sort_order)
VALUES
    (1, 'AGREEMENT_BUDGET_RANGE', '예산 범위 합의하기', 1),
    (1, 'AGREEMENT_MEETING_PLACE', '상견례 장소 예약', 2),
    (2, 'VENUE_BUDGET_CAP', '예산 상한 합의하기', 1),
    (2, 'VENUE_TOUR_BOOKING', '후보 예식장 3곳 투어 예약', 2),
    (2, 'VENUE_SHARE_PARENTS', '부모님께 후보 공유', 3),
    (3, 'SDM_BUDGET_BAND', '스드메 예산대 정하기', 1),
    (3, 'SDM_CONSULT_BOOKING', '스드메 조합 후보 상담 예약', 2),
    (3, 'SDM_DRESS_TOUR', '드레스 투어 일정 잡기', 3)
ON CONFLICT (code) DO NOTHING;
