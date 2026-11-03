import { describe, expect, it } from "@jest/globals";
import { getAuthRouteDestination } from "./authRouteGuard";

describe("AUTH-02 route guard", () => {
  it("never enters authenticated routes before restore completes", () => {
    expect(getAuthRouteDestination("restoring")).toBe("restoring");
    expect(getAuthRouteDestination("recovery-required")).toBe("recovery");
    expect(getAuthRouteDestination("unauthenticated")).toBe("auth");
  });

  it("allows authenticated routes only for a validated in-memory session", () => {
    expect(getAuthRouteDestination("authenticated")).toBe("authenticated");
  });
});
