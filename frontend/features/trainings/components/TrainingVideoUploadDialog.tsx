"use client";

import { Button, Modal, Progress } from "@douyinfe/semi-ui";
import type { DraftVideoProgress } from "../video-upload";

export type VideoUploadRow = {
  file: File;
  phase: DraftVideoProgress["phase"] | "waiting" | "failed";
  uploadedBytes: number;
  storedBytes?: number;
  message?: string;
  descriptor?: File;
};

function size(bytes: number) {
  return bytes >= 1024 ** 3
    ? `${(bytes / 1024 ** 3).toFixed(2)} GiB`
    : `${(bytes / 1024 ** 2).toFixed(1)} MiB`;
}

export function TrainingVideoUploadDialog({ rows, saving, uploading, copying, error, onClose, onRetry, onSave }: {
  rows: VideoUploadRow[]; saving: boolean; uploading: boolean; copying: boolean; error: string;
  onClose: () => void; onRetry: () => void; onSave: () => void;
}) {
  const ready = rows.filter(row => row.phase === "ready").length;
  const failed = rows.some(row => row.phase === "failed");
  const locked = saving;
  return <Modal title="视频上传进度" visible centered closable={!locked} maskClosable={false}
    closeOnEsc={!locked} onCancel={onClose} style={{ width: "min(740px, 95vw)" }}
    bodyStyle={{ maxHeight: "65vh", overflow: "auto" }} footer={
      <div className="flex flex-wrap items-center justify-end gap-2">
        <Button disabled={locked} onClick={onClose}>返回编辑</Button>
        {failed && <Button disabled={locked || uploading} onClick={onRetry}>重试失败视频</Button>}
        <Button theme="solid" type="primary" disabled={uploading || ready !== rows.length || saving} loading={saving} onClick={onSave}>
          {copying ? "保存培训副本" : "保存培训"}
        </Button>
      </div>
    }>
    <p className="mb-3 text-sm" role="status">{saving ? (copying ? "正在保存副本并复制所选原材料…" : "正在保存培训…") : `已就绪 ${ready} / ${rows.length} 个视频`}</p>
    <table className="w-full table-fixed text-left text-sm">
      <thead><tr className="border-b text-slate-500"><th className="w-1/3 py-2">视频</th><th className="py-2">上传到服务器</th><th className="py-2">写入对象存储</th></tr></thead>
      <tbody>{rows.map((row, index) => {
        const percent = (bytes: number) => Math.min(100, Math.floor(100 * bytes / row.file.size));
        const sent = percent(row.uploadedBytes);
        const written = percent(row.storedBytes ?? 0);
        const status = { waiting: "等待上传", uploading: "正在上传", storing: written === 100 ? "正在确认写入并准备缓存" : "正在写入存储", ready: "已就绪", failed: "上传失败" }[row.phase];
        return <tr key={index} className="border-b align-top">
          <td className="py-3 pr-4"><div className="break-all">{row.file.name}</div><span className="text-slate-500">{size(row.file.size)} · {status}</span>{row.message && <div role="alert" className="break-words text-red-600">{row.message}</div>}</td>
          <td className="py-3 pr-3">{sent}%<Progress percent={sent} aria-label={row.file.name + " 上传到服务器"} /></td>
          <td className="py-3">{written}%<Progress percent={written} aria-label={row.file.name + " 写入对象存储"} /></td>
        </tr>;
      })}</tbody>
    </table>
    {error && <p role="alert" className="mt-3 text-red-600">{error}</p>}
  </Modal>;
}
