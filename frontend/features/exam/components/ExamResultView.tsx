"use client";
import { Typography } from "@douyinfe/semi-ui";
import { Layout } from "@douyinfe/semi-ui";
import { useEffect, useState } from "react";
import { Button, Banner } from "@douyinfe/semi-ui";
import { IconTickCircle, IconAlertCircle, IconArrowLeft, IconArrowRight } from "@douyinfe/semi-icons";
import type { ExamPaper, ExamQuestion } from "../types";
import { TrainingExamLinks } from "@/features/trainings/components/TrainingExamLinks";

export type QuestionGrade = { questionId: number; isCorrect: boolean; score: number };
export type ExamResultViewProps = {
  paper: ExamPaper; score: number; passed: boolean; answers: Record<number, string[]>;
  showAnswers: boolean; grades?: QuestionGrade[]; candidateName?: string; candidatePhone?: string;
  attemptNo?: number; startedAt?: string; submittedAt?: string; remainingAttempts: number;
  attemptId?: string; details?: boolean; practice?: boolean;
};
const dateLabel = (value?: string) => value && Number.isFinite(Date.parse(value)) ? new Date(value).toLocaleString("zh-CN", { timeZone: "Asia/Shanghai", hour12: false }) : "—";
const durationLabel = (start?: string, end?: string) => {
  if (!start || !end || !Number.isFinite(Date.parse(end) - Date.parse(start))) return "—";
  const seconds = Math.max(0, Math.floor((Date.parse(end) - Date.parse(start)) / 1000));
  return `${String(Math.floor(seconds / 60)).padStart(2, "0")}:${String(seconds % 60).padStart(2, "0")}`;
};
const optionsFor = (q: ExamQuestion) => q.options?.length ? q.options : q.kind === "judge" ? [{ key: "A", text: "正确" }, { key: "B", text: "错误" }] : [];

