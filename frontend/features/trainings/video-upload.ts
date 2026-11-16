import { apiClient, ApiError } from "@/lib/api/client";

export const MAX_VIDEO_BYTES = 2 * 1024 ** 3;
const PART_BYTES = 8 * 1024 ** 2;
const sessions = new WeakMap<File, { id: string; uploaded: Set<number>; created: number }>();

export async function prepareVideoUpload(file: File, report: (message: string) => void) {
  if (file.size < 12 || file.size > MAX_VIDEO_BYTES) throw new Error("请选择 2 GiB 以内的 MP4 或 WebM 视频");
  const endpoint = "/management/video-uploads";
  let session = sessions.get(file);
  if (!session || Date.now() - session.created > 23 * 60 * 60_000) {
    const { id } = await apiClient.post<{ id: string }>(endpoint, { name: file.name, size: file.size });
    session = { id, uploaded: new Set(), created: Date.now() };
    sessions.set(file, session);
  }
  const { id, uploaded } = session;
  const count = Math.ceil(file.size / PART_BYTES);
  let next = 0;
  let failed = false;
  let uploadedBytes = 0;
  for (const index of uploaded) uploadedBytes += Math.min(PART_BYTES, file.size - index * PART_BYTES);
  const progress = () => report(`正在上传「${file.name}」 ${Math.floor(100 * uploadedBytes / file.size)}%`);
  progress();
  async function worker() {
    while (!failed && next < count) {
      const index = next++;
      if (uploaded.has(index)) continue;
      const part = file.slice(index * PART_BYTES, (index + 1) * PART_BYTES);
      for (let attempt = 0; ; attempt++) {
        try {
          await apiClient.put(`${endpoint}/${id}/parts/${index}`, part, {
            timeout: 300000, headers: { "Content-Type": "application/octet-stream" },
          });
          uploaded.add(index);
          uploadedBytes += part.size;
          progress();
          break;
        } catch (error) {
          const status = error instanceof ApiError ? error.status : undefined;
          if (status === 404 || status === 410) sessions.delete(file);
          if (failed || attempt >= 2 || (status && status < 500 && status !== 429)) {
            failed = true;
            throw error;
          }
          await new Promise(resolve => setTimeout(resolve, 500 * 2 ** attempt));
        }
      }
    }
  }
  const workers = await Promise.allSettled(Array.from({ length: Math.min(count, 3) }, worker));
  for (const result of workers) if (result.status === "rejected") throw result.reason;
  report("上传完成，正在保存…");
  return new File([id], file.name, { type: "application/vnd.aiquestion.video-upload" });
}

/** A server restart can invalidate even a fully uploaded file before the form is saved. */
export function forgetExpiredUploads(error: unknown, files: File[]) {
  if (error instanceof ApiError && error.code === "VIDEO_UPLOAD_EXPIRED") {
    for (const file of files) sessions.delete(file);
  }
}
