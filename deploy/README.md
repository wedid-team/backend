# 배포 가이드 (AWS EC2 t4g + Neon PostgreSQL, 시드니)

```
사용자 ──HTTPS──▶ EC2 t4g (arm64, ap-southeast-2)
                  [ Nginx(443) ──▶ Spring(app:8080) ] ──SSL──▶ Neon PostgreSQL (aws-ap-southeast-2)
                    Certbot: 인증서 자동 갱신
```

| 파일 | 역할 |
|---|---|
| `../Dockerfile` | Spring 이미지 (멀티스테이지, 실행 단계 arm64, JVM 메모리 옵션) |
| `docker-compose.yml` | app + nginx + certbot |
| `nginx/templates/default.conf.template` | HTTP→HTTPS 리다이렉트, 프록시, X-Forwarded 헤더 |
| `scripts/init-cert.sh` | Let's Encrypt 인증서 최초 발급 |
| `scripts/apply-sql.sh` | Neon에 `sql/` 마이그레이션 적용 |
| `.env.example` | 배포 환경변수 목록 → `deploy/.env`로 복사해서 사용 |

> 로컬 개발용 DB는 기존처럼 루트의 `docker-compose.yml`을 씁니다. 이 폴더의 파일은 배포 전용입니다.

---

## 0. 준비물

- **도메인**: Let's Encrypt는 IP 주소로는 발급하지 않고, 구글 로그인도 IP 주소 리다이렉트를 허용하지 않아 도메인이 꼭 필요합니다. (도메인은 팀에서 정하는 중. 아래 `<도메인>` 자리에 넣으세요.)

### 도메인 구조 (기본)

| 주소 | 용도 | 가리키는 곳 |
|---|---|---|
| `https://api.<도메인>` | 백엔드 (이 서버, Swagger 포함) | EC2 탄력적 IP |
| `https://<도메인>` | 프론트엔드 | 프론트 배포처 |

**왜 이렇게 나누나**: 인증 쿠키가 `SameSite=Lax`입니다. 프론트와 백엔드가 **같은 사이트(같은 등록 도메인)** 아래에 있어야 프론트에서 보내는 API 요청에 로그인 쿠키가 실립니다.
- `api.<도메인>`과 `<도메인>`은 같은 사이트라서 지금 코드 그대로 동작합니다.
- 프론트를 `*.vercel.app` 같은 다른 도메인에 두면 로그인 상태가 유지되지 않습니다. 쿠키 정책을 따로 바꿔야 합니다.
- 같은 이유로 `duckdns.org` 같은 공용 서브도메인 서비스(`a.duckdns.org` / `b.duckdns.org`)는 서로 다른 사이트로 취급되니, **직접 등록한 도메인**을 쓰세요.
- **Neon 프로젝트**: Postgres 16, 리전 **AWS Asia Pacific (Sydney) `aws-ap-southeast-2`**. EC2와 같은 리전이라 요청마다 오가는 DB 지연이 작습니다.
- **XLSX 원본 9개** (데이터 적재 시): `wedding_venues`, `wedding_hall_prices`, `wedding_options`, `sdm_vendors`, `sdm_prices`, `sdm_penalties`, `sdm_packages`, `sdm_options`, `market_stats` (.xlsx)

---

## 1. EC2에서 실행할 순서

### 1-1. AWS 콘솔 (직접)
1. EC2 인스턴스 생성
   - 리전: **ap-southeast-2 (시드니)**. 콘솔 오른쪽 위에서 리전을 먼저 바꾸고 만드세요.
   - AMI: Ubuntu Server 24.04 LTS, 아키텍처 **64비트(Arm)**. x86 AMI는 t4g에서 안 뜹니다.
   - 유형: **t4g.small**(2GB) / 스토리지 20GB (gp3)
     - `deploy/.env`의 `APP_MEM_LIMIT=1536m`으로 앱 메모리를 잡습니다 (`.env.example`에 들어 있음).
   - 키 페어 생성 후 `.pem` 보관
2. 보안 그룹 인바운드
   | 포트 | 소스 | 용도 |
   |---|---|---|
   | 22 | 내 IP | SSH |
   | 80 | 0.0.0.0/0 | 인증서 발급·갱신, HTTPS 리다이렉트 |
   | 443 | 0.0.0.0/0 | 서비스 |
   - 8080은 열지 않습니다 (Nginx만 외부에 노출).
