#!/bin/sh
# 최초 1회: Let's Encrypt 인증서 발급
# Nginx 설정이 인증서 파일을 필요로 해서, Nginx를 띄우기 전에 certbot이 직접 80 포트로 발급받는다.
# 실행: deploy/ 폴더에서  sh scripts/init-cert.sh
# 전제: DOMAIN의 A 레코드가 이 EC2의 탄력적 IP를 가리키고, 보안 그룹에서 80 포트가 열려 있어야 함
set -eu

cd "$(dirname "$0")/.."

if [ ! -f .env ]; then
    echo "deploy/.env가 없습니다. cp .env.example .env 후 값을 채워주세요." >&2
    exit 1
fi

DOMAIN=$(grep -E '^DOMAIN=' .env | cut -d= -f2-)
CERT_EMAIL=$(grep -E '^CERT_EMAIL=' .env | cut -d= -f2-)

if [ -z "$DOMAIN" ] || [ -z "$CERT_EMAIL" ]; then
    echo "deploy/.env의 DOMAIN, CERT_EMAIL을 채워주세요." >&2
    exit 1
fi

if [ -d "certbot/conf/live/$DOMAIN" ]; then
    echo "이미 $DOMAIN 인증서가 있습니다. 갱신은 certbot 컨테이너가 자동으로 합니다."
    exit 0
fi

mkdir -p certbot/conf certbot/www

# Nginx가 80 포트를 쓰고 있으면 발급이 실패하므로 내려둔다
docker compose stop nginx 2>/dev/null || true

docker compose run --rm -p 80:80 --entrypoint certbot certbot \
    certonly --standalone \
    -d "$DOMAIN" \
    --email "$CERT_EMAIL" \
    --agree-tos --no-eff-email

echo "인증서 발급 완료: certbot/conf/live/$DOMAIN"
echo "이제 docker compose up -d --build 로 전체를 띄우세요."
