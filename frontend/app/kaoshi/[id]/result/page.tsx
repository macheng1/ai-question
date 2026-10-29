import { H5Shell } from "@/features/exam/components/H5Shell";
import { loadExamPaper } from "@/features/exam/load-paper";
import { ResultLoader } from "./ResultLoader";
export default async function ExamResultPage({ params, searchParams }: { params: Promise<{ id: string }>; searchParams: Promise<{ attemptId?: string }> }) {
  const { id } = await params;
  const query = await searchParams;
  const paper = await loadExamPaper(id, { revealAnswers: true, attemptId: query.attemptId ? Number(query.attemptId) : undefined });
  return <H5Shell title="考试结果">{query.attemptId ? <ResultLoader paper={paper} attemptId={query.attemptId} /> : <div className="h5-empty">缺少答题记录，无法查看成绩。</div>}</H5Shell>;
}