3. **탄력적 IP** 할당 → 인스턴스에 연결 (재시작해도 IP 고정)
4. 도메인 DNS에 **A 레코드**: `api.<도메인> → 탄력적 IP` (프론트용 `<도메인>`은 프론트 배포처 안내대로 설정)

### 1-2. 서버 초기 설정 (SSH 접속 후 1회)
```bash
ssh -i wedid.pem ubuntu@<탄력적 IP>

# Docker 설치
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker ubuntu
exit   # 다시 접속해야 docker 그룹 적용

# 스왑 2GB (메모리 1~2GB라 Gradle 이미지 빌드 시 메모리 부족 방지)
sudo fallocate -l 2G /swapfile && sudo chmod 600 /swapfile
sudo mkswap /swapfile && sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
```

### 1-3. 코드 받기와 환경변수
```bash
git clone https://github.com/wedid-team/backend.git
cd backend
git checkout develop        # PR 머지 전이라면 feat/deploy

cp deploy/.env.example deploy/.env
nano deploy/.env            # 2번 목록 참고해서 채우기
chmod 600 deploy/.env
```

### 1-4. DB 스키마 적용 (Neon이 빈 DB일 때 1회)
4번 참고. EC2에서 실행해도 되고 로컬 PC에서 해도 됩니다.

### 1-5. 인증서 발급 (1회)
```bash
cd ~/backend/deploy
sh scripts/init-cert.sh
```
도메인 A 레코드가 아직 전파되지 않았으면 실패합니다. `nslookup api.<도메인>`이 탄력적 IP를 가리키는지 먼저 확인하세요.

### 1-6. 실행
```bash
cd ~/backend/deploy
docker compose up -d --build      # 첫 빌드는 5~10분 (EC2가 arm64라 네이티브 빌드)
docker compose ps
docker compose logs -f app        # "Started BackendApplication" 나오면 성공
```
확인: `https://api.<도메인>/swagger-ui.html`

### 1-7. 업데이트 배포
```bash
cd ~/backend && git pull
sh deploy/scripts/apply-sql.sh 036   # 새 sql 파일이 있을 때만, 새 파일 번호부터
cd deploy && docker compose up -d --build
```

---

## 2. 필요한 환경변수 (`deploy/.env`)

| 변수 | 필수 | 설명 | 예시 |
|---|---|---|---|
| `DOMAIN` | ✅ | 백엔드 도메인 (Nginx·인증서) | `api.<도메인>` |
| `CERT_EMAIL` | ✅ | 인증서 만료 알림 이메일 | |
| `DB_URL` | ✅ | Neon JDBC 주소 | `jdbc:postgresql://ep-xxx.ap-southeast-2.aws.neon.tech/neondb?sslmode=require` |
| `DB_USERNAME` | ✅ | Neon 사용자 | `neondb_owner` |
| `DB_PASSWORD` | ✅ | Neon 비밀번호 | |
| `JWT_SECRET` | ✅ | 32바이트 이상, 로컬과 다른 값 | `openssl rand -base64 48` |
| `KAKAO_CLIENT_ID` / `KAKAO_CLIENT_SECRET` | ✅ | 카카오 REST API 키 / Client Secret | |
| `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` | ✅ | 구글 OAuth 클라이언트 | |
| `FRONTEND_BASE_URL` | ✅ | 로그인 후 돌아갈 프론트 주소 | `https://<도메인>` |
| `CORS_ALLOWED_ORIGINS` | ✅ | 허용 origin, 쉼표 구분 | `https://<도메인>,http://localhost:3000` |

| `APP_MEM_LIMIT` | | 앱 컨테이너 메모리 제한 (비우면 `768m`) | `1536m` (t4g.small) |

- `AUTH_COOKIE_SECURE=true`는 `docker-compose.yml`에서 고정합니다.
- `WEDID_IMPORT_DIRECTORY`는 서버에 필요 없습니다 (XLSX 적재 때만 사용).
- Neon 주소는 대시보드 **Connect**에서 **Connection pooling을 끈** 주소를 씁니다.
  `postgresql://USER:PASSWORD@HOST/DB?sslmode=require` → `DB_URL=jdbc:postgresql://HOST/DB?sslmode=require`

---

