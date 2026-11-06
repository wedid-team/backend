from enum import Enum

from pydantic import Field

from app.schemas.common import CamelModel


class OAuthProvider(str, Enum):
    KAKAO = "kakao"
    GOOGLE = "google"


class SocialLoginRequest(CamelModel):
    authorization_code: str = Field(description="프론트가 소셜 로그인 콜백에서 받은 인가 코드")
    redirect_uri: str


class TokenResponse(CamelModel):
    access_token: str
    token_type: str = "bearer"
    expires_in: int = Field(description="access 토큰 만료(초)")
    is_new_user: bool = Field(description="첫 로그인이면 true → 프론트가 계획 입력 화면으로 이동")


class RefreshRequest(CamelModel):
    refresh_token: str | None = Field(
        default=None, description="쿠키 방식으로 가면 body 없이 쿠키로 받음 (미정)"
    )
