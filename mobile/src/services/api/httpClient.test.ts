import { afterEach, describe, expect, it, jest } from "@jest/globals";
import { ApiRequestError } from "@/types/api.types";
import { parseRetryAfter, postJson, safeApiMessage } from "./httpClient";

const originalApiBaseUrl = process.env.EXPO_PUBLIC_API_BASE_URL;

afterEach(() => {
  jest.restoreAllMocks();
  if (originalApiBaseUrl === undefined) delete process.env.EXPO_PUBLIC_API_BASE_URL;
  else process.env.EXPO_PUBLIC_API_BASE_URL = originalApiBaseUrl;
});

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
});
