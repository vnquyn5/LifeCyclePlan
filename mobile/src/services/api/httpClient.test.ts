import { afterEach, describe, expect, it, jest } from "@jest/globals";
import { ApiRequestError } from "@/types/api.types";
import { refreshSession as requestRefresh } from "@/features/auth/api/authApi";
import { useAuthSessionStore } from "@/features/auth/store/authSessionStore";
import { clearStoredTokens, readRefreshToken, saveRefreshToken } from "@/services/storage/tokenStorage";
import { authenticatedJson, parseRetryAfter, postJson, safeApiMessage } from "./httpClient";

jest.mock("@/features/auth/api/authApi", () => ({ refreshSession: jest.fn() }));
jest.mock("@/services/storage/tokenStorage", () => ({
  clearStoredTokens: jest.fn(),
  readRefreshToken: jest.fn(),
  saveRefreshToken: jest.fn()
}));
jest.mock("@/services/api/queryClient", () => ({
  clearPrivateQueryCache: jest.fn()
}));

const refreshMock = jest.mocked(requestRefresh);
const readRefreshMock = jest.mocked(readRefreshToken);
const saveRefreshMock = jest.mocked(saveRefreshToken);
const clearTokensMock = jest.mocked(clearStoredTokens);

const originalApiBaseUrl = process.env.EXPO_PUBLIC_API_BASE_URL;

afterEach(() => {
  jest.restoreAllMocks();
  jest.resetAllMocks();
  if (originalApiBaseUrl === undefined) delete process.env.EXPO_PUBLIC_API_BASE_URL;
  else process.env.EXPO_PUBLIC_API_BASE_URL = originalApiBaseUrl;
});

function setAuthenticatedSession(): void {
  useAuthSessionStore.getState().setAuthenticated("old-access-token", {
    userId: "user-a",
    email: "synthetic@example.test",
    onboardingStatus: "NOT_STARTED"
  });
}

function refreshResponse(accessToken = "new-access-token", refreshToken = "new-refresh-token") {
  return {
    tokens: { accessToken, refreshToken, tokenType: "Bearer" as const, expiresIn: 900 }
  };
}

