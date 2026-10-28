"use client";
import { IconTickCircle } from "@douyinfe/semi-icons";
import { useState } from "react";
import "@douyinfe/semi-ui/react19-adapter";
import { Button, Radio, RadioGroup, TextArea, Toast } from "@douyinfe/semi-ui";
import { submitSurvey, type Training } from "../api";
export function SurveyForm({ training, preview = false }: { training: Training; preview?: boolean }) {
  const [answers, setAnswers] = useState<Record<string, string>>({});
  const [busy, setBusy] = useState(false);
  const [done, setDone] = useState(training.submitted || false);
  const set = (id: string, value: string) => setAnswers(old => ({ ...old, [id]: value }));
  const submit = async () => {
    const missing = training.questions.find(q => q.required && !answers[q.id]?.trim());
    if (missing) { Toast.error(`请填写：${missing.title}`); document.getElementById(`question-${missing.id}`)?.focus(); return; }
    if (preview) { Toast.info("预览填写完成，不会保存反馈"); return; }
    setBusy(true);
    try { await submitSurvey(training.id, training.version, answers); setDone(true); }
    catch (e) { Toast.error(e instanceof Error ? e.message : "提交失败，请重试"); }
    finally { setBusy(false); }
  };
  if (done) return <div role="status" className="rounded-2xl border border-teal-100 bg-white px-5 py-9 text-center"><IconTickCircle aria-hidden="true" style={{ fontSize: 44, color: "#2db9b0", marginBottom: 16 }} /><h2 className="text-xl font-bold text-teal-700">感谢你的反馈</h2><p className="mt-3 text-sm leading-6 text-slate-500">本次满意度评估已提交，无需重复填写。</p></div>;
  return <div className="survey-form space-y-4">
    <div className="rounded-xl bg-teal-50 px-4 py-3 text-xs leading-6 text-teal-800">{preview ? "预览模式：可模拟填写，不会提交正式记录。" : "本次评估实名填写，每个账号可提交一次。标记 * 的题目为必填。"}</div>
    {training.questions.map((q, index) => <section tabIndex={-1} id={`question-${q.id}`} aria-labelledby={`question-title-${q.id}`} key={q.id} className="min-w-0 rounded-2xl border border-slate-200 bg-white p-4 sm:p-5">
      <h3 id={`question-title-${q.id}`} className="mb-4 text-sm font-semibold leading-6 text-slate-800">{index + 1}. {q.title} {q.required && <span className="text-red-500">*</span>}</h3>
      {q.type === "RATING" && <div><RadioGroup name={`survey-${training.id}-${q.id}`} disabled={busy} aria-label={q.title} type="button" value={answers[q.id]} onChange={e => set(q.id, e.target.value)} className="survey-rating">{[1,2,3,4,5].map(n => <Radio key={n} value={String(n)}>{n} 分</Radio>)}</RadioGroup><p className="mt-3 text-xs text-slate-500">1 分非常不满意 · 5 分非常满意</p></div>}
      {q.type === "SINGLE" && <RadioGroup name={`survey-${training.id}-${q.id}`} disabled={busy} aria-label={q.title} direction="vertical" className="survey-choices" value={answers[q.id]} onChange={e => set(q.id, e.target.value)}>{q.options.map((option, i) => <Radio key={i} value={option}>{option}</Radio>)}</RadioGroup>}
      {q.type === "TEXT" && <><TextArea disabled={busy} aria-label={q.title} maxLength={1000} rows={4} placeholder="请输入你的意见或建议" value={answers[q.id] || ""} onChange={value => set(q.id, value)} className="survey-comment w-full" /><p className="mt-2 text-right text-xs text-slate-400">{answers[q.id]?.length || 0}/1000</p></>}
    </section>)}
    <Button theme="solid" type="warning" size="large" block loading={busy} disabled={!training.questions.length} onClick={submit}>{preview ? "体验提交（不保存）" : "提交评估"}</Button>
  </div>;
}
