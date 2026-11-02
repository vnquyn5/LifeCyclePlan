import { ApiRequestError } from "@/types/api.types";

export interface NormalizedApiError {
  message: string;
  fieldErrors: Record<string, string>;
  correlationId?: string;
  retryAfterSeconds?: number;
}

export function normalizeApiError(error: unknown): NormalizedApiError {
  if (error instanceof ApiRequestError) {
    const retryMessage =
      error.retryAfterSeconds &&
      Number.isFinite(error.retryAfterSeconds) &&
      error.retryAfterSeconds > 0
        ? ` Thử lại sau ${error.retryAfterSeconds} giây.`
        : "";
    return {
      message: `${error.message}${retryMessage}`,
      fieldErrors: error.fieldErrors,
      correlationId: error.correlationId,
      retryAfterSeconds: error.retryAfterSeconds
    };
  }
  return { message: "Đã có lỗi xảy ra. Vui lòng thử lại.", fieldErrors: {} };
}
