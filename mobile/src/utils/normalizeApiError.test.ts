import { describe, expect, it } from "@jest/globals";
import { ApiRequestError } from "@/types/api.types";
import { normalizeApiError } from "./normalizeApiError";

describe("API error presentation", () => {
  it("preserves safe field errors and correlation metadata", () => {
    expect(
      normalizeApiError(
        new ApiRequestError({
          message: "Một số thông tin chưa hợp lệ.",
          status: 400,
          fieldErrors: { email: "Email chưa đúng định dạng." },
          correlationId: "trace-123",
          retryAfterSeconds: 15
        })
      )
    ).toEqual({
      message: "Một số thông tin chưa hợp lệ. Thử lại sau 15 giây.",
      fieldErrors: { email: "Email chưa đúng định dạng." },
      correlationId: "trace-123",
      retryAfterSeconds: 15
    });
  });

  it("does not expose arbitrary internal errors", () => {
    expect(normalizeApiError(new Error("database password leaked"))).toEqual({
      message: "Đã có lỗi xảy ra. Vui lòng thử lại.",
      fieldErrors: {}
    });
  });
});
