from datetime import date
from enum import Enum

from fastapi import APIRouter, HTTPException, Query

from app.schemas.common import ErrorResponse, PriceRange, PriceSource
from app.schemas.vendor import (
    BudgetFitStatus,
    CostItem,
    HallDetail,
    HallSummary,
    MoodMatch,
    Recommendation,
    ScoreBreakdown,
    VendorListResponse,
)

router = APIRouter(prefix="/api/v1/vendors", tags=["예식장 후보 조회"])


class VendorSort(str, Enum):
    RECOMMENDED = "recommended"
    PRICE_ASC = "price_asc"
    PRICE_DESC = "price_desc"


def _mock_summary() -> HallSummary:
    return HallSummary(
        id=1,
        name="샘플웨딩홀 강남",
        region_name="강남구",
        min_guests=150,
        max_guests=200,
        estimated_price=PriceRange(min=15_000_000, max=19_000_000),
        price_source=PriceSource.VENDOR_OFFICIAL,
        price_as_of=date(2026, 10, 15),
        tags=["화사한", "클래식"],
        recommendation=Recommendation(
            rank=1,
            score=ScoreBreakdown(
                budget_fit=40, mood_match=30, price_competitiveness=15, scale_fit=10, total=95
            ),
            budget_fit_status=BudgetFitStatus.GOOD,
            mood_match=MoodMatch.MATCH,
            capacity_rate=85.0,
            reasons=["예식장 배분 예산 안에서 이용 가능", "선택한 '화사한' 분위기와 일치"],
        ),
    )


@router.get(
    "",
    response_model=VendorListResponse,
    summary="예식장 후보 목록 및 필터링 (추천 상위 5개 포함)",
    description=(
        "필수 필터: 운영 중 · 지역 · 최대 수용인원 ≥ 하객 수 · 최소 보증인원 ≤ 하객 수.\n\n"
        "`sort=recommended`(기본)이면 규칙 기반 추천 점수순 상위 5개와 `recommendation` 필드를 반환. "
        "가격 정보가 빠진 업체는 추천 순위에서 제외."
    ),
)
def list_vendors(
    region_id: int = Query(alias="regionId"),
    guest_count: int = Query(alias="guestCount", ge=1, description="예상 하객 수"),
    wedding_date: date | None = Query(default=None, alias="weddingDate"),
    budget: int | None = Query(default=None, description="총예산(원). 없으면 예산 적합도 제외"),
    moods: list[str] = Query(default=[], description="선호 분위기 (반복 파라미터: ?moods=화사한&moods=클래식)"),
    sort: VendorSort = VendorSort.RECOMMENDED,
    page: int = Query(default=1, ge=1),
    size: int = Query(default=5, ge=1, le=50),
):
    item = _mock_summary()
    return VendorListResponse(
        items=[item],
        total=1,
        page=page,
        size=size,
        hall_budget=None if budget is None else int(budget * 0.7),
    )


@router.get(
    "/{vendor_id}",
    response_model=HallDetail,
    summary="예식장 상세 정보 조회",
    responses={404: {"model": ErrorResponse}},
)
def get_vendor(
    vendor_id: int,
    guest_count: int | None = Query(default=None, alias="guestCount", description="넣으면 그 인원 기준 예상 비용 계산"),
    wedding_date: date | None = Query(default=None, alias="weddingDate"),
):
    if vendor_id != 1:
        raise HTTPException(status_code=404, detail="VENDOR_NOT_FOUND")
    base = _mock_summary().model_dump()
    base["recommendation"] = None
    return HallDetail(
        **base,
        address="서울 강남구 (샘플)",
        hall_types=["단독홀"],
        cost_breakdown=[
            CostItem(name="대관료", amount=PriceRange(min=2_000_000, max=3_000_000)),
            CostItem(
                name="식대",
                amount=PriceRange(min=11_000_000, max=13_000_000),
                note="1인 5.5만~6.5만 × 보증인원 200명",
            ),
            CostItem(name="필수 장식비", amount=PriceRange(min=1_500_000, max=2_500_000)),
            CostItem(name="기타 필수 비용", amount=PriceRange(min=500_000, max=500_000)),
        ],
        remarks="주말 저녁 타임 할증 있음 (샘플)",
    )
