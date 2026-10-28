"use client";

import { AppIcon } from "@/components/ui/AppIcon";

import Link from "next/link";
import { useEffect, useState } from "react";
import { useParams } from "next/navigation";
import { PageShell } from "@/components/layout/PageShell";
import { getNewsArticle, type NewsArticle } from "@/features/news/api";

export default function NewsDetailPage() {
  const params = useParams<{ id: string }>();
  const [item, setItem] = useState<NewsArticle | null>(null);
  const [error, setError] = useState("");
  useEffect(() => { const id = Number(params.id); if (!Number.isInteger(id)) { setError("公告不存在"); return; } getNewsArticle(id).then(setItem).catch((e) => setError(e instanceof Error ? e.message : "公告加载失败")); }, [params.id]);
  return <PageShell><main className="min-h-[720px] bg-[#f7fafc] px-4 py-10 sm:py-16"><article className="mx-auto max-w-3xl rounded-2xl border border-slate-200 bg-white p-6 shadow-sm sm:p-10">{error ? <p className="py-20 text-center text-sm text-red-500">{error}</p> : !item ? <p className="py-20 text-center text-sm text-slate-400">正在加载公告...</p> : <><Link href="/news" className="text-sm text-teal-600 no-underline"><AppIcon name="arrowLeft" /> 返回公告资讯</Link><h1 className="mt-6 text-3xl font-bold leading-tight text-slate-800">{item.title}</h1><time className="mt-3 block text-xs text-slate-400">{new Date(item.publishedAt).toLocaleDateString("zh-CN")}</time><p className="mt-8 whitespace-pre-wrap text-sm leading-8 text-slate-600">{item.content}</p></>}</article></main></PageShell>;
}
