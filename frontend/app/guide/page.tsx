import { PageShell } from "@/components/layout/PageShell";

const steps = [["01", "创建企业题库", "按部门、岗位或培训主题沉淀企业专属题目，后续考试可以持续复用。"], ["02", "组卷并发布考试", "选择固定试卷或随机组卷，设置考试时间、次数和及格线后发布。"], ["03", "员工进入答题", "员工通过考试链接、二维码或考试码参加，无需额外注册账号。"], ["04", "查看结果与统计", "考试结束后查看成绩、参与情况和题目表现，及时调整培训内容。"]];

export default function GuidePage() {
  return <PageShell><main className="min-h-[720px] bg-[#f7fafc]">
    <section className="border-b border-slate-100 bg-white"><div className="mx-auto w-[92%] max-w-4xl py-14 sm:py-20"><p className="text-xs font-bold tracking-[.25em] text-teal-600">ENTERPRISE GUIDE</p><h1 className="mt-4 text-4xl font-bold text-slate-800">企业考试帮助手册</h1><p className="mt-4 text-sm text-slate-500">从建立企业题库到完成员工考核，按下面的流程即可快速上手。</p></div></section>
    <section className="mx-auto w-[92%] max-w-4xl py-10 sm:py-14"><div className="grid gap-4 sm:grid-cols-2">{steps.map(([number, title, desc]) => <article key={number} className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm"><span className="text-sm font-bold text-teal-500">{number}</span><h2 className="mt-4 text-lg font-bold text-slate-800">{title}</h2><p className="mt-2 text-sm leading-7 text-slate-500">{desc}</p></article>)}</div><div className="mt-8 rounded-2xl border border-slate-200 bg-slate-50 p-6 text-sm leading-7 text-slate-500"><b className="text-slate-600">温馨提示：</b>考试过程中请保持网络稳定；如果考试时间结束，系统会按照后端状态提示考试已结束。</div></section>
  </main></PageShell>;
}
