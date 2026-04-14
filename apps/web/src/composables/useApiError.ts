import { ElMessage } from "element-plus";

import { ApiError } from "../lib/api";

export function apiErrorMessage(error: unknown, fallback: string): string {
  if (error instanceof ApiError) {
    if (error.status === 401) {
      return "Your session has expired. Sign in again.";
    }
    if (error.status === 403) {
      return "You do not have permission to perform this action.";
    }
    if (error.status === 409 && error.message) {
      return error.message;
    }
    if (error.status >= 500) {
      return "The service is temporarily unavailable. Try again later.";
    }
    return error.message || fallback;
  }
  if (error instanceof Error && error.message) {
    return error.message;
  }
  return fallback;
}

export function useApiError() {
  function showApiError(error: unknown, fallback: string) {
    ElMessage.error(apiErrorMessage(error, fallback));
  }

  return {
    showApiError
  };
}
