
import { IconArrowLeft, IconEdit } from "@/features/exam/components/ExamIcons";
import { ExamRunner } from "@/features/exam/components/ExamRunner";
import { loadExamPaper } from "@/features/exam/load-paper";

export default async function ExamPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = await params;
  const paper = await loadExamPaper(id);
  return (
    <main className="min-h-screen bg-[#f3f6fa] px-4 py-8 sm:py-12">
      <div className="mx-auto max-w-[510px]">
        <a
          href="/profile"
          className="mb-4 block text-sm text-slate-500 no-underline"
        >
          <IconArrowLeft aria-hidden="true" /> 返回个人中心
        </a>
        <section className="overflow-hidden rounded-2xl bg-white shadow-sm">
          <div className="bg-gradient-to-r from-[#4bcac2] to-[#3db9ad] px-6 py-8 text-center text-white">
            <div className="mx-auto mb-3 grid h-11 w-11 place-items-center rounded-xl bg-white/80 text-2xl">
              <IconEdit aria-hidden="true" />
            </div>
            <h1 className="text-xl font-bold">
        {paper.title}
            </h1>
          </div>
          <div className="px-6 py-5">
            <h2 className="text-sm font-bold text-slate-700">考试详情</h2>
            <div className="mt-4 grid grid-cols-2 gap-3">
              <Stat value={paper.questionCount ?? paper.questions.length} label="题目数量" accent />
              <Stat value={paper.duration} label="限时（分钟）" />
              <Stat value={paper.totalScore} label="总分" />
              <Stat value={paper.passScore} label="及格分" orange />
            </div>
            <p className="mt-4 text-center text-xs text-slate-400">
              考试时间：{paper.startAt ? new Date(paper.startAt).toLocaleString("zh-CN", { timeZone: "Asia/Shanghai" }) : "不限"} ~ {paper.endAt ? new Date(paper.endAt).toLocaleString("zh-CN", { timeZone: "Asia/Shanghai" }) : "不限"}
            </p>
            <div className="mt-5 border-t border-slate-100 pt-4">
              <ExamRunner paper={paper} compact allowRestore={false} />
            </div>
          </div>
        </section>
      </div>
    </main>
  );
}
function Stat({
  value,
  label,
  accent,
  orange,
}: {
  value: number;
  label: string;
  accent?: boolean;
  orange?: boolean;
}) {
  return (
    <div className="rounded-xl bg-[#f4f7fb] px-4 py-4 text-center">
      <b
        className={`block text-2xl ${accent ? "text-[#42c7bf]" : orange ? "text-orange-500" : "text-slate-800"}`}
      >
        {value}
      </b>
      <span className="mt-1 block text-xs text-slate-400">{label}</span>
    </div>
  );
}
