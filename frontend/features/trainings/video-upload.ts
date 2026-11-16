import { apiClient, ApiError } from "@/lib/api/client";

export const MAX_VIDEO_BYTES = 2 * 1024 ** 3;
const PART_BYTES = 8 * 1024 ** 2;
const endpoint = "/management/video-uploads";
const sessions = new WeakMap<File, { id: string; uploaded: Set<number>; created: number }>();

export type DraftVideoProgress = {
  phase: "uploading" | "storing" | "ready";
  uploadedBytes: number;
  totalBytes: number;
  storedBytes?: number;
};

function checkCancelled(signal?: AbortSignal) {
  if (signal?.aborted) throw new DOMException("上传已取消", "AbortError");
}

function waitForRetry(delay: number, signal?: AbortSignal) {
  checkCancelled(signal);
  return new Promise<void>((resolve, reject) => {
    function cancel() {
      clearTimeout(timer);
      reject(new DOMException("上传已取消", "AbortError"));
    }
    const timer = setTimeout(() => {
      signal?.removeEventListener("abort", cancel);
      resolve();
    }, delay);
    signal?.addEventListener("abort", cancel, { once: true });
  });
}

async function uploadParts(file: File, report: (bytes: number) => void, signal?: AbortSignal) {
  checkCancelled(signal);
  if (file.size < 12 || file.size > MAX_VIDEO_BYTES) throw new Error("请选择 2 GiB 以内的 MP4 或 WebM 视频");
  let session = sessions.get(file);
  if (!session || Date.now() - session.created > 23 * 60 * 60_000) {
    const { id } = await apiClient.post<{ id: string }>(endpoint, { name: file.name, size: file.size }, { signal });
    session = { id, uploaded: new Set(), created: Date.now() };
    sessions.set(file, session);
  }
  const { id, uploaded } = session;
  const count = Math.ceil(file.size / PART_BYTES);
  let next = 0;
  let failed = false;
  let confirmedBytes = 0;
  for (const index of uploaded) confirmedBytes += Math.min(PART_BYTES, file.size - index * PART_BYTES);
  const inFlight = new Map<number, number>();
  function progress() {
    if (signal?.aborted) return;
    let bytes = confirmedBytes;
    for (const loaded of inFlight.values()) bytes += loaded;
    report(bytes);
  }
  progress();

  async function worker() {
    while (!failed && next < count) {
      checkCancelled(signal);
      const index = next++;
      if (uploaded.has(index)) continue;
      const part = file.slice(index * PART_BYTES, (index + 1) * PART_BYTES);
      for (let attempt = 0; !failed; attempt++) {
        let sending = true;
        try {
          await apiClient.put(`${endpoint}/${id}/parts/${index}`, part, {
            signal, timeout: 300000,
            headers: { "Content-Type": "application/octet-stream" },
            onUploadProgress: event => {
              if (!sending) return;
              inFlight.set(index, Math.min(part.size, event.loaded));
              progress();
            },
          });
          sending = false;
          inFlight.delete(index);
          uploaded.add(index);
          confirmedBytes += part.size;
          progress();
          break;
        } catch (error) {
          sending = false;
          inFlight.delete(index);
          progress();
          checkCancelled(signal);
          const status = error instanceof ApiError ? error.status : undefined;
          if (status === 404 || status === 410) sessions.delete(file);
          if (failed || attempt >= 2 || (status && status < 500 && status !== 429)) {
            failed = true;
            throw error;
          }
          await waitForRetry(500 * 2 ** attempt, signal);
        }
      }
    }
  }
  const workers = await Promise.allSettled(Array.from({ length: Math.min(count, 3) }, worker));
  checkCancelled(signal);
  for (const result of workers) if (result.status === "rejected") throw result.reason;
  return id;
}

function descriptor(file: File, id: string) {
  return new File([id], file.name, { type: "application/vnd.aiquestion.video-upload" });
}

export async function prepareVideoUpload(file: File, report: (message: string) => void) {
  const id = await uploadParts(file, bytes => report(`正在上传「${file.name}」 ${Math.floor(100 * bytes / file.size)}%`));
  report("上传完成，正在保存…");
  return descriptor(file, id);
}

export async function prepareDraftVideo(
  file: File, onProgress: (progress: DraftVideoProgress) => void, signal?: AbortSignal
): Promise<File> {
  checkCancelled(signal);
  const totalBytes = file.size;
  onProgress({ phase: "uploading", uploadedBytes: 0, totalBytes });
  const id = await uploadParts(file, uploadedBytes => onProgress({ phase: "uploading", uploadedBytes, totalBytes }), signal);
  checkCancelled(signal);
  const base = `${endpoint}/${id}`;
  const controller = new AbortController();
  let timer: ReturnType<typeof setTimeout>;
  function storing(storedBytes: number) {
    onProgress({ phase: "storing", uploadedBytes: totalBytes, totalBytes, storedBytes });
  }
  async function poll() {
    try {
      const result = await apiClient.get<{ storedBytes: number; totalBytes: number }>(
        `${base}/progress`, undefined, { signal: controller.signal, timeout: 5000 }
      );
      if (!controller.signal.aborted && result.totalBytes === totalBytes && Number.isFinite(result.storedBytes)) {
        storing(Math.max(0, Math.min(totalBytes, result.storedBytes)));
      }
    } catch {
      // A missed progress sample does not change the outcome of the storage request.
    } finally {
      if (!controller.signal.aborted) timer = setTimeout(poll, 1000);
    }
  }

  function stopPolling() {
    clearTimeout(timer);
    controller.abort();
  }
  signal?.addEventListener("abort", stopPolling, { once: true });
  try {
    storing(0);
    checkCancelled(signal);
    timer = setTimeout(poll, 1000);
    await apiClient.post(`${base}/prepare`, undefined, { timeout: 0, signal });
    checkCancelled(signal);
  } catch (error) {
    checkCancelled(signal);
    if (error instanceof ApiError && (error.status === 404 || error.status === 410)) sessions.delete(file);
    throw error;
  } finally {
    signal?.removeEventListener("abort", stopPolling);
    stopPolling();
  }
  onProgress({ phase: "ready", uploadedBytes: totalBytes, totalBytes, storedBytes: totalBytes });
  return descriptor(file, id);
}

/** A server restart can invalidate even a fully uploaded file before the form is saved. */
export function forgetExpiredUploads(error: unknown, files: File[]) {
  if (error instanceof ApiError && error.code === "VIDEO_UPLOAD_EXPIRED") {
    for (const file of files) sessions.delete(file);
  }
}
