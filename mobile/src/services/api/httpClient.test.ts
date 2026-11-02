import { describe, expect, it } from "@jest/globals";
import { parseRetryAfter, safeApiMessage } from "./httpClient";

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
  });

  it("parses both Retry-After seconds and HTTP dates", () => {
    expect(parseRetryAfter("30")).toBe(30);
    expect(parseRetryAfter(null)).toBeUndefined();
    expect(parseRetryAfter("not-a-date")).toBeUndefined();
    expect(parseRetryAfter(new Date(Date.now() + 3_000).toUTCString())).toBeGreaterThanOrEqual(2);
  });
});
