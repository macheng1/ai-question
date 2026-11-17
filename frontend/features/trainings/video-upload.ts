import axios from "axios";
import { apiClient, ApiError } from "@/lib/api/client";

const endpoint = "/management/video-uploads";
type UploadSession = {
  id: string;
  partSize: number;
  browserDirect: boolean;
  uploaded: Set<number>;
  created: number;
};
const sessions = new WeakMap<File, UploadSession>();

export type DraftVideoProgress = {
  phase: "uploading" | "storing" | "ready";
  uploadedBytes: number;
  totalBytes: number;
  storedBytes?: number;
  transport?: "object";
  confirmedBytes?: number;
  waitingForStorage?: boolean;
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

async function startSession(file: File, signal?: AbortSignal): Promise<UploadSession> {
  const info = { name: file.name, size: file.size };
  const direct = await apiClient.post<{ id: string | null; partSize: number; browserDirect: boolean }>(
    endpoint + "/direct", info, { signal }
  );
  if (!direct.id || direct.partSize <= 0) {
    throw new Error("对象存储分片上传不可用，请检查存储配置。");
  }
  return { id: direct.id, partSize: direct.partSize, browserDirect: direct.browserDirect, created: Date.now(), uploaded: new Set<number>() };
}

async function sendPart(session: UploadSession, index: number, part: Blob, signal: AbortSignal | undefined, progress: (bytes: number) => void) {
  const path = `${endpoint}/direct/${session.id}/parts/${index}`;
  const config = {
    signal, timeout: 300000, headers: { "Content-Type": "application/octet-stream" },
    onUploadProgress: (event: { loaded: number }) => progress(Math.min(event.loaded, part.size)),
  };
  if (!session.browserDirect) {
    await apiClient.put(path, part, config);
    return;
  }
  const { url } = await apiClient.post<{ url: string }>(path, undefined, { signal });
  try {
    await axios.put(url, part, { ...config, withCredentials: false });
  } catch (error) {
    checkCancelled(signal);
    if (axios.isAxiosError(error) && (!error.response || error.response.status === 403)) {
      throw new Error("对象存储直传失败，请检查网络、上传地址和存储桶的跨域 PUT 规则后重试。");
    }
    throw error;
  }
}

async function uploadParts(file: File, report: (bytes: number, confirmedBytes: number, waitingForStorage: boolean) => void, signal?: AbortSignal) {
  checkCancelled(signal);
  if (file.size < 12) throw new Error("请选择有效的 MP4 或 WebM 视频");
  let session = sessions.get(file);
  if (!session || Date.now() - session.created > 23 * 60 * 60_000) {
    session = await startSession(file, signal);
    sessions.set(file, session);
  }
  const active = session;
  const { partSize, uploaded } = active;
  const count = Math.ceil(file.size / partSize);
  let next = 0;
  let failed = false;
  let confirmedBytes = 0;
  for (const index of uploaded) confirmedBytes += Math.min(partSize, file.size - index * partSize);
  const inFlight = new Map<number, number>();
  function progress() {
    if (signal?.aborted) return;
    let bytes = confirmedBytes;
    for (const loaded of inFlight.values()) bytes += loaded;
    const waitingForStorage = inFlight.size > 0 && Array.from(inFlight).every(([index, loaded]) =>
      loaded >= Math.min(partSize, file.size - index * partSize));
    report(bytes, confirmedBytes, waitingForStorage);
  }
  progress();

  async function worker() {
    while (!failed && next < count) {
      checkCancelled(signal);
      const index = next++;
      if (uploaded.has(index)) continue;
      const part = file.slice(index * partSize, (index + 1) * partSize);
      for (let attempt = 0; !failed; attempt++) {
        let sending = true;
        try {
          await sendPart(active, index, part, signal, bytes => {
            if (!sending) return;
            inFlight.set(index, bytes);
            progress();
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
  return active;
}

function descriptor(file: File, id: string) {
  return new File([id], file.name, { type: "application/vnd.aiquestion.video-upload" });
}

export async function prepareDraftVideo(
  file: File, onProgress: (progress: DraftVideoProgress) => void, signal?: AbortSignal
): Promise<File> {
  checkCancelled(signal);
  const totalBytes = file.size;
  onProgress({ phase: "uploading", transport: "object", uploadedBytes: 0, totalBytes });
  const session = await uploadParts(file, (uploadedBytes, confirmedBytes, waitingForStorage) => onProgress({
    phase: "uploading", uploadedBytes, confirmedBytes, waitingForStorage, totalBytes, transport: "object",
  }), signal);
  const { id } = session;
  checkCancelled(signal);
  onProgress({ phase: "storing", transport: "object", uploadedBytes: totalBytes, totalBytes, storedBytes: totalBytes });
  try {
    await apiClient.post(`${endpoint}/direct/${id}/complete`, undefined, { timeout: 0, signal });
  } catch (error) {
    if (error instanceof ApiError && (error.status === 404 || error.status === 410 || error.code === "VIDEO_UPLOAD_EXPIRED")) sessions.delete(file);
    throw error;
  }
  checkCancelled(signal);
  onProgress({ phase: "ready", transport: "object", uploadedBytes: totalBytes, totalBytes, storedBytes: totalBytes });
  return descriptor(file, id);
}

/** A server restart can invalidate even a fully uploaded file before the form is saved. */
export function forgetExpiredUploads(error: unknown, files: File[]) {
  if (error instanceof ApiError && error.code === "VIDEO_UPLOAD_EXPIRED") {
    for (const file of files) sessions.delete(file);
  }
}
