
import { IconArrowLeft, IconEdit } from "@/features/exam/components/ExamIcons";
import { ExamRunner } from "@/features/exam/components/ExamRunner";
import { loadExamPaper } from "@/features/exam/load-paper";

export default async function ExamAnswerPage({
  params,
  searchParams,
}: {
  params: Promise<{ id: string }>;
  searchParams: Promise<{ name?: string; phone?: string; attemptId?: string }>;
}) {
  const { id } = await params;
  const query = await searchParams;
  const attemptId = query.attemptId ? Number(query.attemptId) : undefined;
  const paper = await loadExamPaper(id, {
    // 随机试卷必须按本次答题实例读取快照，不能回退到原始试卷。
    attemptId: Number.isFinite(attemptId) ? attemptId : undefined,
  });
  return (
    <main className="min-h-screen overflow-x-hidden bg-[#f3f6fa] px-3 py-5 sm:px-4 sm:py-12">
      <div className="mx-auto w-full max-w-[1100px]">
        <a
          href="/profile"
          className="mb-4 block py-1 text-sm text-slate-500 no-underline"
        >
          <IconArrowLeft aria-hidden="true" /> 返回个人中心
        </a>
        <div className="mb-6 text-center">
          <div className="mx-auto mb-3 grid h-11 w-11 place-items-center rounded-xl bg-[#dff7f5] text-2xl">
            <IconEdit aria-hidden="true" />
          </div>
          <h1 className="break-words text-xl font-bold leading-8 text-slate-800 sm:text-2xl">
            {paper.title}
          </h1>
          <p className="mt-2 text-sm text-slate-400">
            答题过程中会自动保存进度
          </p>
        </div>
        <ExamRunner
          paper={paper}
          initialStage="answer"
          initialName={query.name || ""}
          initialPhone={query.phone || ""}
          initialAttemptId={Number.isFinite(attemptId) ? attemptId : undefined}
        />
      </div>
    </main>
  );
}
