import { afterEach, describe, expect, it, jest } from "@jest/globals";
import { login, register } from "./authApi";
import { postJson } from "@/services/api/httpClient";

jest.mock("@/services/api/httpClient", () => ({ postJson: jest.fn() }));

const postJsonMock = jest.mocked(postJson);

describe("AUTH-01 API contract", () => {
  afterEach(() => {
    jest.resetAllMocks();
  });

  it("posts the contract registration fields and keeps the returned account status", async () => {
    const response = {
      userId: "user-1",
      email: "person@example.com",
      onboardingStatus: "NOT_STARTED"
    };
    postJsonMock.mockResolvedValue(response);
    const request = {
      email: "person@example.com",
      password: "password123",
      confirmPassword: "password123"
    };

    await expect(register(request)).resolves.toEqual(response);
    expect(postJsonMock).toHaveBeenCalledWith("/api/v1/auth/register", request);
  });

  it("posts login credentials and reads the backend tokens field", async () => {
    const response = {
      user: { userId: "user-1", email: "person@example.com", onboardingStatus: "IN_PROGRESS" },
      tokens: null
    };
    postJsonMock.mockResolvedValue(response);
    const request = { email: "person@example.com", password: "password123" };

    await expect(login(request)).resolves.toEqual(response);
    expect(postJsonMock).toHaveBeenCalledWith("/api/v1/auth/login", request);
  });
});
