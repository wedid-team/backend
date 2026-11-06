from enum import Enum

from pydantic import BaseModel, ConfigDict
from pydantic.alias_generators import to_camel


class CamelModel(BaseModel):
    """JSON은 camelCase(프론트 Next 기준), 파이썬 코드는 snake_case."""

    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True)


class PriceRange(CamelModel):
    min: int  # 원 단위
    max: int


class PriceSource(str, Enum):
    VENDOR_OFFICIAL = "VENDOR_OFFICIAL"  # 업체 공식
    USER_CONTRACT = "USER_CONTRACT"  # 사용자 인증 계약
    EXTERNAL_REFERENCE = "EXTERNAL_REFERENCE"  # 외부 참고(참가격 등)


class BudgetCategory(str, Enum):
    HALL = "HALL"
    STUDIO = "STUDIO"
    DRESS = "DRESS"
    MAKEUP = "MAKEUP"


class SdmTier(str, Enum):
    LOW = "LOW"
    MID = "MID"
    HIGH = "HIGH"


class ErrorResponse(CamelModel):
    code: str
    message: str
