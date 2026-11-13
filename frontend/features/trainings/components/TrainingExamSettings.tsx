"use client";

import { useEffect, useState } from "react";
import { Banner, Button, Checkbox, Input, InputNumber, Select, Switch, Tooltip } from "@douyinfe/semi-ui";
import { IconHelpCircleStroked } from "@douyinfe/semi-icons";
import { getPaper, getPapers, type Paper } from "@/features/papers/api";
import type { TrainingExam } from "../api";

export const emptyTrainingExam: TrainingExam = {
  enabled: false, name: "", paperId: null, startAt: null, endAt: null, durationMinutes: 30,
  allowRetake: false, maxAttempts: 1, shuffleQuestions: true, shuffleOptions: true, showResultImmediately: true,
};

export function validateTrainingExam(exam?: TrainingExam) {
  if (!exam?.enabled) return "";
  if (!exam.name?.trim()) return "请输入考试名称";
  if (!exam.paperId) return "请选择考试试卷";
  if (!exam.startAt || !exam.endAt || !Number.isFinite(Date.parse(exam.startAt)) || !Number.isFinite(Date.parse(exam.endAt)) || Date.parse(exam.endAt) <= Date.parse(exam.startAt)) return "考试结束时间必须晚于开始时间";
  if (!Number.isInteger(exam.durationMinutes) || exam.durationMinutes < 1 || exam.durationMinutes > 1440) return "考试时长须为 1–1440 分钟";
  if (exam.allowRetake && (!Number.isInteger(exam.maxAttempts) || exam.maxAttempts < 1 || exam.maxAttempts > 100)) return "最多答题次数须为 1–100 次";
  return "";
}

function RuleTip({ label, children }: { label: string; children: string }) {
  return <Tooltip trigger="click" position="topLeft" content={children}>
    <Button theme="borderless" type="tertiary" size="small" icon={<IconHelpCircleStroked />} aria-label={label} />
  </Tooltip>;
}

