"use client";
import { Typography } from "@douyinfe/semi-ui";
import { Layout } from "@douyinfe/semi-ui";

import { useEffect, useRef, useState } from "react";
import { Banner, Button, Progress, Spin, VideoPlayer } from "@douyinfe/semi-ui";
import { IconPlay, IconTick, IconClock, IconArrowRight } from "@douyinfe/semi-icons";
import { confirmLearner } from "@/features/auth/learner-session";
import { apiClient } from "@/lib/api/client";

export type ExamVideo = { id: number; trainingId: number; title: string; positionSeconds: number; completed: boolean };
export type ExamLearning = { materials?: import("./TrainingMaterials").Material[]; token: string; videos: ExamVideo[]; trainings: { id: number; name: string; category?: string; requirementType?: string; surveyEnabled: boolean }[] };
export const learningBase = (id:string) => Number(id)<0?`/public/courses/${-Number(id)}/learning`:`/public/exams/${id}/learning`;
export async function loadExamLearning(examId: string, name: string, phone: string) {
  const id = Number(examId);
  await confirmLearner(id < 0 ? "TRAINING" : "EXAM", Math.abs(id), name, phone);
  return apiClient.post<ExamLearning>(learningBase(examId), { name: name.trim(), phone: phone.trim() });
}

/** 观看完成以服务端确认的进度为准，尚未观看的片段不能通过拖动跳过。 */
export function ExamTrainingVideos({ examId, data, onBack, onContinue, starting, continueLabel = "开始考试", onProgress }: {
  examId: string; data: ExamLearning; onBack: () => void; onContinue: () => void; starting: boolean; continueLabel?: string; onProgress?: (video: ExamVideo) => void;
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
    apiClient.get<Blob>(`${learningBase(examId)}/videos/${selectedId}/content`, { token: data.token }, { responseType: "blob", signal: controller.signal, timeout: 300000 })
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
      pending = true;
      // 常规心跳静默保存，仅完成确认时锁定操作，避免按钮每次上报都闪动。
      if (alive && finished) setSaving(true);
      const position = Math.min(element.currentTime, furthest);
      try {
        const progress = await apiClient.put<{ positionSeconds: number; completed: boolean }>(`${learningBase(examId)}/videos/${video.id}/progress`, {
          token: data.token, position, duration: element.duration, completed: finished,
        });
        lastSaved = position;
        onProgress?.({ ...video, ...progress });
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
    const metadata = () => { element.currentTime = Math.min(video.positionSeconds, element.duration); lastTime = performance.now(); void persist(); };
    const seek = () => { if (!video.completed && element.currentTime > furthest + 0.4) element.currentTime = furthest; };
    const tick = () => {
      const now = performance.now();
      // 浏览器后台恢复或拖动产生的跳跃不能算作观看。
      if (!element.seeking && element.currentTime <= furthest + Math.min(2, (now - lastTime) / 1000 + 0.5)) furthest = Math.max(furthest, element.currentTime);
      lastTime = now;
      if (Math.abs(element.currentTime - lastSaved) >= 2) void persist();
    };
    const pause = () => { void persist(element.ended); };
    const ended = () => { void persist(true); };
    const rate = () => { if (element.playbackRate !== 1) element.playbackRate = 1; };
    const flushOnLeave = () => {
      if (video.completed || !Number.isFinite(element.duration) || element.duration <= 0) return;
      const base = apiClient.instance.defaults.baseURL || "/api";
      void fetch(`${base}${learningBase(examId)}/videos/${video.id}/progress`, {
        method: "PUT", keepalive: true, headers: { "Content-Type": "application/json", Authorization: `Bearer ${window.localStorage.getItem("aiquestion_learner_access_token") || ""}` },
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
    element.addEventListener("play", pause);
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
      element.removeEventListener("timeupdate", tick); element.removeEventListener("play", pause); element.removeEventListener("pause", pause);
      element.removeEventListener("ended", ended); element.removeEventListener("ratechange", rate); element.removeEventListener("error", failed);
    };
    // 进度回写不重新初始化播放器；只在切换视频或重载资源时恢复。
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [src, selectedId, examId, data.token]);

  return <Layout.Content>
    <div className="h5-content">
      {src ? <VideoPlayer key={src} ref={player} src={src} width="100%" height="auto" style={{ aspectRatio: "16 / 9", borderRadius: 15, overflow: "hidden" }} autoPlay={false} clickToPlay defaultPlaybackRate={1} muted={false} playbackRateList={[{ label: "1.0x", value: 1 }]} theme="dark" volume={80} controlsList={["play", "time", "volume", "fullscreen"]} /> : !error && <div className="flex flex-col items-center justify-center gap-3 rounded-xl bg-slate-100" style={{ aspectRatio: "16 / 9" }}><Spin size="large" /><p className="text-sm text-slate-500">正在加载视频…</p></div>}
      {video && <div className="h5-video-resume"><IconClock /> {video.completed ? "本节视频已完成，可再次观看" : video.positionSeconds > 0 ? `已保存到 ${Math.floor(video.positionSeconds / 60)} 分 ${Math.floor(video.positionSeconds % 60)} 秒，下次可继续观看` : (Number(examId)<0?"观看进度自动保存，退出后可继续学习":"观看进度自动保存，完成学习后进入考试")}</div>}
      <Typography.Title heading={1} className="h5-video-title !text-xl !leading-7 !mb-2">{video?.title || "培训视频学习"}</Typography.Title><p className="h5-muted">{Number(examId)<0?"请完整观看全部培训视频，进度自动保存。":"请完整观看全部培训视频。学习期间不占用考试答题时间。"}</p>
      <div className="h5-lesson-progress"><Typography.Title heading={2} className="!text-sm">课程完成进度</Typography.Title><span>{completedCount} / {videos.length} 节</span></div>
      <Progress percent={Math.round(completedCount / Math.max(1, videos.length) * 100)} stroke="#53ab8b" showInfo={false} />
      <div className="h5-lesson-list">{videos.map((v, i) => <Button key={v.id} block className={`h5-lesson ${v.id === selectedId ? "selected" : ""}`} theme="borderless" icon={v.completed ? <IconTick /> : <IconPlay />} disabled={saving || starting} onClick={async () => { player.current?.pause(); await retrySave.current?.(); setSelectedId(v.id); }}>{i + 1}. {v.title} · {v.completed ? "已完成" : "待完成"}</Button>)}</div>
      {error && <Banner type="danger" description={error} closeIcon={null}>{src && <Button theme="borderless" disabled={saving} onClick={() => void retrySave.current?.()}>重试保存进度</Button>}<Button theme="borderless" disabled={saving} onClick={() => setRetry(n => n + 1)}>重新加载视频</Button></Banner>}

    </div>
    <div className="h5-bottom"><p>{allCompleted ? "全部视频已完成，可以继续" : "请先完成全部视频学习"}</p><Button block theme="solid" className="h5-primary" size="large" disabled={!allCompleted || saving || starting} loading={starting} onClick={onContinue}>{allCompleted ? continueLabel : "请先完成视频学习"}<IconArrowRight /></Button><Button block theme="borderless" className="h5-link" disabled={saving || starting} onClick={async () => { player.current?.pause(); await retrySave.current?.(); onBack(); }}>返回填写信息</Button></div>
  </Layout.Content>;
}
