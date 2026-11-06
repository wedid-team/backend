from datetime import date, datetime
from enum import Enum

from pydantic import Field

from app.schemas.common import BudgetCategory, CamelModel, SdmTier


class PlanCreate(CamelModel):
    region_id: int
    guest_range_id: int
    wedding_date: date | None = None
    desired_budget: int | None = None
    sdm_tier: SdmTier | None = None
    moods: list[str] = Field(default_factory=list, description="희망 예식장 분위기 (미선택이면 [])")


class PlanUpdate(CamelModel):
    """보낸 필드만 수정 (PATCH)."""

    region_id: int | None = None
    guest_range_id: int | None = None
    wedding_date: date | None = None
    desired_budget: int | None = None
    sdm_tier: SdmTier | None = None
    moods: list[str] | None = None


class Plan(CamelModel):
    id: int
    region_id: int
    region_name: str
    guest_range_id: int
    guest_range_label: str
    wedding_date: date | None
    desired_budget: int | None
    sdm_tier: SdmTier | None
    moods: list[str]
    created_at: datetime
    updated_at: datetime


class TaskStatus(str, Enum):
    TODO = "TODO"
    DONE = "DONE"


class RoadmapTask(CamelModel):
    id: int
    title: str = Field(examples=["웨딩홀 투어 3곳 이상"])
    status: TaskStatus
    due_date: date | None = None


class RoadmapStage(CamelModel):
    id: int
    order: int
    title: str = Field(examples=["D-300 예식장 계약"])
    completed: bool
    tasks: list[RoadmapTask]


class Roadmap(CamelModel):
    plan_id: int
    progress_percent: int = Field(ge=0, le=100)
    stages: list[RoadmapStage]


class TaskStatusUpdate(CamelModel):
    status: TaskStatus


class StageCompleteUpdate(CamelModel):
    completed: bool


class BudgetItemUpsert(CamelModel):
    contract_amount: int = Field(description="실제 계약 금액(원)")
    vendor_id: int | None = Field(default=None, description="계약한 업체 (선택)")
    memo: str | None = None


class BudgetItem(CamelModel):
    category: BudgetCategory
    contract_amount: int
    vendor_id: int | None
    memo: str | None
    updated_at: datetime
