import React from "react";
import { Redirect } from "expo-router";
import { useAuthSessionStore } from "@/features/auth/store/authSessionStore";
import { getAuthRouteDestination } from "@/features/auth/session/authRouteGuard";
import {
  AuthenticatedSessionScreen,
  SessionRecoveryScreen,
  SessionRestoringScreen
} from "@/screens/auth/AuthSessionScreens";

export default function IndexRoute() {
  const status = useAuthSessionStore((state) => state.status);
  const destination = getAuthRouteDestination(status);
  if (destination === "restoring") return <SessionRestoringScreen />;
  if (destination === "recovery") return <SessionRecoveryScreen />;
  if (destination === "authenticated") return <AuthenticatedSessionScreen />;
  return <Redirect href="/(auth)/login" />;
}
