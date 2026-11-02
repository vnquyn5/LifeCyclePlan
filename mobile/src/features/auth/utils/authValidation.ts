export type AuthField = "email" | "password" | "confirmPassword";
export type AuthFieldErrors = Partial<Record<AuthField, string>>;

const EMAIL_MAX_LENGTH = 254;

export function validateEmail(value: string): string | undefined {
  const email = value.trim();
  if (!email) return "Vui lòng nhập email.";
  if (email.length > EMAIL_MAX_LENGTH || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
    return "Email chưa đúng định dạng.";
  }
  return undefined;
}

export function validatePassword(value: string): string | undefined {
  if (!value) return "Vui lòng nhập mật khẩu.";
  if (value.length < 8 || value.length > 72) return "Mật khẩu phải có từ 8 đến 72 ký tự.";
  return undefined;
}

export function validateLogin(values: { email: string; password: string }): AuthFieldErrors {
  return {
    ...(validateEmail(values.email) ? { email: validateEmail(values.email) } : {}),
    ...(values.password ? {} : { password: "Vui lòng nhập mật khẩu." })
  };
}

export function validateRegistration(values: {
  email: string;
  password: string;
  confirmPassword: string;
}): AuthFieldErrors {
  const emailError = validateEmail(values.email);
  const passwordError = validatePassword(values.password);
  const errors: AuthFieldErrors = {};
  if (emailError) errors.email = emailError;
  if (passwordError) errors.password = passwordError;
  if (!values.confirmPassword) errors.confirmPassword = "Vui lòng xác nhận mật khẩu.";
  else if (values.password !== values.confirmPassword)
    errors.confirmPassword = "Mật khẩu xác nhận không khớp.";
  return errors;
}

export function hasAuthErrors(errors: AuthFieldErrors): boolean {
  return Object.keys(errors).length > 0;
}
