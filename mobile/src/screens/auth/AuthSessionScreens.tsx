import React, { useEffect, useState } from "react";
import { ActivityIndicator, Text, View } from "react-native";
import { useRouter } from "expo-router";
import {
  AuthHeader,
  AuthLayout,
  AuthNotice,
  AuthSubmitButton,
  useAuthTheme
} from "@/components/common/AuthUI";
import { endLocalSession, restoreAuthSession } from "@/features/auth/session/authSession";
import { useAuthSessionStore } from "@/features/auth/store/authSessionStore";

export function SessionRestoringScreen() {
  const theme = useAuthTheme();
  return (
    <AuthLayout>
      <View accessibilityRole="progressbar" accessibilityLabel="Đang khôi phục phiên đăng nhập">
        <AuthHeader title="Đang kiểm tra phiên" subtitle="Vui lòng chờ trong giây lát." />
        <ActivityIndicator color={theme.primary} size="large" />
      </View>
    </AuthLayout>
  );
}

export function SessionRecoveryScreen() {
  const router = useRouter();
  const theme = useAuthTheme();
  const message = useAuthSessionStore((state) => state.message);
  const retryAfterSeconds = useAuthSessionStore((state) => state.retryAfterSeconds);
  const retryAtTimestamp = useAuthSessionStore((state) => state.retryAtTimestamp);
  const [clock, setClock] = useState<{ retryAt: number | null; now: number }>({ retryAt: null, now: 0 });
  const [pending, setPending] = useState(false);
  const fallbackTime = retryAtTimestamp && retryAfterSeconds
    ? retryAtTimestamp - retryAfterSeconds * 1000
    : 0;
  const currentTime = clock.retryAt === retryAtTimestamp ? clock.now : fallbackTime;
  const remaining = retryAtTimestamp
    ? Math.max(0, Math.ceil((retryAtTimestamp - currentTime) / 1000))
    : 0;

  useEffect(() => {
    if (!retryAtTimestamp) return;
    const timer = setInterval(() => setClock({ retryAt: retryAtTimestamp, now: Date.now() }), 1000);
    return () => clearInterval(timer);
  }, [retryAtTimestamp]);

  const retry = async () => {
    if (pending || remaining > 0) return;
    setPending(true);
    await restoreAuthSession();
    setPending(false);
  };

  const clearAndReturnToLogin = async () => {
    setPending(true);
    try {
      await endLocalSession();
      router.replace("/(auth)/login");
    } catch {
      setPending(false);
    }
  };

  return (
    <AuthLayout>
      <AuthHeader title="Không thể khôi phục phiên" subtitle="Kiểm tra kết nối rồi thử lại." />
      <AuthNotice message={message ?? "Không thể kết nối để khôi phục phiên."} />
      {remaining > 0 ? (
        <Text accessibilityRole="timer" style={{ color: theme.secondary, textAlign: "center" }}>
          Có thể thử lại sau {remaining} giây.
        </Text>
      ) : null}
      <AuthSubmitButton
        title="Thử lại"
        pending={pending}
        disabled={remaining > 0}
        onPress={() => void retry()}
      />
      <AuthSubmitButton
        title="Xóa phiên trên thiết bị"
        pending={pending}
        onPress={() => void clearAndReturnToLogin()}
      />
    </AuthLayout>
  );
}

export function AuthenticatedSessionScreen() {
  const router = useRouter();
  const user = useAuthSessionStore((state) => state.user);
  const theme = useAuthTheme();
  const [pending, setPending] = useState(false);
  const [error, setError] = useState("");

  const clearOnDevice = async () => {
    setPending(true);
    setError("");
    try {
      await endLocalSession();
      router.replace("/(auth)/login");
    } catch {
      setError("Không thể xóa phiên an toàn trên thiết bị. Vui lòng thử lại.");
    } finally {
      setPending(false);
    }
  };

  return (
    <AuthLayout>
      <AuthHeader
        title="Phiên đăng nhập đang hoạt động"
        subtitle={user?.email ?? "Bạn đã đăng nhập trên thiết bị này."}
      />
      <Text style={{ color: theme.secondary, fontSize: 14, lineHeight: 21, marginBottom: 18 }}>
        Nút đăng xuất hiện xóa thông tin phiên trên thiết bị. Máy chủ chưa cung cấp API thu hồi phiên.
      </Text>
      {error ? <AuthNotice message={error} /> : null}
      <AuthSubmitButton
        title="Xóa phiên trên thiết bị"
        pending={pending}
        onPress={() => void clearOnDevice()}
      />
    </AuthLayout>
  );
}
