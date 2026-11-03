import { ApiRequestError } from "@/types/api.types";
import { clearPrivateQueryCache } from "@/services/api/queryClient";
import {
  clearRefreshTokenIfMatches,
  clearStoredTokens,
  readRefreshToken,
  saveRefreshToken
} from "@/services/storage/tokenStorage";
import { refreshSession as requestRefresh } from "../api/authApi";
import { RefreshResponse, TokenResponse } from "../types/auth.types";
import { useAuthSessionStore } from "../store/authSessionStore";

let sessionGeneration = 0;
let refreshInFlight: { generation: number; promise: Promise<string> } | null = null;
let startupRestore: Promise<boolean> | null = null;

export function beginAuthSessionOperation(): number {
  sessionGeneration += 1;
  return sessionGeneration;
}

export function isCurrentAuthSessionOperation(generation: number): boolean {
  return generation === sessionGeneration;
}

export function refreshSessionSingleFlight(): Promise<string> {
  const generation = sessionGeneration;
  if (refreshInFlight?.generation === generation) return refreshInFlight.promise;
  let operation: Promise<string>;
  operation = refreshStoredSession(generation).finally(() => {
    if (refreshInFlight?.promise === operation) refreshInFlight = null;
  });
  refreshInFlight = { generation, promise: operation };
  return operation;
}

export async function restoreAuthSession(): Promise<boolean> {
  beginAuthSessionOperation();
  useAuthSessionStore.getState().setRestoring();
  try {
    await refreshSessionSingleFlight();
    return true;
  } catch {
    return false;
  }
}

export function initializeAuthSession(): Promise<boolean> {
  if (!startupRestore) startupRestore = restoreAuthSession();
  return startupRestore;
}

export async function endLocalSession(): Promise<void> {
  const generation = beginAuthSessionOperation();
  useAuthSessionStore.getState().setRecoveryRequired("Đang xóa phiên đăng nhập khỏi thiết bị.");
  try {
    await Promise.all([clearStoredTokens(), clearPrivateQueryCache()]);
    if (generation === sessionGeneration) useAuthSessionStore.getState().setUnauthenticated();
  } catch {
    if (generation === sessionGeneration) {
      useAuthSessionStore.getState().setRecoveryRequired(
        "Không thể xóa dữ liệu phiên an toàn trên thiết bị. Vui lòng thử lại.");
    }
    throw new ApiRequestError({
      message: "Không thể xóa dữ liệu phiên an toàn trên thiết bị. Vui lòng thử lại.",
      code: "SECURE_STORE_ERROR"
    });
  }
}

async function refreshStoredSession(generation: number): Promise<string> {
  let refreshToken: string | null;
  try {
    refreshToken = await readRefreshToken();
  } catch {
    if (generation === sessionGeneration) {
      useAuthSessionStore.getState().setRecoveryRequired(
        "Không thể truy cập phiên an toàn trên thiết bị. Hãy thử lại.");
    }
    throw new ApiRequestError({
      message: "Không thể truy cập phiên an toàn trên thiết bị. Hãy thử lại.",
      code: "SECURE_STORE_ERROR"
    });
  }

  if (generation !== sessionGeneration) throw staleSessionOperation();

  if (!refreshToken) {
    useAuthSessionStore.getState().setUnauthenticated();
    await clearPrivateQueryCache();
    throw new ApiRequestError({
      message: "Vui lòng đăng nhập để tiếp tục.",
      status: 401,
      code: "AUTH_UNAUTHENTICATED"
    });
  }

  let response: RefreshResponse;
  try {
    response = await requestRefresh({ refreshToken });
  } catch (error) {
    if (generation !== sessionGeneration) throw staleSessionOperation();
    if (isInvalidRefresh(error)) {
      await clearStoredTokens().catch(() => undefined);
      await clearPrivateQueryCache();
      if (generation === sessionGeneration) useAuthSessionStore.getState().setUnauthenticated();
    } else {
      useAuthSessionStore.getState().setRecoveryRequired(
        error instanceof ApiRequestError ? error.message : "Không thể khôi phục phiên. Hãy thử lại.",
        error instanceof ApiRequestError ? error.retryAfterSeconds : undefined);
    }
    throw error;
  }

  if (generation !== sessionGeneration) throw staleSessionOperation();

  const tokens = response?.tokens;
  if (!isValidTokenResponse(tokens)) {
    await clearStoredTokens().catch(() => undefined);
    await clearPrivateQueryCache();
    if (generation === sessionGeneration) useAuthSessionStore.getState().setUnauthenticated();
    throw new ApiRequestError({
      message: "Máy chủ trả về phiên chưa hợp lệ. Vui lòng đăng nhập lại.",
      code: "AUTH_RESPONSE_INVALID"
    });
  }

  try {
    await saveRefreshToken(tokens.refreshToken);
  } catch {
    // The old refresh token is already consumed by rotation; never restore or reuse it.
    await clearStoredTokens().catch(() => undefined);
    await clearPrivateQueryCache();
    if (generation !== sessionGeneration) throw staleSessionOperation();
    useAuthSessionStore.getState().setUnauthenticated();
    throw new ApiRequestError({
      message: "Không thể lưu phiên an toàn trên thiết bị. Vui lòng đăng nhập lại.",
      code: "SECURE_STORE_ERROR"
    });
  }

  if (generation !== sessionGeneration) {
    await clearRefreshTokenIfMatches(tokens.refreshToken);
    throw staleSessionOperation();
  }

  const currentUser = useAuthSessionStore.getState().user;
  useAuthSessionStore.getState().setAuthenticated(tokens.accessToken, currentUser);
  return tokens.accessToken;
}

function staleSessionOperation(): ApiRequestError {
  return new ApiRequestError({
    message: "Phiên đã thay đổi. Vui lòng thử lại.",
    code: "SESSION_OPERATION_STALE"
  });
}

function isInvalidRefresh(error: unknown): boolean {
  return error instanceof ApiRequestError &&
    (error.status === 401 || error.code === "AUTH_INVALID_REFRESH_TOKEN");
}

function isValidTokenResponse(tokens: TokenResponse | null | undefined): tokens is TokenResponse {
  return Boolean(
    tokens?.accessToken?.trim() &&
    tokens.refreshToken?.trim() &&
    tokens.tokenType === "Bearer" &&
    Number.isFinite(tokens.expiresIn) &&
    tokens.expiresIn > 0
  );
}
