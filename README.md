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
   for f in sql/*.sql; do docker exec -i backend-db-1 psql -U postgres -d wedid -v ON_ERROR_STOP=1 < "$f"; done
   ```

2. 환경변수 설정
   ```
   cp .env.example .env
   ```
   `.env` 파일을 열어 `WEDID_IMPORT_DIRECTORY` 등 필요한 값을 채워주세요.
   `.env`는 애플리케이션 실행 시 자동으로 로드되며 git에는 커밋되지 않습니다.

3. 애플리케이션 실행
   ```
   ./gradlew bootRun
   ```

## API 문서
서버 실행 후 아래 경로에서 Swagger UI를 확인할 수 있습니다.
- http://localhost:8080/swagger-ui.html
