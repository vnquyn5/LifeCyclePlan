import { useMutation } from "@tanstack/react-query";
import { login } from "../api/authApi";
import { LoginRequest } from "../types/auth.types";
import { clearRefreshTokenIfMatches, saveRefreshToken } from "@/services/storage/tokenStorage";
import {
  beginAuthSessionOperation,
  isCurrentAuthSessionOperation
} from "../session/authSession";
import { useAuthSessionStore } from "../store/authSessionStore";
import { clearPrivateQueryCache } from "@/services/api/queryClient";
import { ApiRequestError } from "@/types/api.types";

export function useLoginMutation() {
  return useMutation<void, Error, LoginRequest>({
    mutationFn: async (request) => {
      const generation = beginAuthSessionOperation();
      const response = await login(request);
      if (!isCurrentAuthSessionOperation(generation)) throw staleSessionOperation();
      if (!response.user?.userId || !response.user.email || !response.user.onboardingStatus) {
        throw new ApiRequestError({
          message: "Máy chủ trả về thông tin tài khoản chưa hợp lệ. Vui lòng thử lại sau.",
          code: "AUTH_RESPONSE_INVALID"
        });
      }
      if (
        !response.tokens?.accessToken?.trim() ||
        !response.tokens.refreshToken?.trim() ||
        response.tokens.tokenType !== "Bearer" ||
        !Number.isFinite(response.tokens.expiresIn) ||
        response.tokens.expiresIn <= 0
      ) {
        throw new ApiRequestError({
          message: "Máy chủ chưa cấp phiên đăng nhập. Vui lòng thử lại sau.",
          code: "AUTH_SESSION_UNAVAILABLE"
        });
      }
      try {
        const existingUser = useAuthSessionStore.getState().user;
        if (existingUser && existingUser.userId !== response.user.userId) {
          await clearPrivateQueryCache();
        }
        if (!isCurrentAuthSessionOperation(generation)) throw staleSessionOperation();
        await saveRefreshToken(response.tokens.refreshToken);
      } catch {
        if (!isCurrentAuthSessionOperation(generation)) throw staleSessionOperation();
        useAuthSessionStore.getState().setUnauthenticated();
        throw new ApiRequestError({
          message: "Không thể lưu phiên an toàn trên thiết bị. Vui lòng thử lại.",
          code: "SECURE_STORE_ERROR"
        });
      }
      if (!isCurrentAuthSessionOperation(generation)) {
        await clearRefreshTokenIfMatches(response.tokens.refreshToken);
        throw staleSessionOperation();
      }
      useAuthSessionStore.getState().setAuthenticated(response.tokens.accessToken, response.user);
    },
    retry: false,
    gcTime: 0
  });
}

function staleSessionOperation(): ApiRequestError {
  return new ApiRequestError({
    message: "Phiên đã thay đổi. Vui lòng thử lại.",
    code: "SESSION_OPERATION_STALE"
  });
}
