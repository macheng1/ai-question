"use client";

import { useRef, useState } from "react";
import { Button, Progress, Upload } from "@douyinfe/semi-ui";
import { apiClient } from "@/lib/api/client";

type UploadRow = { file: File; percent: number; phase: "queued" | "uploading" | "processing" | "failed"; error?: string };

export function MaterialUploader<T>({ endpoint, disabled, validate, onUploaded, onPending }: {
  endpoint: string;
  disabled: boolean;
  validate?: (file: File) => void;
  onUploaded: (file: File, result: T) => void | Promise<void>;
  onPending: (pending: boolean) => void;
}) {
  const [rows, setRows] = useState<UploadRow[]>([]);
  const current = useRef<UploadRow[]>([]);
  const queue = useRef(Promise.resolve());
  function update(next: UploadRow[]) {
    current.current = next;
    setRows(next);
    onPending(next.length > 0);
  }
  function patch(file: File, changes: Partial<UploadRow>) {
    update(current.current.map(row => row.file === file ? { ...row, ...changes } : row));
  }
  function remove(file: File) {
    update(current.current.filter(row => row.file !== file));
  }
  function enqueue(file: File) {
    if (current.current.some(row => row.file === file)) patch(file, { phase: "queued", percent: 0, error: undefined });
    else update([...current.current, { file, phase: "queued", percent: 0 }]);
    const task = queue.current.then(async () => {
      try {
        if (!file.size || !/\.(pdf|png|jpe?g)$/i.test(file.name)) throw new Error("请选择有效的 PDF、PNG 或 JPG 文件");
        if (file.size > 20 * 1024 * 1024) throw new Error("单个材料不能超过20MB");
        validate?.(file);
        patch(file, { phase: "uploading" });
        const form = new FormData();
        form.append("file", file);
        const result = await apiClient.post<T>(endpoint, form, {
          timeout: 300000,
          onUploadProgress: ({ loaded, total }) => {
            if (!total) return;
            patch(file, { percent: Math.min(99, Math.floor(loaded / total * 100)), phase: loaded >= total ? "processing" : "uploading" });
          },
        });
        await onUploaded(file, result);
        remove(file);
      } catch (error) {
        patch(file, { phase: "failed", error: error instanceof Error ? error.message : "材料上传失败" });
        throw error;
      }
    });
    queue.current = task.catch(() => {});
    return task;
  }
  return <div className="flex w-full min-w-0 flex-col gap-3">
    {rows.map((row, index) => <div key={index} className="w-full min-w-0">
      <div className="flex flex-wrap justify-between gap-2 text-sm">
        <span className="break-all">{row.file.name}</span>
        <span>{row.phase === "processing" ? "正在确认材料" : row.phase === "queued" ? "等待上传" : row.phase === "failed" ? "上传失败" : `正在上传 ${row.percent}%`}</span>
      </div>
      <Progress percent={row.percent} aria-label={row.file.name + " 上传进度"} />
      {row.phase === "failed" && <div role="alert" className="flex flex-wrap items-center gap-2">
        <span className="text-red-600">{row.error}</span>
        <Button disabled={disabled} onClick={() => void enqueue(row.file).catch(() => {})}>重试上传</Button>
        <Button disabled={disabled} onClick={() => remove(row.file)}>移除</Button>
      </div>}
    </div>)}
    <Upload multiple accept=".pdf,.png,.jpg,.jpeg" aria-label="上传PDF / 图片" disabled={disabled} fileList={[]} showUploadList={false}
      customRequest={({ fileInstance, onSuccess, onError }) => { void enqueue(fileInstance).then(() => onSuccess({}), () => onError({})); }}>
      <Button disabled={disabled}>上传PDF / 图片</Button>
    </Upload>
  </div>;
}
