import React, { useRef, useState } from "react";
import { Text, View } from "react-native";
import type { TextInputInstance } from "react-native";
import { useRouter } from "expo-router";
import {
  AuthField,
  AuthFooterLink,
  AuthHeader,
  AuthLayout,
  AuthNotice,
  AuthSubmitButton,
  useAuthTheme
} from "@/components/common/AuthUI";
import { useRegisterMutation } from "@/features/auth/hooks/useRegisterMutation";
import { useAuthRetryDelay } from "@/features/auth/hooks/useAuthRetryDelay";
import {
  AuthFieldErrors,
  hasAuthErrors,
  validateRegistration
} from "@/features/auth/utils/authValidation";
import { normalizeApiError } from "@/utils/normalizeApiError";

export default function RegisterScreen() {
  const theme = useAuthTheme();
  const router = useRouter();
  const mutation = useRegisterMutation();
  const retryDelay = useAuthRetryDelay();
  const passwordRef = useRef<TextInputInstance>(null);
  const confirmPasswordRef = useRef<TextInputInstance>(null);
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [passwordVisible, setPasswordVisible] = useState(false);
  const [confirmVisible, setConfirmVisible] = useState(false);
  const [fieldErrors, setFieldErrors] = useState<AuthFieldErrors>({});

  const clearFeedback = () => mutation.reset();

  const submit = () => {
    if (retryDelay.isWaiting) return;
    clearFeedback();
    const errors = validateRegistration({ email, password, confirmPassword });
    setFieldErrors(errors);
    if (hasAuthErrors(errors)) return;

    mutation.mutate(
      { email: email.trim(), password, confirmPassword },
      {
        onSuccess: () => router.replace({ pathname: "/(auth)/login", params: { registered: "1" } }),
        onError: (error) => {
          const normalized = normalizeApiError(error);
          retryDelay.blockRetry(normalized.retryAfterSeconds);
          const mapped: AuthFieldErrors = {};
          for (const [key, message] of Object.entries(normalized.fieldErrors)) {
            if (key === "email" || key === "password" || key === "confirmPassword")
              mapped[key] = message;
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
        title="Tạo tài khoản"
        subtitle="Bắt đầu xây dựng lịch sinh hoạt phù hợp với bạn."
      />
      {requestError ? <AuthNotice message={requestError} /> : null}
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
            setFieldErrors((current) => ({
              ...current,
              password: undefined,
              confirmPassword: undefined
            }));
            clearFeedback();
          }}
          error={fieldErrors.password}
          placeholder="Tạo mật khẩu"
          autoCapitalize="none"
          autoCorrect={false}
          autoComplete="new-password"
          textContentType="newPassword"
          returnKeyType="next"
          onSubmitEditing={() => confirmPasswordRef.current?.focus()}
          secureTextEntry
          isPasswordVisible={passwordVisible}
          onToggleSecure={() => setPasswordVisible((visible) => !visible)}
          editable={!mutation.isPending}
        />
        <Text style={[styles.guidance, { color: theme.secondary }]}>
          Mật khẩu cần có từ 8 đến 72 ký tự.
        </Text>
        <AuthField
          inputRef={confirmPasswordRef}
          label="Xác nhận mật khẩu"
          value={confirmPassword}
          onChangeText={(value) => {
            setConfirmPassword(value);
            setFieldErrors((current) => ({ ...current, confirmPassword: undefined }));
            clearFeedback();
          }}
          error={fieldErrors.confirmPassword}
          placeholder="Nhập lại mật khẩu"
          autoCapitalize="none"
          autoCorrect={false}
          autoComplete="new-password"
          textContentType="newPassword"
          returnKeyType="done"
          onSubmitEditing={submit}
          secureTextEntry
          isPasswordVisible={confirmVisible}
          onToggleSecure={() => setConfirmVisible((visible) => !visible)}
          editable={!mutation.isPending}
        />
        <AuthSubmitButton
          title="Tạo tài khoản"
          pending={mutation.isPending}
          disabled={retryDelay.isWaiting}
          onPress={submit}
        />
        <AuthFooterLink
          prompt="Đã có tài khoản?"
          action="Đăng nhập"
          disabled={mutation.isPending}
          onPress={() => router.replace("/(auth)/login")}
        />
      </View>
    </AuthLayout>
  );
}

const styles = {
  guidance: {
    fontSize: 13,
    lineHeight: 18,
    marginTop: -10,
    marginBottom: 16
  }
};
