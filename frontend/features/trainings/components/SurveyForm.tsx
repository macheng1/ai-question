"use client";
import { Typography } from "@douyinfe/semi-ui";
import { IconComment } from "@douyinfe/semi-icons";
import { useState } from "react";
import "@douyinfe/semi-ui/react19-adapter";
import { Button, Radio, RadioGroup, TextArea, Toast } from "@douyinfe/semi-ui";
import { submitSurvey, type Training } from "../api";
export function SurveyForm({ training, preview = false, h5 = false, returnHref = "/learner", onComplete }: { training: Training; preview?: boolean; h5?: boolean; returnHref?: string; onComplete?: () => void }) {
  const [answers, setAnswers] = useState<Record<string, string>>(preview ? {} : training.submittedAnswers || {});
  const [busy, setBusy] = useState(false);
  const [done, setDone] = useState(!preview && (training.submitted || false));
  const set = (id: string, value: string) => setAnswers(old => ({ ...old, [id]: value }));
  const submit = async () => {
    const missing = training.questions.find(q => q.required && !answers[q.id]?.trim());
    if (missing) { Toast.error(`请填写：${missing.title}`); document.getElementById(`question-${missing.id}`)?.focus(); return; }
    if (preview) { Toast.info("预览填写完成，不会保存反馈"); return; }
    setBusy(true);
    try { await submitSurvey(training.id, training.version, answers); setDone(true); onComplete?.(); }
    catch (e) { Toast.error(e instanceof Error ? e.message : "提交失败，请重试"); }
    finally { setBusy(false); }
  };
  const feedback = <div className="mt-6 flex w-full flex-col gap-4 text-left">
    <h3 className="text-base font-semibold">我的反馈</h3>
    {training.questions.map((q, index) => <section key={q.id} className="rounded-xl border border-slate-200 bg-white p-4">
      <h4 className="mb-2 text-sm font-medium">{index + 1}. {q.title}</h4>
      <p className="whitespace-pre-wrap break-words text-sm leading-7 text-slate-600">{answers[q.id]?.trim() ? `${answers[q.id]}${q.type === "RATING" ? " 分 / 5 分" : ""}` : "未填写"}</p>
    </section>)}
  </div>;
  if (done) return h5 ? <div className="h5-survey-success" role="status"><div className="h5-success-symbol"><IconComment /></div><span className="h5-eyebrow">THANK YOU</span><Typography.Title heading={2} className="!my-4 !text-2xl">反馈已收到</Typography.Title><p>感谢你的认真参与。<br />你的建议将帮助我们持续改进培训。</p><div className="h5-success-training"><span className="h5-muted">本次评估</span><Typography.Title heading={3} className="!my-3 !text-base !leading-7">{training.name}</Typography.Title><p><span>满意度评估</span><b>已提交</b></p></div>{feedback}<p className="mt-5">本次评估无需重复填写</p><Button block size="large" className="h5-secondary mt-8" onClick={() => window.location.assign(returnHref)}>{returnHref.startsWith("/kaoshi/") ? "返回考试" : returnHref.startsWith("/training/") ? "返回培训" : returnHref === "/learner/surveys" ? "返回评估列表" : returnHref.startsWith("/learner/trainings/") ? "返回培训详情" : "返回个人中心"}</Button></div> : <div role="status" className="rounded-2xl border border-teal-100 bg-white px-5 py-9 text-center"><Typography.Title heading={2} className="!text-xl !font-bold !text-teal-700">感谢你的反馈</Typography.Title><p className="mt-3 text-sm leading-6 text-slate-500">本次满意度评估已提交，无需重复填写。</p>{feedback}</div>;
  return <div className="flex flex-col survey-form gap-4">
    <div className="survey-notice rounded-xl bg-teal-50 px-4 py-3 text-xs leading-6 text-teal-800">{preview ? "预览模式：可模拟填写，不会提交正式记录。" : "本次评估实名填写，每个账号可提交一次。标记 * 的题目为必填。"}</div>
    {training.questions.map((q, index) => <section tabIndex={-1} id={`question-${q.id}`} aria-labelledby={`question-title-${q.id}`} key={q.id} className="survey-question min-w-0 rounded-2xl border border-slate-200 bg-white p-4 sm:p-5">
      <h3 id={`question-title-${q.id}`} className="mb-4 text-sm font-semibold leading-6 text-slate-800"><span className="h5-question-index">{String(index + 1).padStart(2, "0")}</span>{q.title} {q.required && <span className="text-red-500">*</span>}</h3>
      {q.type === "RATING" && <div><RadioGroup name={`survey-${training.id}-${q.id}`} disabled={busy} aria-label={q.title} type="button" value={answers[q.id]} onChange={e => set(q.id, e.target.value)} className="survey-rating">{[1,2,3,4,5].map(n => <Radio key={n} value={String(n)}>{n} 分</Radio>)}</RadioGroup><div className="h5-rating-labels"><span>1 分非常不满意</span><span>5 分非常满意</span></div></div>}
      {q.type === "SINGLE" && <RadioGroup name={`survey-${training.id}-${q.id}`} disabled={busy} aria-label={q.title} direction="vertical" className="survey-choices" value={answers[q.id]} onChange={e => set(q.id, e.target.value)}>{q.options.map((option, i) => <Radio key={i} value={option}>{option}</Radio>)}</RadioGroup>}
      {q.type === "TEXT" && <><TextArea disabled={busy} aria-label={q.title} maxLength={1000} rows={4} placeholder="请输入你的意见或建议" value={answers[q.id] || ""} onChange={value => set(q.id, value)} className="survey-comment w-full" /><p className="mt-2 text-right text-xs text-slate-400">{answers[q.id]?.length || 0}/1000</p></>}
    </section>)}
    <div className={h5 ? "h5-bottom h5-survey-submit" : "pt-2"}><Button className={h5 ? "h5-primary" : ""} theme="solid" type={h5 || preview ? "primary" : "warning"} size="large" block loading={busy} disabled={!training.questions.length} onClick={submit}>{preview ? "体验提交（不保存）" : "提交评估"}</Button></div>
  </div>;
}
