"use client";

import axios, { type AxiosInstance, type AxiosRequestConfig, type AxiosResponse } from "axios";
import { ApiError, normalizeApiError } from "./error";
import type { ApiResponse, DownloadOptions, UploadOptions } from "./types";

const baseURL = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:18080/api";
const TOKEN_KEY = "aiquestion_access_token";

const LEARNER_TOKEN_KEY = "aiquestion_learner_access_token";
function getToken(url = "") {
  if (typeof window === "undefined") return null;
  const learner = url.startsWith("/learner/") || url.startsWith("/public/exams/") || url.startsWith("/public/courses/") || /^\/trainings\/\d+\/survey/.test(url);
  return window.localStorage.getItem(learner ? LEARNER_TOKEN_KEY : TOKEN_KEY);
}
function extractFilename(response: AxiosResponse<Blob>, fallback: string) {
  const header = response.headers["content-disposition"] as string | undefined;
  const encoded = header?.match(/filename\*=UTF-8''([^;]+)/i);
  if (encoded) return decodeURIComponent(encoded[1]);
  const plain = header?.match(/filename="?([^";]+)"?/i);
  return plain?.[1] ?? fallback;
}

function appendDownloadDate(filename: string) {
  const now = new Date();
  const pad = (value: number) => String(value).padStart(2, "0");
  const date = `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`;
  const time = `${pad(now.getHours())}-${pad(now.getMinutes())}-${pad(now.getSeconds())}`;
  const dot = filename.lastIndexOf(".");
  const name = dot > 0 ? filename.slice(0, dot) : filename;
  const extension = dot > 0 ? filename.slice(dot) : "";
  return `${name}_${date}_${time}${extension}`;
}

class HttpClient {
  readonly instance: AxiosInstance;
  constructor() {
    this.instance = axios.create({ baseURL, timeout: 30_000 });
    this.instance.interceptors.request.use(config => { const token = getToken(config.url); if (token) config.headers.Authorization = `Bearer ${token}`; config.headers["X-Client"] = "aiquestion-web"; return config; });
    this.instance.interceptors.response.use(response => {
      const body = response.data as ApiResponse<unknown>;
      if (body && typeof body === "object" && "success" in body) { if (!body.success) throw new ApiError(body.message, { status: response.status, traceId: body.traceId }); response.data = body.data; }
      return response;
    }, async error => {
      // 文件下载使用 Blob 响应，失败时仍需读取后端返回的 JSON 错误信息。
      const data = error.response?.data;
      if (typeof Blob !== "undefined" && data instanceof Blob && data.type.includes("json")) {
        try {
          const text = await new Promise<string>((resolve, reject) => {
            const reader = new FileReader();
            reader.onload = () => resolve(String(reader.result));
            reader.onerror = () => reject(reader.error);
            reader.readAsText(data);
          });
          error.response.data = JSON.parse(text);
        } catch {
          // 无法解析时继续使用统一错误处理。
        }
      }
      if (error.response?.status === 401 && typeof window !== "undefined") window.dispatchEvent(new CustomEvent("aiquestion:unauthorized"));
      return Promise.reject(normalizeApiError(error));
    });
  }
  async get<T>(url: string, params?: Record<string, unknown>, config?: AxiosRequestConfig) { return (await this.instance.get<T>(url, { ...config, params })).data; }
  async post<T>(url: string, data?: unknown, config?: AxiosRequestConfig) { return (await this.instance.post<T>(url, data, config)).data; }
  async put<T>(url: string, data?: unknown, config?: AxiosRequestConfig) { return (await this.instance.put<T>(url, data, config)).data; }
  async patch<T>(url: string, data?: unknown, config?: AxiosRequestConfig) { return (await this.instance.patch<T>(url, data, config)).data; }
  async delete<T>(url: string, config?: AxiosRequestConfig) { return (await this.instance.delete<T>(url, config)).data; }
  async upload<T>(url: string, files: File | File[], options: UploadOptions = {}) {
    const form = new FormData(); const list = Array.isArray(files) ? files : [files]; list.forEach(file => form.append("files", file));
    if (options.scene) form.append("scene", options.scene); if (options.maxSizeMb !== undefined) form.append("maxSizeMb", String(options.maxSizeMb)); if (options.maxCount !== undefined) form.append("maxCount", String(options.maxCount)); if (options.allowedExtensions?.length) form.append("allowedExtensions", options.allowedExtensions.join(","));
    const response = await this.instance.post<T>(url, form, { ...options.config, headers: { ...options.config?.headers, "Content-Type": "multipart/form-data" }, onUploadProgress: event => { options.onProgress?.(event.total ? Math.round((event.loaded / event.total) * 100) : 0, event); } });
    return response.data;
  }
  async uploadForm<T>(url: string, fields: Record<string, string>, fileField: string, file: File) {
    const form = new FormData(); Object.entries(fields).forEach(([key, value]) => form.append(key, value)); form.append(fileField, file);
    return (await this.instance.post<T>(url, form, { headers: { "Content-Type": "multipart/form-data" } })).data;
  }
  async download(url: string, options: DownloadOptions = {}) {
    const response = await this.instance.get<Blob>(url, { ...options.config, params: options.params, responseType: "blob" });
    const filename = appendDownloadDate(extractFilename(response, options.filename ?? "download")); const objectUrl = URL.createObjectURL(response.data); const anchor = document.createElement("a"); anchor.href = objectUrl; anchor.download = filename; anchor.click(); URL.revokeObjectURL(objectUrl); return response.data;
  }
}

export const http = new HttpClient();
export { TOKEN_KEY, LEARNER_TOKEN_KEY };
