import { afterEach, beforeEach, describe, expect, it, jest } from "@jest/globals";
import { ApiRequestError } from "@/types/api.types";
import { clearPrivateQueryCache } from "@/services/api/queryClient";
import { clearStoredTokens, readRefreshToken, saveRefreshToken } from "@/services/storage/tokenStorage";
import { refreshSession as requestRefresh } from "../api/authApi";
import { useAuthSessionStore } from "../store/authSessionStore";
import {
  endLocalSession,
  refreshSessionSingleFlight,
  restoreAuthSession
} from "./authSession";

jest.mock("@/services/storage/tokenStorage", () => ({
  clearStoredTokens: jest.fn(),
  readRefreshToken: jest.fn(),
  saveRefreshToken: jest.fn()
}));
jest.mock("@/services/api/queryClient", () => ({ clearPrivateQueryCache: jest.fn() }));
jest.mock("../api/authApi", () => ({ refreshSession: jest.fn() }));

const readRefreshMock = jest.mocked(readRefreshToken);
const saveRefreshMock = jest.mocked(saveRefreshToken);
const clearTokensMock = jest.mocked(clearStoredTokens);
const clearQueryCacheMock = jest.mocked(clearPrivateQueryCache);
const refreshMock = jest.mocked(requestRefresh);

const validResponse = {
  tokens: {
    accessToken: "signed-access-token",
    refreshToken: "rotated-refresh-token",
    tokenType: "Bearer" as const,
    expiresIn: 900
  }
};

beforeEach(() => {
  clearTokensMock.mockResolvedValue(undefined);
  clearQueryCacheMock.mockResolvedValue(undefined);
  saveRefreshMock.mockResolvedValue(undefined);
  useAuthSessionStore.setState({
    status: "unauthenticated",
    accessToken: null,
    user: null,
    message: null,
    retryAfterSeconds: null
  });
});

afterEach(() => {
  jest.resetAllMocks();
});

describe("AUTH-02 session lifecycle", () => {
  it("restores a session and keeps access credentials in memory while rotating SecureStore refresh token", async () => {
    readRefreshMock.mockResolvedValue("stored-refresh-token");
    refreshMock.mockResolvedValue(validResponse);

    await expect(restoreAuthSession()).resolves.toBe(true);

    expect(refreshMock).toHaveBeenCalledWith({ refreshToken: "stored-refresh-token" });
    expect(saveRefreshMock).toHaveBeenCalledWith("rotated-refresh-token");
    expect(useAuthSessionStore.getState()).toMatchObject({
      status: "authenticated",
      accessToken: "signed-access-token",
      user: null
    });
  });

  it("does not call refresh when SecureStore has no token", async () => {
    readRefreshMock.mockResolvedValue(null);

    await expect(restoreAuthSession()).resolves.toBe(false);
    expect(refreshMock).not.toHaveBeenCalled();
    expect(useAuthSessionStore.getState().status).toBe("unauthenticated");
  });

  it("clears the session for an invalid or expired refresh token", async () => {
    readRefreshMock.mockResolvedValue("expired-refresh-token");
    refreshMock.mockRejectedValue(new ApiRequestError({
      status: 401,
      code: "AUTH_INVALID_REFRESH_TOKEN",
      message: "Phiên đăng nhập không còn hợp lệ. Vui lòng đăng nhập lại."
    }));

    await expect(restoreAuthSession()).resolves.toBe(false);

    expect(clearTokensMock).toHaveBeenCalled();
    expect(clearQueryCacheMock).toHaveBeenCalled();
    expect(useAuthSessionStore.getState()).toMatchObject({ status: "unauthenticated", accessToken: null });
  });

  it("preserves refresh credentials after a network failure", async () => {
    readRefreshMock.mockResolvedValue("stored-refresh-token");
    refreshMock.mockRejectedValue(new ApiRequestError({ code: "NETWORK_ERROR", message: "Offline" }));

    await expect(restoreAuthSession()).resolves.toBe(false);

    expect(clearTokensMock).not.toHaveBeenCalled();
    expect(useAuthSessionStore.getState()).toMatchObject({ status: "recovery-required", accessToken: null });
  });

  it("preserves refresh credentials and Retry-After on HTTP 429", async () => {
    readRefreshMock.mockResolvedValue("stored-refresh-token");
    refreshMock.mockRejectedValue(new ApiRequestError({
      status: 429,
      code: "AUTH_RATE_LIMITED",
      message: "Bạn thao tác quá nhanh.",
      retryAfterSeconds: 12
    }));

    await expect(restoreAuthSession()).resolves.toBe(false);

    expect(clearTokensMock).not.toHaveBeenCalled();
    expect(useAuthSessionStore.getState()).toMatchObject({
      status: "recovery-required",
      retryAfterSeconds: 12
    });
  });

  it("fails closed if rotated refresh persistence fails", async () => {
    readRefreshMock.mockResolvedValue("consumed-refresh-token");
    refreshMock.mockResolvedValue(validResponse);
    saveRefreshMock.mockRejectedValue(new Error("SecureStore unavailable"));

    await expect(restoreAuthSession()).resolves.toBe(false);

    expect(clearTokensMock).toHaveBeenCalled();
    expect(useAuthSessionStore.getState()).toMatchObject({ status: "unauthenticated", accessToken: null });
  });

  it("coalesces concurrent refresh attempts into one request", async () => {
    readRefreshMock.mockResolvedValue("stored-refresh-token");
    let resolveRefresh: ((value: typeof validResponse) => void) | undefined;
    refreshMock.mockImplementation(() => new Promise((resolve) => { resolveRefresh = resolve; }));

    const first = refreshSessionSingleFlight();
    const second = refreshSessionSingleFlight();
    expect(second).toBe(first);
    await Promise.resolve();
    expect(refreshMock).toHaveBeenCalledTimes(1);
    resolveRefresh?.(validResponse);
    await expect(Promise.all([first, second])).resolves.toEqual([
      "signed-access-token",
      "signed-access-token"
    ]);
  });

  it("does not restore a session when a refresh completes after logout", async () => {
    readRefreshMock.mockResolvedValue("stored-refresh-token");
    let resolveRefresh: ((value: typeof validResponse) => void) | undefined;
    refreshMock.mockImplementation(() => new Promise((resolve) => { resolveRefresh = resolve; }));

    const restoration = restoreAuthSession();
    await Promise.resolve();
    await Promise.resolve();
    expect(refreshMock).toHaveBeenCalledTimes(1);

    await endLocalSession();
    resolveRefresh?.(validResponse);
    await expect(restoration).resolves.toBe(false);

    expect(saveRefreshMock).not.toHaveBeenCalled();
    expect(useAuthSessionStore.getState()).toMatchObject({
      status: "unauthenticated",
      accessToken: null,
      user: null
    });
  });

  it("clears local user state, SecureStore and private query cache on logout", async () => {
    useAuthSessionStore.getState().setAuthenticated("memory-token", {
      userId: "user-a",
      email: "synthetic@example.test",
      onboardingStatus: "NOT_STARTED"
    });

    await endLocalSession();

    expect(clearTokensMock).toHaveBeenCalled();
    expect(clearQueryCacheMock).toHaveBeenCalled();
    expect(useAuthSessionStore.getState()).toMatchObject({
      status: "unauthenticated",
      accessToken: null,
      user: null
    });
  });
});
