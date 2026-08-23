"use client";

import { useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { Button, Input, Switch, Tag, Toast } from "@douyinfe/semi-ui";
import "@douyinfe/semi-ui/react19-adapter";
import { IconClock, IconRefresh, IconTick } from "@douyinfe/semi-icons";
import { getPublicBankQuestions, getQuestionBank } from "@/features/banks/api";
import type { PublicQuestion, QuestionBank } from "@/features/banks/types";
import { getPublicSelfTest, type PublicSelfTestDetail } from "@/features/self-test/api";

type Stage = "config" | "answer" | "result";
type Answers = Record<number, string[]>;

const typeOf = (question: PublicQuestion) =>
  question.questionType === "MULTIPLE" ? "多选" : question.questionType === "SINGLE" ? "单选" : "判断";

const values = (value?: string) =>
  (value || "").split(/[，,、|]/).map((item) => item.trim().toUpperCase()).filter(Boolean).sort();

/** 判断题后端保存 TRUE/FALSE，答题选项使用 A/B，评分前统一转换。 */
const answerValues = (question: PublicQuestion, value?: string) => {
  const normalized = values(value);
  if (question.questionType === "TRUE_FALSE") {
    return normalized.map((item) => item === "TRUE" ? "A" : item === "FALSE" ? "B" : item).sort();
  }
  return normalized;
};

const answerLabel = (question: PublicQuestion) => {
  if (question.questionType === "TRUE_FALSE") {
    return answerValues(question, question.answerText).map((item) => item === "A" ? "A. 正确" : item === "B" ? "B. 错误" : item).join("、");
  }
  return answerValues(question, question.answerText).join("、");
};

const isCorrect = (question: PublicQuestion, selected: string[]) => {
  const expected = answerValues(question, question.answerText);
  const actual = answerValues(question, selected.join(","));
  // 多选题答案按集合比较，A、C、B 与 A、B、C 等价；单选和判断仍要求唯一命中。
  return expected.length > 0 && expected.length === actual.length && expected.every((item) => actual.includes(item));
};

const shuffle = <T,>(items: T[]) => [...items].sort(() => Math.random() - 0.5);

/** 在线自测独立组件：配置抽题、逐题作答、提交评分和查看测试结果。 */
export function OnlineSelfTest({ bankId, selfTestId }: { bankId?: number; selfTestId?: number }) {
  const [bank, setBank] = useState<QuestionBank | null>(null);
  const [selfTest, setSelfTest] = useState<PublicSelfTestDetail | null>(null);
  const [questions, setQuestions] = useState<PublicQuestion[]>([]);
  const [stage, setStage] = useState<Stage>("config");
  const [selectedQuestions, setSelectedQuestions] = useState<PublicQuestion[]>([]);
  const [answers, setAnswers] = useState<Answers>({});
  const [index, setIndex] = useState(0);
  const [singleCount, setSingleCount] = useState("5");
  const [multipleCount, setMultipleCount] = useState("5");
  const [judgeCount, setJudgeCount] = useState("5");
  const [randomOrder, setRandomOrder] = useState(true);
  const [autoNext, setAutoNext] = useState(true);
  const [startedAt, setStartedAt] = useState<number | null>(null);

  useEffect(() => {
    let active = true;
    const load = async () => {
      try {
        const test = selfTestId ? await getPublicSelfTest(selfTestId) : null;
        const targetBankId = test?.bankId || bankId;
        if (!targetBankId) throw new Error("缺少题库参数");
        const [detail, page] = await Promise.all([getQuestionBank(targetBankId), getPublicBankQuestions(targetBankId, { pageSize: 100 })]);
        if (!active) return;
        setSelfTest(test);
        setBank(detail);
        setQuestions(page.records);
        if (test) {
          setSingleCount(String(Math.min(test.singleCount, page.records.filter((item) => item.questionType === "SINGLE").length)));
          setMultipleCount(String(Math.min(test.multipleCount, page.records.filter((item) => item.questionType === "MULTIPLE").length)));
          setJudgeCount(String(Math.min(test.trueFalseCount, page.records.filter((item) => item.questionType === "TRUE_FALSE").length)));
          setRandomOrder(test.randomOrder);
        }
      } catch (error) {
        Toast.error(error instanceof Error ? error.message : "题库加载失败");
      }
    };
    load();
    return () => { active = false; };
  }, [bankId, selfTestId]);

  const groups = useMemo(() => ({
    SINGLE: questions.filter((item) => item.questionType === "SINGLE"),
    MULTIPLE: questions.filter((item) => item.questionType === "MULTIPLE"),
    TRUE_FALSE: questions.filter((item) => item.questionType === "TRUE_FALSE"),
  }), [questions]);

  const start = () => {
    const counts = { SINGLE: Math.max(0, Number(singleCount) || 0), MULTIPLE: Math.max(0, Number(multipleCount) || 0), TRUE_FALSE: Math.max(0, Number(judgeCount) || 0) };
    if (!counts.SINGLE && !counts.MULTIPLE && !counts.TRUE_FALSE) { Toast.error("请至少设置一道题"); return; }
    if (counts.SINGLE > groups.SINGLE.length || counts.MULTIPLE > groups.MULTIPLE.length || counts.TRUE_FALSE > groups.TRUE_FALSE.length) { Toast.error("题目数量不能超过题库中现有数量"); return; }
    const picked = [...shuffle(groups.SINGLE).slice(0, counts.SINGLE), ...shuffle(groups.MULTIPLE).slice(0, counts.MULTIPLE), ...shuffle(groups.TRUE_FALSE).slice(0, counts.TRUE_FALSE)];
    setSelectedQuestions(randomOrder ? shuffle(picked) : picked);
    setAnswers({}); setIndex(0); setStartedAt(Date.now()); setStage("answer");
  };

  const current = selectedQuestions[index];
  const answeredCount = selectedQuestions.filter((item) => (answers[item.id] || []).length > 0).length;

  const choose = (key: string) => {
    if (!current) return;
    const currentAnswer = answers[current.id] || [];
    const next = current.questionType === "MULTIPLE" ? currentAnswer.includes(key) ? currentAnswer.filter((item) => item !== key) : [...currentAnswer, key] : [key];
    setAnswers({ ...answers, [current.id]: next });
    if (autoNext && current.questionType !== "MULTIPLE" && index < selectedQuestions.length - 1) window.setTimeout(() => setIndex((value) => value + 1), 180);
  };

  const submit = () => { setStage("result"); };
  const score = selectedQuestions.reduce((total, question) => total + (isCorrect(question, answers[question.id] || []) ? 1 : 0), 0);
  const scoreValue = selectedQuestions.length ? Math.round((score / selectedQuestions.length) * 100) : 0;
  const duration = startedAt ? Math.max(1, Math.floor((Date.now() - startedAt) / 1000)) : 0;

  if (!bank) return <main className="min-h-[700px] bg-[#f8fbfc] py-16 text-center text-sm text-slate-400">正在加载题库...</main>;
  const title = selfTest?.name || bank.name;

  return <main className="min-h-[760px] bg-[#f8fbfc] py-8"><div className="mx-auto w-[92%] max-w-[1200px]">
    <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
    <Link href={selfTest ? "/self-test" : `/banks/${bankId}`} className="text-sm text-slate-400 no-underline">{selfTest ? "在线自测" : "题库大全"}　›　{title}</Link>
      <h1 className="mt-4 border-b border-slate-200 pb-5 text-3xl font-bold text-slate-800">{title} · 在线自测</h1>
      <div className="mt-5 flex flex-wrap items-center gap-3 text-sm text-slate-600">
        <span>单选题数量</span><Input value={singleCount} onChange={setSingleCount} className="!w-16" disabled={stage !== "config"} /><span className="text-slate-400">/ {groups.SINGLE.length}</span>
        <span>多选题数量</span><Input value={multipleCount} onChange={setMultipleCount} className="!w-16" disabled={stage !== "config"} /><span className="text-slate-400">/ {groups.MULTIPLE.length}</span>
        <span>判断题数量</span><Input value={judgeCount} onChange={setJudgeCount} className="!w-16" disabled={stage !== "config"} /><span className="text-slate-400">/ {groups.TRUE_FALSE.length}</span>
        <label className="flex items-center gap-2"><Switch checked={randomOrder} onChange={setRandomOrder} disabled={stage !== "config"} />随机排序</label>
        <label className="flex items-center gap-2"><Switch checked={autoNext} onChange={setAutoNext} disabled={stage === "result"} />自动下一题</label>
        {stage === "config" && <Button theme="solid" type="warning" icon={<IconClock />} onClick={start}>开始答题</Button>}
        {stage === "answer" && <Button theme="solid" type="warning" icon={<IconTick />} onClick={submit}>交卷</Button>}
        {stage === "result" && <Button theme="solid" type="warning" icon={<IconRefresh />} onClick={() => setStage("config")}>重新自测</Button>}
      </div>
      {stage === "config" && <p className="mt-5 text-sm text-slate-400">设置题目数量后，点击“开始答题”进入自测。</p>}
      {stage === "answer" && <p className="mt-5 text-sm text-slate-500">已答 <b className="text-[#35c5bd]">{answeredCount}</b> / {selectedQuestions.length} 题</p>}
    </div>

    {stage === "answer" && current && <section className="mt-6 rounded-2xl border border-slate-200 bg-white p-7 shadow-sm"><div className="flex items-center gap-3"><span className="grid h-7 w-7 place-items-center rounded-md bg-[#e8faf9] text-sm text-[#35c5bd]">{index + 1}</span><Tag color="blue">{typeOf(current)}</Tag><span className="ml-auto text-sm text-slate-500">{index + 1} / {selectedQuestions.length}</span></div><h2 className="mt-6 text-lg font-bold leading-8 text-slate-800">{current.stem}</h2><div className="mt-7 grid gap-3 md:grid-cols-2">{(current.options || []).map((option) => <button type="button" key={option.optionKey} onClick={() => choose(option.optionKey)} className={`rounded-xl border px-5 py-4 text-left text-sm transition ${answers[current.id]?.includes(option.optionKey) ? "border-blue-500 bg-blue-50 text-blue-700" : "border-slate-200 bg-slate-50 text-slate-600 hover:border-blue-300"}`}>{option.optionKey}. {option.optionText}</button>)}</div><div className="mt-8 flex items-center justify-between"><Button theme="light" disabled={index === 0} onClick={() => setIndex((value) => value - 1)}>上一题</Button>{index === selectedQuestions.length - 1 ? <Button theme="solid" type="warning" onClick={submit}>提交自测</Button> : <Button theme="light" onClick={() => setIndex((value) => value + 1)}>下一题</Button>}</div></section>}

    {stage === "result" && <section className="mt-6 space-y-6"><div className="rounded-2xl border border-[#bcefed] bg-white p-7 shadow-sm"><div className="flex flex-wrap items-center justify-between gap-4"><div><h2 className="text-xl font-bold text-slate-800">自测成绩</h2><p className="mt-2 text-sm text-slate-500">共 {selectedQuestions.length} 题，答对 {score} 题，答错 {selectedQuestions.length - score} 题，用时 {Math.floor(duration / 60).toString().padStart(2, "0")}:{(duration % 60).toString().padStart(2, "0")}</p></div><strong className="text-5xl font-bold text-[#35c5bd]">{scoreValue} 分</strong></div><div className="mt-6 grid gap-3 sm:grid-cols-3"><div className="rounded-xl bg-slate-50 p-4 text-center"><span className="block text-sm text-slate-500">单选</span><b>{selectedQuestions.filter((item) => item.questionType === "SINGLE" && isCorrect(item, answers[item.id] || [])).length}/{selectedQuestions.filter((item) => item.questionType === "SINGLE").length}</b></div><div className="rounded-xl bg-slate-50 p-4 text-center"><span className="block text-sm text-slate-500">多选</span><b>{selectedQuestions.filter((item) => item.questionType === "MULTIPLE" && isCorrect(item, answers[item.id] || [])).length}/{selectedQuestions.filter((item) => item.questionType === "MULTIPLE").length}</b></div><div className="rounded-xl bg-slate-50 p-4 text-center"><span className="block text-sm text-slate-500">判断</span><b>{selectedQuestions.filter((item) => item.questionType === "TRUE_FALSE" && isCorrect(item, answers[item.id] || [])).length}/{selectedQuestions.filter((item) => item.questionType === "TRUE_FALSE").length}</b></div></div></div><div><h2 className="mb-4 text-xl font-bold text-slate-800">测试结果</h2><div className="space-y-4">{selectedQuestions.map((question, questionIndex) => { const correct = isCorrect(question, answers[question.id] || []); return <article key={question.id} className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm"><div className="flex items-center gap-3"><span className="grid h-7 w-7 place-items-center rounded-md bg-[#e8faf9] text-sm text-[#35c5bd]">{questionIndex + 1}</span><Tag color="blue">{typeOf(question)}</Tag><span className={correct ? "text-emerald-600" : "text-red-500"}>{correct ? "回答正确" : "回答错误"}</span></div><h3 className="mt-4 font-bold leading-7 text-slate-800">{question.stem}</h3><div className="mt-5 grid gap-3 md:grid-cols-2">{(question.options || []).map((option) => <div key={option.optionKey} className={`rounded-lg border px-4 py-3 text-sm ${answerValues(question, question.answerText).includes(option.optionKey) ? "border-emerald-200 bg-emerald-50 text-emerald-700" : answers[question.id]?.includes(option.optionKey) ? "border-red-200 bg-red-50 text-red-600" : "border-slate-200 bg-slate-50 text-slate-600"}`}>{option.optionKey}. {option.optionText}</div>)}</div><div className="mt-5 border-t border-slate-100 pt-4 text-sm leading-7 text-slate-600"><p><b>你的答案：</b>{(answers[question.id] || []).join("、") || "未作答"}</p><p><b>正确答案：</b><span className="text-emerald-600">{answerLabel(question) || "暂无"}</span></p>{question.analysis && <p><b>解析：</b>{question.analysis}</p>}</div></article>; })}</div></div></section>}
  </div></main>;
}
