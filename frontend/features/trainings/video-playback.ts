import { apiClient } from "@/lib/api/client";

export async function videoPlaybackUrl(path: string, videoId: number, signal: AbortSignal, token?: string) {
  const started = performance.now();
  console.info("[video-preview]", { event: "ticket_start", videoId });
  try {
  const ticket = await apiClient.post<string>(`${path}/playback`, token ? { token } : undefined, { signal });
  console.info("[video-preview]", { event: "ticket_ready", videoId, elapsedMs: Math.round(performance.now() - started) });
  return apiClient.instance.getUri({ url: `/video-playback/${videoId}`, params: { ticket } });
  } catch (error) {
    // Do not print Axios errors: their config may contain credentials and tickets.
    console.warn("[video-preview]", { event: signal.aborted ? "ticket_cancelled" : "ticket_failed", videoId,
      elapsedMs: Math.round(performance.now() - started) });
    throw error;
  }
}
