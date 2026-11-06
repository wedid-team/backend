from fastapi import Depends, HTTPException, status
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer

bearer = HTTPBearer(auto_error=False, description="소셜 로그인으로 받은 accessToken")


def get_current_user_id(
    cred: HTTPAuthorizationCredentials | None = Depends(bearer),
) -> int:
    # TODO: JWT 검증으로 교체. 지금은 아무 토큰이나 넣으면 통과 (목 단계)
    if cred is None:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="UNAUTHORIZED")
    return 1
