# backend
WEDID 백엔드

## 요구사항
- JDK 21
- Docker / Docker Compose (로컬 DB 실행용)

## 로컬 개발 환경 세팅

1. 로컬 PostgreSQL 실행
   ```
   docker compose up -d
   ```
   `wedid` 데이터베이스가 `localhost:5433`에 뜨며, 계정은 `postgres` / `postgres` 입니다.
   (별도 설정 없이 `application.properties`의 기본값과 일치합니다. 호스트에 PostgreSQL을 직접 설치해 5432를 이미 쓰고 있는 경우가 있어 5433으로 매핑했습니다.)

   처음 띄운 직후에는 테이블이 없는 빈 DB이므로, `sql/` 폴더의 스키마를 순서대로 적용해주세요.
   (`spring.jpa.hibernate.ddl-auto=validate`라 테이블이 없으면 애플리케이션이 기동하지 않습니다.)
   ```
   for f in sql/*.sql; do docker compose exec -T db psql -U postgres -d wedid -v ON_ERROR_STOP=1 < "$f"; done
   ```

2. 환경변수 설정
   ```
   cp .env.example .env
   ```
   `.env` 파일을 열어 `WEDID_IMPORT_DIRECTORY`, `DB_PASSWORD` 등 필요한 값을 채워주세요.
   (`DB_PASSWORD`는 보안 규칙상 기본값이 없어 설정하지 않으면 애플리케이션이 기동하지 않습니다.)
   `.env`는 애플리케이션 실행 시 자동으로 로드되며 git에는 커밋되지 않습니다.
   - `JWT_SECRET`: 32자 이상 아무 랜덤 문자열
   - `KAKAO_*`, `GOOGLE_*`: 팀 공용 키를 받아서 입력 (로그인을 쓰지 않으면 아무 값이나 입력해도 서버는 뜹니다)

3. 애플리케이션 실행
   ```
   ./gradlew bootRun
   ```

## API 문서
서버 실행 후 아래 경로에서 Swagger UI를 확인할 수 있습니다.
- 배포 서버: https://api.wedid.site/swagger-ui.html
- 로컬: http://localhost:8080/swagger-ui.html

## 배포
AWS EC2(Docker Compose: Spring + Nginx HTTPS) + Neon PostgreSQL 구성입니다.
실행 순서, 환경변수, OAuth 리다이렉트 URI, DB 적용 방법은 [deploy/README.md](deploy/README.md)를 참고하세요.
