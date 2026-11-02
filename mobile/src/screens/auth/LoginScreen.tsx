import React, { useRef, useState } from "react";
import { TextInput, View } from "react-native";
import { useLocalSearchParams, useRouter } from "expo-router";
import {
  AuthField,
  AuthFooterLink,
  AuthHeader,
  AuthLayout,
  AuthNotice,
  AuthSubmitButton
} from "@/components/common/AuthUI";
import { useLoginMutation } from "@/features/auth/hooks/useLoginMutation";
import { useAuthRetryDelay } from "@/features/auth/hooks/useAuthRetryDelay";
import {
  AuthFieldErrors,
  hasAuthErrors,
  validateLogin
} from "@/features/auth/utils/authValidation";
import { normalizeApiError } from "@/utils/normalizeApiError";

export default function LoginScreen() {
  const router = useRouter();
  const params = useLocalSearchParams<{ registered?: string }>();
  const mutation = useLoginMutation();
  const retryDelay = useAuthRetryDelay();
  const passwordRef = useRef<TextInput>(null);
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [passwordVisible, setPasswordVisible] = useState(false);
  const [fieldErrors, setFieldErrors] = useState<AuthFieldErrors>({});
  const [successMessage, setSuccessMessage] = useState("");

  const clearFeedback = () => {
    setSuccessMessage("");
    mutation.reset();
  };

  const submit = () => {
    if (retryDelay.isWaiting) return;
    clearFeedback();
    const errors = validateLogin({ email, password });
    setFieldErrors(errors);
    if (hasAuthErrors(errors)) return;

    mutation.mutate(
      { email: email.trim(), password },
      {
        onSuccess: () => {
          setPassword("");
          setSuccessMessage("Đăng nhập thành công. Phiên đã được lưu an toàn.");
        },
        onError: (error) => {
          const normalized = normalizeApiError(error);
          retryDelay.blockRetry(normalized.retryAfterSeconds);
          const mapped: AuthFieldErrors = {};
          for (const [key, message] of Object.entries(normalized.fieldErrors)) {
            if (key === "email" || key === "password") mapped[key] = message;
          }
          setFieldErrors(mapped);
        }
      }
    );
  };

  const requestError = mutation.error ? normalizeApiError(mutation.error).message : "";

  return (
    <AuthLayout>
      <AuthHeader
        title="Chào mừng bạn quay lại"
        subtitle="Tiếp tục hành trình quản lý lối sống của bạn."
      />
      {params.registered === "1" ? (
        <AuthNotice message="Tạo tài khoản thành công. Bạn có thể đăng nhập ngay." tone="success" />
      ) : null}
      {requestError ? <AuthNotice message={requestError} /> : null}
      {successMessage ? <AuthNotice message={successMessage} tone="success" /> : null}
      <View>
        <AuthField
          label="Email"
          value={email}
          onChangeText={(value) => {
            setEmail(value);
            setFieldErrors((current) => ({ ...current, email: undefined }));
            clearFeedback();
          }}
          error={fieldErrors.email}
          placeholder="ten@email.com"
          keyboardType="email-address"
          autoCapitalize="none"
          autoCorrect={false}
          autoComplete="email"
          textContentType="emailAddress"
          returnKeyType="next"
          maxLength={254}
          editable={!mutation.isPending}
          onSubmitEditing={() => passwordRef.current?.focus()}
        />
        <AuthField
          inputRef={passwordRef}
          label="Mật khẩu"
          value={password}
          onChangeText={(value) => {
            setPassword(value);
            setFieldErrors((current) => ({ ...current, password: undefined }));
            clearFeedback();
          }}
          error={fieldErrors.password}
          placeholder="Nhập mật khẩu"
          autoCapitalize="none"
          autoCorrect={false}
          autoComplete="current-password"
          textContentType="password"
          returnKeyType="done"
          onSubmitEditing={submit}
          secureTextEntry
          isPasswordVisible={passwordVisible}
          onToggleSecure={() => setPasswordVisible((visible) => !visible)}
          editable={!mutation.isPending}
        />
        <AuthSubmitButton
          title="Đăng nhập"
          pending={mutation.isPending}
          disabled={retryDelay.isWaiting}
          onPress={submit}
        />
        <AuthFooterLink
          prompt="Chưa có tài khoản?"
          action="Đăng ký"
          disabled={mutation.isPending}
          onPress={() => router.push("/(auth)/register")}
        />
      </View>
    </AuthLayout>
  );
}
