"use client";
import { Typography } from "@douyinfe/semi-ui";
import { Layout } from "@douyinfe/semi-ui";
import { IconArrowRight } from "@douyinfe/semi-icons";
import { useEffect, useState } from "react";
import { useParams } from "next/navigation";
import Link from "next/link";
import { getSurvey, type Training } from "@/features/trainings/api";
import { SurveyForm } from "@/features/trainings/components/SurveyForm";
import { Banner, Spin } from "@douyinfe/semi-ui";
import { H5Shell } from "@/features/exam/components/H5Shell";
import { useLearnerStore } from "@/features/auth/store";
export default function TrainingSurveyPage() {
  const { id } = useParams<{ id: string }>();
  const [returnPath, setReturnPath] = useState("");
  const [submitted, setSubmitted] = useState(false);
  const [training, setTraining] = useState<Training>();
  const [error, setError] = useState("");
  const [loginRequired, setLoginRequired] = useState(false);
  const [loading, setLoading] = useState(true);
  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    const exam = params.get("examId"), attempt = params.get("attemptId");
    if (exam && attempt && /^\d+$/.test(exam) && /^\d+$/.test(attempt)) setReturnPath(`/kaoshi/${exam}/result?attemptId=${attempt}`);
  }, []);
  const token = useLearnerStore(state => state.accessToken);
  useEffect(() => { setLoading(true); setError(""); if (!useLearnerStore.getState().accessToken) { setLoginRequired(true); setLoading(false); return; } setLoginRequired(false); getSurvey(id).then(setTraining).catch(e => { if (e.status === 401) setLoginRequired(true); else setError(e.message); }).finally(() => setLoading(false)); }, [id, token]);
  const backHref = returnPath || (training?.assessmentId ? `/kaoshi/${training.assessmentId}` : `/training/${id}`);
  return <H5Shell title={submitted || training?.submitted ? "提交成功" : "满意度评估"} backHref={backHref}><div className="h5-content">
    {!submitted && !training?.submitted && <Layout.Header className="h5-survey-heading"><span className="h5-eyebrow">YOUR FEEDBACK MATTERS</span><Typography.Title heading={1} className="!my-3 !text-2xl !leading-9">你的感受，很重要</Typography.Title><p>{training?.name || "你的反馈将帮助我们改进培训内容与体验"}</p></Layout.Header>}
    {loading ? <div className="h5-empty flex flex-col items-center justify-center gap-3"><Spin size="large" /><p>正在加载评估…</p></div>
      : loginRequired ? <Layout.Content className="h5-empty"><Typography.Title heading={2} className="!text-lg !leading-7">请先确认学员身份</Typography.Title><p>请从培训入口确认姓名和手机号后继续。</p><Link className="inline-flex min-h-11 items-center gap-2 text-teal-700" href={`/training/${id}`}>返回培训入口<IconArrowRight aria-hidden="true" /></Link></Layout.Content>
      : error ? <Banner type="danger" description={error} closeIcon={null} />
      : training && <SurveyForm key={training.id} training={training} h5 returnHref={backHref} onComplete={() => setSubmitted(true)} />}
  </div></H5Shell>;
}
