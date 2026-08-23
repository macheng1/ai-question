"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { PageShell } from "@/components/layout/PageShell";
import { getNews, type NewsArticle } from "@/features/news/api";

export default function NewsPage() {
  const [items, setItems] = useState<NewsArticle[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  useEffect(() => { getNews().then(setItems).catch((e) => setError(e instanceof Error ? e.message : "公告加载失败")).finally(() => setLoading(false)); }, []);
  return <PageShell><main className="min-h-[720px] bg-[#f7fafc]"><section className="border-b border-slate-100 bg-white"><div className="mx-auto w-[92%] max-w-4xl py-14 sm:py-20"><p className="text-xs font-bold tracking-[.25em] text-teal-600">NEWS & UPDATES</p><h1 className="mt-4 text-4xl font-bold text-slate-800">公告资讯</h1><p className="mt-4 text-sm text-slate-500">平台公告、功能更新与考试相关资讯。</p></div></section><section className="mx-auto w-[92%] max-w-4xl py-10 sm:py-14">{loading ? <div className="py-20 text-center text-sm text-slate-400">正在加载公告...</div> : error ? <div className="rounded-2xl border border-red-100 bg-red-50 p-6 text-center text-sm text-red-500">{error}</div> : items.length === 0 ? <div className="py-20 text-center text-sm text-slate-400">暂无公告资讯</div> : <div className="space-y-4">{items.map((item) => <Link key={item.id} href={`/news/${item.id}`} className="block rounded-2xl border border-slate-200 bg-white p-6 no-underline shadow-sm transition hover:-translate-y-0.5 hover:border-teal-200 hover:shadow-md"><div className="flex flex-wrap items-center justify-between gap-3"><h2 className="text-lg font-bold text-slate-800">{item.title}</h2><time className="text-xs text-slate-400">{new Date(item.publishedAt).toLocaleDateString("zh-CN")}</time></div><p className="mt-3 text-sm leading-7 text-slate-500">{item.summary || item.content}</p></Link>)}</div>}</section></main></PageShell>;
}
