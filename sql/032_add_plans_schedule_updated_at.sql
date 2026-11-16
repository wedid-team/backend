-- 예식 일정 변경 시각을 일반 조건 변경 시각과 분리
ALTER TABLE public.plans
    ADD COLUMN schedule_updated_at TIMESTAMPTZ
    NOT NULL DEFAULT CURRENT_TIMESTAMP;