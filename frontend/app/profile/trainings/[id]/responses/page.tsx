"use client";
import { IconArrowLeft } from "@douyinfe/semi-icons";
import { useEffect, useState } from "react";
import { useParams } from "next/navigation";
import Link from "next/link";
import { getTraining, getResponses, type Training, type SurveyResponse } from "@/features/trainings/api";
import { TrainingShell } from "@/features/trainings/components/TrainingShell";
export default function ResponsesPage() {
  const { id } = useParams<{ id: string }>();
  const [training, setTraining] = useState<Training>();
  const [items, setItems] = useState<SurveyResponse[]>([]);
  const [error, setError] = useState("");
  useEffect(() => { Promise.all([getTraining(id), getResponses(id)]).then(([t,r]) => { setTraining(t); setItems(r); }).catch(e => setError(e.message)); }, [id]);
  return <TrainingShell><Link className="text-teal-700" href="/profile/trainings">{<IconArrowLeft />} 返回培训列表</Link><h1 className="my-5 text-2xl font-bold">{training?.name} · 评估反馈</h1>{error ? <p role="alert">{error}</p> : !training ? <p>加载中…</p> : <><p className="mb-5 text-slate-500">共 {items.length} 人提交，反馈按提交时间倒序排列。</p><div className="mb-6 grid gap-3 sm:grid-cols-2">{training.questions.filter(q => q.type === "RATING").map(q => { const values = items.map(r => Number(r.answers[q.id])).filter(n => n >= 1 && n <= 5); return <div key={q.id} className="rounded-xl border border-teal-100 bg-white p-5"><p className="text-sm text-slate-500">{q.title}</p><p className="mt-2 text-2xl font-bold text-teal-700">{values.length ? (values.reduce((a,b) => a+b,0)/values.length).toFixed(1) : "—"}<span className="ml-2 text-xs font-normal text-slate-500">/ 5 分 · {values.length} 人作答</span></p></div>; })}</div>{!items.length && <p className="rounded-xl bg-white p-10 text-center text-slate-500">暂无反馈</p>}<div className="space-y-4">{items.map(item => <article key={item.id} className="rounded-2xl border border-slate-200 bg-white p-5"><div className="mb-4 flex flex-wrap justify-between gap-2"><strong>{item.respondentName}</strong><span className="text-sm text-slate-400">{new Date(item.submittedAt).toLocaleString("zh-CN", { timeZone: "Asia/Shanghai", hour12: false })}</span></div>{training.questions.map(q => <div key={q.id} className="mt-3"><p className="text-sm text-slate-500">{q.title}</p><p className="mt-1 whitespace-pre-wrap break-words">{item.answers[q.id] || "未填写"}{q.type === "RATING" && item.answers[q.id] ? " 分" : ""}</p></div>)}</article>)}</div></>}</TrainingShell>;
}
