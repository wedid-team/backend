-- 계획 테스트용 데이터. 테스트 트랜잭션과 함께 롤백된다.
-- 계산 정책 · 예산대 · 로드맵 6단계는 sql/034 시드(DRAFT, 금액 없음)를 그대로 쓴다.

INSERT INTO public.regions (id, code, display_name, source_names, sort_order)
VALUES (32001, 'PLAN_TEST_REGION', '계획 테스트 권역', '[]', 32001);

INSERT INTO public.users (id, display_name, email)
VALUES
    ('00000000-0000-0000-0000-0000000000a1', '신랑', NULL),
    ('00000000-0000-0000-0000-0000000000a2', '신부', NULL),
    ('00000000-0000-0000-0000-0000000000a3', '다른 회원', NULL);

-- 기본 할 일 콘텐츠는 아직 미정이라 테스트용 문구만 둔다.
INSERT INTO public.todo_templates (step_template_id, code, title, sort_order)
VALUES
    (2, 'PLAN_TEST_VENUE_TOUR', '테스트: 예식장 투어 예약', 32001),
    (3, 'PLAN_TEST_SDM_CONSULT', '테스트: 스드메 상담 예약', 32001);
