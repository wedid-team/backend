from fastapi import APIRouter

from app.schemas.budget import (
    BudgetEstimateRequest,
    BudgetEstimateResponse,
    GuestRange,
    Region,
    SdmBreakdown,
)
from app.schemas.common import PriceRange, SdmTier

router = APIRouter(prefix="/api/v1", tags=["예상 비용 계산"])

# TODO: DB 연동 전 목 데이터
MOCK_REGIONS = [
    Region(id=1, name="강남구", parent_name="서울"),
    Region(id=2, name="서초구", parent_name="서울"),
]
MOCK_GUEST_RANGES = [
    GuestRange(id=1, label="100명 미만", min=0, max=99),
    GuestRange(id=2, label="100~150명", min=100, max=150),
    GuestRange(id=3, label="150~200명", min=151, max=200),
    GuestRange(id=4, label="200명 이상", min=201, max=None),
]


@router.get("/regions", response_model=list[Region], summary="선택 가능한 결혼 지역 조회")
def list_regions():
    return MOCK_REGIONS


@router.get("/guest-ranges", response_model=list[GuestRange], summary="하객 수 선택 구간 조회")
def list_guest_ranges():
    return MOCK_GUEST_RANGES


@router.post(
    "/budgets/estimate",
    response_model=BudgetEstimateResponse,
    summary="조건별 예상 비용 범위 계산 (스드메 예산대 변경 시 재호출)",
    description=(
        "첫 계산은 `sdmTier` 없이 호출 → 서버 기본 예산대로 계산.\n\n"
        "사용자가 스드메 예산대를 바꾸면 같은 조건 + `sdmTier`를 넣어 다시 호출."
    ),
)
def estimate_budget(body: BudgetEstimateRequest):
    tier = body.sdm_tier or SdmTier.MID
    hall = PriceRange(min=14_000_000, max=21_000_000)
    sdm_total = PriceRange(min=3_500_000, max=5_500_000)
    total = PriceRange(min=hall.min + sdm_total.min, max=hall.max + sdm_total.max)
    return BudgetEstimateResponse(
        total=total,
        hall=hall,
        sdm=SdmBreakdown(
            tier=tier,
            studio=PriceRange(min=1_300_000, max=2_000_000),
            dress=PriceRange(min=1_500_000, max=2_500_000),
            makeup=PriceRange(min=700_000, max=1_000_000),
            total=sdm_total,
        ),
        within_budget=None if body.desired_budget is None else total.max <= body.desired_budget,
        basis_note="목 데이터 (참가격 통계 + 업체 공개가 연동 예정)",
    )
