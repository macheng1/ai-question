"use client";

import { useEffect, useState } from "react";
import { Banner, Button, Spin } from "@douyinfe/semi-ui";
import { loadExamPaper } from "../load-paper";
import type { ExamPaper } from "../types";
import { ExamRunner } from "./ExamRunner";
import { H5Shell } from "./H5Shell";
import { ResultLoader } from "@/app/kaoshi/[id]/result/ResultLoader";
import { useLearnerStore } from "@/features/auth/store";

/** 答卷含个人信息，必须在浏览器携带学员凭证加载，不能在公开服务端页面读取。 */
export function ExamAttemptPage({ id, attemptId, mode }: { id: string; attemptId?: string; mode: "answer" | "result" | "details" }) {
  const [paper, setPaper] = useState<ExamPaper>();
  const [error, setError] = useState("");
  useEffect(() => {
    let active = true;
    if (!attemptId || !/^\d+$/.test(attemptId)) { setError("缺少有效的答题记录，请从考试入口进入。"); return; }
    loadExamPaper(id, { attemptId: Number(attemptId), revealAnswers: mode !== "answer" })
      .then(value => { if (active) setPaper(value); })
      .catch(reason => { if (active) setError(reason instanceof Error ? reason.message : "加载失败"); });
    return () => { active = false; };
  }, [id, attemptId, mode]);
  const user = useLearnerStore(state => state.user);
  return <H5Shell title={mode === "answer" ? "考试答题" : mode === "details" ? "考试详情" : "考试结果"} backHref={mode === "answer" ? `/kaoshi/${id}` : "/learner"}>
    {error ? <div className="h5-empty"><Banner type="danger" description={error} /><Button className="!mt-4" onClick={() => window.location.assign(`/kaoshi/${id}`)}>返回考试入口</Button></div>
      : !paper ? <div className="h5-empty"><Spin /><p>正在加载答题记录…</p></div>
      : mode === "answer" ? <ExamRunner key={attemptId} paper={paper} initialStage="answer" initialName={user?.name || ""} initialPhone={user?.phone || ""} initialAttemptId={Number(attemptId)} />
      : <ResultLoader paper={paper} attemptId={attemptId!} details={mode === "details"} />}
  </H5Shell>;
}
