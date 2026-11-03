import { describe, expect, it } from "@jest/globals";
import { QueryClient } from "@tanstack/react-query";
import { authKeys } from "@/features/auth/queryKeys/authKeys";
import { clearPrivateQueryCache, shouldRetryQuery } from "./queryClient";
import { ApiRequestError } from "@/types/api.types";

describe("authenticated query cache boundaries", () => {
  it("removes private user cache while preserving public cache", async () => {
    const client = new QueryClient();
    client.setQueryData([...authKeys.privateUser("user-a"), "profile"], { email: "private" });
    client.setQueryData(["public", "catalog"], { item: "public" });

    await clearPrivateQueryCache(client);

    expect(client.getQueryData([...authKeys.privateUser("user-a"), "profile"])).toBeUndefined();
    expect(client.getQueryData(["public", "catalog"])).toEqual({ item: "public" });
    client.clear();
  });

  it("does not auto-retry authentication, permission, throttling or cancellation errors", () => {
    expect(shouldRetryQuery(0, new ApiRequestError({ status: 401, message: "expired" }))).toBe(false);
    expect(shouldRetryQuery(0, new ApiRequestError({ status: 403, message: "forbidden" }))).toBe(false);
    expect(shouldRetryQuery(0, new ApiRequestError({ status: 429, message: "limited" }))).toBe(false);
    expect(shouldRetryQuery(0, new ApiRequestError({ code: "REQUEST_CANCELLED", message: "cancelled" }))).toBe(false);
    expect(shouldRetryQuery(0, new ApiRequestError({ code: "NETWORK_ERROR", message: "offline" }))).toBe(true);
    expect(shouldRetryQuery(1, new ApiRequestError({ code: "NETWORK_ERROR", message: "offline" }))).toBe(false);
  });
});
