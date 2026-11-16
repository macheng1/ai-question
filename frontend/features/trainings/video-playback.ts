import { apiClient } from "@/lib/api/client";

export async function videoPlaybackUrl(path: string, videoId: number, signal: AbortSignal, token?: string) {
  const ticket = await apiClient.post<string>(`${path}/playback`, token ? { token } : undefined, { signal });
  return apiClient.instance.getUri({ url: `/video-playback/${videoId}`, params: { ticket } });
}
