"use client";
import { IconArrowRight } from "@douyinfe/semi-icons";
import { useEffect, useState } from "react";
import { useParams } from "next/navigation";
import Link from "next/link";
import { getSurvey, type Training } from "@/features/trainings/api";
import { SurveyForm } from "@/features/trainings/components/SurveyForm";
import { Banner, Spin } from "@douyinfe/semi-ui";
import { H5Shell } from "@/features/exam/components/H5Shell";
import { useAuthStore } from "@/features/auth/store";
export default function TrainingSurveyPage() {
  const { id } = useParams<{ id: string }>();
  const [loginRedirect, setLoginRedirect] = useState(`/training/${id}/survey`);
  const [returnPath, setReturnPath] = useState("");
  const [submitted, setSubmitted] = useState(false);
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
  const backHref = returnPath || (training?.assessmentId ? `/kaoshi/${training.assessmentId}` : "/profile");
  return <H5Shell title={submitted || training?.submitted ? "提交成功" : "满意度评估"} backHref={backHref}><div className="h5-content">
    {!submitted && !training?.submitted && <header className="h5-survey-heading"><span className="h5-eyebrow">YOUR FEEDBACK MATTERS</span><h1>你的感受，很重要</h1><p>{training?.name || "你的反馈将帮助我们改进培训内容与体验"}</p></header>}
    {loading ? <div className="h5-empty"><Spin tip="正在加载评估…" /></div>
      : loginRequired ? <section className="h5-empty"><h2>请先登录后填写本次培训评估</h2><p>登录后将返回此页面继续填写。</p><Link className="inline-flex min-h-11 items-center gap-2 text-teal-700" href={`/login?redirect=${encodeURIComponent(loginRedirect)}`}>前往登录<IconArrowRight aria-hidden="true" /></Link></section>
      : error ? <Banner type="danger" description={error} closeIcon={null} />
      : training && <SurveyForm key={training.id} training={training} h5 returnHref={backHref} onComplete={() => setSubmitted(true)} />}
  </div></H5Shell>;
}
