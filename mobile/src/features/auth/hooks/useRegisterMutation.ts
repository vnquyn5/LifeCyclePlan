import { useMutation } from "@tanstack/react-query";
import { register } from "../api/authApi";
import { RegisterRequest, RegisterResponse } from "../types/auth.types";
import { ApiRequestError } from "@/types/api.types";

export function useRegisterMutation() {
  return useMutation<void, Error, RegisterRequest>({
    mutationFn: async (request) => {
      const response: RegisterResponse = await register(request);
      if (!response.userId || !response.email || !response.onboardingStatus) {
        throw new ApiRequestError({
          message: "Máy chủ trả về thông tin tài khoản chưa hợp lệ. Vui lòng thử lại sau.",
          code: "AUTH_RESPONSE_INVALID"
        });
      }
    },
    retry: false,
    gcTime: 0
  });
}
