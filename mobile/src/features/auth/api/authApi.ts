import { postJson } from "@/services/api/transport";
import {
  LoginRequest,
  LoginResponse,
  RefreshResponse,
  RefreshTokenRequest,
  RegisterRequest,
  RegisterResponse
} from "../types/auth.types";

export function register(request: RegisterRequest): Promise<RegisterResponse> {
  return postJson<RegisterResponse, RegisterRequest>("/api/v1/auth/register", request);
}

export function login(request: LoginRequest): Promise<LoginResponse> {
  return postJson<LoginResponse, LoginRequest>("/api/v1/auth/login", request);
}

export function refreshSession(request: RefreshTokenRequest): Promise<RefreshResponse> {
  return postJson<RefreshResponse, RefreshTokenRequest>("/api/v1/auth/refresh", request);
}
