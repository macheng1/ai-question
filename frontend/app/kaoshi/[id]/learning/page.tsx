import { ExamRunner } from "@/features/exam/components/ExamRunner";
import { loadExamPaper } from "@/features/exam/load-paper";

export default async function ExamLearningPage({ params, searchParams }: {
  params: Promise<{ id: string }>;
  searchParams: Promise<{ name?: string; phone?: string }>;
}) {
  const { id } = await params;
  const query = await searchParams;
  const paper = await loadExamPaper(id);
  return <main className="min-h-screen bg-[#f3f6fa] px-4 py-8 sm:py-12">
    <div className="mx-auto max-w-3xl">
      <h1 className="mb-2 text-2xl font-bold text-slate-800">培训视频学习</h1>
      <p className="mb-6 text-sm text-slate-500">{paper.title}</p>
      <div className="rounded-2xl bg-white p-5 shadow-sm sm:p-8">
        <ExamRunner paper={paper} initialName={query.name || ""} initialPhone={query.phone || ""} allowRestore={false} learningPage compact />
      </div>
    </div>
  </main>;
}
