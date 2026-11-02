import React, { ReactNode, Ref, useState } from "react";
import {
  ActivityIndicator,
  KeyboardAvoidingView,
  Platform,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  TextInputProps,
  TouchableOpacity,
  useColorScheme,
  View
} from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { colors } from "@/theme/colors";

export const authPalette = {
  background: colors.background,
  surface: colors.surface,
  input: colors.surface,
  border: colors.border,
  text: colors.textPrimary,
  secondary: colors.textSecondary,
  tertiary: colors.textTertiary,
  primary: colors.primary[500],
  primaryPressed: colors.primary[700],
  danger: colors.danger,
  surfaceSecondary: colors.surfaceSecondary
};

export function useAuthTheme() {
  const dark = useColorScheme() === "dark";
  return dark
    ? {
        ...authPalette,
        background: "#0F1715",
        surface: "#17211F",
        input: "#17211F",
        border: "#2B3B37",
        text: "#F1F5F4",
        secondary: "#A7B4B0",
        tertiary: "#A7B4B0",
        primary: "#2DD4BF",
        primaryPressed: "#2DD4BF",
        surfaceSecondary: "#1E2B28",
        buttonText: "#0F1715",
        disabled: "#38524C",
        noticeBackground: "#431D1D",
        noticeText: "#FECACA",
        successBackground: "#163B34",
        successText: "#B8FFF0",
        brandBackground: "#1E2B28",
        brandText: "#2DD4BF"
      }
    : {
        ...authPalette,
        buttonText: authPalette.text,
        disabled: colors.disabled,
        noticeBackground: "#FEF2F2",
        noticeText: "#991B1B",
        successBackground: colors.primary[100],
        successText: colors.primary[900],
        brandBackground: colors.primary[100],
        brandText: colors.primary[800]
      };
}

