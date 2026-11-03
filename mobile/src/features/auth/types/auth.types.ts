export type OnboardingStatus = "NOT_STARTED" | "IN_PROGRESS" | "COMPLETED";

export interface RegisterRequest {
  email: string;
  password: string;
  confirmPassword: string;
}

export interface RegisterResponse {
  userId: string;
  email: string;
  onboardingStatus: OnboardingStatus;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface AuthUser {
  userId: string;
  email: string;
  onboardingStatus: OnboardingStatus;
}

export interface TokenResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: "Bearer";
  expiresIn: number;
}

export interface RefreshTokenRequest {
  refreshToken: string;
}

export interface LogoutRequest {
  refreshToken: string;
}

export interface RefreshResponse {
  tokens: TokenResponse;
}

export interface LoginResponse {
  user: AuthUser;
  tokens: TokenResponse;
}
