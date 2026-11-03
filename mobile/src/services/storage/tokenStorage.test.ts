import { afterEach, describe, expect, it, jest } from "@jest/globals";
import * as SecureStore from "expo-secure-store";
import {
  clearRefreshTokenIfMatches,
  clearStoredTokens,
  readRefreshToken,
  saveRefreshToken
} from "./tokenStorage";

jest.mock("expo-secure-store", () => ({
  setItemAsync: jest.fn(),
  getItemAsync: jest.fn(),
  deleteItemAsync: jest.fn()
}));

const setItemMock = jest.mocked(SecureStore.setItemAsync);
const getItemMock = jest.mocked(SecureStore.getItemAsync);
const deleteItemMock = jest.mocked(SecureStore.deleteItemAsync);

afterEach(() => {
  jest.resetAllMocks();
});

describe("refresh token storage", () => {
  it("stores only the refresh token and deletes the legacy persisted access token", async () => {
    await saveRefreshToken("synthetic-refresh-token");

    expect(setItemMock).toHaveBeenCalledTimes(1);
    expect(setItemMock).toHaveBeenCalledWith("auth.refreshToken", "synthetic-refresh-token");
    expect(deleteItemMock).toHaveBeenCalledWith("auth.accessToken");
  });

  it("deletes legacy access storage before reading refresh credentials", async () => {
    getItemMock.mockResolvedValue("synthetic-refresh-token");

    await expect(readRefreshToken()).resolves.toBe("synthetic-refresh-token");
    expect(deleteItemMock).toHaveBeenCalledWith("auth.accessToken");
    expect(getItemMock).toHaveBeenCalledWith("auth.refreshToken");
  });

  it("clears both legacy access and refresh keys", async () => {
    await clearStoredTokens();

    expect(deleteItemMock).toHaveBeenCalledWith("auth.accessToken");
    expect(deleteItemMock).toHaveBeenCalledWith("auth.refreshToken");
  });

  it("removes both keys when persisting a rotated refresh token fails", async () => {
    setItemMock.mockRejectedValue(new Error("storage unavailable"));

    await expect(saveRefreshToken("rotated-refresh-token")).rejects.toThrow("storage unavailable");
    expect(deleteItemMock).toHaveBeenCalledWith("auth.refreshToken");
    expect(deleteItemMock).toHaveBeenCalledWith("auth.accessToken");
  });

  it("serializes logout cleanup after an in-flight refresh-token write", async () => {
    let finishWrite: (() => void) | undefined;
    setItemMock.mockImplementation(() => new Promise((resolve) => {
      finishWrite = () => resolve();
    }));

    const saving = saveRefreshToken("stale-refresh-token");
    await Promise.resolve();
    const clearing = clearStoredTokens();
    expect(deleteItemMock).not.toHaveBeenCalled();

    finishWrite?.();
    await Promise.all([saving, clearing]);
    expect(deleteItemMock).toHaveBeenCalledWith("auth.refreshToken");
  });

  it("does not clear a newer refresh token when cleaning a stale write", async () => {
    getItemMock.mockResolvedValue("newer-refresh-token");

    await clearRefreshTokenIfMatches("stale-refresh-token");

    expect(deleteItemMock).not.toHaveBeenCalledWith("auth.refreshToken");
  });
});
