"use client";

import { useEffect, useRef, useState } from "react";
import { Banner, Button, Image, InputNumber, Modal, Space, Table, Toast, Upload, VideoPlayer } from "@douyinfe/semi-ui";

import { apiClient } from "@/lib/api/client";
export type DraftMaterial = { file: File; sourceId?: number; sourceTrainingId?: string; kind: "videos" | "materials"; pageCount?: number; pages?: { pageNumber: number; requiredSeconds: number }[] };

/** Files stay in this form until the final create request succeeds. */
export function TrainingDraftMaterials({ items, onChange, disabled, onBusyChange }: {
  items: DraftMaterial[]; onChange: (items: DraftMaterial[]) => void; disabled: boolean; onBusyChange: (busy: boolean) => void;
}) {
  const rowKeys = useRef(new WeakMap<File, number>());
  const nextRowKey = useRef(0);
  const rowKey = (item: DraftMaterial) => {
    if (!rowKeys.current.has(item.file)) rowKeys.current.set(item.file, ++nextRowKey.current);
    return rowKeys.current.get(item.file)!;
  };
  const currentItems = useRef(items);
  currentItems.current = items;
  const [inspecting, setInspecting] = useState(false);
  const [page, setPage] = useState(1);
  const [seconds, setSeconds] = useState(120);
  const [preview, setPreview] = useState<DraftMaterial | null>(null);
  const [previewUrl, setPreviewUrl] = useState("");
  const [previewError, setPreviewError] = useState("");
  const previewFile = preview?.file;
  const sourceId = preview?.sourceId;
  const sourceTrainingId = preview?.sourceTrainingId;
  const previewKind = preview?.kind;
  useEffect(() => {
    setPreviewUrl(""); setPreviewError("");
    if (!previewFile) return;
    if (sourceId && sourceTrainingId) {
      const controller = new AbortController();
      let url = "";
      const path = previewKind === "videos" ? `/trainings/${sourceTrainingId}/videos/${sourceId}/content` : `/management/trainings/${sourceTrainingId}/materials/${sourceId}/pages/${page}`;
      apiClient.get<Blob>(path, undefined, { responseType: "blob", signal: controller.signal, timeout: 300000 }).then(blob => {
        if (!controller.signal.aborted) { url = URL.createObjectURL(blob); setPreviewUrl(url); }
      }).catch(e => { if (!controller.signal.aborted) setPreviewError(e instanceof Error ? e.message : "预览加载失败"); });
      return () => { controller.abort(); if (url) URL.revokeObjectURL(url); };
    }
    const extension = previewFile.name.split(".").pop()?.toLowerCase();
    const mime = extension === "pdf" ? "application/pdf" : extension === "mp4" ? "video/mp4" : extension === "webm" ? "video/webm" : extension === "png" ? "image/png" : "image/jpeg";
    const url = URL.createObjectURL(previewFile.slice(0, previewFile.size, mime));
    setPreviewUrl(url);
    return () => URL.revokeObjectURL(url);
  }, [previewFile, sourceId, sourceTrainingId, previewKind, page]);
  useEffect(() => {
    if (preview && !items.some(item => item.file === preview.file)) setPreview(null);
  }, [items, preview]);
  const inspect = async (item: DraftMaterial): Promise<DraftMaterial> => {
    if (item.kind === "videos" || item.pages) return item;
    const data = new FormData(); data.append("file", item.file);
    const info = await apiClient.post<{ pageCount: number }>("/management/trainings/materials/inspect", data, { timeout: 300000 });
    return { ...item, pageCount: info.pageCount, pages: Array.from({ length: info.pageCount }, (_, index) => ({ pageNumber: index + 1, requiredSeconds: 120 })) };
  };
  const openPreview = async (item: DraftMaterial) => {
    setInspecting(true); onBusyChange(true);
    try {
      const prepared = await inspect(item);
      if (prepared !== item) onChange(currentItems.current.map(row => row.file === item.file ? prepared : row));
      setPreview({ ...prepared, pages: prepared.pages?.map(p => ({ ...p })) }); setPage(1); setSeconds(120);
    } catch (e) { Toast.error(e instanceof Error ? e.message : "文件解析失败"); }
    finally { setInspecting(false); onBusyChange(false); }
  };
  const add = async (files: File[], kind: DraftMaterial["kind"]) => {
    const added: DraftMaterial[] = [];
    for (const file of Array.from(files || [])) {
      const valid = kind === "videos" ? /\.(mp4|webm)$/i.test(file.name) : /\.(pdf|png|jpe?g)$/i.test(file.name);
      const limit = kind === "videos" ? 100 : 20;
      if (!valid || !file.size || file.size > limit * 1024 * 1024) {
        Toast.error(`${file.name}：请选择支持的格式，单个文件不超过 ${limit}MB`); continue;
      }
      if (items.concat(added).some(item => item.file.name === file.name && item.file.size === file.size && item.file.lastModified === file.lastModified)) continue;
      added.push({ file, kind });
    }
    if (items.concat(added).reduce((sum, item) => sum + item.file.size, 0) > 190 * 1024 * 1024) {
      Toast.error("本次创建的材料总大小不能超过 190MB，请减少文件后重试"); return;
    }
    setInspecting(true); onBusyChange(true);
    try {
      const prepared = await Promise.all(added.map(inspect));
      onChange([...currentItems.current, ...prepared]);
    } finally { setInspecting(false); onBusyChange(false); }
  };
  const move = (item: DraftMaterial, offset: number) => {
    const group = items.filter(row => row.kind === item.kind);
    const target = group[group.indexOf(item) + offset];
    if (!target) return;
    const next = [...items], a = next.indexOf(item), b = next.indexOf(target);
    [next[a], next[b]] = [next[b], next[a]]; onChange(next);
  };
  const actions = (item: DraftMaterial, group: DraftMaterial[]) => <Space>
    <Button theme="borderless" disabled={disabled || inspecting} onClick={() => void openPreview(item)}>{item.kind === "videos" ? "预览" : "设置时长"}</Button>
    <Button disabled={disabled || inspecting || group[0] === item} onClick={() => move(item, -1)}>上移</Button>
    <Button disabled={disabled || inspecting || group[group.length - 1] === item} onClick={() => move(item, 1)}>下移</Button>
    <Button type="danger" theme="borderless" disabled={disabled || inspecting} onClick={() => onChange(items.filter(row => row !== item))}>删除</Button>
  </Space>;
  return <section className="flex flex-col gap-6" aria-label="培训材料">
    {(["videos", "materials"] as const).map(kind => {
      const group = items.filter(item => item.kind === kind);
      return <div key={kind} className="flex flex-col gap-3">
        {kind === "materials" && <strong>PDF / 图片材料</strong>}
        <p className="text-sm text-slate-500">{kind === "videos"
          ? "上传 MP4 / WebM 视频，单个文件不超过 100MB。最后一步保存时统一上传。"
          : "支持 PDF、PNG、JPG，单个文件不超过20MB，PDF最多200页。默认每页120秒，可统一设置或逐页调整，最后一步保存时统一上传。"}</p>
        {kind === "videos" ? <>
          <ul className="divide-y divide-slate-100">{group.map((item, index) => <li key={`${item.file.name}-${item.file.lastModified}`} className="flex flex-wrap items-center justify-between gap-3 py-3">
            <span className="break-all">{index + 1}. {item.file.name}</span>{actions(item, group)}
          </li>)}</ul>
          {!group.length && <p className="text-slate-500">暂无视频。</p>}
        </> : <Table<{ material: DraftMaterial; key: number }> rowKey="key" dataSource={group.map(item => ({ material: item, key: rowKey(item) }))} pagination={false} scroll={{ x: 650 }} columns={[
          { title: "材料", width: 200, render: (_, row) => row.material.file.name },
          { title: "页数", width: 120, render: (_, row) => row.material.pageCount ?? (/\.pdf$/i.test(row.material.file.name) ? "点击预览识别" : 1) },
          { title: "操作", width: 380, render: (_, item) => actions(item.material, group) },
        ]} />}
        <Upload aria-label={kind === "videos" ? "上传培训视频" : "上传PDF / 图片"} disabled={disabled || inspecting} accept={kind === "videos" ? ".mp4,.webm" : ".pdf,.png,.jpg,.jpeg"} fileList={[]} showUploadList={false}
          customRequest={async ({ fileInstance, onSuccess, onError }) => { try { await add([fileInstance], kind); onSuccess({}); } catch (e) { Toast.error(e instanceof Error ? e.message : "文件解析失败"); onError({}); } }}>
          <Button disabled={disabled || inspecting} loading={inspecting}>{kind === "videos" ? "上传培训视频" : "上传PDF / 图片"}</Button>
        </Upload>
      </div>;
    })}
    <Modal title={preview ? `材料预览：${preview.file.name}` : "材料预览"} visible={!!preview}
      onCancel={() => setPreview(null)} footer={preview?.kind === "materials" ? <Button theme="solid" disabled={disabled} onClick={() => {
        onChange(currentItems.current.map(item => item.file === preview.file ? { ...item, pageCount: preview.pageCount, pages: preview.pages } : item));
        setPreview(null);
      }}>保存阅读时长</Button> : null} centered style={{ width: "min(900px, 94vw)" }}
      bodyStyle={{ maxHeight: "calc(100dvh - 240px)", overflowY: "auto" }}>
      {preview?.kind === "materials" && preview.pages && <div className="mb-4 flex flex-col gap-3">
        <Space wrap><span>统一时长（秒）</span><InputNumber aria-label="统一阅读时长" min={1} max={3600} precision={0} value={seconds} onChange={value => setSeconds(Number(value) || 1)} />
          <Button onClick={() => setPreview({ ...preview, pages: preview.pages!.map(p => ({ ...p, requiredSeconds: seconds })) })}>应用到全部页</Button></Space>
        <Space wrap><Button disabled={page === 1} onClick={() => setPage(page - 1)}>上一页</Button><span>{page} / {preview.pageCount}</span><Button disabled={page === preview.pageCount} onClick={() => setPage(page + 1)}>下一页</Button>
          <span>本页阅读秒数</span><InputNumber aria-label="本页阅读秒数" min={1} max={3600} precision={0} value={preview.pages[page - 1]?.requiredSeconds} onChange={value => setPreview({ ...preview, pages: preview.pages!.map(p => p.pageNumber === page ? { ...p, requiredSeconds: Number(value) || 1 } : p) })} /></Space>
      </div>}
      {previewError && <Banner type="danger" description={previewError} />}
      {preview && !previewUrl && !previewError && <p role="status">正在加载预览…</p>}
      {preview && previewUrl && (preview.kind === "videos"
        ? <VideoPlayer key={previewUrl} src={previewUrl} width="100%" height="min(560px, 60dvh)" style={{ display: "block", maxHeight: "calc(100dvh - 180px)", borderRadius: 8, overflow: "hidden" }}
            autoPlay={false} clickToPlay muted={false} defaultPlaybackRate={1} volume={80} theme="dark"
            playbackRateList={[{ label: "1x", value: 1 }, { label: "1.5x", value: 1.5 }, { label: "2x", value: 2 }]}
            ref={element => { if (element) element.onerror = () => setPreviewError("视频无法播放，请检查文件格式或重新选择文件。"); }} />
        : !preview.sourceId && /\.pdf$/i.test(preview.file.name)
          ? <iframe key={`${previewUrl}-${page}`} src={`${previewUrl}#page=${page}`} title={`PDF 预览：${preview.file.name}`} className="h-[65vh] w-full border-0" />
          : <Image src={previewUrl} alt={preview.file.name} width="100%" onError={() => setPreviewError("图片无法显示，请检查文件或重新选择。")} />)}
    </Modal>
  </section>;
}
