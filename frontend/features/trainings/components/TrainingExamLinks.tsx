"use client";
import { useEffect, useState } from "react";
import Link from "next/link";
import { apiClient } from "@/lib/api/client";
export function TrainingExamLinks({ examId }: { examId: string }) {
  const [attemptId, setAttemptId] = useState("");
  const [trainings, setTrainings] = useState<{ id: number; name: string; surveyEnabled: boolean }[]>([]);
  useEffect(() => {
    const currentAttempt = new URLSearchParams(window.location.search).get("attemptId");
    if (currentAttempt && /^\d+$/.test(currentAttempt)) setAttemptId(currentAttempt);
    const controller = new AbortController();
    apiClient.get<typeof trainings>(`/public/exams/${examId}/trainings`, undefined, { signal: controller.signal })
      .then(setTrainings).catch(() => undefined);
    return () => controller.abort();
  }, [examId]);
  return <div className="print-hidden my-5 flex flex-wrap justify-center gap-4">{trainings.filter(t => t.surveyEnabled).map(t => <Link key={t.id} className="rounded-lg bg-orange-500 px-4 py-3 text-white" href={`/training/${t.id}/survey${attemptId ? `?examId=${examId}&attemptId=${attemptId}` : ""}`}>填写满意度评估{trainings.length > 1 ? ` · ${t.name}` : ""}</Link>)}</div>;
}