export function ExamResultView(props: ExamResultViewProps) {
  const { paper, score, passed, answers, showAnswers, grades, candidateName, candidatePhone, attemptNo, startedAt, submittedAt, remainingAttempts, attemptId, details = false, practice = false } = props;
  const answerLabel = (question: ExamQuestion, values: string[]) => values.map(value =>
    question.kind === "judge" ? (optionsFor(question).find(option => option.key === value)?.text || value) : value,
  ).join("、");
  const [trainingId, setTrainingId] = useState("");
  useEffect(() => { const id = new URLSearchParams(window.location.search).get("trainingId"); if (id && /^\d+$/.test(id)) setTrainingId(id); }, []);
  const withContext = (path: string) => trainingId ? `${path}${path.includes("?") ? "&" : "?"}trainingId=${trainingId}` : path;
  const canRetake = remainingAttempts > 0 && paper.taskStatus !== "ENDED" && paper.taskStatus !== "DISABLED" && (!paper.endAt || Date.now() < Date.parse(paper.endAt));
  const [wrongOnly, setWrongOnly] = useState(false);
  const [practiceDetails, setPracticeDetails] = useState(false);
  const reviewing = details || practiceDetails;
  // 正式考试以服务端判分为准，不能根据展示选项重新推算部分得分。
  const gradeFor = (q: ExamQuestion) => grades?.find(g => g.questionId === q.id) || {
    isCorrect: Boolean(q.answer?.length) && [...(answers[q.id] || [])].sort().join(",") === [...q.answer].sort().join(","),
    score: 0,
  };
  const correctCount = paper.questions.filter(q => gradeFor(q).isCorrect).length;
  const wrongCount = paper.questions.length - correctCount;
  const visibleQuestions = paper.questions.filter(q => !wrongOnly || !gradeFor(q).isCorrect);
  const resultUrl = withContext(`/kaoshi/${paper.id}/result${attemptId ? `?attemptId=${attemptId}` : ""}`);
  const detailsUrl = withContext(`/kaoshi/${paper.id}/result/details${attemptId ? `?attemptId=${attemptId}` : ""}`);
  const stats = <div className="h5-stats h5-result-stats">
    <div><strong>{reviewing ? score : showAnswers ? correctCount : score}<small>{reviewing ? "" : showAnswers ? ` / ${paper.questions.length}` : "分"}</small></strong><span>{reviewing ? `得分 / ${paper.totalScore}` : showAnswers ? "答对题数" : "考试得分"}</span></div>
    <div><strong>{durationLabel(startedAt, submittedAt)}</strong><span>答题用时</span></div>
    <div><strong>{showAnswers ? Math.round(correctCount / Math.max(1, paper.questions.length) * 100) : paper.passScore}<small>{showAnswers ? "%" : "分"}</small></strong><span>{showAnswers ? "正确率" : "及格分"}</span></div>
  </div>;
  return <div className="result-print-root">
    <div className="h5-content">
      {reviewing ? <><Typography.Title heading={1} className="h5-detail-title !text-[21px] !leading-normal !mb-3">{paper.title}</Typography.Title><p className="h5-muted">{candidateName || "考生"} · 第 {attemptNo || 1} 次答题</p><p className="h5-muted">{dateLabel(submittedAt)} 提交</p></> : <Layout.Content className="h5-result-hero">
        <div className={`h5-result-badge ${passed ? "" : "failed"}`}>{passed ? <IconTickCircle /> : <IconAlertCircle />}{passed ? "考试通过" : "暂未通过"}</div>
        <div className="h5-score">{score}<small>分</small></div><p className="h5-muted">满分 {paper.totalScore} 分 · 及格分 {paper.passScore} 分</p>
        <Typography.Title heading={1} className="h5-result-title !text-[19px] !mt-6 !mb-2">{passed ? "每一次认真，都有收获" : "再巩固一下，继续进步"}</Typography.Title><p className="h5-muted">{paper.title}</p>
      </Layout.Content>}
      {stats}
      {!reviewing && <>
        <dl className="h5-result-meta"><div><dt>考生</dt><dd>{candidateName || "—"}{candidatePhone ? ` · ${candidatePhone}` : ""}</dd></div><div><dt>提交时间</dt><dd>{dateLabel(submittedAt)}</dd></div><div><dt>试卷</dt><dd>{paper.questions.length} 题 · 限时 {paper.duration} 分钟</dd></div><div><dt>答题次数</dt><dd>第 {attemptNo || 1} 次</dd></div></dl>
        {!practice && <TrainingExamLinks examId={paper.id} />}
        <div className="h5-result-links print-hidden">{canRetake && <a href={trainingId ? `/training/${trainingId}?exam=1` : `/kaoshi/${paper.id}?direct=1`}>重新考试（还可 {remainingAttempts} 次）</a>}{trainingId && <a href={`/training/${trainingId}?review=1`}>回看培训材料</a>}<a href="/learner">返回个人中心</a></div>
      </>}
      {reviewing && (showAnswers ? <>
        <div className="h5-detail-tabs print-hidden"><Button theme="borderless" className={!wrongOnly ? "active" : ""} onClick={() => setWrongOnly(false)}>全部题目（{paper.questions.length}）</Button><Button theme="borderless" className={wrongOnly ? "active" : ""} onClick={() => setWrongOnly(true)}>只看错题（{wrongCount}）</Button></div>
        {visibleQuestions.length === 0 && <div className="h5-empty"><IconTickCircle /> 本次没有错题，继续保持。</div>}
        {visibleQuestions.map(question => {
          const picked = answers[question.id] || [], grade = gradeFor(question);
          const questionIndex = paper.questions.findIndex(q => q.id === question.id);
          return <article className="h5-review-question result-print-question" key={question.id}>
            <div className="h5-review-meta"><span className="h5-tag">{String(questionIndex + 1).padStart(2, "0")} · {question.kind === "multi" ? "多选题" : question.kind === "judge" ? "判断题" : "单选题"}</span><b className={grade.isCorrect ? "" : "incorrect"}>{grades ? grade.score : grade.isCorrect ? question.score || 0 : 0} / {question.score || 0} 分</b></div>
            <Typography.Title heading={3} className="!my-3 !text-base !leading-7">{question.title}</Typography.Title><div className="h5-review-options">{optionsFor(question).map(option => {
              const correct = question.answer.includes(option.key), mine = picked.includes(option.key);
              return <div key={option.key} className={`h5-review-option ${correct ? "correct" : mine ? "wrong" : ""}`}><span>{option.key}</span><p>{option.text}</p>{(correct || mine) && <small>{correct ? "正确答案" : "我的选择"}</small>}</div>;
            })}</div>
            <div className="h5-answer-comparison"><span>我的答案 <b>{answerLabel(question, picked) || "未作答"}</b></span><span>正确答案 <b>{answerLabel(question, question.answer) || "未公布"}</b></span><span>{grade.isCorrect ? "回答正确" : "回答错误"}</span></div>
            <div className="h5-explanation"><b>答案解析</b>{question.analysis || "暂无解析"}</div>
          </article>;
        })}
        <p className="h5-muted text-center mt-5">已展示 {visibleQuestions.length} 道{wrongOnly ? "错题" : "题目"}</p>
      </> : <Banner type="info" description="本次考试暂未开放答案与解析，请以管理员公布规则为准。" closeIcon={null} />)}
    </div>
    <div className="h5-bottom print-hidden">{reviewing ? <Button block size="large" className="h5-secondary" icon={<IconArrowLeft />} onClick={() => practice ? setPracticeDetails(false) : window.location.assign(resultUrl)}>返回考试结果</Button> : <Button block size="large" theme="solid" className="h5-primary" icon={<IconArrowRight />} onClick={() => practice ? setPracticeDetails(true) : window.location.assign(detailsUrl)}>查看考试详情</Button>}
      <Button theme="borderless" className="h5-link" onClick={() => window.print()}>打印{reviewing ? "答题详情" : "成绩"}</Button>
    </div>
  </div>;
}
