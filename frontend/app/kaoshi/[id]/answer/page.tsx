import { H5Shell } from "@/features/exam/components/H5Shell";
import { ExamRunner } from "@/features/exam/components/ExamRunner";
import { loadExamPaper } from "@/features/exam/load-paper";
export default async function ExamAnswerPage({ params, searchParams }: { params: Promise<{ id: string }>; searchParams: Promise<{ name?: string; phone?: string; attemptId?: string }> }) {
  const { id } = await params;
  const query = await searchParams;
  const attemptId = query.attemptId ? Number(query.attemptId) : undefined;
  const paper = await loadExamPaper(id, { attemptId: Number.isFinite(attemptId) ? attemptId : undefined });
  return <H5Shell title={paper.title} backHref={`/kaoshi/${id}`}><ExamRunner paper={paper} initialStage="answer" initialName={query.name || ""} initialPhone={query.phone || ""} initialAttemptId={Number.isFinite(attemptId) ? attemptId : undefined} /></H5Shell>;
}