## 3. 카카오·구글 콘솔 리다이렉트 URI

로그인 콜백 경로는 `{백엔드 주소}/api/v1/auth/callback/{kakao|google}`입니다. (`application.properties`의 `{baseUrl}`이 Nginx가 넘긴 헤더로 `https://api.<도메인>`이 됩니다.)

**카카오 developers** (내 애플리케이션 → 해당 앱)
- 플랫폼 → Web → 사이트 도메인: `https://api.<도메인>`, `https://<도메인>` 추가
- 카카오 로그인 → Redirect URI: `https://api.<도메인>/api/v1/auth/callback/kakao` 추가
- 카카오 로그인 활성화 ON 확인

**Google Cloud Console** (API 및 서비스 → 사용자 인증 정보 → OAuth 2.0 클라이언트 ID)
- 승인된 리디렉션 URI: `https://api.<도메인>/api/v1/auth/callback/google` 추가
- OAuth 동의 화면이 "테스트" 상태면 등록한 테스트 사용자만 로그인됩니다.

로컬용 `http://localhost:8080/...` URI는 지우지 말고 같이 둡니다.

---

## 4. Neon에 sql 적용과 XLSX 원본 적재

둘 다 **Docker가 있는 아무 PC**(로컬 PC 또는 EC2)에서 저장소 루트 기준으로 실행합니다. 접속 정보는 `deploy/.env`를 씁니다. 로컬 PC에서 하려면 로컬에도 `deploy/.env`를 만들어 DB 값만 채우면 됩니다.

### 4-1. sql 마이그레이션
```bash
sh deploy/scripts/apply-sql.sh          # 빈 DB: 001부터 전부
sh deploy/scripts/apply-sql.sh 036      # 이후: 새 파일 번호부터
```
- psql 설치 없이 `postgres:16-alpine` 컨테이너의 psql을 씁니다.
- 파일마다 트랜잭션 하나로 실행하고, 에러가 나면 그 파일은 되돌린 뒤 멈춥니다.
- 이미 적용한 파일을 다시 돌리면 `already exists`로 멈춥니다. 어디까지 적용했는지는 직접 기록해야 합니다 (마이그레이션 도구 없음).

### 4-2. XLSX 원본 적재 (XLSX 파일이 있는 PC에서)
```bash
./gradlew bootJar -x test
set -a; . deploy/.env; set +a
WEDID_IMPORT_DIRECTORY="C:/경로/xlsx폴더" \
SPRING_PROFILES_ACTIVE=import-file-metadata \
java -jar build/libs/backend-0.0.1-SNAPSHOT.jar --server.port=0
```
- 로그에 `원본 행 저장 완료: market_stats.xlsx`가 나오면 끝난 것입니다. 서버가 계속 떠 있으니 **Ctrl+C로 종료**합니다.
  (보안 설정이 웹 서버를 전제로 해서 웹 서버를 끈 채로는 실행되지 않고, 그래서 `--server.port=0`으로 빈 포트에 띄웁니다.)
- 같은 파일을 다시 돌려도 중복 적재되지 않습니다 (`ON CONFLICT DO NOTHING`).
- `deploy/.env`의 비밀번호에 `$`, `!`, 공백이 있으면 `. deploy/.env`가 깨질 수 있습니다. 그때는 `DB_URL`/`DB_USERNAME`/`DB_PASSWORD`만 따옴표로 감싸 직접 export 하세요.
- 이 단계는 `source_files`/`source_rows`에 **원본 행만** 넣습니다. 원본 행을 `halls`·`vendors`·`price_records` 등으로 옮기는 코드는 아직 없습니다.

---

## 문제 해결

| 증상 | 확인 |
|---|---|
| nginx가 계속 재시작 | `docker compose logs nginx`. 인증서 파일이 없으면 `init-cert.sh`를 먼저 실행 |
| app이 바로 종료 | `docker compose logs app`. 환경변수 누락이나 DB 스키마 미적용(`ddl-auto=validate`) |
| 빌드 중 멈춤·Killed | 스왑 설정 확인 (`free -h`) |
| Swagger "Try it out"이 http로 요청 | Nginx 경유인지 확인 (`X-Forwarded-Proto`) |
| 첫 요청이 느림 | Neon은 5분간 요청이 없으면 잠들고, 깨어나는 데 1초 안팎이 걸림 |
