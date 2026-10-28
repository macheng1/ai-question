
import { IconArrowLeft } from "@/features/exam/components/ExamIcons";
import { loadExamPaper } from "@/features/exam/load-paper";
import { ResultLoader } from "./ResultLoader";

export default async function ExamResultPage({
  params,
  searchParams,
}: {
  params: Promise<{ id: string }>;
  searchParams: Promise<{ attemptId?: string }>;
}) {
  const { id } = await params;
  const query = await searchParams;
  // 随机组卷的题目是在开始考试时生成的，成绩详情必须按本次 attemptId
  // 读取题目快照，不能重新读取未抽题的试卷配置。
  const paper = await loadExamPaper(id, {
    revealAnswers: true,
    attemptId: query.attemptId ? Number(query.attemptId) : undefined,
  });

  return (
    <main className="min-h-screen overflow-x-hidden bg-[#f3f6fa] px-3 py-5 sm:px-4 sm:py-12">
      <div className="mx-auto w-full max-w-[1100px]">
        <a
          href="/profile"
          className="print-hidden block py-1 text-sm text-slate-500 no-underline"
        >
          <IconArrowLeft aria-hidden="true" /> 返回个人中心
        </a>
        {query.attemptId ? (
          <ResultLoader paper={paper} attemptId={query.attemptId} />
        ) : (
          <section className="mx-auto mt-8 max-w-3xl rounded-2xl border border-slate-200 bg-white p-8 text-center text-sm text-slate-500 shadow-sm">
            缺少答题记录，无法查看成绩详情。
          </section>
        )}
      </div>
    </main>
  );
}
