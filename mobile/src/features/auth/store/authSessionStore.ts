import { create } from "zustand";
import { AuthUser } from "../types/auth.types";

export type AuthSessionStatus =
  | "restoring"
  | "unauthenticated"
  | "authenticated"
  | "recovery-required";

interface AuthSessionState {
  status: AuthSessionStatus;
  accessToken: string | null;
  user: AuthUser | null;
  message: string | null;
  retryAfterSeconds: number | null;
  retryAtTimestamp: number | null;
  setRestoring: () => void;
  setAuthenticated: (accessToken: string, user: AuthUser | null) => void;
  setRecoveryRequired: (message: string, retryAfterSeconds?: number) => void;
  setUnauthenticated: () => void;
}

export const useAuthSessionStore = create<AuthSessionState>((set) => ({
  status: "restoring",
  accessToken: null,
  user: null,
  message: null,
  retryAfterSeconds: null,
  retryAtTimestamp: null,
  setRestoring: () =>
    set({
      status: "restoring",
      accessToken: null,
      user: null,
      message: null,
      retryAfterSeconds: null,
      retryAtTimestamp: null
    }),
  setAuthenticated: (accessToken, user) =>
    set({
      status: "authenticated",
      accessToken,
      user,
      message: null,
      retryAfterSeconds: null,
      retryAtTimestamp: null
    }),
  setRecoveryRequired: (message, retryAfterSeconds) =>
    set({
      status: "recovery-required",
      accessToken: null,
      user: null,
      message,
      retryAfterSeconds: retryAfterSeconds ?? null,
      retryAtTimestamp: retryAfterSeconds && retryAfterSeconds > 0
        ? Date.now() + retryAfterSeconds * 1000
        : null
    }),
  setUnauthenticated: () =>
    set({
      status: "unauthenticated",
      accessToken: null,
      user: null,
      message: null,
      retryAfterSeconds: null,
      retryAtTimestamp: null
    })
}));
