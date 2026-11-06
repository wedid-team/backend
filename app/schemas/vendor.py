from datetime import date
from enum import Enum

from pydantic import Field

from app.schemas.common import CamelModel, PriceRange, PriceSource


class BudgetFitStatus(str, Enum):
    GOOD = "GOOD"  # 예상 최대 비용 ≤ 배분 예산
    CAUTION = "CAUTION"  # 예상 최소 ≤ 배분 예산 < 예상 최대
    OVER = "OVER"  # 배분 예산 < 예상 최소


class MoodMatch(str, Enum):
    MATCH = "MATCH"
    UNKNOWN = "UNKNOWN"  # 원본 태그 없음 (불일치와 구분)
    MISMATCH = "MISMATCH"


class ScoreBreakdown(CamelModel):
    """각 항목 원점수. 입력 누락 항목은 null, total은 100점 기준으로 환산된 값."""

    budget_fit: float | None = Field(default=None, ge=0, le=40, description="예산 적합도 (최대 40)")
    mood_match: float | None = Field(default=None, ge=0, le=30, description="분위기 일치도 (최대 30)")
    price_competitiveness: float = Field(ge=0, le=20, description="가격 경쟁력 (최대 20)")
    scale_fit: float = Field(ge=0, le=10, description="규모 적합도 (최대 10)")
    total: float = Field(ge=0, le=100)


class Recommendation(CamelModel):
    rank: int = Field(ge=1, le=5)
    score: ScoreBreakdown
    budget_fit_status: BudgetFitStatus | None = Field(
        default=None, description="예산 미입력 시 null"
    )
    mood_match: MoodMatch | None = Field(default=None, description="분위기 미선택 시 null")
    capacity_rate: float = Field(description="예상 하객 수 / 최대 수용인원 × 100")
    reasons: list[str] = Field(
        examples=[["예식장 배분 예산 안에서 이용 가능", "선택한 '화사한' 분위기와 일치"]]
    )


class HallSummary(CamelModel):
    id: int
    name: str = Field(examples=["더채플앳청담"])
    region_name: str
    min_guests: int | None = Field(default=None, description="최소 보증인원")
    max_guests: int | None = Field(default=None, description="최대 수용인원")
    estimated_price: PriceRange = Field(
        description="대관료 + 식대 + 필수 장식비 + 기타 필수 비용 (하객 수·예식 시기 기준)"
    )
    price_source: PriceSource
    price_as_of: date | None = Field(default=None, description="가격 기준일")
    tags: list[str] = Field(default_factory=list, description="분위기 태그")
    thumbnail_url: str | None = None
    recommendation: Recommendation | None = Field(
        default=None, description="sort=recommended 일 때만 채워짐"
    )


class CostItem(CamelModel):
    name: str = Field(examples=["식대"])
    amount: PriceRange
    note: str | None = Field(default=None, examples=["1인 6.5만~7.5만 × 보증인원 200명"])


class HallPackage(CamelModel):
    name: str = Field(examples=["주말 2부 패키지"])
    meal_price_per_person: int | None = None
    rental_fee: int | None = None
    price_source: PriceSource


class HallDetail(HallSummary):
    address: str | None = None
    phone: str | None = None
    website_url: str | None = None
    hall_types: list[str] = Field(default_factory=list, examples=[["채플", "단독홀"]])
    cost_breakdown: list[CostItem] = Field(
        default_factory=list, description="예상 비용 항목별 내역"
    )
    remarks: str | None = Field(default=None, description="비고 (빠뜨리기 쉬운 추가 비용 확인용)")
    packages: list[HallPackage] = Field(default_factory=list)
    image_urls: list[str] = Field(default_factory=list)


class VendorListResponse(CamelModel):
    items: list[HallSummary]
    total: int
    page: int
    size: int
    hall_budget: int | None = Field(
        default=None, description="예산 적합도 계산에 쓴 예식장 배분 예산 (총예산 × 배분율)"
    )
    notice: str | None = Field(
        default=None,
        description="예산 안에 드는 업체가 없을 때 등 안내 문구",
        examples=["예산 안에 드는 예식장이 없어요. 예산을 조정하거나 지역을 넓혀보세요."],
    )
