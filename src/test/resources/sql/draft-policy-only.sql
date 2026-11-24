-- 경계 미정(DRAFT 정책만 있는) 상태를 확인하는 테스트용. 로컬 DB의 ACTIVE 정책(sql/035)을
-- 이 트랜잭션 안에서만 물러나게 해 sql/034의 DRAFT 정책을 쓰게 한다. 테스트와 함께 롤백된다.
UPDATE public.calculation_policies SET status = 'RETIRED' WHERE status = 'ACTIVE';
