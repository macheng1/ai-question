import axios from "axios";

export class ApiError extends Error {
  readonly status?: number;
  readonly code?: string;
  readonly traceId?: string;
  readonly details?: unknown;
  constructor(message: string, options?: { status?: number; code?: string; traceId?: string; details?: unknown }) {
    super(message); this.name = "ApiError"; this.status = options?.status; this.code = options?.code; this.traceId = options?.traceId; this.details = options?.details;
  }
}

export function normalizeApiError(error: unknown): ApiError {
  if (error instanceof ApiError) return error;
  if (axios.isAxiosError(error)) {
    const payload = error.response?.data as { message?: string; traceId?: string } | undefined;
    return new ApiError(payload?.message ?? (error.code === "ECONNABORTED" ? "请求超时，请稍后重试" : "网络请求失败"), { status: error.response?.status, traceId: payload?.traceId, details: payload });
  }
  return new ApiError(error instanceof Error ? error.message : "未知错误");
}
