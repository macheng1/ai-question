"use client";

import { Button, Modal, Progress } from "@douyinfe/semi-ui";
import type { DraftVideoProgress } from "../video-upload";

export type VideoUploadRow = {
  file: File;
  phase: DraftVideoProgress["phase"] | "waiting" | "failed";
  uploadedBytes: number;
  storedBytes?: number;
  confirmedBytes?: number;
  waitingForStorage?: boolean;
  transport?: DraftVideoProgress["transport"];
  message?: string;
};

function size(bytes: number) {
  return bytes >= 1024 ** 3
    ? `${(bytes / 1024 ** 3).toFixed(2)} GiB`
    : `${(bytes / 1024 ** 2).toFixed(1)} MiB`;
}

export function VideoUploadStatus({ row }: { row: VideoUploadRow }) {
  const percent = row.phase === "ready" ? 100 : Math.min(99, Math.floor(100 * (row.confirmedBytes ?? 0) / row.file.size));
  const status = {
    waiting: "等待上传",
    uploading: row.waitingForStorage ? "正在上传，等待服务器响应 ·" : "正在上传",
    storing: "正在完成上传…",
    ready: "上传完成",
    failed: "上传失败",
  }[row.phase];
  return <div className="min-w-32 text-sm">
    <span>{status}{row.phase === "uploading" && ` ${percent}%`}</span>
    <Progress percent={percent} aria-label={row.file.name + " " + status} />
    {row.message && <div role="alert" className="break-words text-red-600">{row.message}</div>}
  </div>;
}

export function TrainingVideoUploadDialog({ rows, saving, waiting, copying, error, onClose, onRetry }: {
  rows: VideoUploadRow[]; saving: boolean; waiting: boolean; copying: boolean; error: string;
  onClose: () => void; onRetry: () => void;
}) {
  const ready = rows.filter(row => row.phase === "ready").length;
  const failed = rows.some(row => row.phase === "failed");
  const locked = saving;
  return <Modal title="视频上传进度" visible centered closable={!locked} maskClosable={false}
    closeOnEsc={!locked} onCancel={onClose} style={{ width: "min(740px, 95vw)" }}
    bodyStyle={{ maxHeight: "65vh", overflow: "auto" }} footer={
      <div className="flex flex-wrap items-center justify-end gap-2">
        <Button disabled={locked} onClick={onClose}>返回编辑</Button>
        {error && <Button theme="solid" disabled={locked || waiting} onClick={onRetry}>{failed ? "重试失败视频并保存" : "重新保存"}</Button>}
      </div>
    }>
    <p className="mb-3 text-sm" role="status">{saving ? (copying ? "正在保存副本并复制所选原材料…" : "正在保存培训…") : `已就绪 ${ready} / ${rows.length} 个视频${waiting ? "，全部就绪后将自动保存。" : ""}`}</p>
    <table className="w-full table-fixed text-left text-sm">
      <thead><tr className="border-b text-slate-500"><th className="w-1/2 py-2">视频</th><th className="py-2">上传进度</th></tr></thead>
      <tbody>{rows.map((row, index) => <tr key={index} className="border-b align-top">
        <td className="py-3 pr-4"><div className="break-all">{row.file.name}</div><span className="text-slate-500">{size(row.file.size)}</span></td>
        <td className="py-3"><VideoUploadStatus row={row} /></td>
      </tr>)}</tbody>
    </table>
    {error && <p role="alert" className="mt-3 text-red-600">{error}</p>}
  </Modal>;
}
