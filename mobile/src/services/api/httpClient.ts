import { ApiErrorBody, ApiRequestError } from "@/types/api.types";

const REQUEST_TIMEOUT_MS = 15_000;

function getApiBaseUrl(): string {
  const baseUrl = process.env.EXPO_PUBLIC_API_BASE_URL?.trim();
  if (!baseUrl) {
    throw new ApiRequestError({ message: "Dịch vụ chưa được cấu hình. Vui lòng thử lại sau." });
  }
  return baseUrl.replace(/\/$/, "");
}

export async function postJson<TResponse, TRequest>(
  path: string,
  body: TRequest
): Promise<TResponse> {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS);

  try {
    const url = `${getApiBaseUrl()}${path}`;
    let response: Response;
    try {
      response = await fetch(url, {
        method: "POST",
        headers: { Accept: "application/json", "Content-Type": "application/json" },
        body: JSON.stringify(body),
        signal: controller.signal
      });
    } catch {
      const timedOut = controller.signal.aborted;
      throw new ApiRequestError({
        message: timedOut
          ? "Yêu cầu mất quá nhiều thời gian. Hãy kiểm tra kết nối và thử lại."
          : "Không thể kết nối. Hãy kiểm tra mạng và thử lại.",
        code: timedOut ? "REQUEST_TIMEOUT" : "NETWORK_ERROR"
      });
    }

    const responseBody: unknown = await response.json().catch(() => undefined);
    if (!response.ok) {
      const errorBody = (responseBody ?? {}) as ApiErrorBody;
      const retryAfter = response.headers.get("Retry-After");
      throw new ApiRequestError({
        message: safeApiMessage(errorBody, response.status),
        status: response.status,
        code: errorBody.code,
        fieldErrors: errorBody.fieldErrors,
        correlationId: errorBody.correlationId,
        retryAfterSeconds: parseRetryAfter(retryAfter)
      });
    }

    return responseBody as TResponse;
  } finally {
    clearTimeout(timeout);
  }
}

export function parseRetryAfter(headerValue: string | null): number | undefined {
  if (!headerValue) return undefined;
  const seconds = Number(headerValue);
  if (Number.isFinite(seconds) && seconds >= 0) return seconds;
  const retryAt = Date.parse(headerValue);
  if (Number.isNaN(retryAt)) return undefined;
  return Math.max(0, Math.ceil((retryAt - Date.now()) / 1000));
}

export function safeApiMessage(body: ApiErrorBody, status: number): string {
  const knownMessages: Record<string, string> = {
    AUTH_INVALID_CREDENTIALS: "Email hoặc mật khẩu chưa chính xác.",
    AUTH_PASSWORD_CONFIRM_MISMATCH: "Mật khẩu xác nhận không khớp.",
    AUTH_ACCOUNT_DISABLED: "Tài khoản hiện chưa thể đăng nhập.",
    EMAIL_ALREADY_EXISTS: "Email này đã được đăng ký. Hãy đăng nhập hoặc dùng email khác.",
    VALIDATION_ERROR: "Một số thông tin chưa hợp lệ. Vui lòng kiểm tra lại."
  };
  if (body.code && knownMessages[body.code]) return knownMessages[body.code];
  if (status === 429) return "Bạn thao tác quá nhanh. Vui lòng chờ một chút rồi thử lại.";
  if (status === 401) return "Email hoặc mật khẩu chưa chính xác.";
  if (status === 403) return "Tài khoản hiện chưa thể thực hiện thao tác này.";
  if (status === 409) return "Thông tin này đã được sử dụng. Hãy đăng nhập hoặc dùng email khác.";
  if (status >= 500) return "Dịch vụ đang gặp sự cố. Vui lòng thử lại sau.";
  if (body.fieldErrors && Object.keys(body.fieldErrors).length > 0)
    return "Một số thông tin chưa hợp lệ. Vui lòng kiểm tra lại.";
  return "Không thể hoàn tất yêu cầu. Vui lòng thử lại.";
}
