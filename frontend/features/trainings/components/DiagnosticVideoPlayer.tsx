"use client";

import { VideoPlayer } from "@douyinfe/semi-ui";
import { useEffect, useRef, useState, type ComponentProps } from "react";

type Snapshot = {
  event: string; elapsedMs: number; position: number; duration: number | null;
  readyState: number; networkState: number; buffered: string; errorCode: number | null; stalledMs: number; bufferedAhead: number; paused: boolean; playbackRate: number;
};
const events = ["loadstart", "loadedmetadata", "loadeddata", "canplay", "playing", "waiting", "stalled", "seeking", "seeked", "pause", "ended", "error", "abort", "emptied"];

/** Administrator preview diagnostics; never log signed URLs or playback tickets. */
export function DiagnosticVideoPlayer(props: ComponentProps<typeof VideoPlayer>) {
  const root = useRef<HTMLDivElement>(null);
  const [records, setRecords] = useState<Snapshot[]>([]);
  useEffect(() => {
    const container = root.current;
    if (!container) return;
    const started = performance.now();
    const session = `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 7)}`;
    let cleanup = () => {};
    let attached: HTMLVideoElement | null = null;
    setRecords([]);
    const attach = () => {
      const video = container.querySelector("video");
      if (!video || video === attached) return;
      cleanup();
      attached = video;
      let waitingSince: number | null = null;
      let firstFrame = false;
      const record = (event: string) => {
        const now = performance.now();
        if ((event === "waiting" || event === "stalled") && !video.paused && waitingSince === null) waitingSince = now;
        const stalledMs = waitingSince === null ? 0 : Math.round(now - waitingSince);
        if (["playing", "pause", "ended", "error", "seeking"].includes(event)) waitingSince = null;
        if (event === "loadeddata" && !firstFrame) {
          firstFrame = true;
          console.info("[video-preview]", { session, event: "first_frame_ready", elapsedMs: Math.round(now - started) });
        }
        let bufferedAhead = 0;
        for (let i = 0; i < video.buffered.length; i++) {
          if (video.currentTime >= video.buffered.start(i) && video.currentTime <= video.buffered.end(i))
            bufferedAhead = Math.round((video.buffered.end(i) - video.currentTime) * 10) / 10;
        }
        const buffered = Array.from({ length: video.buffered.length }, (_, i) =>
          `${video.buffered.start(i).toFixed(1)}–${video.buffered.end(i).toFixed(1)}秒`).join(", ") || "无";
        const snapshot: Snapshot = {
          stalledMs, bufferedAhead, paused: video.paused, playbackRate: video.playbackRate,
          event, elapsedMs: Math.round(performance.now() - started),
          position: Math.round(video.currentTime * 10) / 10,
          duration: Number.isFinite(video.duration) ? Math.round(video.duration * 10) / 10 : null,
          readyState: video.readyState, networkState: video.networkState, buffered,
          errorCode: video.error?.code ?? null,
        };
        setRecords(rows => [...rows.slice(-29), snapshot]);
        const detail = { session, ...snapshot, online: navigator.onLine, width: video.videoWidth, height: video.videoHeight };
        if (["error", "stalled"].includes(event)) console.warn("[video-preview]", detail);
        else console.info("[video-preview]", detail);
      };
      const listener = (event: Event) => record(event.type);
      events.forEach(event => video.addEventListener(event, listener));
      record("attached");
      const timer = window.setInterval(() => record("sample"), 5000);
      cleanup = () => {
        window.clearInterval(timer);
        events.forEach(event => video.removeEventListener(event, listener));
      };
    };
    let resourceObserver: PerformanceObserver | undefined;
    if (typeof PerformanceObserver !== "undefined") {
      resourceObserver = new PerformanceObserver(list => {
        for (const entry of list.getEntries()) {
          const resource = entry as PerformanceResourceTiming;
          if (resource.startTime < started || !attached || resource.name !== attached.currentSrc) continue;
          console.info("[video-preview]", { session, event: "request_finished",
            durationMs: Math.round(resource.duration),
            ttfbMs: resource.responseStart > 0 ? Math.round(resource.responseStart - resource.startTime) : null,
            transferBytes: resource.transferSize, bodyBytes: resource.encodedBodySize,
            note: "0字节可能表示缓存或浏览器未开放计时；不是实时下载速度" });
        }
      });
      resourceObserver.observe({ entryTypes: ["resource"] });
    }
    attach();
    const observer = new MutationObserver(attach);
    observer.observe(container, { childList: true, subtree: true });
    return () => { observer.disconnect(); resourceObserver?.disconnect(); cleanup(); };
  }, [props.src]);
  const latest = records.at(-1);
  return <div ref={root}>
    <details className="mb-3 rounded border border-slate-200 p-2 text-xs" open>
      <summary>播放诊断 · {latest?.event ?? "等待播放器"} · 缓冲：{latest?.buffered ?? "无"}</summary>
      <p className="my-2">每 5 秒采样，保留最近 30 条；控制台筛选 [video-preview]。错误码：1 取消、2 网络、3 解码、4 格式或来源不支持。</p>
      <pre className="max-h-32 overflow-auto whitespace-pre-wrap break-all">{records.map(row =>
        `+${row.elapsedMs}ms ${row.event} 位置=${row.position}/${row.duration ?? "?"}秒 缓冲=${row.buffered} ready=${row.readyState} network=${row.networkState} 可播=${row.bufferedAhead}秒 等待=${row.stalledMs}ms error=${row.errorCode ?? "无"}`
      ).join("\n")}</pre>
    </details>
    <VideoPlayer {...props} />
  </div>;
}
