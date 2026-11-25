# 배포 가이드 (AWS EC2 t4g + Neon PostgreSQL, 시드니)

```
사용자 ──HTTPS──▶ api.wedid.site = EC2 t4g.small (arm64, ap-southeast-2)
                  [ Nginx(443) ──▶ Spring(app:8080) ] ──SSL──▶ Neon PostgreSQL (aws-ap-southeast-2)
                    Certbot: 인증서 자동 갱신

main에 머지 ──▶ GitHub Actions(.github/workflows/deploy.yml) ──SSH──▶ EC2: git pull → docker compose up -d --build
```

| 파일 | 역할 |
|---|---|
| `../Dockerfile` | Spring 이미지 (멀티스테이지, 실행 단계 arm64, JVM 메모리 옵션) |
| `docker-compose.yml` | app + nginx + certbot |
| `nginx/templates/default.conf.template` | HTTP→HTTPS 리다이렉트, 프록시, X-Forwarded 헤더 |
| `scripts/init-cert.sh` | Let's Encrypt 인증서 최초 발급 |
| `scripts/apply-sql.sh` | Neon에 `sql/` 마이그레이션 적용, 또는 sql 파일 하나 실행 |
| `.env.example` | 배포 환경변수 목록 → 서버에서 `deploy/.env`로 복사해서 사용 |
| `../.github/workflows/deploy.yml` | main 머지 시 자동 배포 + 헬스체크 |
| `../sql/data/sdm_normalization_rebuild.sql` | 스드메 원본 → 정리 테이블 적재 |

> 로컬 개발용 DB는 기존처럼 루트의 `docker-compose.yml`을 씁니다. 이 폴더의 파일은 배포 전용입니다.

---

## 0. 도메인

| 주소 | 용도 | DNS |
|---|---|---|
| `https://api.wedid.site` | 백엔드 (이 서버, Swagger 포함) | A 레코드 → EC2 탄력적 IP |
| `https://wedid.site` | 프론트엔드 | 프론트 배포처 안내대로 |

- 인증 쿠키가 `SameSite=Lax`라서, 프론트와 백엔드가 **같은 사이트(wedid.site)** 아래에 있어야 프론트의 API 요청에 로그인 쿠키가 실립니다.
  `api.wedid.site`와 `wedid.site`는 같은 사이트라서 지금 코드 그대로 동작합니다.
- 프론트를 `*.vercel.app` 같은 다른 도메인 주소로 직접 쓰면 로그인 상태가 유지되지 않습니다. 프론트도 `wedid.site`로 연결해서 쓰세요.
- Let's Encrypt와 구글 로그인은 IP 주소를 받지 않아서 도메인이 꼭 필요합니다.

---

## 1. EC2 최초 설정 (1회)

### 1-1. AWS 콘솔
1. EC2 인스턴스 생성
   - 리전: **ap-southeast-2 (시드니)**. 콘솔 오른쪽 위에서 리전을 먼저 바꾸고 만드세요.
   - AMI: Ubuntu Server 24.04 LTS, 아키텍처 **64비트(Arm)**. x86 AMI는 t4g에서 안 뜹니다.
   - 유형: **t4g.small**(2GB) / 스토리지 20GB (gp3)
   - 키 페어 생성 후 `.pem` 보관 (사람이 접속할 때 쓰는 키. 자동 배포용 키는 3-1에서 따로 만듭니다)
2. 보안 그룹 인바운드
   | 포트 | 소스 | 용도 |
   |---|---|---|
   | 22 | 0.0.0.0/0 | SSH (사람 + GitHub Actions 자동 배포) |
   | 80 | 0.0.0.0/0 | 인증서 발급·갱신, HTTPS 리다이렉트 |
   | 443 | 0.0.0.0/0 | 서비스 |
   - **22번을 전체에 여는 이유**: GitHub Actions 러너는 실행할 때마다 IP가 바뀌고 범위가 매우 넓어서 IP로 제한할 수 없습니다.
     대신 비밀번호 로그인을 막고(키로만 접속) fail2ban으로 반복 실패한 IP를 차단합니다 (1-2).
   - 8080은 열지 않습니다 (Nginx만 외부에 노출).
3. **탄력적 IP** 할당 → 인스턴스에 연결 (재시작해도 IP 고정)
4. 도메인 DNS에 **A 레코드** `api` → 탄력적 IP (`api.wedid.site`)

