#!/bin/sh
# Neon(배포 DB)에 sql/ 마이그레이션을 번호 순서대로 적용한다.
# psql 설치 없이 postgres:16 이미지의 psql을 쓴다 (Docker만 있으면 로컬 PC·EC2 어디서든 실행 가능).
#
# 사용법 (저장소 루트에서):
#   sh deploy/scripts/apply-sql.sh            # 001부터 전부 (빈 DB 최초 1회)
#   sh deploy/scripts/apply-sql.sh 036        # 036 이상만 (새 sql 파일이 추가됐을 때)
#   sh deploy/scripts/apply-sql.sh sql/data/sdm_normalization_rebuild.sql   # 파일 하나만 (데이터 정리 스크립트)
#   (sql/data/ 아래 파일은 번호 순서 적용 대상이 아니라 직접 지정할 때만 실행된다)
#
# 접속 정보는 deploy/.env의 DB_URL, DB_USERNAME, DB_PASSWORD를 그대로 쓴다.
# 각 파일은 하나의 트랜잭션으로 실행되고, 실패하면 그 파일은 되돌리고 즉시 멈춘다.
set -eu

ROOT=$(cd "$(dirname "$0")/../.." && pwd)
ENV_FILE="${ENV_FILE:-$ROOT/deploy/.env}"
FROM="${1:-000}"

if [ ! -f "$ENV_FILE" ]; then
    echo "deploy/.env가 없습니다." >&2
    exit 1
fi

read_env() { grep -E "^$1=" "$ENV_FILE" | cut -d= -f2-; }

DB_URL=$(read_env DB_URL)
DB_USERNAME=$(read_env DB_USERNAME)
DB_PASSWORD=$(read_env DB_PASSWORD)

# jdbc:postgresql://HOST[:PORT]/DB?옵션  →  HOST, PORT, DB
rest=${DB_URL#jdbc:postgresql://}
hostport=${rest%%/*}
dbname=${rest#*/}
dbname=${dbname%%\?*}
host=${hostport%%:*}
port=5432
case "$hostport" in *:*) port=${hostport##*:} ;; esac

# sslmode는 DB_URL에 적힌 값을 따르고, 없으면 require (Neon은 SSL 필수)
sslmode=require
case "$rest" in *sslmode=*) sslmode=${rest#*sslmode=}; sslmode=${sslmode%%&*} ;; esac

CONN="host=$host port=$port dbname=$dbname user=$DB_USERNAME sslmode=$sslmode"

case "$FROM" in
    *.sql)
        # 파일 하나 실행. 파일 안에 BEGIN;이 있으면(sql/data 스크립트) 그 트랜잭션을 그대로 쓰고,
        # 없으면(pg_dump 덤프 등) --single-transaction으로 감싸 중간에 실패해도 전부 되돌린다.
        FILE="$FROM"
        [ -f "$FILE" ] || FILE="$ROOT/$FROM"
        if [ ! -f "$FILE" ]; then
            echo "파일이 없습니다: $FROM" >&2
            exit 1
        fi
        TX=""
        if ! grep -qE '^BEGIN;' "$FILE"; then
            TX="--single-transaction"
        fi
        echo "대상 DB: $host / $dbname (파일 하나: $FROM${TX:+, 한 트랜잭션으로})"
        docker run --rm -i -e PGPASSWORD="$DB_PASSWORD" postgres:16-alpine \
            psql "$CONN" -v ON_ERROR_STOP=1 $TX < "$FILE"
        exit 0
        ;;
esac

echo "대상 DB: $host / $dbname (파일 번호 $FROM 이상)"

for f in "$ROOT"/sql/*.sql; do
    name=$(basename "$f")
    num=${name%%_*}
    if [ "$num" \< "$FROM" ]; then
        continue
    fi
    echo "적용: $name"
    docker run --rm -i -e PGPASSWORD="$DB_PASSWORD" postgres:16-alpine \
        psql "$CONN" -q -v ON_ERROR_STOP=1 --single-transaction < "$f"
done

echo "완료. public 스키마 테이블 수:"
docker run --rm -e PGPASSWORD="$DB_PASSWORD" postgres:16-alpine \
    psql "$CONN" -tAc "select count(*) from information_schema.tables where table_schema='public'"
