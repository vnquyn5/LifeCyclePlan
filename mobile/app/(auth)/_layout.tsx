import React from "react";
import { Redirect, Stack } from "expo-router";
import { useAuthSessionStore } from "@/features/auth/store/authSessionStore";
import { getAuthRouteDestination } from "@/features/auth/session/authRouteGuard";
import { SessionRestoringScreen } from "@/screens/auth/AuthSessionScreens";

export default function AuthLayout() {
  const status = useAuthSessionStore((state) => state.status);
  const destination = getAuthRouteDestination(status);
  if (destination === "restoring") return <SessionRestoringScreen />;
  if (destination === "authenticated" || destination === "recovery") return <Redirect href="/" />;
  return <Stack screenOptions={{ headerShown: false, animation: "fade" }} />;
}
