import { AuthSessionStatus } from "../store/authSessionStore";

export type AuthRouteDestination = "restoring" | "recovery" | "authenticated" | "auth";

export function getAuthRouteDestination(status: AuthSessionStatus): AuthRouteDestination {
  switch (status) {
    case "restoring":
      return "restoring";
    case "recovery-required":
      return "recovery";
    case "authenticated":
      return "authenticated";
    case "unauthenticated":
      return "auth";
  }
}
