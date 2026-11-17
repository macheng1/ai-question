"use client";
import type { FileItem } from "@douyinfe/semi-ui/lib/es/upload";
import { useEffect, useState } from "react";
import { videoPlaybackUrl } from "../video-playback";
import { apiClient } from "@/lib/api/client";
import { Button, Layout, Modal, Spin, Toast, Upload, VideoPlayer } from "@douyinfe/semi-ui";
import { forgetExpiredUploads, prepareDraftVideo } from "../video-upload";
type Video = { id: number; title: string };
export function TrainingVideos({ id, readOnly = false }: { id: number; readOnly?: boolean }) {
  const [videos, setVideos] = useState<Video[]>([]);
  const [busy, setBusy] = useState(false);
  const [uploadFiles, setUploadFiles] = useState<FileItem[]>([]);
  const [uploadStatus, setUploadStatus] = useState("");
  const [error, setError] = useState("");
  const [preview, setPreview] = useState<Video | null>(null);
  const [previewUrl, setPreviewUrl] = useState("");
  const [previewError, setPreviewError] = useState("");
  const [previewLoading, setPreviewLoading] = useState(false);
  const [previewAttempt, setPreviewAttempt] = useState(0);
  useEffect(() => {
    setPreviewUrl(""); setPreviewError("");
    if (!preview) { setPreviewLoading(false); return; }
    const controller = new AbortController();
    setPreviewLoading(true);
    videoPlaybackUrl(`/trainings/${id}/videos/${preview.id}`, preview.id, controller.signal).then(url => {
      if (!controller.signal.aborted) setPreviewUrl(url);
    }).catch(e => {
      if (!controller.signal.aborted) setPreviewError(e instanceof Error ? e.message : "视频加载失败");
    }).finally(() => { if (!controller.signal.aborted) setPreviewLoading(false); });
    return () => controller.abort();
  }, [id, preview, previewAttempt]);
  const load = async () => { const data = await apiClient.get<{ videos: Video[] }>(`/trainings/${id}`); setVideos(data.videos); setError(""); };
  useEffect(() => { apiClient.get<{ videos: Video[] }>(`/trainings/${id}`).then(data => setVideos(data.videos)).catch(e => setError(e.message)); }, [id]);
  const move = async (index: number, offset: number) => {
    const ordered = [...videos]; [ordered[index], ordered[index + offset]] = [ordered[index + offset], ordered[index]];
    setBusy(true);
    try { await apiClient.put(`/management/trainings/${id}/videos/order`, ordered.map(v => v.id)); setVideos(ordered); Toast.success("顺序已保存"); }
    catch (e) { Toast.error(e instanceof Error ? e.message : "排序失败"); await load().catch(() => {}); }
    finally { setBusy(false); }
  };
  const remove = (video: Video) => Modal.confirm({ title: "删除视频", content: `确定删除“${video.title}”？已有学习记录的视频不能删除。`, onOk: async () => {
    setBusy(true); try { await apiClient.delete(`/management/trainings/${id}/videos/${video.id}`); await load(); Toast.success("视频已删除"); }
    catch (e) { Toast.error(e instanceof Error ? e.message : "删除失败"); } finally { setBusy(false); }
  }});
  return <Layout.Content className="flex flex-col gap-3"><p className="text-sm">上传 MP4 / WebM 视频。上传、排序和删除立即生效。</p>
    {error && <p role="alert" className="text-red-600">{error}<Button theme="borderless" onClick={() => load().catch(e => setError(e.message))}>重试</Button></p>}
    <ul className="divide-y divide-slate-100">{videos.map((v, i) => <li key={v.id} className="flex flex-wrap items-center justify-between gap-3 py-3"><span className="break-all">{i + 1}. {v.title}</span><span className="flex gap-3"><Button onClick={() => setPreview(v)}>预览</Button><Button disabled={readOnly || busy || i === 0} onClick={() => move(i, -1)}>上移</Button><Button disabled={readOnly || busy || i === videos.length - 1} onClick={() => move(i, 1)}>下移</Button><Button disabled={readOnly || busy} type="danger" onClick={() => remove(v)}>删除</Button></span></li>)}</ul>
    {!videos.length && !uploadFiles.length && !error && <p className="text-slate-500">暂无视频。</p>}
    <Upload aria-label="上传培训视频" disabled={readOnly || busy} accept=".mp4,.webm"
      fileList={uploadFiles} showClear={false} showRetry={!busy && !readOnly}
      beforeRemove={() => !busy} onChange={({ fileList }) => setUploadFiles(fileList.filter(file => file.status !== "success"))}
      beforeUpload={({ file }) => {
        return true;
      }}
      customRequest={async ({ fileInstance, onProgress, onSuccess, onError }) => {
        setBusy(true);
        try {
          const descriptor = await prepareDraftVideo(fileInstance, ({ phase, totalBytes, confirmedBytes = 0, waitingForStorage }) => {
            onProgress({ total: totalBytes, loaded: phase === "uploading" ? confirmedBytes : totalBytes });
            if (phase === "uploading") setUploadStatus(`${waitingForStorage ? "正在等待存储确认" : "正在上传视频"} · 已确认 ${Math.floor(confirmedBytes / totalBytes * 100)}%`);
            if (phase === "storing") setUploadStatus("正在确认视频完整性…");
            if (phase === "ready") setUploadStatus("正在保存到培训…");
          });
          const form = new FormData();
          form.append("file", descriptor);
          const saved = await apiClient.post<{ videos: Video[] }>(`/management/trainings/${id}/videos`, form, { timeout: 0 });
          setVideos(saved.videos);
          setError("");
          onSuccess({});
          Toast.success("视频已上传");
        } catch (error) {
          forgetExpiredUploads(error, [fileInstance]);
          onError({});
          Toast.error(error instanceof Error ? error.message : "上传失败");
        } finally {
          setBusy(false);
          setUploadStatus("");
        }
      }}>
      <Button disabled={readOnly || busy}>上传培训视频</Button>
    </Upload>
    {uploadStatus && <p role="status" className="text-sm">{uploadStatus}</p>}
    <Modal centered bodyStyle={{ maxHeight: "calc(100dvh - 240px)", overflowY: "auto" }} title={preview ? `视频预览：${preview.title}` : "视频预览"} visible={!!preview} onCancel={() => setPreview(null)} footer={null} style={{ width: "min(900px, 94vw)" }}>
      {preview && <div className="flex flex-col gap-3">
        {previewLoading && <div role="status" className="flex items-center gap-2 py-10"><Spin />正在加载视频…</div>}
        {previewError && <p role="alert" className="text-red-600">{previewError}</p>}
        {previewUrl && !previewError && <VideoPlayer key={previewUrl} src={previewUrl} width="100%" height="min(560px, 60dvh)" style={{ display: "block", maxHeight: "calc(100dvh - 180px)", borderRadius: 8, overflow: "hidden" }} autoPlay={false} clickToPlay muted={false} defaultPlaybackRate={1} volume={80} theme="dark" playbackRateList={[{ label: "1x", value: 1 }, { label: "1.5x", value: 1.5 }, { label: "2x", value: 2 }]} ref={element => { if (element) { element.onerror = () => setPreviewError("视频无法播放，请重试或检查视频格式。"); } }} />}
        {previewError && <Button onClick={() => setPreviewAttempt(value => value + 1)}>重新加载</Button>}
      </div>}
    </Modal>
  </Layout.Content>;
}
