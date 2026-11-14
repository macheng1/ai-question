"use client";

import { Button, Card, InputNumber, Toast } from "@douyinfe/semi-ui";
import Link from "next/link";
import { useParams } from "next/navigation";
import { useEffect, useState } from "react";
import { ProfileShell } from "@/components/layout/ProfileShell";
import { apiClient } from "@/lib/api/client";

type Attempt = { attemptId: number; name: string; attemptNo: number; submittedAt: string };
type Question = { questionId: number; stem: string; referenceAnswer: string; answer?: string; maxScore: number };

export default function GradingPage() {
  const task = useParams<{ id: string }>().id;
  const base = `/management/assessments/${task}/grading`;
  const [queue, setQueue] = useState<Attempt[]>([]);
  const [selected, setSelected] = useState<Attempt>();
  const [questions, setQuestions] = useState<Question[]>([]);
  const [scores, setScores] = useState<Record<number, number>>({});
  const [revision, setRevision] = useState(0);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    let active = true;
    setLoading(true);
    setError("");
    apiClient.get<Attempt[]>(base).then(rows => { if (active) setQueue(rows); })
      .catch(e => { if (active) setError(e.message); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [base, revision]);

  useEffect(() => {
    if (!selected) return;
    let active = true;
    apiClient.get<Question[]>(`${base}/${selected.attemptId}`)
      .then(rows => { if (active) setQuestions(rows); })
      .catch(e => { if (active) { Toast.error(e.message); setSelected(undefined); setRevision(n => n + 1); } });
    return () => { active = false; };
  }, [base, selected]);

  const finish = async () => {
    if (!selected || !questions.length || saving) return;
    if (questions.some(q => !Number.isFinite(scores[q.questionId]) || scores[q.questionId] < 0 || scores[q.questionId] > q.maxScore)) {
      Toast.error("请为每道问答题填写有效得分");
      return;
    }
    setSaving(true);
    try {
      await apiClient.put(`${base}/${selected.attemptId}`, scores);
      Toast.success("阅卷完成，成绩已更新");
      setSelected(undefined);
      setRevision(n => n + 1);
    } catch (e) { Toast.error(e instanceof Error ? e.message : "保存失败"); }
    finally { setSaving(false); }
  };

  return <ProfileShell>
    <Link href="/profile/trainings">返回培训管理</Link>
    <h1 className="my-4 text-2xl font-semibold">人工阅卷</h1>
    {selected ? <Card>
      <h2>{selected.name} · 第 {selected.attemptNo} 次答卷</h2>
      {!questions.length && <p>正在加载答卷…</p>}
      {questions.map((q, i) => <section key={q.questionId} className="space-y-3 border-b border-slate-200 py-4">
        <h3 className="whitespace-pre-wrap break-words">{i + 1}. {q.stem}</h3>
        <div><strong>学员回答</strong><p className="whitespace-pre-wrap break-words">{q.answer || "未作答"}</p></div>
        <div><strong>参考答案</strong><p className="whitespace-pre-wrap break-words">{q.referenceAnswer}</p></div>
        <label>得分（满分 {q.maxScore}） <InputNumber aria-label={`第 ${i + 1} 题得分`} min={0} max={q.maxScore} precision={2} disabled={saving}
          value={scores[q.questionId] ?? ""} onChange={value => setScores(old => {
            const next = { ...old };
            if (value === "" || value == null) delete next[q.questionId];
            else next[q.questionId] = Number(value);
            return next;
          })} /></label>
      </section>)}
      <div className="mt-4 flex gap-3"><Button disabled={saving} onClick={() => setSelected(undefined)}>返回名单</Button>
        <Button theme="solid" disabled={!questions.length} loading={saving} onClick={finish}>完成阅卷</Button></div>
    </Card> : <Card>
      <div className="flex items-center justify-between"><h2>待阅答卷</h2><Button onClick={() => setRevision(n => n + 1)}>刷新</Button></div>
      {error ? <p role="alert">{error}</p> : loading ? <p>加载中…</p> : !queue.length ? <p>暂无待阅答卷</p> :
        queue.map(a => <div key={a.attemptId} className="flex flex-wrap items-center justify-between gap-3 border-b border-slate-200 py-3">
          <span>{a.name} · 第 {a.attemptNo} 次 · {new Date(a.submittedAt).toLocaleString("zh-CN")}</span>
          <Button onClick={() => { setQuestions([]); setScores({}); setSelected(a); }}>阅卷</Button>
        </div>)}
    </Card>}
  </ProfileShell>;
}
