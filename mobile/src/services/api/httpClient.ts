import { ApiRequestError } from "@/types/api.types";
import {
  endLocalSession,
  refreshSessionSingleFlight
} from "@/features/auth/session/authSession";
import { useAuthSessionStore } from "@/features/auth/store/authSessionStore";
import { requestJson } from "./transport";
import type { JsonRequestOptions } from "./transport";

export { parseRetryAfter, postJson, safeApiMessage } from "./transport";

export interface AuthenticatedRequestOptions extends Omit<JsonRequestOptions, "headers"> {
  headers?: Record<string, string>;
}

export async function authenticatedJson<TResponse>(
  path: string,
  options: AuthenticatedRequestOptions = {}
): Promise<TResponse> {
  if (path.split("?")[0] === "/api/v1/auth/refresh") {
    throw new ApiRequestError({
      message: "Yêu cầu phiên không hợp lệ.",
      code: "AUTH_REFRESH_RECURSION"
    });
  }

  const session = useAuthSessionStore.getState();
  if (session.status !== "authenticated" || !session.accessToken) {
    throw new ApiRequestError({
      message: "Vui lòng đăng nhập để tiếp tục.",
      status: 401,
      code: "AUTH_UNAUTHENTICATED"
    });
  }

  try {
    return await requestWithAccessToken<TResponse>(path, options, session.accessToken);
  } catch (error) {
    if (!isUnauthorized(error)) throw error;
  }

  let newAccessToken: string;
  try {
    newAccessToken = await waitForRefreshOrCancel(refreshSessionSingleFlight(), options.signal);
  } catch (error) {
    throw error;
  }

  if (!isSafeToReplay(options.method ?? "GET", options.headers)) {
    throw new ApiRequestError({
      message: "Phiên đã được gia hạn. Vui lòng gửi lại thao tác.",
      status: 401,
      code: "AUTH_RETRY_REQUIRED"
    });
  }

  try {
    return await requestWithAccessToken<TResponse>(path, options, newAccessToken);
  } catch (error) {
    if (isUnauthorized(error)) {
      await endLocalSession();
    }
    throw error;
  }
}

async function requestWithAccessToken<TResponse>(
  path: string,
  options: AuthenticatedRequestOptions,
  accessToken: string
): Promise<TResponse> {
  return requestJson<TResponse>(path, {
    ...options,
    headers: { ...options.headers, Authorization: `Bearer ${accessToken}` }
  });
}

function isUnauthorized(error: unknown): error is ApiRequestError {
  return error instanceof ApiRequestError && error.status === 401;
}

function isSafeToReplay(method: string, headers: Record<string, string> = {}): boolean {
  if (["GET", "HEAD", "OPTIONS"].includes(method.toUpperCase())) return true;
  return Object.keys(headers).some(
    (key) => key.toLowerCase() === "idempotency-key" && headers[key].trim().length > 0
  );
}

function waitForRefreshOrCancel(
  refreshPromise: Promise<string>,
  signal?: AbortSignal
): Promise<string> {
  if (!signal) return refreshPromise;
  if (signal.aborted) return Promise.reject(cancelledRequest());

  return new Promise((resolve, reject) => {
    const onAbort = () => reject(cancelledRequest());
    signal.addEventListener("abort", onAbort, { once: true });
    refreshPromise.then(
      (token) => {
        signal.removeEventListener("abort", onAbort);
        resolve(token);
      },
      (error: unknown) => {
        signal.removeEventListener("abort", onAbort);
        reject(error);
      }
    );
  });
}

function cancelledRequest(): ApiRequestError {
  return new ApiRequestError({ message: "Yêu cầu đã được hủy.", code: "REQUEST_CANCELLED" });
}
