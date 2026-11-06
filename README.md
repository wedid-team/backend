# WEDID Backend

예산 기반 웨딩 예상 비용 계산 · 예식장 추천 API (FastAPI + PostgreSQL)

> 현재 목(mock) 응답 단계예요. 프론트는 Swagger 보고 바로 붙여볼 수 있어요.

## 실행

```bash
pip install -r requirements.txt
cp .env.example .env
uvicorn app.main:app --reload
```

👉 Swagger: http://localhost:8000/docs

## 브랜치

- `main`: 배포용, 직접 push 금지
- `develop`: 개발 통합, **모든 PR은 여기로**
- 작업 브랜치: `feat/기능명`, `fix/버그명`

## 커밋 · PR

- 커밋/PR 제목: `feat: ...`, `fix: ...`, `refactor: ...`, `docs: ...`, `chore: ...`
- 리뷰어 1명 Approve 후 merge