### 1-2. 서버 기본 설정
```bash
ssh -i wedid.pem ubuntu@api.wedid.site

# Docker 설치
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker ubuntu

# 스왑 2GB (Gradle 이미지 빌드 시 메모리 부족 방지)
sudo fallocate -l 2G /swapfile && sudo chmod 600 /swapfile
sudo mkswap /swapfile && sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab

# SSH: 비밀번호 로그인·root 로그인이 꺼져 있는지 확인 (Ubuntu AMI 기본값은 둘 다 no)
sudo sshd -T | grep -E '^(passwordauthentication|permitrootlogin|kbdinteractiveauthentication) '
#   passwordauthentication no / permitrootlogin no(또는 prohibit-password) / kbdinteractiveauthentication no 이어야 함

# fail2ban: SSH 로그인을 10분에 5번 실패한 IP를 1시간 차단
sudo apt-get update && sudo apt-get install -y fail2ban python3-systemd
sudo tee /etc/fail2ban/jail.local > /dev/null <<'EOF'
[sshd]
enabled  = true
backend  = systemd
maxretry = 5
findtime = 10m
bantime  = 1h
EOF
sudo systemctl enable --now fail2ban
sudo systemctl restart fail2ban
sudo fail2ban-client status sshd      # "Status for the jail: sshd" 가 나오면 동작 중

exit   # 다시 접속해야 docker 그룹 적용
```
- 내 IP가 차단됐으면 다른 네트워크(휴대폰 핫스팟 등)로 접속해서 `sudo fail2ban-client set sshd unbanip <내 IP>`.
- GitHub Actions는 키로 바로 성공하므로 차단되지 않습니다.

### 1-3. 코드 받기
**레포가 public일 때** (지금 상태)
```bash
git clone https://github.com/wedid-team/backend.git ~/backend
cd ~/backend && git checkout main
```

**레포가 private일 때: deploy key (읽기 전용 키)**
1. 서버에서 GitHub용 키를 만듭니다.
   ```bash
   ssh-keygen -t ed25519 -C "wedid-ec2-deploy-key" -f ~/.ssh/github_deploy -N ""
   cat ~/.ssh/github_deploy.pub      # 출력된 한 줄을 복사
   ```
2. GitHub 레포 → **Settings → Deploy keys → Add deploy key**
   - Title: `wedid-ec2`, Key: 복사한 한 줄
   - **Allow write access는 체크하지 않습니다** (서버는 pull만 함)
