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

3. 애플리케이션 실행
   ```
   ./gradlew bootRun
   ```

## 소셜 로그인 설정

로그인은 카카오·구글 OAuth로 하고, 로그인 상태는 HttpOnly 쿠키에 담은 JWT 액세스 토큰(30분)과 리프레시 토큰(14일)으로 유지합니다.
`sql/033_create_refresh_tokens.sql`이 적용되어 있어야 합니다.

### 카카오 (https://developers.kakao.com)
1. 내 애플리케이션 → 애플리케이션 추가
2. 앱 키 → **REST API 키**를 `KAKAO_CLIENT_ID`에 넣습니다.
3. 보안 → **Client Secret** 코드를 생성하고 활성화한 뒤 `KAKAO_CLIENT_SECRET`에 넣습니다.
4. 카카오 로그인 → 활성화 ON, **Redirect URI**에 `http://localhost:8080/api/v1/auth/callback/kakao`를 등록합니다.
5. 카카오 로그인 → 동의항목에서 **닉네임**(profile_nickname)을 필수 동의로 설정합니다. 현재는 비즈 앱이 아니라 이메일은 요청하지 않습니다.
   비즈 앱으로 전환해 이메일 동의항목을 설정하면 `application.properties`의 kakao scope에 `account_email`을 추가하세요. 동의항목 없이 scope에만 넣으면 로그인 오류(KOE205)가 납니다.

### 구글 (https://console.cloud.google.com)
1. API 및 서비스 → OAuth 동의 화면 구성 (범위: `openid`, `profile`, `email`)
2. 사용자 인증 정보 → OAuth 클라이언트 ID 만들기 → 애플리케이션 유형 **웹 애플리케이션**
3. 승인된 리디렉션 URI에 `http://localhost:8080/api/v1/auth/callback/google`을 등록합니다.
4. 발급된 클라이언트 ID·보안 비밀을 `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`에 넣습니다.

배포 환경에서는 Redirect URI를 `https://{API 도메인}/api/v1/auth/callback/{kakao|google}`로 추가 등록하고 `AUTH_COOKIE_SECURE=true`로 설정하세요.
인증 쿠키가 `SameSite=Lax`이므로 프론트엔드와 API는 같은 사이트(예: `wedid.app` / `api.wedid.app`)에 있어야 합니다.

### 프론트엔드 연동 요약
- 로그인 버튼: `location.href = API + '/api/v1/auth/login/kakao?redirect=' + encodeURIComponent('/roadmap?resume=save-plan')`
- 성공하면 `redirect` 경로로 돌아오고, 실패하면 같은 경로에 `loginError=cancelled|failed`가 붙습니다.
- 모든 API 요청에 `credentials: 'include'`를 붙입니다. 401을 받으면 `POST /api/v1/auth/refresh`를 한 번만 시도한 뒤 다시 요청하고, 그래도 401이면 로그인 모달을 띄웁니다.
- 로그인 상태: `GET /api/v1/auth/me` → `{ authenticated, user: { id, displayName, email } }`
- 로그아웃: `POST /api/v1/auth/logout`

## API 문서
서버 실행 후 아래 경로에서 Swagger UI를 확인할 수 있습니다.
- http://localhost:8080/swagger-ui.html
