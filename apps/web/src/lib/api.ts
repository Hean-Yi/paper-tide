import { authState } from "../stores/auth";

const DEFAULT_API_BASE_URL = "/api";

export class ApiError extends Error {
  readonly status: number;
  readonly code?: string;
  readonly traceId?: string;

  constructor(status: number, message: string, code?: string, traceId?: string) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.code = code;
    this.traceId = traceId;
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
    throw await apiError(response);
  }

  if (response.status === 204 || response.headers?.get?.("Content-Length") === "0") {
    return undefined as T;
  }

  try {
    return await response.json() as T;
  } catch (error) {
    if (isEmptyJsonBodyError(error)) {
      return undefined as T;
    }
    throw error;
  }
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
    throw await apiError(response);
  }
  return response.blob();
}

export function login(username: string, password: string): Promise<LoginResponse> {
  return apiRequest<LoginResponse>("/auth/login", {
    method: "POST",
    json: { username, password }
  });
}

function isEmptyJsonBodyError(error: unknown): boolean {
  return error instanceof SyntaxError && /unexpected end of json input/i.test(error.message);
}

function apiBaseUrl(): string {
  return (import.meta.env.VITE_API_BASE_URL || DEFAULT_API_BASE_URL).replace(/\/$/, "");
}

async function apiError(response: Response): Promise<ApiError> {
  try {
    const body = await response.json();
    const message = typeof body?.message === "string" ? body.message : "Request failed";
    const code = typeof body?.code === "string" ? body.code : undefined;
    const traceId = typeof body?.traceId === "string" ? body.traceId : undefined;
    return new ApiError(response.status, message, code, traceId);
  } catch {
    return new ApiError(response.status, "Request failed");
  }
}
