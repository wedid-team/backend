from datetime import date

from pydantic import Field

from app.schemas.common import CamelModel, PriceRange, SdmTier


class Region(CamelModel):
    id: int
    name: str = Field(examples=["강남구"])
    parent_name: str | None = Field(default=None, examples=["서울"])


class GuestRange(CamelModel):
    id: int
    label: str = Field(examples=["150~200명"])
    min: int
    max: int | None = Field(default=None, description="상한 없음이면 null")


class BudgetEstimateRequest(CamelModel):
    region_id: int
    guest_range_id: int
    wedding_date: date | None = Field(default=None, description="예식 시기 (미정이면 null)")
    desired_budget: int | None = Field(default=None, description="희망 총예산(원)")
    sdm_tier: SdmTier | None = Field(
        default=None,
        description="스드메 예산대. 첫 계산 땐 null(서버 기본값), 사용자가 바꾸면 값을 넣어 재계산",
    )


class SdmBreakdown(CamelModel):
    tier: SdmTier
    studio: PriceRange
    dress: PriceRange
    makeup: PriceRange
    total: PriceRange


class BudgetEstimateResponse(CamelModel):
    total: PriceRange
    hall: PriceRange
    sdm: SdmBreakdown
    within_budget: bool | None = Field(
        default=None, description="희망 예산 대비 적합 여부 (예산 미입력 시 null)"
    )
    basis_note: str = Field(examples=["강남구 150~200명 기준, 참가격 통계 + 업체 공개가"])
