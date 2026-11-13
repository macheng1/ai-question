"use client";
import { Typography } from "@douyinfe/semi-ui";
import { Layout } from "@douyinfe/semi-ui";
import { useEffect, useState } from "react";
import { Button } from "@douyinfe/semi-ui";
import { IconArrowRight } from "@douyinfe/semi-icons";
import { apiClient } from "@/lib/api/client";
export function TrainingExamLinks({ examId }: { examId: string }) {
  const [attemptId, setAttemptId] = useState("");
  const [trainings, setTrainings] = useState<{ id: number; name: string; surveyEnabled: boolean; surveySubmitted: boolean }[]>([]);
  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    const currentTraining = params.get("trainingId");
    const currentAttempt = params.get("attemptId");
    if (currentAttempt && /^\d+$/.test(currentAttempt)) setAttemptId(currentAttempt);
    const controller = new AbortController();
    apiClient.get<(typeof trainings[number] & { assessmentId: number | null })[]>("/learner/trainings", undefined, { signal: controller.signal })
      .then(rows => setTrainings(rows.filter(training => String(training.assessmentId) === examId && (!currentTraining || String(training.id) === currentTraining))))
      .catch(() => undefined);
    return () => controller.abort();
  }, [examId]);
  return <div className="print-hidden">{trainings.filter(t => t.surveyEnabled).map(t => <Layout.Content className="h5-training-link" key={t.id}><Typography.Title heading={3} className="!mb-2 !text-base !leading-6">{t.name}</Typography.Title><p>这次培训，对你有帮助吗？分享你的感受，帮助我们改进培训。</p><Button block theme="solid" className="h5-primary" onClick={() => window.location.assign(`/training/${t.id}/survey${attemptId ? `?examId=${examId}&attemptId=${attemptId}` : ""}`)}>{t.surveySubmitted ? "查看评估" : "填写满意度评估"} <IconArrowRight /></Button></Layout.Content>)}</div>;
}
