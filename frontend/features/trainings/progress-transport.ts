import { apiClient, ApiError } from "@/lib/api/client";

let sequence = 0;
const writer = `${Date.now().toString(36)}-${Math.random().toString(36).slice(2)}`;
// Correlation only; authentication remains the learner credential.
export const nextProgressRequestId = () => `${writer}-${++sequence}`;

/** Small progress payloads may finish after pagehide; this is best-effort, not an offline guarantee. */
export async function saveLearningProgress<T>(path: string, payload: unknown): Promise<T> {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 15000);
  try {
    const response = await fetch(`${apiClient.instance.defaults.baseURL || "/api"}${path}`, {
      method: "PUT", keepalive: true, signal: controller.signal,
      headers: { "Content-Type": "application/json", "X-Client": "aiquestion-web",
        Authorization: `Bearer ${window.localStorage.getItem("aiquestion_learner_access_token") || ""}` },
      body: JSON.stringify(payload),
    });
    const body = await response.json();
    if (!response.ok || !body.success) {
      if (response.status === 401) window.dispatchEvent(new CustomEvent("aiquestion:unauthorized"));
      throw new ApiError(body.message || "进度保存失败，请重试", { status: response.status, code: body.code });
    }
    return body.data as T;
  } finally { clearTimeout(timeout); }
}
