from datetime import date, datetime

from fastapi import APIRouter, Depends, status

from app.deps import get_current_user_id
from app.schemas.common import BudgetCategory, ErrorResponse
from app.schemas.plan import (
    BudgetItem,
    BudgetItemUpsert,
    Plan,
    PlanCreate,
    PlanUpdate,
    Roadmap,
    RoadmapStage,
    RoadmapTask,
    StageCompleteUpdate,
    TaskStatus,
    TaskStatusUpdate,
)

router = APIRouter(
    prefix="/api/v1/plans",
    dependencies=[Depends(get_current_user_id)],
    responses={401: {"model": ErrorResponse, "description": "로그인 필요"}},
)

_NOW = datetime(2026, 11, 4, 12, 0)


def _mock_plan() -> Plan:
    return Plan(
        id=1,
        region_id=1,
        region_name="강남구",
        guest_range_id=3,
        guest_range_label="150~200명",
        wedding_date=date(2027, 10, 9),
        desired_budget=30_000_000,
        sdm_tier=None,
        moods=["화사한"],
        created_at=_NOW,
        updated_at=_NOW,
    )


def _mock_roadmap() -> Roadmap:
    return Roadmap(
        plan_id=1,
        progress_percent=25,
        stages=[
            RoadmapStage(
                id=1,
                order=1,
                title="예산·날짜 정하기",
                completed=True,
                tasks=[RoadmapTask(id=1, title="총예산 정하기", status=TaskStatus.DONE)],
            ),
            RoadmapStage(
                id=2,
                order=2,
                title="예식장 계약",
                completed=False,
                tasks=[RoadmapTask(id=2, title="웨딩홀 투어 3곳 이상", status=TaskStatus.TODO)],
            ),
        ],
    )


# ---- 사용자 정보 및 계획 저장 ----
@router.post("", response_model=Plan, status_code=status.HTTP_201_CREATED,
             summary="사용자 결혼 계획 저장", tags=["사용자 정보 및 계획 저장"])
def create_plan(body: PlanCreate):
    return _mock_plan()


@router.get("/me", response_model=Plan, summary="저장된 계획 조회",
            tags=["사용자 정보 및 계획 저장"], responses={404: {"model": ErrorResponse}})
def get_my_plan():
    return _mock_plan()


@router.patch("/me", response_model=Plan, summary="지역·하객·예식일·예산 수정",
              tags=["사용자 정보 및 계획 저장"])
def update_my_plan(body: PlanUpdate):
    plan = _mock_plan()
    return plan.model_copy(update=body.model_dump(exclude_unset=True))


# ---- 준비 로드맵 ----
@router.get("/me/roadmap", response_model=Roadmap, summary="내 준비 로드맵 전체 조회",
            tags=["준비 로드맵"])
def get_roadmap():
    return _mock_roadmap()


@router.patch("/me/roadmap/tasks/{task_id}", response_model=RoadmapTask,
              summary="체크리스트 완료 상태 수정", tags=["준비 로드맵"],
              responses={404: {"model": ErrorResponse}})
def update_task(task_id: int, body: TaskStatusUpdate):
    return RoadmapTask(id=task_id, title="웨딩홀 투어 3곳 이상", status=body.status)


@router.patch("/me/roadmap/stages/{stage_id}", response_model=RoadmapStage,
              summary="단계 완료 처리", tags=["준비 로드맵"],
              responses={404: {"model": ErrorResponse}})
def update_stage(stage_id: int, body: StageCompleteUpdate):
    stage = _mock_roadmap().stages[1]
    return stage.model_copy(update={"id": stage_id, "completed": body.completed})


@router.put("/me/budget-items/{category}", response_model=BudgetItem,
            summary="실제 계약 금액 입력·수정", tags=["준비 로드맵"])
def upsert_budget_item(category: BudgetCategory, body: BudgetItemUpsert):
    return BudgetItem(category=category, updated_at=_NOW, **body.model_dump())