describe("normalized backend messages", () => {
  it("uses safe messages for server and rate-limit failures", () => {
    expect(safeApiMessage({ message: "database password leaked" }, 500)).toBe(
      "Dịch vụ đang gặp sự cố. Vui lòng thử lại sau."
    );
    expect(safeApiMessage({ message: "internal throttling reason" }, 429)).toBe(
      "Bạn thao tác quá nhanh. Vui lòng chờ một chút rồi thử lại."
    );
  });

  it("maps known authentication codes to user-facing copy", () => {
    expect(safeApiMessage({ code: "AUTH_INVALID_CREDENTIALS", message: "private" }, 401)).toBe(
      "Email hoặc mật khẩu chưa chính xác."
    );
    expect(safeApiMessage({ code: "AUTH_RATE_LIMITED" }, 429)).toBe(
      "Bạn thao tác quá nhanh. Vui lòng chờ rồi thử lại."
    );
    expect(safeApiMessage({ code: "AUTH_INVALID_REFRESH_TOKEN" }, 401)).toBe(
      "Phiên đăng nhập không còn hợp lệ. Vui lòng đăng nhập lại."
    );
  });

  it("parses both Retry-After seconds and HTTP dates", () => {
    expect(parseRetryAfter("30")).toBe(30);
    expect(parseRetryAfter(null)).toBeUndefined();
    expect(parseRetryAfter("not-a-date")).toBeUndefined();
    expect(parseRetryAfter(new Date(Date.now() + 3_000).toUTCString())).toBeGreaterThanOrEqual(2);
  });

  it("maps a network failure without retrying the POST", async () => {
    process.env.EXPO_PUBLIC_API_BASE_URL = "http://localhost:8080";
    const fetchMock = jest.spyOn(globalThis, "fetch").mockRejectedValue(new TypeError("offline"));

    await expect(postJson("/api/v1/auth/login", { email: "synthetic@example.test", password: "synthetic" }))
      .rejects.toMatchObject({ code: "NETWORK_ERROR" });
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it("does not retry an HTTP 401 response", async () => {
    process.env.EXPO_PUBLIC_API_BASE_URL = "http://localhost:8080";
    const fetchMock = jest.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response(JSON.stringify({ code: "AUTH_INVALID_CREDENTIALS" }), {
        status: 401,
        headers: { "Content-Type": "application/json" }
      })
    );

    await expect(postJson("/api/v1/auth/login", { email: "synthetic@example.test", password: "synthetic" }))
      .rejects.toBeInstanceOf(ApiRequestError);
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it("coalesces concurrent 401 responses into one refresh and retries safe reads once", async () => {
    process.env.EXPO_PUBLIC_API_BASE_URL = "http://localhost:8080";
    setAuthenticatedSession();
    readRefreshMock.mockResolvedValue("old-refresh-token");
    saveRefreshMock.mockResolvedValue(undefined);
    refreshMock.mockResolvedValue(refreshResponse());
    const fetchMock = jest.spyOn(globalThis, "fetch").mockImplementation(async (_input, init) => {
      const authorization = new Headers(init?.headers).get("Authorization");
      if (authorization === "Bearer old-access-token") {
        return new Response(JSON.stringify({ code: "AUTH_UNAUTHENTICATED" }), { status: 401 });
      }
      return new Response(JSON.stringify({ ok: true }), { status: 200 });
    });

    const results = await Promise.all([
      authenticatedJson<{ ok: boolean }>("/api/v1/private/one"),
      authenticatedJson<{ ok: boolean }>("/api/v1/private/two")
    ]);

    expect(results).toEqual([{ ok: true }, { ok: true }]);
    expect(refreshMock).toHaveBeenCalledTimes(1);
    expect(fetchMock).toHaveBeenCalledTimes(4);
    expect(saveRefreshMock).toHaveBeenCalledWith("new-refresh-token");
  });

  it("does not refresh for 403 or 429 and preserves Retry-After", async () => {
    process.env.EXPO_PUBLIC_API_BASE_URL = "http://localhost:8080";
    setAuthenticatedSession();
    const fetchMock = jest.spyOn(globalThis, "fetch").mockImplementation(async (input) => {
      const status = String(input).endsWith("forbidden") ? 403 : 429;
      return new Response(JSON.stringify({ code: status === 403 ? "AUTH_ACCESS_DENIED" : "AUTH_RATE_LIMITED" }), {
        status,
        headers: status === 429 ? { "Retry-After": "19" } : undefined
      });
    });

    await expect(authenticatedJson("/api/v1/private/forbidden")).rejects.toMatchObject({ status: 403 });
    await expect(authenticatedJson("/api/v1/private/limited")).rejects.toMatchObject({
      status: 429,
      retryAfterSeconds: 19
    });
    expect(refreshMock).not.toHaveBeenCalled();
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });

  it("never automatically replays an unsafe mutation after refresh", async () => {
    process.env.EXPO_PUBLIC_API_BASE_URL = "http://localhost:8080";
    setAuthenticatedSession();
    readRefreshMock.mockResolvedValue("old-refresh-token");
    saveRefreshMock.mockResolvedValue(undefined);
    refreshMock.mockResolvedValue(refreshResponse());
    const fetchMock = jest.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response(JSON.stringify({ code: "AUTH_UNAUTHENTICATED" }), { status: 401 })
    );

    await expect(authenticatedJson("/api/v1/private/change", { method: "POST", body: { value: 1 } }))
      .rejects.toMatchObject({ code: "AUTH_RETRY_REQUIRED" });
    expect(refreshMock).toHaveBeenCalledTimes(1);
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it("does not refresh the refresh endpoint itself", async () => {
    process.env.EXPO_PUBLIC_API_BASE_URL = "http://localhost:8080";

    await expect(authenticatedJson("/api/v1/auth/refresh", { method: "POST", body: {} }))
      .rejects.toMatchObject({ code: "AUTH_REFRESH_RECURSION" });
    expect(refreshMock).not.toHaveBeenCalled();
  });

  it("preserves cancellation as cancellation and does not refresh", async () => {
    process.env.EXPO_PUBLIC_API_BASE_URL = "http://localhost:8080";
    setAuthenticatedSession();
    const controller = new AbortController();
    controller.abort();
    const fetchMock = jest.spyOn(globalThis, "fetch");

    await expect(authenticatedJson("/api/v1/private/data", { signal: controller.signal }))
      .rejects.toMatchObject({ code: "REQUEST_CANCELLED" });
    expect(fetchMock).not.toHaveBeenCalled();
    expect(refreshMock).not.toHaveBeenCalled();
  });

  it("clears the local session if the single retry is still unauthorized", async () => {
    process.env.EXPO_PUBLIC_API_BASE_URL = "http://localhost:8080";
    setAuthenticatedSession();
    readRefreshMock.mockResolvedValue("old-refresh-token");
    saveRefreshMock.mockResolvedValue(undefined);
    clearTokensMock.mockResolvedValue(undefined);
    refreshMock.mockResolvedValue(refreshResponse());
    const fetchMock = jest.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response(JSON.stringify({ code: "AUTH_UNAUTHENTICATED" }), { status: 401 })
    );

    await expect(authenticatedJson("/api/v1/private/data")).rejects.toMatchObject({ status: 401 });
    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(clearTokensMock).toHaveBeenCalled();
    expect(useAuthSessionStore.getState()).toMatchObject({ status: "unauthenticated", accessToken: null });
  });
});
