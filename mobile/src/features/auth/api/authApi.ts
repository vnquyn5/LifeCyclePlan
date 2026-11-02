import { postJson } from "@/services/api/httpClient";
import {
  LoginRequest,
  LoginResponse,
  RegisterRequest,
  RegisterResponse
} from "../types/auth.types";

export function register(request: RegisterRequest): Promise<RegisterResponse> {
  return postJson<RegisterResponse, RegisterRequest>("/api/v1/auth/register", request);
}

export function login(request: LoginRequest): Promise<LoginResponse> {
  return postJson<LoginResponse, LoginRequest>("/api/v1/auth/login", request);
}