export function AuthLayout({ children }: { children: ReactNode }) {
  const theme = useAuthTheme();
  return (
    <SafeAreaView style={[styles.safeArea, { backgroundColor: theme.background }]}>
      <KeyboardAvoidingView
        style={styles.flex}
        behavior={Platform.OS === "ios" ? "padding" : undefined}
        keyboardVerticalOffset={Platform.OS === "ios" ? 12 : 0}
      >
        <ScrollView
          contentContainerStyle={styles.scrollContent}
          keyboardShouldPersistTaps="handled"
          automaticallyAdjustKeyboardInsets
        >
          <View style={styles.content}>{children}</View>
        </ScrollView>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}

export function AuthHeader({ title, subtitle }: { title: string; subtitle: string }) {
  const theme = useAuthTheme();
  return (
    <View style={styles.header}>
      <View
        style={[styles.brandMark, { backgroundColor: theme.brandBackground }]}
        accessible={false}
      >
        <Text style={[styles.brandMarkText, { color: theme.brandText }]}>L</Text>
      </View>
      <Text style={[styles.brandName, { color: theme.secondary }]}>LifeCyclePlan</Text>
      <Text accessibilityRole="header" style={[styles.title, { color: theme.text }]}>
        {title}
      </Text>
      <Text style={[styles.subtitle, { color: theme.secondary }]}>{subtitle}</Text>
    </View>
  );
}

interface AuthFieldProps extends Omit<TextInputProps, "style" | "secureTextEntry"> {
  label: string;
  error?: string;
  secureTextEntry?: boolean;
  inputRef?: Ref<TextInput>;
  onToggleSecure?: () => void;
  isPasswordVisible?: boolean;
}

export function AuthField({
  label,
  error,
  secureTextEntry,
  inputRef,
  onToggleSecure,
  isPasswordVisible,
  ...inputProps
}: AuthFieldProps) {
  const theme = useAuthTheme();
  const [focused, setFocused] = useState(false);
  const inputStyle = [styles.input, error ? styles.inputError : undefined];
  return (
    <View style={styles.field}>
      <Text style={[styles.label, { color: theme.text }]}>{label}</Text>
      <View
        style={[
          styles.inputRow,
          {
            backgroundColor: theme.input,
            borderColor: error ? authPalette.danger : focused ? theme.primary : theme.border,
            borderWidth: focused ? 2 : 1
          },
          error ? styles.inputError : undefined
        ]}
      >
        <TextInput
          ref={inputRef}
          {...inputProps}
          style={[
            inputStyle,
            styles.inputInRow,
            { color: theme.text, backgroundColor: theme.input }
          ]}
          placeholderTextColor={theme.tertiary}
          secureTextEntry={secureTextEntry && !isPasswordVisible}
          accessibilityLabel={label}
          accessibilityHint={error ? `Lỗi: ${error}` : undefined}
          accessibilityState={{ disabled: inputProps.editable === false }}
          onFocus={(event) => {
            setFocused(true);
            inputProps.onFocus?.(event);
          }}
          onBlur={(event) => {
            setFocused(false);
            inputProps.onBlur?.(event);
          }}
        />
        {onToggleSecure ? (
          <TouchableOpacity
            accessibilityRole="button"
            accessibilityLabel={isPasswordVisible ? "Ẩn mật khẩu" : "Hiện mật khẩu"}
            accessibilityState={{ selected: isPasswordVisible }}
            onPress={onToggleSecure}
            style={styles.visibilityButton}
            hitSlop={8}
          >
            <Text style={[styles.visibilityText, { color: theme.brandText }]}>
              {isPasswordVisible ? "Ẩn" : "Hiện"}
            </Text>
          </TouchableOpacity>
        ) : null}
      </View>
      {error ? (
        <Text accessibilityRole="alert" style={styles.fieldError}>
          {error}
        </Text>
      ) : null}
    </View>
  );
}

export function AuthNotice({
  message,
  tone = "error"
}: {
  message: string;
  tone?: "error" | "success";
}) {
  const theme = useAuthTheme();
  return (
    <View
      accessibilityRole="alert"
      style={[
        styles.notice,
        { backgroundColor: tone === "success" ? theme.successBackground : theme.noticeBackground }
      ]}
    >
      <Text
        style={[
          styles.noticeText,
          { color: tone === "success" ? theme.successText : theme.noticeText }
        ]}
      >
        {message}
      </Text>
    </View>
  );
}

export function AuthSubmitButton({
  title,
  pending,
  disabled,
  onPress
}: {
  title: string;
  pending: boolean;
  disabled?: boolean;
  onPress: () => void;
}) {
  const theme = useAuthTheme();
  const unavailable = pending || disabled;
  return (
    <TouchableOpacity
      accessibilityRole="button"
      accessibilityLabel={pending ? "Đang gửi yêu cầu" : title}
      accessibilityState={{ disabled: unavailable, busy: pending }}
      disabled={unavailable}
      onPress={onPress}
      style={[
        styles.submitButton,
        { backgroundColor: unavailable ? theme.disabled : theme.primary }
      ]}
      activeOpacity={0.78}
    >
      {pending ? (
        <ActivityIndicator color={theme.buttonText} />
      ) : (
        <Text style={[styles.submitText, { color: theme.buttonText }]}>{title}</Text>
      )}
    </TouchableOpacity>
  );
}

export function AuthFooterLink({
  prompt,
  action,
  onPress,
  disabled
}: {
  prompt: string;
  action: string;
  onPress: () => void;
  disabled?: boolean;
}) {
  const theme = useAuthTheme();
  return (
    <View style={styles.footer}>
      <Text style={[styles.footerPrompt, { color: theme.secondary }]}>{prompt} </Text>
      <TouchableOpacity
        accessibilityRole="button"
        accessibilityLabel={action}
        accessibilityState={{ disabled }}
        disabled={disabled}
        onPress={onPress}
        hitSlop={8}
        style={styles.footerAction}
      >
        <Text style={[styles.footerActionText, { color: theme.brandText }]}>{action}</Text>
      </TouchableOpacity>
    </View>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1 },
  safeArea: { flex: 1, backgroundColor: authPalette.background },
  scrollContent: { flexGrow: 1, justifyContent: "center", paddingVertical: 28 },
  content: { width: "100%", maxWidth: 480, alignSelf: "center", paddingHorizontal: 24 },
  header: { alignItems: "center", marginBottom: 32 },
  brandMark: {
    width: 48,
    height: 48,
    borderRadius: 16,
    alignItems: "center",
    justifyContent: "center",
    backgroundColor: colors.primary[100],
    marginBottom: 10
  },
  brandMarkText: { fontSize: 24, fontWeight: "700", color: colors.primary[800] },
  brandName: { color: authPalette.secondary, fontSize: 14, fontWeight: "600", marginBottom: 28 },
  title: {
    color: authPalette.text,
    fontSize: 28,
    lineHeight: 36,
    fontWeight: "700",
    textAlign: "center"
  },
  subtitle: {
    color: authPalette.secondary,
    fontSize: 16,
    lineHeight: 24,
    textAlign: "center",
    marginTop: 8
  },
  field: { marginBottom: 18 },
  label: { color: authPalette.text, fontSize: 15, fontWeight: "600", marginBottom: 8 },
  inputRow: {
    minHeight: 52,
    flexDirection: "row",
    alignItems: "center",
    borderWidth: 1,
    borderColor: authPalette.border,
    borderRadius: 12,
    backgroundColor: authPalette.input
  },
  input: {
    minHeight: 50,
    borderWidth: 1,
    borderColor: authPalette.border,
    borderRadius: 12,
    paddingHorizontal: 14,
    color: authPalette.text,
    fontSize: 16,
    flexGrow: 1
  },
  inputInRow: { borderWidth: 0, borderRadius: 12, paddingRight: 8, flexShrink: 1 },
  inputError: { borderColor: authPalette.danger, borderWidth: 1.5 },
  visibilityButton: {
    minWidth: 52,
    minHeight: 48,
    justifyContent: "center",
    alignItems: "center",
    paddingHorizontal: 8
  },
  visibilityText: { color: colors.primary[800], fontSize: 14, fontWeight: "600" },
  fieldError: { color: authPalette.danger, fontSize: 14, lineHeight: 20, marginTop: 6 },
  notice: { borderRadius: 10, padding: 12, backgroundColor: "#FEF2F2", marginBottom: 18 },
  noticeText: { color: "#991B1B", fontSize: 14, lineHeight: 20 },
  successNotice: { backgroundColor: colors.primary[100] },
  successText: { color: colors.primary[900] },
  submitButton: {
    minHeight: 52,
    alignItems: "center",
    justifyContent: "center",
    borderRadius: 12,
    backgroundColor: authPalette.primary,
    marginTop: 6
  },
  submitDisabled: { backgroundColor: colors.disabled },
  submitText: { color: authPalette.text, fontSize: 16, fontWeight: "700" },
  footer: {
    minHeight: 52,
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "center",
    marginTop: 16
  },
  footerPrompt: { color: authPalette.secondary, fontSize: 15 },
  footerAction: { minHeight: 44, justifyContent: "center", paddingHorizontal: 4 },
  footerActionText: { color: colors.primary[800], fontSize: 15, fontWeight: "700" }
});
