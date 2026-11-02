import { describe, expect, it } from "@jest/globals";
import {
  hasAuthErrors,
  validateEmail,
  validateLogin,
  validateRegistration
} from "./authValidation";

describe("auth form validation", () => {
  it("accepts valid email and trims surrounding whitespace for validation", () => {
    expect(validateEmail("  planner@example.com  ")).toBeUndefined();
  });

  it("rejects empty and malformed email addresses", () => {
    expect(validateEmail(" ")).toBe("Vui lòng nhập email.");
    expect(validateEmail("planner.example.com")).toBe("Email chưa đúng định dạng.");
  });

  it("requires a login password without imposing registration policy", () => {
    const errors = validateLogin({ email: "person@example.com", password: "x" });
    expect(errors.password).toBeUndefined();
    expect(hasAuthErrors(errors)).toBe(false);
    expect(validateLogin({ email: "person@example.com", password: "" }).password).toBe(
      "Vui lòng nhập mật khẩu."
    );
  });

  it("enforces the backend registration password length", () => {
    expect(
      validateRegistration({ email: "a@b.com", password: "short", confirmPassword: "short" })
        .password
    ).toBe("Mật khẩu phải có từ 8 đến 72 ký tự.");
    expect(
      validateRegistration({
        email: "a@b.com",
        password: "a".repeat(73),
        confirmPassword: "a".repeat(73)
      }).password
    ).toBe("Mật khẩu phải có từ 8 đến 72 ký tự.");
  });

  it("requires a matching password confirmation", () => {
    expect(
      validateRegistration({ email: "a@b.com", password: "Password8", confirmPassword: "" })
        .confirmPassword
    ).toBe("Vui lòng xác nhận mật khẩu.");
    expect(
      validateRegistration({
        email: "a@b.com",
        password: "Password8",
        confirmPassword: "Password9"
      }).confirmPassword
    ).toBe("Mật khẩu xác nhận không khớp.");
  });

  it("rejects invalid registration without submitting", () => {
    const errors = validateRegistration({ email: "bad", password: "x", confirmPassword: "y" });
    expect(hasAuthErrors(errors)).toBe(true);
    expect(errors.email).toBeDefined();
    expect(errors.password).toBeDefined();
    expect(errors.confirmPassword).toBeDefined();
  });
});
