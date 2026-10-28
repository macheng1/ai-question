"use client";
import { IconArrowLeft, IconArrowRight } from "@douyinfe/semi-icons";
import { useEffect, useState } from "react";
import { useParams } from "next/navigation";
import Link from "next/link";
import { getSurvey, type Training } from "@/features/trainings/api";
import { SurveyForm } from "@/features/trainings/components/SurveyForm";
import { Banner, Spin } from "@douyinfe/semi-ui";
import { useAuthStore } from "@/features/auth/store";
export default function TrainingSurveyPage() {
  const { id } = useParams<{ id: string }>();
  const [loginRedirect, setLoginRedirect] = useState(`/training/${id}/survey`);
  const [returnPath, setReturnPath] = useState("");
  const [training, setTraining] = useState<Training>();
  const [error, setError] = useState("");
  const [loginRequired, setLoginRequired] = useState(false);
  const [loading, setLoading] = useState(true);
  useEffect(() => {
    setLoginRedirect(window.location.pathname + window.location.search);
    const params = new URLSearchParams(window.location.search);
    const exam = params.get("examId"), attempt = params.get("attemptId");
    if (exam && attempt && /^\d+$/.test(exam) && /^\d+$/.test(attempt)) setReturnPath(`/kaoshi/${exam}/result?attemptId=${attempt}`);
  }, []);
  const token = useAuthStore(state => state.accessToken);
  useEffect(() => { setLoading(true); setError(""); if (!useAuthStore.getState().accessToken) { setLoginRequired(true); setLoading(false); return; } setLoginRequired(false); getSurvey(id).then(setTraining).catch(e => { if (e.status === 401) setLoginRequired(true); else setError(e.message); }).finally(() => setLoading(false)); }, [id, token]);
  return <main className="survey-h5 min-h-dvh bg-[#f3f6fa] px-4 pt-5 pb-[max(24px,env(safe-area-inset-bottom))] sm:py-10">
    <div className="mx-auto w-full max-w-[510px]">
      <Link href={returnPath || (training?.assessmentId ? `/kaoshi/${training.assessmentId}` : "/profile")} className="mb-4 inline-flex min-h-11 items-center gap-2 text-sm text-slate-500"><IconArrowLeft aria-hidden="true" />返回考试</Link>
      <header className="mb-5 rounded-2xl bg-gradient-to-r from-[#4bcac2] to-[#3db9ad] px-5 py-6 text-white">
        <h1 className="text-xl font-bold">满意度评估</h1>
        <p className="mt-2 text-sm leading-6 text-white/90">你的反馈将帮助我们改进培训内容与体验</p>
      </header>
      {loading ? <div className="rounded-2xl bg-white p-10 text-center"><Spin tip="正在加载评估…" /></div>
        : loginRequired ? <section className="rounded-2xl bg-white p-6"><h2 className="font-semibold">请先登录后填写本次培训评估</h2><p className="my-3 text-sm leading-6 text-slate-500">登录后将返回此页面继续填写。</p><Link className="inline-flex min-h-11 items-center gap-2 text-teal-700" href={`/login?redirect=${encodeURIComponent(loginRedirect)}`}>前往登录<IconArrowRight aria-hidden="true" /></Link></section>
        : error ? <Banner type="danger" description={error} closeIcon={null} />
        : training && <>
          <section className="mb-5 px-1"><h2 className="break-words text-base font-semibold leading-7 text-slate-800">{training.name}</h2>{training.description && <p className="mt-2 whitespace-pre-wrap break-words text-sm leading-6 text-slate-500">{training.description}</p>}</section>
          <SurveyForm key={training.id} training={training} />
        </>}
    </div>
  </main>;
}
