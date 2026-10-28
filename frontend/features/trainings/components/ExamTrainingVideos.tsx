"use client";

import { useEffect, useRef, useState } from "react";
import { Banner, Button, Progress, Spin, Tag, VideoPlayer } from "@douyinfe/semi-ui";
import { IconPlay, IconTick } from "@douyinfe/semi-icons";
import { apiClient } from "@/lib/api/client";

export type ExamVideo = { id: number; trainingId: number; title: string; positionSeconds: number; completed: boolean };
export type ExamLearning = { token: string; videos: ExamVideo[]; trainings: { id: number; name: string; surveyEnabled: boolean }[] };
export const loadExamLearning = (examId: string, name: string, phone: string) =>
  apiClient.post<ExamLearning>(`/public/exams/${examId}/learning`, { name: name.trim(), phone: phone.trim() });

/** 观看完成以服务端确认的进度为准，尚未观看的片段不能通过拖动跳过。 */
export function ExamTrainingVideos({ examId, data, onBack, onContinue, starting }: {
  examId: string; data: ExamLearning; onBack: () => void; onContinue: () => void; starting: boolean;
}) {
  const [videos, setVideos] = useState(data.videos);
  const [selectedId, setSelectedId] = useState(data.videos.find(v => !v.completed)?.id ?? data.videos[0]?.id);
  const [src, setSrc] = useState("");
  const [error, setError] = useState("");
  const [retry, setRetry] = useState(0);
  const [saving, setSaving] = useState(false);
  const player = useRef<HTMLVideoElement>(null);
  const retrySave = useRef<(() => Promise<void>) | null>(null);
  const video = videos.find(v => v.id === selectedId);
  const completedCount = videos.filter(v => v.completed).length;
  const allCompleted = completedCount === videos.length;

  useEffect(() => {
    const controller = new AbortController(); let objectUrl = "";
    setSrc(""); setError("");
    apiClient.get<Blob>(`/public/exams/${examId}/learning/videos/${selectedId}/content`, { token: data.token }, { responseType: "blob", signal: controller.signal, timeout: 300000 })
      .then(blob => { if (!controller.signal.aborted) { objectUrl = URL.createObjectURL(blob); setSrc(objectUrl); } })
      .catch(e => { if (!controller.signal.aborted) setError(e.message || "视频加载失败，请重试"); });
    return () => { controller.abort(); if (objectUrl) URL.revokeObjectURL(objectUrl); };
  }, [examId, selectedId, data.token, retry]);

  useEffect(() => {
    const element = player.current;
    if (!element || !src || !video) return;
    let alive = true, lastSaved = video.positionSeconds, furthest = video.positionSeconds;
    let lastTime = performance.now(), pending = false, finishPending = false;
    const persist = async (finished = false) => {
      if (!Number.isFinite(element.duration) || element.duration <= 0) return;
      if (pending) { finishPending ||= finished; return; }
      pending = true; if (alive) setSaving(true);
      const position = Math.min(element.currentTime, furthest);
      try {
        const progress = await apiClient.put<{ positionSeconds: number; completed: boolean }>(`/public/exams/${examId}/learning/videos/${video.id}/progress`, {
          token: data.token, position, duration: element.duration, completed: finished,
        });
        lastSaved = position;
        if (alive) {
          setError("");
          setVideos(old => old.map(v => v.id === video.id ? { ...v, ...progress } : v));
        }
      } catch (e) { if (alive) setError(e instanceof Error ? e.message : "进度保存失败，请重试"); }
      finally {
        pending = false; if (alive) setSaving(false);
        if (finishPending && alive) { finishPending = false; void persist(true); }
      }
    };
    retrySave.current = () => persist(element.ended || furthest >= element.duration - 0.05);
    const metadata = () => { element.currentTime = Math.min(video.positionSeconds, element.duration); lastTime = performance.now(); };
    const seek = () => { if (!video.completed && element.currentTime > furthest + 0.4) element.currentTime = furthest; };
    const tick = () => {
      const now = performance.now();
      // 浏览器后台恢复或拖动产生的跳跃不能算作观看。
      if (!element.seeking && element.currentTime <= furthest + Math.min(2, (now - lastTime) / 1000 + 0.5)) furthest = Math.max(furthest, element.currentTime);
      lastTime = now;
      if (!video.completed && element.currentTime - lastSaved >= 2) void persist();
    };
    const pause = () => { if (!video.completed) void persist(element.ended); };
    const ended = () => { if (!video.completed) void persist(true); };
    const rate = () => { if (element.playbackRate !== 1) element.playbackRate = 1; };
    const flushOnLeave = () => {
      if (video.completed || !Number.isFinite(element.duration) || element.duration <= 0) return;
      const base = apiClient.instance.defaults.baseURL || "/api";
      void fetch(`${base}/public/exams/${examId}/learning/videos/${video.id}/progress`, {
        method: "PUT", keepalive: true, headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ token: data.token, position: Math.min(element.currentTime, furthest), duration: element.duration, completed: element.ended }),
      }).catch(() => undefined);
    };
    const hide = () => { if (document.visibilityState === "hidden") { element.pause(); flushOnLeave(); } };
    window.addEventListener("pagehide", flushOnLeave);
    document.addEventListener("visibilitychange", hide);
    const failed = () => setError("视频无法播放，请重试或联系培训管理员检查文件。");
    element.addEventListener("loadedmetadata", metadata);
    element.addEventListener("seeking", seek);
    element.addEventListener("timeupdate", tick);
    element.addEventListener("pause", pause);
    element.addEventListener("ended", ended);
    element.addEventListener("ratechange", rate);
    element.addEventListener("error", failed);
    if (element.readyState >= 1) metadata();
    return () => {
      flushOnLeave();
      window.removeEventListener("pagehide", flushOnLeave);
      document.removeEventListener("visibilitychange", hide);
      alive = false; retrySave.current = null;
      element.removeEventListener("loadedmetadata", metadata); element.removeEventListener("seeking", seek);
      element.removeEventListener("timeupdate", tick); element.removeEventListener("pause", pause);
      element.removeEventListener("ended", ended); element.removeEventListener("ratechange", rate); element.removeEventListener("error", failed);
    };
    // 进度回写不重新初始化播放器；只在切换视频或重载资源时恢复。
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [src, selectedId, examId, data.token]);

  return <section className="space-y-4">
    <div className="flex items-center justify-between gap-3"><h3 className="font-semibold text-slate-700">考前视频学习</h3><Tag color={allCompleted ? "green" : "cyan"}>{completedCount} / {videos.length} 已完成</Tag></div>
    <p className="text-sm leading-6 text-slate-500">请完整观看全部培训视频，再进入考试。学习期间不计考试用时，已保存的进度可继续观看。</p>
    <Progress percent={Math.round(completedCount / Math.max(1, videos.length) * 100)} showInfo={false} />
    <div className="space-y-2">{videos.map((v, i) => <Button key={v.id} block theme={v.id === selectedId ? "light" : "borderless"} icon={v.completed ? <IconTick /> : <IconPlay />} style={{ height: "auto", minHeight: 40, justifyContent: "flex-start", whiteSpace: "normal", textAlign: "left" }} disabled={saving || starting} onClick={async () => { player.current?.pause(); await retrySave.current?.(); setSelectedId(v.id); }}>{i + 1}. {v.title} · {v.completed ? "已观看" : "待完成"}</Button>)}</div>
    {src ? <VideoPlayer key={src} ref={player} src={src} width="100%" height="auto" style={{ aspectRatio: "16 / 9", borderRadius: 12, overflow: "hidden" }} autoPlay={false} clickToPlay defaultPlaybackRate={1} muted={false} playbackRateList={[{ label: "1.0x", value: 1 }]} theme="dark" volume={80} controlsList={["play", "time", "volume", "fullscreen"]} /> : !error && <div className="flex h-48 items-center justify-center rounded-xl bg-slate-100"><Spin tip="正在加载视频…" /></div>}
    {error && <Banner type="danger" description={error} closeIcon={null}>{src && <Button theme="borderless" disabled={saving} onClick={() => void retrySave.current?.()}>重试保存进度</Button>}<Button theme="borderless" disabled={saving} onClick={() => setRetry(n => n + 1)}>重新加载视频</Button></Banner>}
    {saving && <p className="text-xs text-slate-400">正在保存观看进度…</p>}
    <Button block theme="solid" type="warning" size="large" disabled={!allCompleted || saving || starting} loading={starting} onClick={onContinue}>{allCompleted ? "继续答题" : "请先完成全部视频学习"}</Button>
    <Button block theme="borderless" disabled={saving || starting} onClick={async () => { player.current?.pause(); await retrySave.current?.(); onBack(); }}>返回填写信息</Button>
  </section>;
}
