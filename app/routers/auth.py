from fastapi import APIRouter, Depends, status

from app.deps import get_current_user_id
from app.schemas.auth import OAuthProvider, RefreshRequest, SocialLoginRequest, TokenResponse
from app.schemas.common import ErrorResponse

router = APIRouter(prefix="/api/v1/auth", tags=["사용자 정보 및 계획 저장"])


@router.post(
    "/login/{provider}",
    response_model=TokenResponse,
    summary="소셜 로그인 (카카오/구글)",
    description="프론트가 소셜 인가 코드를 받아 전달 → 서버가 토큰 교환·회원 조회/가입 후 JWT 발급.",
    responses={400: {"model": ErrorResponse, "description": "인가 코드 오류"}},
)
def social_login(provider: OAuthProvider, body: SocialLoginRequest):
    return TokenResponse(access_token="mock-access-token", expires_in=1800, is_new_user=True)


@router.post("/refresh", response_model=TokenResponse, summary="인증 토큰 갱신",
             responses={401: {"model": ErrorResponse}})
def refresh(body: RefreshRequest | None = None):
    return TokenResponse(access_token="mock-access-token-2", expires_in=1800, is_new_user=False)


@router.post("/logout", status_code=status.HTTP_204_NO_CONTENT, summary="로그아웃")
def logout(_: int = Depends(get_current_user_id)):
    return None
