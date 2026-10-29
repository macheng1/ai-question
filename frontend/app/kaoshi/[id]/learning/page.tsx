import { H5Shell } from "@/features/exam/components/H5Shell";
import { ExamRunner } from "@/features/exam/components/ExamRunner";
import { loadExamPaper } from "@/features/exam/load-paper";
export default async function ExamLearningPage({ params, searchParams }: { params: Promise<{ id: string }>; searchParams: Promise<{ name?: string; phone?: string }> }) {
  const { id } = await params;
  const query = await searchParams;
  const paper = await loadExamPaper(id);
  return <H5Shell title="视频学习" backHref={`/kaoshi/${id}`}><ExamRunner paper={paper} initialName={query.name || ""} initialPhone={query.phone || ""} allowRestore={false} learningPage compact /></H5Shell>;
}
