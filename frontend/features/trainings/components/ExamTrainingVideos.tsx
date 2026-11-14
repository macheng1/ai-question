"use client";
import { Typography } from "@douyinfe/semi-ui";
import { Layout } from "@douyinfe/semi-ui";

import { useEffect, useRef, useState } from "react";
import { Banner, Button, Progress, Spin, VideoPlayer, Toast } from "@douyinfe/semi-ui";
import { IconPlay, IconTick, IconClock, IconArrowRight } from "@douyinfe/semi-icons";
import { confirmLearner } from "@/features/auth/learner-session";
import { registerProgressSaver } from "../progress-navigation";
import { createProgressQueue } from "../progress-queue";
import { saveLearningProgress, nextProgressRequestId } from "../progress-transport";
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
export function ExamTrainingVideos({ examId, data, onBack, onContinue, starting, continueLabel = "开始考试", onProgress, reviewTrainingId }: {
  reviewTrainingId?: string; examId: string; data: ExamLearning; onBack: () => void; onContinue: () => void; starting: boolean; continueLabel?: string; onProgress?: (video: ExamVideo) => void;
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
    apiClient.get<Blob>(reviewTrainingId ? `/learner/trainings/${reviewTrainingId}/review/videos/${selectedId}` : `${learningBase(examId)}/videos/${selectedId}/content`, reviewTrainingId ? undefined : { token: data.token }, { responseType: "blob", signal: controller.signal, timeout: 300000 })
      .then(blob => { if (!controller.signal.aborted) { objectUrl = URL.createObjectURL(blob); setSrc(objectUrl); } })
      .catch(e => { if (!controller.signal.aborted) setError(e.message || "视频加载失败，请重试"); });
    return () => { controller.abort(); if (objectUrl) URL.revokeObjectURL(objectUrl); };
  }, [examId, selectedId, data.token, retry, reviewTrainingId]);

  useEffect(() => {
    const element = player.current;
    if (reviewTrainingId || !element || !src || !video) return;
    let alive = true, lastSaved = video.positionSeconds, furthest = video.positionSeconds;
    let lastTime = performance.now(), requested = false, finishRequested = false, initialized = false, blocked = false, refreshRequested = false, savedComplete = video.completed;
    const sample = () => {
      const now=performance.now();
      if (!element.seeking && element.currentTime <= furthest + Math.min(2,(now-lastTime)/1000+0.5)) furthest=Math.max(furthest,element.currentTime);
      lastTime=now;
    };
    type Result={positionSeconds:number;completed:boolean};
    const queue=createProgressQueue({
      snapshot:()=>{
        if(!requested||!Number.isFinite(element.duration)||element.duration<=0)return null;
        requested=false;sample();
        const position=Math.min(element.currentTime,furthest), completed=finishRequested;
        finishRequested=false;
        if(initialized&&!refreshRequested&&position===lastSaved&&(!completed||savedComplete))return null;
        refreshRequested=false;
        return {token:data.token,position,duration:element.duration,completed,requestId:nextProgressRequestId()};
      },
      send:payload=>saveLearningProgress<Result>(`${learningBase(examId)}/videos/${video.id}/progress`,payload),
      acknowledge:(payload,progress)=>{
        initialized=true;lastSaved=payload.position;savedComplete=progress.completed;
        if(alive){onProgress?.({...video,...progress});setVideos(old=>old.map(v=>v.id===video.id?{...v,...progress}:v));}
      },
    });
    const persist=async(finished=false)=>{
      requested=true;finishRequested ||= finished;
      if(alive&&finished)setSaving(true);
      try{await queue.flush();blocked=false;if(alive)setError("");}
      catch(e){blocked=true;element.pause();if(alive)setError(e instanceof Error?e.message:"进度保存失败，请重试");throw e;}
      finally{if(alive&&finished)setSaving(false);}
    };
    retrySave.current=()=>persist(element.ended||furthest>=element.duration-0.05);
    const unregister=registerProgressSaver(async()=>{element.pause();await persist(element.ended);});
    const metadata=()=>{element.currentTime=Math.min(video.positionSeconds,element.duration);lastTime=performance.now();void persist().catch(()=>{});};
    const seek = () => { if (!video.completed && element.currentTime > furthest + 0.4) element.currentTime = furthest; };
    const tick = () => {
      const now = performance.now();
      // 浏览器后台恢复或拖动产生的跳跃不能算作观看。
      if (!element.seeking && element.currentTime <= furthest + Math.min(2, (now - lastTime) / 1000 + 0.5)) furthest = Math.max(furthest, element.currentTime);
      lastTime = now;
      if (!queue.pending() && !blocked && Math.abs(element.currentTime - lastSaved) >= 2) void persist().catch(()=>{});
    };
    const pause = () => { if(!blocked)void persist(element.ended).catch(()=>{}); };
    const play = () => { refreshRequested=true;void persist().catch(()=>{}); };
    const ended = () => { void persist(true).catch(()=>{}); };
    const rate = () => { if (element.playbackRate !== 1) element.playbackRate = 1; };
    const flushOnLeave = () => { if(!video.completed)void persist(element.ended).catch(()=>{}); };
    const hide = () => { if (document.visibilityState === "hidden") { element.pause(); flushOnLeave(); } };
    window.addEventListener("pagehide", flushOnLeave);
    document.addEventListener("visibilitychange", hide);
    const failed = () => setError("视频无法播放，请重试或联系培训管理员检查文件。");
    element.addEventListener("loadedmetadata", metadata);
    element.addEventListener("seeking", seek);
    element.addEventListener("timeupdate", tick);
    element.addEventListener("play", play);
    element.addEventListener("pause", pause);
    element.addEventListener("ended", ended);
    element.addEventListener("ratechange", rate);
    element.addEventListener("error", failed);
    if (element.readyState >= 1) metadata();
    return () => {
      unregister();flushOnLeave();
      window.removeEventListener("pagehide", flushOnLeave);
      document.removeEventListener("visibilitychange", hide);
      alive = false; retrySave.current = null;
      element.removeEventListener("loadedmetadata", metadata); element.removeEventListener("seeking", seek);
      element.removeEventListener("timeupdate", tick); element.removeEventListener("play", play); element.removeEventListener("pause", pause);
      element.removeEventListener("ended", ended); element.removeEventListener("ratechange", rate); element.removeEventListener("error", failed);
    };
    // 进度回写不重新初始化播放器；只在切换视频或重载资源时恢复。
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [src, selectedId, examId, data.token, reviewTrainingId]);

  return <Layout.Content>
    <div className="h5-content">
      {src ? <VideoPlayer key={src} ref={player} src={src} width="100%" height="auto" style={{ aspectRatio: "16 / 9", borderRadius: 15, overflow: "hidden" }} autoPlay={false} clickToPlay defaultPlaybackRate={1} muted={false} playbackRateList={[{ label: "1.0x", value: 1 }]} theme="dark" volume={80} controlsList={["play", "time", "volume", "fullscreen"]} /> : !error && <div className="flex flex-col items-center justify-center gap-3 rounded-xl bg-slate-100" style={{ aspectRatio: "16 / 9" }}><Spin size="large" /><p className="text-sm text-slate-500">正在加载视频…</p></div>}
      {!reviewTrainingId && video && <div className="h5-video-resume"><IconClock /> {video.completed ? "本节视频已完成，可再次观看" : video.positionSeconds > 0 ? `已保存到 ${Math.floor(video.positionSeconds / 60)} 分 ${Math.floor(video.positionSeconds % 60)} 秒，下次可继续观看` : (Number(examId)<0?"观看进度自动保存，退出后可继续学习":"观看进度自动保存，完成学习后进入考试")}</div>}
      <Typography.Title heading={1} className="h5-video-title !text-xl !leading-7 !mb-2">{video?.title || "培训视频学习"}</Typography.Title><p className="h5-muted">{reviewTrainingId ? "回看不计入学习时长，也不改变学习记录。" : Number(examId)<0?"请完整观看全部培训视频，进度自动保存。":"请完整观看全部培训视频。学习期间不占用考试答题时间。"}</p>
      {!reviewTrainingId && <><div className="h5-lesson-progress"><Typography.Title heading={2} className="!text-sm">课程完成进度</Typography.Title><span>{completedCount} / {videos.length} 节</span></div>
      <Progress percent={Math.round(completedCount / Math.max(1, videos.length) * 100)} stroke="#53ab8b" showInfo={false} /></>}
      <div className="h5-lesson-list">{videos.map((v, i) => <Button key={v.id} block className={`h5-lesson ${v.id === selectedId ? "selected" : ""}`} theme="borderless" icon={v.completed ? <IconTick /> : <IconPlay />} disabled={saving || starting} onClick={async () => { try { player.current?.pause(); await retrySave.current?.(); setSelectedId(v.id); } catch(e) { Toast.error(e instanceof Error?e.message:"进度保存失败，请重试"); } }}>{i + 1}. {v.title}{!reviewTrainingId && <> · {v.completed ? "已完成" : "待完成"}</>}</Button>)}</div>
      {error && <Banner type="danger" description={error} closeIcon={null}>{src && <Button theme="borderless" disabled={saving} onClick={() => void retrySave.current?.().catch(()=>{})}>重试保存进度</Button>}<Button theme="borderless" disabled={saving} onClick={async () => { try { await retrySave.current?.(); setRetry(n => n + 1); } catch { /* Keep the unacknowledged batch for another retry. */ } }}>重新加载视频</Button></Banner>}

    </div>
    <div className="h5-bottom">{!reviewTrainingId && <><p>{allCompleted ? "全部视频已完成，可以继续" : "请先完成全部视频学习"}</p><Button block theme="solid" className="h5-primary" size="large" disabled={!allCompleted || saving || starting} loading={starting} onClick={onContinue}>{allCompleted ? continueLabel : "请先完成视频学习"}<IconArrowRight /></Button></>}<Button block theme="borderless" className="h5-link" disabled={saving || starting} onClick={async () => { try { player.current?.pause(); await retrySave.current?.(); onBack(); } catch(e) { Toast.error(e instanceof Error?e.message:"进度保存失败，请重试"); } }}>{reviewTrainingId ? "返回" : "返回填写信息"}</Button></div>
  </Layout.Content>;
}