3. 서버에서 github.com 접속에 이 키를 쓰도록 설정합니다.
   ```bash
   cat >> ~/.ssh/config <<'EOF'
   Host github.com
     HostName github.com
     User git
     IdentityFile ~/.ssh/github_deploy
     IdentitiesOnly yes
   EOF
   chmod 600 ~/.ssh/config
   ssh -T git@github.com
   ```
   처음 접속할 때 나오는 지문(fingerprint)이 [GitHub 공식 SSH 키 지문](https://docs.github.com/ko/authentication/keeping-your-account-and-data-secure/githubs-ssh-key-fingerprints)과 같은지 확인한 뒤 `yes`.
   `Hi wedid-team/backend! You've successfully authenticated...`가 나오면 성공입니다.
4. SSH 주소로 clone 합니다. 이미 https로 받아뒀다면 주소만 바꿉니다.
   ```bash
   git clone git@github.com:wedid-team/backend.git ~/backend        # 새로 받을 때
   cd ~/backend && git remote set-url origin git@github.com:wedid-team/backend.git   # 이미 받았을 때
   git checkout main && git pull
   ```

### 1-4. 환경변수 (서버에만)
```bash
cd ~/backend
cp deploy/.env.example deploy/.env
nano deploy/.env            # 2번 목록 참고해서 채우기
chmod 600 deploy/.env
```
`deploy/.env`는 **서버에만** 둡니다. git에 커밋하지 않고 GitHub Secrets에도 넣지 않습니다. 자동 배포는 서버에 있는 이 파일을 그대로 씁니다.

### 1-5. DB 준비
4번 순서대로 Neon에 스키마와 데이터를 넣습니다. 스키마가 없으면 앱이 뜨지 않습니다(`ddl-auto=validate`).

### 1-6. 인증서 발급
```bash
nslookup api.wedid.site               # 탄력적 IP가 나오는지 먼저 확인
cd ~/backend/deploy && sh scripts/init-cert.sh
```

### 1-7. 첫 실행
```bash
cd ~/backend/deploy
docker compose up -d --build      # 첫 빌드는 5~10분 (EC2가 arm64라 네이티브 빌드)
docker compose ps
docker compose logs -f app        # "Started BackendApplication" 나오면 성공
```
확인: https://api.wedid.site/swagger-ui.html

여기까지 되면 이후 배포는 3번(자동 배포)으로 합니다.

---

## 2. 필요한 환경변수 (`deploy/.env`, 서버에만)

| 변수 | 필수 | 설명 | 값 |
|---|---|---|---|
| `DOMAIN` | ✅ | 백엔드 도메인 (Nginx·인증서) | `api.wedid.site` |
| `CERT_EMAIL` | ✅ | 인증서 만료 알림 이메일 | |
| `DB_URL` | ✅ | Neon JDBC 주소 | `jdbc:postgresql://ep-xxx.ap-southeast-2.aws.neon.tech/neondb?sslmode=require` |
| `DB_USERNAME` | ✅ | Neon 사용자 | `neondb_owner` |
| `DB_PASSWORD` | ✅ | Neon 비밀번호 | |
| `JWT_SECRET` | ✅ | 32바이트 이상, 로컬과 다른 값 | `openssl rand -base64 48` |
| `KAKAO_CLIENT_ID` / `KAKAO_CLIENT_SECRET` | ✅ | 카카오 REST API 키 / Client Secret | |
| `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` | ✅ | 구글 OAuth 클라이언트 | |
| `FRONTEND_BASE_URL` | ✅ | 로그인 후 돌아갈 프론트 주소 | `https://wedid.site` |
| `CORS_ALLOWED_ORIGINS` | ✅ | 허용 origin, 쉼표 구분 | `https://wedid.site,http://localhost:3000` |
| `APP_MEM_LIMIT` | | 앱 컨테이너 메모리 제한 | 비우면 `1536m` (t4g.small) |

- `AUTH_COOKIE_SECURE=true`는 `docker-compose.yml`에서 고정합니다.
- `WEDID_IMPORT_DIRECTORY`는 서버에 필요 없습니다 (XLSX 적재 때만 사용).
- `CORS_ALLOWED_ORIGINS`의 `http://localhost:3000`은 프론트 로컬 개발 서버에서 배포 API를 부를 때 필요합니다. 필요 없으면 빼세요.
- Neon 주소는 대시보드 **Connect**에서 **Connection pooling을 끈** 주소를 씁니다.
  `postgresql://USER:PASSWORD@HOST/DB?sslmode=require` → `DB_URL=jdbc:postgresql://HOST/DB?sslmode=require`

---

## 3. 자동 배포 (GitHub Actions)

`main`에 머지(push)되면 `.github/workflows/deploy.yml`이 돕니다.

1. EC2에 SSH로 접속 (Secret `EC2_HOST_KEY`에 미리 넣은 호스트 키와 다르면 접속하지 않고 실패)
2. `~/backend`에서 `git pull --ff-only origin main`
3. `deploy/`에서 `docker compose up -d --build` (빌드는 서버에서)
4. 남은 이미지 정리 (`docker image prune -f`)
5. 헬스체크: `https://api.wedid.site/v3/api-docs`가 200을 줄 때까지 최대 5분 대기. 실패하면 서버의 `docker compose ps`와 app·nginx 로그를 출력하고 실패 처리
- Actions 탭 → Deploy → **Run workflow**로 수동 실행도 됩니다.
- 서버의 `~/backend`에서 추적 중인 파일을 직접 고치면 `git pull --ff-only`가 실패합니다. 서버에서는 `deploy/.env`와 인증서(`deploy/certbot/`, git 제외)만 만듭니다.
- **새 `sql/` 파일은 자동으로 적용되지 않습니다.** main에 머지하기 **전에** Neon에 먼저 적용하세요 (`sh deploy/scripts/apply-sql.sh 036`). 순서가 바뀌면 앱이 스키마 검증에 실패해서 헬스체크가 실패합니다.

### 3-1. 배포 전용 SSH 키 만들기
사람이 쓰는 `.pem`과 별도로, GitHub Actions만 쓰는 키를 만듭니다. 문제가 생기면 이 키만 지우면 됩니다.

로컬 PC(Git Bash)에서:
```bash
ssh-keygen -t ed25519 -C "github-actions-deploy@wedid" -f ~/.ssh/wedid_deploy -N ""

# 공개키를 서버에 등록 (포트 포워딩 등은 막은 상태로)
(printf 'no-port-forwarding,no-agent-forwarding,no-X11-forwarding '; cat ~/.ssh/wedid_deploy.pub) \
  | ssh -i wedid.pem ubuntu@api.wedid.site 'cat >> ~/.ssh/authorized_keys'

# 접속 확인
ssh -i ~/.ssh/wedid_deploy ubuntu@api.wedid.site 'echo ok && docker compose version'
```
- 개인키(`~/.ssh/wedid_deploy`)는 3-2에서 Secret에 넣은 뒤 로컬에서 지워도 됩니다. 잃어버리면 새로 만들어 다시 등록하면 됩니다.
- 키를 폐기할 때는 서버의 `~/.ssh/authorized_keys`에서 `github-actions-deploy@wedid` 줄을 지웁니다.

### 3-2. GitHub Secrets 등록
레포 → **Settings → Secrets and variables → Actions → New repository secret** (레포 관리자 권한 필요)

| Secret | 값 |
|---|---|
| `EC2_HOST` | `api.wedid.site` (또는 탄력적 IP) |
| `EC2_USER` | `ubuntu` |
| `EC2_SSH_KEY` | `~/.ssh/wedid_deploy` **개인키 파일 전체** (`-----BEGIN OPENSSH PRIVATE KEY-----`부터 `-----END ...-----`까지, 줄바꿈 포함) |
| `EC2_HOST_KEY` | 서버 **호스트 공개키** 한 줄 (3-3에서 확인한 `ssh-ed25519 AAAA...`) |

`gh` CLI로 넣을 수도 있습니다.
```bash
gh secret set EC2_HOST --repo wedid-team/backend --body "api.wedid.site"
gh secret set EC2_USER --repo wedid-team/backend --body "ubuntu"
gh secret set EC2_SSH_KEY --repo wedid-team/backend < ~/.ssh/wedid_deploy
gh secret set EC2_HOST_KEY --repo wedid-team/backend < ec2_host_ed25519.pub     # 3-3에서 저장한 파일
```
`.env` 값(DB 비밀번호, JWT, OAuth 키)은 Secrets에 **넣지 않습니다.**

### 3-3. 서버 호스트 키 고정 (`EC2_HOST_KEY`)
자동 배포는 접속할 때마다 서버 키를 받아오지(`ssh-keyscan`) 않고, 여기서 **미리 확인한 키만** 믿습니다. 중간에서 다른 서버가 끼어들면 키가 달라서 접속을 거부합니다.

1. **서버에서 호스트 공개키를 뽑습니다.** (`.pem`으로 SSH 접속한 상태)
   ```bash
   cat /etc/ssh/ssh_host_ed25519_key.pub          # ssh-ed25519 AAAA... root@ip-172-...  ← 이 한 줄이 EC2_HOST_KEY
   ssh-keygen -lf /etc/ssh/ssh_host_ed25519_key.pub   # 지문: 256 SHA256:xxxx... (ED25519)
   ```
2. **그 지문이 진짜 이 서버 것인지 AWS 콘솔로 확인합니다.** (SSH가 아닌 경로로 확인해야 의미가 있음)
   - EC2 → 인스턴스 선택 → **작업 → 모니터링 및 문제 해결 → 시스템 로그 가져오기**
   - 로그의 `-----BEGIN SSH HOST KEY FINGERPRINTS-----` 부분에 있는 `ED25519` 줄의 `SHA256:...`이 1번 지문과 같아야 합니다.
   - 처음 `.pem`으로 접속할 때 `ED25519 key fingerprint is SHA256:...`라고 물어본 값과도 같아야 합니다.
3. 1번의 한 줄을 로컬 파일(`ec2_host_ed25519.pub`)로 저장해서 3-2처럼 `EC2_HOST_KEY`에 넣습니다. 웹 화면에서 넣을 때는 한 줄을 그대로 붙여넣으면 됩니다.
   ```bash
   ssh -i wedid.pem ubuntu@api.wedid.site 'cat /etc/ssh/ssh_host_ed25519_key.pub' > ec2_host_ed25519.pub
   ```
4. 배포가 돌면 Actions 로그의 "고정한 호스트 키 지문"이 2번 지문과 같은지 한 번 확인하세요.

- **인스턴스를 새로 만들면 호스트 키가 바뀝니다.** 그때는 1~3을 다시 해서 `EC2_HOST_KEY`를 갱신하세요. 안 하면 배포가 `Host key verification failed`로 멈춥니다.
- 호스트 공개키는 비밀은 아니지만, 바꿔치기되면 안 되는 값이라 Secret으로 관리합니다.

---

## 4. 카카오·구글 콘솔 리다이렉트 URI

로그인 콜백 경로는 `https://api.wedid.site/api/v1/auth/callback/{kakao|google}`입니다. (`application.properties`의 `{baseUrl}`이 Nginx가 넘긴 헤더로 `https://api.wedid.site`가 됩니다.)

**카카오 developers** (내 애플리케이션 → 해당 앱)
- 플랫폼 → Web → 사이트 도메인: `https://api.wedid.site`, `https://wedid.site` 추가
- 카카오 로그인 → Redirect URI: `https://api.wedid.site/api/v1/auth/callback/kakao` 추가
- 카카오 로그인 활성화 ON 확인

**Google Cloud Console** (API 및 서비스 → 사용자 인증 정보 → OAuth 2.0 클라이언트 ID)
- 승인된 리디렉션 URI: `https://api.wedid.site/api/v1/auth/callback/google` 추가
- OAuth 동의 화면이 "테스트" 상태면 등록한 테스트 사용자만 로그인됩니다.

로컬용 `http://localhost:8080/...` URI는 지우지 말고 같이 둡니다.

---

## 5. Neon에 스키마·데이터 넣기

### Neon 적용 기록

| 항목 | 내용 |
|---|---|
| 적용일 | 2026-11-26 |
| Neon | 프로젝트 리전 AWS ap-southeast-2 (시드니), 직접 연결(pooling 끔), 서버 **PostgreSQL 18.7** |
| 스키마 | `sql/` **001 ~ 035** (`apply-sql.sh`, 테이블 32개) |
| 데이터 | 가연 님 DB 전체 데이터 덤프 `wedid_full_data.sql` (아래 출처) |
| 업체-대행사 연결 | `sql/data/sdm_vendor_agencies_rebuild.sql` 실행 (덤프의 vendor_agencies가 0건이라 따로 채움, 1,548건) |
| 검증 | 로컬 테스트 DB에 같은 순서로 넣은 결과와 전체 테이블 행 수·시퀀스·내용 해시 비교 → 같음 (시드 실행 시각 컬럼 `created_at`·`effective_at`만 다름) |
| 다음에 적용할 번호 | **036부터** (`sh deploy/scripts/apply-sql.sh 036`) |

**덤프 출처**
- 파일: `wedid_full_data.sql` (78,546,620 bytes, sha256 `1d7dddf3b88f8fcc19246ea5f5d07d2abd7438ed264b188a950ddb6ff77adc82`). 레포에는 넣지 않음 (원본 데이터 포함, 78MB)
- 만든 곳: 가연 님 로컬 DB, `pg_dump 18.7` 데이터 전용(`--data-only`), 2026-11-25 전달
- 그 DB의 스키마: `sql/` **032까지** 적용된 상태. 033~035는 덤프에 들어 있는 17개 테이블을 건드리지 않아 충돌 없음
- 원본 XLSX: 2026-11-16에 그 DB로 적재한 버전 (`source_files.sha256` 기준, 우리 로컬 원본과 파일 해시는 다르고 스드메 정리 결과는 같음)
- 넣을 때 PG 18 전용 줄 3개를 뺌: `\restrict …`, `\unrestrict …`, `SET transaction_timeout = 0;`

| 테이블 | 행 수 | | 테이블 | 행 수 |
|---|---|---|---|---|
| source_files | 9 | | price_records | 3,507 (스드메 3,415 + 예식장 92) |
| source_rows | 18,469 | | hall_price_details | 92 |
| regions | 3 (SEOUL_GANGNAM, SEOUL_OTHER, GYEONGGI) | | hall_cost_items | 293 |
| agencies | 24 | | market_statistics | 70 |
| vendors | 605 (스드메 555 + 예식장 50) | | options / option_prices | 8,061 / 8,061 |
| products | 3,415 | | cancellation_policies | 879 |
| product_price_details | 3,415 | | hall_mood_tags | 0 |
| halls | 76 (모두 강남) | | vendor_agencies | 1,548 (덤프 0 → 스크립트로 채움) |

### 적재 방법 A: 전체 덤프로 채우기 (지금 Neon에 쓴 방법)
빈 DB 기준. **Docker가 있는 PC**에서 저장소 루트 기준으로 실행하고, 접속 정보는 `deploy/.env`의 `DB_*`를 씁니다.
```bash
# 1) 스키마
sh deploy/scripts/apply-sql.sh

# 2) 덤프: PG 18 전용 줄 3개를 빼고 한 트랜잭션으로 (중간에 실패하면 전부 되돌아감)
awk 'BEGIN{b=sprintf("%c",92)} index($0, b "restrict ")==1 || index($0, b "unrestrict ")==1 || $0=="SET transaction_timeout = 0;" {next} {print}' \
  wedid_full_data.sql > wedid_full_data.clean.sql
grep -c 'restrict\|transaction_timeout' wedid_full_data.clean.sql   # 0이어야 함
sh deploy/scripts/apply-sql.sh wedid_full_data.clean.sql            # BEGIN이 없는 파일은 자동으로 한 트랜잭션

# 3) 업체-대행사 연결 (덤프에 vendor_agencies가 비어 있을 때)
sh deploy/scripts/apply-sql.sh sql/data/sdm_vendor_agencies_rebuild.sql
```
- `\restrict` 줄은 `grep -v`로 빼려고 하면 셸에 따라 백슬래시가 줄어 안 빠질 수 있어서, 위처럼 문자 코드(92)로 비교합니다.
- 덤프는 시퀀스 값(`setval`)도 함께 넣습니다. 이후 앱이 INSERT해도 id가 겹치지 않습니다.

### 적재 방법 B: XLSX 원본부터 채우기
**적재 순서**: ① sql 스키마 → ② XLSX 원본 적재 → ③ 스드메 정리 적재 스크립트

예식장·지역·통계는 원본에서 정리 테이블로 옮기는 코드가 없어서 이 방법으로는 채워지지 않습니다 (5-4). 스드메만 필요할 때, 또는 원본 파일이 새로 바뀌었을 때 씁니다.

모두 **Docker가 있는 아무 PC**(로컬 PC 또는 EC2)에서 저장소 루트 기준으로 실행합니다. 접속 정보는 `deploy/.env`의 `DB_*`를 씁니다. 로컬 PC에서 하려면 로컬에도 `deploy/.env`를 만들어 DB 값만 채우면 됩니다.

### 5-1. ① sql 스키마
```bash
sh deploy/scripts/apply-sql.sh          # 빈 DB: 001부터 전부
sh deploy/scripts/apply-sql.sh 036      # 이후: 새 파일 번호부터
```
- psql 설치 없이 `postgres:16-alpine` 컨테이너의 psql을 씁니다.
- 파일마다 트랜잭션 하나로 실행하고, 에러가 나면 그 파일은 되돌린 뒤 멈춥니다.
- 이미 적용한 파일을 다시 돌리면 `already exists`로 멈춥니다. 어디까지 적용했는지는 직접 기록해야 합니다 (마이그레이션 도구 없음).
- `sql/data/` 아래 파일은 번호 순서 적용 대상이 아닙니다 (③에서 직접 지정).

### 5-2. ② XLSX 원본 적재 (XLSX 파일이 있는 PC에서)
```bash
./gradlew bootJar -x test
set -a; . deploy/.env; set +a
WEDID_IMPORT_DIRECTORY="C:/경로/xlsx폴더" \
SPRING_PROFILES_ACTIVE=import-file-metadata \
java -jar build/libs/app.jar --server.port=0
```
- 필요한 파일 9개: `wedding_venues`, `wedding_hall_prices`, `wedding_options`, `sdm_vendors`, `sdm_prices`, `sdm_penalties`, `sdm_packages`, `sdm_options`, `market_stats` (.xlsx)
- 로그에 `원본 행 저장 완료: market_stats.xlsx`가 나오면 끝난 것입니다. 서버가 계속 떠 있으니 **Ctrl+C로 종료**합니다.
  (보안 설정이 웹 서버를 전제로 해서 웹 서버를 끈 채로는 실행되지 않고, 그래서 `--server.port=0`으로 빈 포트에 띄웁니다.)
- 같은 파일을 다시 돌려도 중복 적재되지 않습니다 (`ON CONFLICT DO NOTHING`).
- `deploy/.env`의 비밀번호에 `$`, `!`, 공백이 있으면 `. deploy/.env`가 깨질 수 있습니다. 그때는 `DB_URL`/`DB_USERNAME`/`DB_PASSWORD`만 따옴표로 감싸 직접 export 하세요.
- 이 단계는 `source_files`/`source_rows`에 **원본 행만** 넣습니다.

### 5-3. ③ 스드메 정리 적재
```bash
sh deploy/scripts/apply-sql.sh sql/data/sdm_normalization_rebuild.sql
```
- 스드메 원본(`sdm_*`)을 `agencies`·`vendors`·`products`·`price_records`·`product_price_details`·`vendor_agencies`로 옮깁니다. `/api/v1/sdm/combinations`가 이 데이터를 씁니다.
- 전체가 트랜잭션 하나이고, 여러 번 실행해도 결과가 같습니다.
- 사전 점검에 걸리면 아무것도 넣지 않고 이유를 보여주며 멈춥니다.
  - 원본이 없을 때 (②를 먼저 하세요)
  - 수집일·자료 게시일이 `YYYY-MM-DD`가 아닐 때
  - `출처 대행사` 한 칸에 쉼표로 여러 대행사가 있을 때
- 끝나면 테이블별 행 수가 나옵니다. 로컬 원본 기준(11/25): agencies 24, vendors_sdm 555, products 3,409 + 패키지 6, vendor_agencies 1,548.

### 5-4. 예식장·지역·통계 데이터 (방법 B로는 안 채워짐)
- 예식장(`halls`·`hall_price_details`·`hall_cost_items`·예식장 업체), 지역(`regions`), 통계(`market_statistics`)를 원본에서 옮기는 코드나 SQL은 아직 레포에 없습니다. 지금은 **방법 A(전체 덤프)**로만 채웁니다.
- 방법 B만 하면 `/api/v1/halls`, `/api/v1/estimates/hall`, 추천 API는 데이터가 비어 있고, `regions`가 비어서 `/api/v1/regions`도 빈 목록이며 계획 저장이 "올바른 지역을 선택해주세요."로 막힙니다.
- 현재 데이터 범위: 지역 3개(강남·강남외·경기), 예식장은 강남만(업체 50·홀 76). 강남외·경기는 홀 목록이 비고 견적은 시장 통계(`MARKET_STATISTICS`)로 나옵니다.

---

## 문제 해결

| 증상 | 확인 |
|---|---|
| 자동 배포가 SSH 단계에서 `Permission denied` | `EC2_SSH_KEY`가 개인키 전체인지, 서버 `authorized_keys`에 공개키가 있는지, `EC2_USER` |
| 자동 배포가 `Host key verification failed` | 서버 호스트 키가 `EC2_HOST_KEY`와 다름. 인스턴스를 새로 만들었으면 3-3으로 갱신. 아니라면 지문을 AWS 시스템 로그와 대조 |
| IP 주소로 접속하면 응답 없음 | 정상. `api.wedid.site`가 아닌 Host·SNI는 Nginx가 거부함 (`curl https://api.wedid.site/...`로 확인) |
| 자동 배포가 `git pull`에서 실패 | 서버에서 추적 파일을 고쳤는지 `git status` |
| 헬스체크 실패 | 실패 시 출력된 app 로그. 새 sql 미적용, 환경변수 누락이 흔함 |
| nginx가 계속 재시작 | `docker compose logs nginx`. 인증서 파일이 없으면 `init-cert.sh`를 먼저 실행 |
| app이 바로 종료 | `docker compose logs app`. 환경변수 누락이나 DB 스키마 미적용(`ddl-auto=validate`) |
| 빌드 중 멈춤·Killed | 스왑 설정 확인 (`free -h`) |
| Swagger "Try it out"이 http로 요청 | Nginx 경유인지 확인 (`X-Forwarded-Proto`) |
| 첫 요청이 느림 | Neon은 5분간 요청이 없으면 잠들고, 깨어나는 데 1초 안팎이 걸림 |
| SSH 접속이 갑자기 안 됨 | fail2ban 차단일 수 있음. 1-2의 unbanip |
