export { http as apiClient } from "./http";
export { ApiError, normalizeApiError } from "./error";
export type * from "./types";

/** 兼容旧接口调用，新的业务代码直接使用 apiClient。 */
export function apiRequest<T>(path: string, init?: RequestInit): Promise<T> {
  const method = init?.method?.toUpperCase() ?? "GET";
  if (method === "POST") return import("./http").then(({ http }) => http.post<T>(path, init?.body ? JSON.parse(String(init.body)) : undefined));
  if (method === "PUT") return import("./http").then(({ http }) => http.put<T>(path, init?.body ? JSON.parse(String(init.body)) : undefined));
  if (method === "DELETE") return import("./http").then(({ http }) => http.delete<T>(path));
  const [url, query] = path.split("?"); return import("./http").then(({ http }) => http.get<T>(url, query ? Object.fromEntries(new URLSearchParams(query)) : undefined));
}
