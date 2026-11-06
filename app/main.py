from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.routers import auth, budgets, plans, vendors

app = FastAPI(
    title="WEDID API",
    version="0.1.0",
    description="WEDID 백엔드 API (목 응답 단계 — 실제 데이터·추천 로직 연동 전)",
    openapi_tags=[
        {"name": "예상 비용 계산"},
        {"name": "예식장 후보 조회"},
        {"name": "준비 로드맵"},
        {"name": "사용자 정보 및 계획 저장"},
    ],
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["http://localhost:3000"],  # Next dev 서버
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(budgets.router)
app.include_router(vendors.router)
app.include_router(plans.router)
app.include_router(auth.router)
