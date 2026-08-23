"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { Button, Input, Toast } from "@douyinfe/semi-ui";
import { IconClock, IconSearch, IconFile } from "@douyinfe/semi-icons";
import { PageShell } from "@/components/layout/PageShell";
import { getPublicSelfTests, type PublicSelfTest } from "@/features/self-test/api";
import { OnlineSelfTest } from "@/features/self-test/components/OnlineSelfTest";

/** 在线自测公开入口：只展示已发布的自测试卷，游客无需登录即可浏览。 */
export default function SelfTestPage() {
  const [legacyBankId, setLegacyBankId] = useState(0);
  const [keyword, setKeyword] = useState("");
  const [tests, setTests] = useState<PublicSelfTest[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const load = (value = keyword) => {
    setLoading(true);
    getPublicSelfTests({ keyword: value, pageSize: 100 }).then((result) => { setTests(result.records); setTotal(result.total); }).catch((error) => Toast.error(error instanceof Error ? error.message : "在线自测加载失败")).finally(() => setLoading(false));
  };
  useEffect(() => {
    const id = Number(new URLSearchParams(window.location.search).get("bankId"));
    setLegacyBankId(id || 0);
    if (!id) load("");
  }, []);
  if (legacyBankId) return <PageShell><OnlineSelfTest bankId={legacyBankId} /></PageShell>;
  return <PageShell><main className="min-h-[760px] bg-[#f5f8fa]">
    <section className="border-b border-slate-200 bg-gradient-to-r from-[#eefbfb] to-[#fffaf5]"><div className="mx-auto flex min-h-[220px] w-[92%] max-w-[1240px] items-center justify-between"><div><span className="inline-flex items-center gap-2 rounded-full bg-[#d9f6f3] px-4 py-2 text-xs font-semibold text-[#239f99]">◷ 岗位自测</span><h1 className="mt-6 text-4xl font-bold text-slate-800">岗位自测</h1><p className="mt-3 text-sm text-slate-500">用于员工培训预习、岗位练习和正式考核前的模拟检验</p></div><div className="mr-8 text-8xl text-[#9ce5e0] opacity-80">◷</div></div></section>
    <div className="mx-auto w-[92%] max-w-[1240px] py-14"><div className="rounded-2xl border border-slate-100 bg-white px-6 py-5 shadow-sm"><div className="flex flex-wrap items-center justify-between gap-4"><Input className="!w-[320px] !rounded-full" prefix={<IconSearch />} value={keyword} onChange={setKeyword} onEnterPress={() => load()} showClear placeholder="搜索试卷名称..." /><span className="text-sm text-slate-500">共 <b className="mx-1 text-[#35c5bd]">{total}</b> 套试卷</span></div></div>
      {loading ? <div className="py-24 text-center text-sm text-slate-400">正在加载自测试卷...</div> : tests.length === 0 ? <div className="py-24 text-center text-sm text-slate-400">暂无符合条件的自测试卷</div> : <div className="mt-7 grid gap-6 md:grid-cols-2 xl:grid-cols-3">{tests.map((test, index) => <article key={test.id} className="rounded-2xl border border-slate-100 bg-white p-6 shadow-sm transition hover:-translate-y-0.5 hover:shadow-md"><div className="flex items-start justify-between gap-3"><div className={`grid h-12 w-12 place-items-center rounded-xl ${index % 3 === 0 ? "bg-blue-50 text-blue-500" : index % 3 === 1 ? "bg-amber-50 text-amber-500" : "bg-emerald-50 text-emerald-500"}`}><IconFile size="large" /></div><span className="rounded-md bg-[#e8faf9] px-2 py-1 text-xs text-[#27aaa3]">随机题目</span></div><h2 className="mt-5 min-h-14 text-lg font-bold leading-7 text-slate-800">{test.name}</h2><p className="mt-2 min-h-12 text-sm leading-6 text-slate-500">{test.description || test.bankName}</p><div className="mt-5 flex gap-3 border-b border-slate-100 pb-5 text-xs"><span className="text-blue-600">单选 {test.singleCount}</span><span className="text-purple-600">多选 {test.multipleCount}</span><span className="text-orange-500">判断 {test.trueFalseCount}</span></div><div className="mt-5 flex items-center justify-between"><span className="flex items-center gap-1 text-sm text-slate-400"><IconFile size="small" /> 共 {test.questionCount} 题</span><Link href={`/self-test/${test.id}`}><Button theme="solid" type="warning" icon={<IconClock />}>开始自测</Button></Link></div></article>)}</div>}
    </div>
  </main></PageShell>;
}
