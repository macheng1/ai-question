"use client";

import { useRouter } from "next/navigation";
import { useAuthStore } from "@/features/auth/store";
import Link from "next/link";
import { FormEvent, useState } from "react";
import { PageShell } from "@/components/layout/PageShell";

const features = [
  ["▣", "企业在线考试", "创建培训考核任务并发布考试链接，员工无需注册即可快速参加。"],
  ["◈", "题库与组卷复用", "沉淀企业专属题库，固定组卷或随机抽题，适配不同岗位和批次。"],
  ["▤", "自动判分交卷", "客观题自动判分，减少人工阅卷，让培训管理员更快拿到结果。"],
  ["◌", "成绩与数据分析", "查看参与情况、通过率、成绩分布和题目表现，辅助培训决策。"],
];

const useCases = [
  ["企业培训", "新员工培训、岗位认证和安全生产考核", "bg-[#e8faf9] text-[#239f99]", "培训"],
  ["岗位自测", "让员工在正式考核前完成岗位知识练习和模拟考试", "bg-[#fff3e8] text-[#e87516]", "自测"],
  ["培训管理", "组卷、发卷、阅卷和统计一站式完成，过程清晰可追踪", "bg-[#f0ebff] text-[#7654d6]", "管理"],
];

export default function HomePage() {
  const router = useRouter();
  const accessToken = useAuthStore((state) => state.accessToken);
  const openEnterpriseWorkspace = () => {
    router.push(accessToken ? "/profile/banks" : "/login?redirect=/profile/banks");
  };

  const [code, setCode] = useState("");
  const enterExam = (event: FormEvent) => {
    event.preventDefault();
    const value = code.trim();
    if (value) window.location.href = `/kaoshi/${encodeURIComponent(value)}`;
  };

  return <PageShell><main className="overflow-hidden bg-white">
    <section className="relative bg-[#f5fbfa]">
      <div className="absolute -right-28 -top-28 h-80 w-80 rounded-full bg-[#dff8f6] blur-3xl" />
      <div className="mx-auto grid w-[92%] max-w-[1240px] items-center gap-12 py-16 sm:py-24 lg:grid-cols-[1fr_.9fr] lg:gap-20 lg:py-28">
        <div className="relative"><span className="inline-flex rounded-full bg-[#dff8f6] px-4 py-2 text-xs font-bold tracking-wide text-[#239f99]">企业培训与人才测评平台</span><h1 className="mt-6 max-w-2xl text-4xl font-bold leading-[1.18] tracking-tight text-[#18353d] sm:text-6xl">让企业考试，<br/><span className="text-[#239f99]">更简单、更可靠</span></h1><p className="mt-6 max-w-xl text-base leading-8 text-slate-500 sm:text-lg">从企业题库、岗位组卷到员工答题和成绩分析，智企云考帮助培训管理者高效完成全流程考核。</p><div className="mt-8 flex flex-col gap-3 sm:flex-row"><button type="button" onClick={openEnterpriseWorkspace} className="rounded-lg bg-[#ff8500] px-6 py-3.5 text-center text-sm font-bold text-white shadow-lg shadow-orange-100 transition hover:-translate-y-0.5 hover:bg-[#e97600]">创建企业考试</button><Link href="/banks" className="rounded-lg border border-slate-300 bg-white px-6 py-3.5 text-center text-sm font-bold text-slate-700 transition hover:border-[#52ccc4] hover:text-[#239f99]">查看公开题库</Link></div><div className="mt-8 flex flex-wrap gap-x-6 gap-y-2 text-xs text-slate-500"><span>✓ 员工免注册参试</span><span>✓ 支持手机答题</span><span>✓ 结果自动汇总</span></div></div>
        <div className="relative"><div className="absolute -inset-5 rounded-[2rem] bg-gradient-to-br from-[#b9ece8]/60 to-[#fff0df]/70 blur-2xl"/><div className="relative rounded-2xl border border-slate-200 bg-white p-5 shadow-[0_20px_60px_rgba(25,80,80,.12)] sm:p-7"><div className="flex items-center justify-between border-b border-slate-100 pb-5"><div className="flex items-center gap-3"><span className="grid h-11 w-11 place-items-center rounded-xl bg-[#e8faf9] text-xl text-[#239f99]">✓</span><div><b className="block text-sm text-slate-800">企业安全培训考试</b><span className="mt-1 block text-xs text-slate-400">2026 年度线上考核</span></div></div><span className="rounded-full bg-[#e8faf9] px-3 py-1 text-xs font-semibold text-[#239f99]">进行中</span></div><div className="space-y-3 py-6"><div className="rounded-xl bg-[#f7fafc] p-4"><div className="flex items-center justify-between text-xs text-slate-400"><span>考试完成度</span><b className="text-[#239f99]">76%</b></div><div className="mt-3 h-2 rounded-full bg-slate-200"><div className="h-2 w-3/4 rounded-full bg-[#35c5bd]"/></div></div><div className="grid grid-cols-3 gap-3 text-center"><div className="rounded-xl bg-[#f7fafc] p-3"><b className="block text-xl text-slate-800">128</b><span className="mt-1 block text-[11px] text-slate-400">已参与</span></div><div className="rounded-xl bg-[#f7fafc] p-3"><b className="block text-xl text-slate-800">92%</b><span className="mt-1 block text-[11px] text-slate-400">完成率</span></div><div className="rounded-xl bg-[#f7fafc] p-3"><b className="block text-xl text-slate-800">86</b><span className="mt-1 block text-[11px] text-slate-400">平均分</span></div></div></div><div className="rounded-xl bg-[#18353d] p-4 text-center text-sm font-semibold text-white">考试数据已实时更新</div></div></div>
      </div>
    </section>

    <section className="hidden mx-auto w-[92%] max-w-[1240px] py-14 sm:py-20"><div className="mx-auto max-w-2xl text-center"><p className="text-xs font-bold tracking-[.24em] text-[#239f99]">EMPLOYEE ACCESS</p><h2 className="mt-3 text-3xl font-bold text-slate-800 sm:text-4xl">员工收到考试码后即可参加</h2><p className="mt-3 text-sm leading-7 text-slate-500">管理员发布考试后，员工输入考试码即可进入答题，不需要单独注册账号。</p></div><form onSubmit={enterExam} className="mx-auto mt-8 flex max-w-xl flex-col gap-3 rounded-2xl border border-slate-200 bg-white p-3 shadow-[0_10px_35px_rgba(15,23,42,.06)] sm:flex-row"><input value={code} onChange={(event) => setCode(event.target.value)} placeholder="请输入企业考试码" aria-label="企业考试码" className="min-w-0 flex-1 rounded-xl border-0 bg-slate-50 px-5 py-3.5 text-sm text-slate-700 outline-none placeholder:text-slate-400 focus:ring-2 focus:ring-[#b9ece8]"/><button type="submit" className="rounded-xl bg-[#239f99] px-7 py-3.5 text-sm font-bold text-white transition hover:bg-[#1d817b]">进入考试</button></form></section>

    <section className="border-y border-slate-100 bg-[#fbfcfd]"><div className="mx-auto w-[92%] max-w-[1240px] py-16 sm:py-20"><div className="max-w-2xl"><p className="text-xs font-bold tracking-[.24em] text-[#239f99]">ONE PLATFORM</p><h2 className="mt-3 text-3xl font-bold text-slate-800 sm:text-4xl">一套平台，覆盖完整考试流程</h2><p className="mt-4 text-sm leading-7 text-slate-500">把复杂的考试工作拆成清晰、简单、可追踪的每一步。</p></div><div className="mt-10 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">{features.map(([icon, title, desc]) => <article key={title} className="rounded-2xl border border-slate-200 bg-white p-6 transition hover:-translate-y-1 hover:border-[#b9ece8] hover:shadow-lg hover:shadow-[#e8faf9]"><span className="grid h-11 w-11 place-items-center rounded-xl bg-[#e8faf9] text-xl text-[#2db9b0]">{icon}</span><h3 className="mt-5 font-bold text-slate-800">{title}</h3><p className="mt-3 text-sm leading-7 text-slate-500">{desc}</p></article>)}</div></div></section>

    <section className="mx-auto w-[92%] max-w-[1240px] py-16 sm:py-20"><div className="flex flex-col justify-between gap-5 sm:flex-row sm:items-end"><div><p className="text-xs font-bold tracking-[.24em] text-[#239f99]">BUILT FOR YOU</p><h2 className="mt-3 text-3xl font-bold text-slate-800 sm:text-4xl">适用于不同的考试场景</h2></div><Link href="/guide" className="text-sm font-bold text-[#239f99] no-underline">查看使用指南 →</Link></div><div className="mt-10 grid gap-5 lg:grid-cols-3">{useCases.map(([title, desc, color, icon]) => <article key={title} className="rounded-2xl border border-slate-200 bg-white p-7 shadow-sm"><span className={`grid h-12 w-12 place-items-center rounded-xl text-sm font-bold ${color}`}>{icon}</span><h3 className="mt-6 text-xl font-bold text-slate-800">{title}</h3><p className="mt-3 text-sm leading-7 text-slate-500">{desc}</p><Link href={title === "在线自测" ? "/self-test" : title === "企业培训" ? "/login" : "/profile"} className="mt-6 inline-block text-sm font-bold text-[#239f99] no-underline">了解更多 →</Link></article>)}</div></section>

    <section className="bg-[#18353d]"><div className="mx-auto flex w-[92%] max-w-[1240px] flex-col items-start justify-between gap-6 py-12 sm:flex-row sm:items-center"><div><h2 className="text-2xl font-bold text-white sm:text-3xl">准备好开始你的下一场考试了吗？</h2><p className="mt-3 text-sm text-slate-300">注册账号，几分钟内创建第一场考试。</p></div><Link href="/login" className="rounded-lg bg-[#ff8500] px-6 py-3.5 text-center text-sm font-bold text-white no-underline">立即开始</Link></div></section>
  </main></PageShell>;
}