export function TrainingExamSettings({ value, onChange, disabled, locked, scopeLocked, started = false }: {
  value: TrainingExam; onChange: (value: TrainingExam) => void; disabled: boolean; locked: boolean; scopeLocked: boolean; started?: boolean;
}) {
  const [papers, setPapers] = useState<Paper[]>([]);
  const [paper, setPaper] = useState<Paper | null>(null);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);
  useEffect(() => {
    if (!value.enabled) return;
    let active = true;
    setLoading(true);
    getPapers().then(r => { if (active) { setPapers(r.records); setError(""); } })
      .catch(e => { if (active) setError(e instanceof Error ? e.message : "试卷加载失败"); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [value.enabled]);
  useEffect(() => {
    let active = true;
    setPaper(null);
    if (value.paperId) getPaper(value.paperId).then(p => { if (active) setPaper(p); })
      .catch(e => { if (active) setError(e instanceof Error ? e.message : "试卷加载失败"); });
    return () => { active = false; };
  }, [value.paperId]);
  const change = (patch: Partial<TrainingExam>) => onChange({ ...value, ...patch });
  const fixed = disabled || locked;
  const options = paper && !papers.some(p => p.id === paper.id) ? [paper, ...papers] : papers;
  return <div className="flex flex-col gap-6 p-6">
    <div className="flex items-center gap-2">
      <Switch checked={value.enabled} disabled={disabled || scopeLocked || locked} aria-label="开启培训考试" onChange={enabled => enabled ? change({ enabled }) : onChange({ ...emptyTrainingExam })} />
      <span className="font-semibold">开启培训考试</span>
      <RuleTip label="培训考试说明">开启后为本培训安排独立考试，复用已有试卷。同一学员可参加使用相同试卷的不同培训，各培训的答题次数与成绩独立记录；最后一步保存时才创建考试。</RuleTip>
    </div>
    {value.enabled && <>
      {error && <Banner type="danger" description={error} />}
      {locked && <Banner type="info" description={started ? "培训已开始，试卷和答题规则不可修改。" : "已有答卷，试卷、开始时间和答题规则已锁定。可以延期或调整允许次数，次数不能少于已经使用的次数。"} />}
      <div>
        <div className="mb-2 flex items-center gap-1"><span className="font-semibold">考试试卷 *</span><RuleTip label="试卷复用说明">直接使用已有试卷，不会复制试卷。考试名称可单独设置，不随培训名称修改。</RuleTip></div>
        <Select aria-label="考试试卷" filter motion={false} loading={loading} disabled={disabled || scopeLocked || loading} style={{ width: "100%" }}
          placeholder="请选择试卷" value={value.paperId ?? undefined} optionList={options.map(p => ({ value: p.id, label: p.name, disabled: p.status !== "PUBLISHED" }))}
          onChange={id => { const selected = options.find(p => p.id === Number(id)); change({ paperId: Number(id), name: value.name?.trim() || selected?.name || "", durationMinutes: selected?.durationMinutes || 30 }); }} />
        {paper && <p className="mt-2 text-sm text-slate-500">{paper.mode === "RANDOM" ? "随机组卷" : "固定组卷"} · {paper.questionCount ?? 0} 题 · 满分 {paper.totalScore} 分 · 及格 {paper.passScore} 分</p>}
      </div>
      <div><span className="mb-2 block font-semibold">考试名称 *</span><Input aria-label="考试名称" maxLength={200} disabled={disabled} value={value.name || ""} placeholder="请输入考试名称" onChange={name => change({name})} /></div>
      <div className="flex items-center gap-1 text-sm text-slate-500"><span>考试时间与培训时间一致</span><RuleTip label="考试时间说明">考试沿用第一步填写的培训开始和结束时间；修改培训时间后，保存时同步更新考试时间。培训开始后，开始时间不可修改。已有答卷时，结束时间只能延长。</RuleTip></div>
      <div className="grid gap-5 sm:grid-cols-2">
        <div><span className="mb-2 block font-semibold">单次考试时长 *</span><InputNumber aria-label="单次考试时长" suffix="分钟" min={1} max={1440} precision={0} disabled={fixed} value={value.durationMinutes} onChange={n => change({ durationMinutes: Number(n) || 0 })} /></div>
        <div><div className="mb-2 flex items-center gap-1"><span className="font-semibold">及格分数</span><RuleTip label="及格分数说明">沿用所选试卷的及格分数，请在试卷中设置。</RuleTip></div><InputNumber aria-label="及格分数" disabled value={paper?.passScore} /></div>
      </div>
      <div className="flex items-center gap-1"><Checkbox disabled={fixed} checked={value.shuffleQuestions} onChange={e => change({ shuffleQuestions: !!e.target.checked, shuffleOptions: !!e.target.checked })}>乱序</Checkbox><RuleTip label="乱序说明">同一题型内部的题目顺序打乱，同时打乱选项顺序。</RuleTip></div>
      <div className="flex flex-wrap items-center gap-2">
        <Checkbox disabled={disabled || started} checked={value.allowRetake} onChange={e => change({ allowRetake: !!e.target.checked, maxAttempts: e.target.checked ? Math.max(2, value.maxAttempts) : 1 })}>允许重考</Checkbox>
        <span className="text-sm">最多答题</span><InputNumber aria-label="最多答题次数" style={{ width: 88 }} min={1} max={100} precision={0} disabled={disabled || started || !value.allowRetake} value={value.maxAttempts} onChange={n => change({ maxAttempts: Number(n) || 0 })} /><span>次</span>
        <RuleTip label="答题次数说明">次数包含首次答题，只统计本培训中的考试；关闭重考时最多答题一次。</RuleTip>
      </div>
      <Checkbox disabled={fixed} checked={value.showResultImmediately} onChange={e => change({ showResultImmediately: !!e.target.checked })}>交卷后可以立即查看答案</Checkbox>
    </>}
  </div>;
}
