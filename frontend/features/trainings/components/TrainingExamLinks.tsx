"use client";
import { useEffect, useState } from "react";
import { Button } from "@douyinfe/semi-ui";
import { IconArrowRight } from "@douyinfe/semi-icons";
import { apiClient } from "@/lib/api/client";
export function TrainingExamLinks({ examId }: { examId: string }) {
  const [attemptId, setAttemptId] = useState("");
  const [trainings, setTrainings] = useState<{ id: number; name: string; surveyEnabled: boolean }[]>([]);
  useEffect(() => {
    const currentAttempt = new URLSearchParams(window.location.search).get("attemptId");
    if (currentAttempt && /^\d+$/.test(currentAttempt)) setAttemptId(currentAttempt);
    const controller = new AbortController();
    apiClient.get<typeof trainings>(`/public/exams/${examId}/trainings`, undefined, { signal: controller.signal }).then(setTrainings).catch(() => undefined);
    return () => controller.abort();
  }, [examId]);
  return <div className="print-hidden">{trainings.filter(t => t.surveyEnabled).map(t => <section className="h5-training-link" key={t.id}><h3>{t.name}</h3><p>这次培训，对你有帮助吗？分享你的感受，帮助我们改进培训。</p><Button block theme="solid" className="h5-primary" onClick={() => window.location.assign(`/training/${t.id}/survey${attemptId ? `?examId=${examId}&attemptId=${attemptId}` : ""}`)}>填写满意度评估 <IconArrowRight /></Button></section>)}</div>;
}
