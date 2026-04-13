import { authState } from "../stores/auth";

const DEFAULT_API_BASE_URL = "/api";

export class ApiError extends Error {
  readonly status: number;

  constructor(status: number, message: string) {
    super(message);
    this.name = "ApiError";
    this.status = status;
  }
}

export interface LoginResponse {
  token: string;
}

export interface ApiRequestOptions extends RequestInit {
  json?: unknown;
}

export async function apiRequest<T = unknown>(path: string, options: ApiRequestOptions = {}): Promise<T> {
  const { json, headers, ...requestOptions } = options;
  const requestHeaders = new Headers(headers);

  if (json !== undefined && !requestHeaders.has("Content-Type")) {
    requestHeaders.set("Content-Type", "application/json");
  }
  if (authState.token && !requestHeaders.has("Authorization")) {
    requestHeaders.set("Authorization", `Bearer ${authState.token}`);
  }

  const response = await fetch(`${apiBaseUrl()}${path}`, {
    ...requestOptions,
    headers: Object.fromEntries(requestHeaders.entries()),
    body: json === undefined ? requestOptions.body : JSON.stringify(json)
  });

  if (!response.ok) {
    throw new ApiError(response.status, await errorMessage(response));
  }

  if (response.status === 204) {
    return undefined as T;
  }
  return response.json() as Promise<T>;
}

export async function apiBlob(path: string, options: RequestInit = {}): Promise<Blob> {
  const requestHeaders = new Headers(options.headers);
  if (authState.token && !requestHeaders.has("Authorization")) {
    requestHeaders.set("Authorization", `Bearer ${authState.token}`);
  }
  const response = await fetch(`${apiBaseUrl()}${path}`, {
    ...options,
    headers: Object.fromEntries(requestHeaders.entries())
  });
  if (!response.ok) {
    throw new ApiError(response.status, await errorMessage(response));
  }
  return response.blob();
}

export function login(username: string, password: string): Promise<LoginResponse> {
  return apiRequest<LoginResponse>("/auth/login", {
    method: "POST",
    json: { username, password }
  });
}

function apiBaseUrl(): string {
  return (import.meta.env.VITE_API_BASE_URL || DEFAULT_API_BASE_URL).replace(/\/$/, "");
}

async function errorMessage(response: Response): Promise<string> {
  try {
    const body = await response.json();
    if (typeof body?.message === "string") {
      return body.message;
    }
    if (typeof body?.detail === "string") {
      return body.detail;
    }
  } catch {
    return response.statusText || "Request failed";
  }
  return response.statusText || "Request failed";
}
