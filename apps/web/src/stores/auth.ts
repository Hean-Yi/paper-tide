import { computed, reactive } from "vue";

import { ApiError, login as loginRequest } from "../lib/api";

export const AUTH_TOKEN_KEY = "review.auth.token";

export interface AuthUser {
  userId: number;
  username: string;
  roles: string[];
  expiresAt: number;
}

interface AuthState {
  token: string | null;
  user: AuthUser | null;
  initialized: boolean;
  loading: boolean;
  error: string | null;
}

interface JwtClaims {
  sub?: string;
  uid?: number;
  roles?: string[];
  exp?: number;
}

export const authState = reactive<AuthState>({
  token: null,
  user: null,
  initialized: false,
  loading: false,
  error: null
});

export const isAuthenticated = computed(() => Boolean(authState.token && authState.user));

export const primaryRole = computed(() => authState.user?.roles[0] ?? null);

export function initializeAuth(): void {
  const token = localStorage.getItem(AUTH_TOKEN_KEY);
  if (!token) {
    clearSession();
    authState.initialized = true;
    return;
  }

  const user = userFromToken(token);
  if (!user) {
    clearSession();
    localStorage.removeItem(AUTH_TOKEN_KEY);
    authState.initialized = true;
    return;
  }

  authState.token = token;
  authState.user = user;
  authState.error = null;
  authState.initialized = true;
}

export async function login(username: string, password: string): Promise<void> {
  authState.loading = true;
  authState.error = null;
  try {
    const response = await loginRequest(username, password);
    const user = userFromToken(response.token);
    if (!user) {
      throw new Error("The authentication response was invalid.");
    }
    localStorage.setItem(AUTH_TOKEN_KEY, response.token);
    authState.token = response.token;
    authState.user = user;
  } catch (error) {
    clearSession();
    localStorage.removeItem(AUTH_TOKEN_KEY);
    authState.error = error instanceof ApiError && error.status === 401
      ? "Invalid username or password."
      : "Unable to sign in. Please try again.";
    throw error;
  } finally {
    authState.loading = false;
  }
}

export function logout(): void {
  clearSession();
  localStorage.removeItem(AUTH_TOKEN_KEY);
}

export function hasRole(role: string): boolean {
  return authState.user?.roles.includes(role) ?? false;
}

export function resetAuthForTests(): void {
  clearSession();
  authState.initialized = false;
  localStorage.removeItem(AUTH_TOKEN_KEY);
}

function clearSession(): void {
  authState.token = null;
  authState.user = null;
  authState.error = null;
}

function userFromToken(token: string): AuthUser | null {
  const claims = decodeToken(token);
  if (!claims?.sub || typeof claims.uid !== "number" || !Array.isArray(claims.roles) || typeof claims.exp !== "number") {
    return null;
  }
  if (claims.exp * 1000 <= Date.now()) {
    return null;
  }
  return {
    userId: claims.uid,
    username: claims.sub,
    roles: claims.roles.map(String),
    expiresAt: claims.exp * 1000
  };
}

function decodeToken(token: string): JwtClaims | null {
  const [, payload] = token.split(".");
  if (!payload) {
    return null;
  }
  try {
    const normalized = payload.replaceAll("-", "+").replaceAll("_", "/");
    const padded = normalized.padEnd(Math.ceil(normalized.length / 4) * 4, "=");
    return JSON.parse(atob(padded)) as JwtClaims;
  } catch {
    return null;
  }
}
