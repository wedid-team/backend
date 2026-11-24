-- 로드맵 조회 테스트용 추가 데이터. plan-fixture.sql 다음에 실행하며, 테스트 트랜잭션과 함께 롤백된다.
-- 기본 할 일 콘텐츠는 아직 미정이라 테스트용 문구만 둔다.

-- 예식장 단계에 할 일을 하나 더 둬서 단계 안 순서(sort_order)를 확인한다. plan-fixture의 예식장 할 일은 32001.
INSERT INTO public.todo_templates (step_template_id, code, title, sort_order)
VALUES (2, 'ROADMAP_TEST_VENUE_BUDGET', '테스트: 예식장 예산 상한 정하기', 32000);

-- 멤버 표시용 로그인 계정 (신랑: 카카오, 신부: 구글)
INSERT INTO public.oauth_accounts (user_id, provider, provider_subject)
VALUES
    ('00000000-0000-0000-0000-0000000000a1', 'KAKAO', 'roadmap-test-kakao-groom'),
    ('00000000-0000-0000-0000-0000000000a2', 'GOOGLE', 'roadmap-test-google-bride');
