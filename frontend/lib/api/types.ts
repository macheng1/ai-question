import type { AxiosRequestConfig, AxiosProgressEvent } from "axios";

export type ApiResponse<T> = { success: boolean; data: T; message: string; traceId?: string };
export type UploadOptions = {
  scene?: string;
  maxSizeMb?: number;
  maxCount?: number;
  allowedExtensions?: string[];
  onProgress?: (progress: number, event: AxiosProgressEvent) => void;
  config?: AxiosRequestConfig;
};
export type DownloadOptions = { params?: Record<string, unknown>; filename?: string; config?: AxiosRequestConfig };
