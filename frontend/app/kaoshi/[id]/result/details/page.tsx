import { H5Shell } from "@/features/exam/components/H5Shell";
import { loadExamPaper } from "@/features/exam/load-paper";
import { ResultLoader } from "../ResultLoader";
export default async function ExamDetailsPage({ params, searchParams }: { params: Promise<{ id: string }>; searchParams: Promise<{ attemptId?: string }> }) {
  const { id } = await params;
  const query = await searchParams;
  const paper = await loadExamPaper(id, { revealAnswers: true, attemptId: query.attemptId ? Number(query.attemptId) : undefined });
  return <H5Shell title="考试详情" backHref={`/kaoshi/${id}/result${query.attemptId ? `?attemptId=${encodeURIComponent(query.attemptId)}` : ""}`}>{query.attemptId ? <ResultLoader paper={paper} attemptId={query.attemptId} details /> : <div className="h5-empty">缺少答题记录，无法查看答题详情。</div>}</H5Shell>;
}
