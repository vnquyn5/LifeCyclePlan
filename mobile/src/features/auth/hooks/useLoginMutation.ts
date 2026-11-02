import { useMutation } from "@tanstack/react-query";
import { login } from "../api/authApi";
import { LoginRequest } from "../types/auth.types";
import { saveTokens } from "@/services/storage/tokenStorage";
import { ApiRequestError } from "@/types/api.types";

export function useLoginMutation() {
  return useMutation<void, Error, LoginRequest>({
    mutationFn: async (request) => {
      const response = await login(request);
      if (!response.user?.userId || !response.user.email || !response.user.onboardingStatus) {
        throw new ApiRequestError({
          message: "Máy chủ trả về thông tin tài khoản chưa hợp lệ. Vui lòng thử lại sau.",
          code: "AUTH_RESPONSE_INVALID"
        });
      }
      if (!response.tokens?.accessToken || !response.tokens.refreshToken) {
        throw new ApiRequestError({
          message: "Máy chủ chưa cấp phiên đăng nhập. Vui lòng thử lại sau.",
          code: "AUTH_SESSION_UNAVAILABLE"
        });
      }
      await saveTokens(response.tokens.accessToken, response.tokens.refreshToken);
    },
    retry: false,
    gcTime: 0
  });
}
