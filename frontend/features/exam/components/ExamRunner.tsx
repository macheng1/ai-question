"use client";
import { Typography } from "@douyinfe/semi-ui";
import { Layout } from "@douyinfe/semi-ui";

import { IconClock, IconList, IconTick, IconArrowRight } from "@douyinfe/semi-icons";
import { ExamResultView, type QuestionGrade } from "./ExamResultView";
import { useEffect, useState } from "react";
import "@douyinfe/semi-ui/react19-adapter";
import { Button, Input, Toast, Modal, Radio, RadioGroup, Checkbox, CheckboxGroup, Progress } from "@douyinfe/semi-ui";
import { ExamTrainingMaterials } from "@/features/trainings/components/ExamTrainingMaterials";
import { loadExamLearning, type ExamLearning } from "@/features/trainings/components/ExamTrainingVideos";
import type { ExamPaper } from "../types";
import { loadExamPaper } from "../load-paper";
import {
  checkPublicExamEligibility,
  getPublicExamProgress,
  savePublicAnswer,
  startPublicExam,
  submitPublicAttempt,
} from "../api";

const withTraining = (path: string) => {
  if (typeof window === "undefined") return path;
  const trainingId = new URLSearchParams(window.location.search).get("trainingId");
  return trainingId && /^\d+$/.test(trainingId) ? `${path}${path.includes("?") ? "&" : "?"}trainingId=${trainingId}` : path;
};

const remainingSeconds = (
  startedAt: string | undefined,
  durationMinutes: number,
  endAt?: string,
) => {
  const durationRemaining = startedAt
    ? (() => {
        const started = Date.parse(startedAt);
        return Number.isNaN(started)
          ? durationMinutes * 60
          : durationMinutes * 60 - Math.floor((Date.now() - started) / 1000);
      })()
    : durationMinutes * 60;
  const parsedEndAt = parseExamTime(endAt);
  const taskRemaining = Number.isFinite(parsedEndAt)
    ? Math.floor((parsedEndAt - Date.now()) / 1000)
    : durationRemaining;
  return Math.max(0, Math.min(durationRemaining, taskRemaining));
};

const parseExamTime = (value?: string) => {
  if (!value) return Number.NaN;
  const parsed = Date.parse(value);
  if (!Number.isNaN(parsed)) return parsed;
  return Date.parse(value.replace(" ", "T"));
};

/** 根据已保存的答案定位到第一道未完成的题目。 */
const getResumeIndex = (
  questions: ExamPaper["questions"],
  answers: Record<number, string[]>,
) => {
  const nextIndex = questions.findIndex(
    (question) => !(answers[question.id] || []).length,
  );
  return nextIndex >= 0 ? nextIndex : Math.max(0, questions.length - 1);
};

/** 将后端保存的答案值转换为答题页面使用的选项 key。 */
const savedAnswerKeys = (
  question: ExamPaper["questions"][number],
  value?: string,
) => {
  const values = value ? value.split(",").filter(Boolean) : [];
  if (question.kind !== "judge") return values;
  return values.map((item) => {
    const normalized = item.trim().toUpperCase();
    return normalized === "TRUE" ? "A" : normalized === "FALSE" ? "B" : item;
  });
};

export function ExamRunner({
  paper: initialPaper,
  practice = false,
  initialStage = "info",
  initialName = "",
  initialPhone = "",
  initialAttemptId,
  initialResult,
  initialAnswers,
  initialRemainingAttempts = 0,
  initialCandidateName = "",
  initialCandidatePhone = "",
  initialAttemptNo,
  initialStartedAt,
  initialSubmittedAt,
  initialShowAnswers = true,
  compact = false,
  allowRestore = true,
  learningPage = false,
  details = false,
  initialGrades,
  resultAttemptId,
}: {
  paper: ExamPaper;
  practice?: boolean;
  initialStage?: "info" | "answer" | "result";
  initialName?: string;
  initialPhone?: string;
  /** 答题页从 URL 传入的正式答题记录 ID，固定组卷和随机组卷都使用。 */
  initialAttemptId?: number;
  initialResult?: {
    score: number;
    totalScore: number;
    passScore: number;
    passed: boolean;
  };
  initialAnswers?: Record<number, string[]>;
  initialRemainingAttempts?: number;
  initialCandidateName?: string;
  initialCandidatePhone?: string;
  initialAttemptNo?: number;
  initialStartedAt?: string;
  initialSubmittedAt?: string;
  /** 正式考试结果是否允许查看答案、正确答案和解析。 */
  initialShowAnswers?: boolean;
  compact?: boolean;
  /** 是否从本地草稿恢复答题进度；考试入口页需要关闭，独立答题页保持开启。 */
  allowRestore?: boolean;
  learningPage?: boolean;
  details?: boolean;
  initialGrades?: QuestionGrade[];
  resultAttemptId?: string;
}) {
  const [stage, setStage] = useState<"info" | "answer" | "result">(
    initialStage,
  );
  const [name, setName] = useState(initialName);
  const [phone, setPhone] = useState(initialPhone);
  const [index, setIndex] = useState(0);
  const [answers, setAnswers] = useState<Record<number, string[]>>(
    initialAnswers || {},
  );
  const [activeQuestions, setActiveQuestions] = useState(initialPaper.questions);
  const paper = { ...initialPaper, questions: activeQuestions };
  const [seconds, setSeconds] = useState(initialPaper.duration * 60);
  const [submitted, setSubmitted] = useState(false);
  const [attemptId, setAttemptId] = useState<number | null>(
    initialAttemptId ?? null,
  );
  const [attemptNo, setAttemptNo] = useState<number | null>(null);
  const [startedAt, setStartedAt] = useState<string | undefined>();
  const [result, setResult] = useState<{
    score: number;
    totalScore: number;
    passScore: number;
    passed: boolean;
  } | null>(initialResult || null);
  const [remainingAttempts, setRemainingAttempts] = useState(
    initialRemainingAttempts,
  );
  const [restored, setRestored] = useState(false);
  const [starting, setStarting] = useState(false);
  const [cardOpen, setCardOpen] = useState(false);
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [navigating, setNavigating] = useState(false);
  const [learning, setLearning] = useState<ExamLearning>();
  const [learningError, setLearningError] = useState("");
  useEffect(() => {
    if (!learningPage) return;
    if (!initialName.trim() || !/^1\d{10}$/.test(initialPhone)) {
      window.location.replace(`/kaoshi/${initialPaper.id}`); return;
    }
    let active = true;
    loadExamLearning(initialPaper.id, initialName, initialPhone)
      .then(data => { if (active) setLearning(data); })
      .catch(e => { if (active) setLearningError(e.message || "学习进度加载失败"); });
    return () => { active = false; };
  }, [learningPage, initialPaper.id, initialName, initialPhone]);
  const draftKey = `aiquestion:exam-draft:${practice ? "practice" : "official"}:${paper.id}`;
  const q = activeQuestions[index];
  const score = result?.score || 0;
  useEffect(() => {
    if (!allowRestore) {
      setRestored(true);
      return;
    }
    try {
      // 答题草稿只在当前浏览器标签页会话内保留，关闭浏览器后自动清除。
      const draft = JSON.parse(sessionStorage.getItem(draftKey) || "null");
      if (draft && draft.stage === "answer" && !draft.submitted) {
        setName(draft.name || "");
        setPhone(draft.phone || "");
        const draftAnswers = draft.answers || {};
        setIndex(getResumeIndex(paper.questions, draftAnswers));
        setAnswers(draftAnswers);
        setAttemptId(draft.attemptId || null);
        setStartedAt(draft.startedAt);
        setSeconds(
          draft.startedAt
            ? remainingSeconds(draft.startedAt, paper.duration, paper.endAt)
            : Math.max(0, Number(draft.seconds) || 0),
        );
        setStage("answer");
      }
    } catch {
      /* ignore a damaged local draft */
    }
    setRestored(true);
  }, [allowRestore, draftKey, paper.duration, paper.questions.length]);
  useEffect(() => {
    if (practice || initialStage !== "info" || learningPage) return;
    try {
      const persisted = JSON.parse(
        localStorage.getItem("aiquestion-auth") || "null",
      );
      const user = persisted?.state?.user;
      if (!user?.name || !/^1\d{10}$/.test(user.phone || "")) return;
      setName((current) => current || user.name);
      setPhone((current) => current || user.phone);
      getPublicExamProgress(paper.id, {
        name: user.name,
        phone: user.phone,
      })
        .then((progress) => {
          if (!progress.inProgress) return;
          const query = new URLSearchParams({
            name: user.name,
            phone: user.phone,
          });
          if (progress.attemptId) {
            query.set("attemptId", String(progress.attemptId));
          }
          window.location.replace(
            withTraining(`/kaoshi/${paper.id}/answer?${query.toString()}`),
          );
        })
        .catch(() => {
          /* 进行中检查失败时仍保留正常的考试入口 */
        });
    } catch {
      /* ignore an invalid persisted session */
    }
  }, [initialStage, paper.id, practice, learningPage]);
  useEffect(() => {
    if (
      practice ||
      initialStage !== "answer" ||
      !initialAttemptId ||
      !name ||
      !/^1\d{10}$/.test(phone)
    )
      return;
    getPublicExamProgress(paper.id, { name, phone })
      .then((progress) => {
        // 只恢复 URL 指定的记录，避免固定组卷和随机组卷串用其他答题记录。
        if (!progress.inProgress || progress.attemptId !== initialAttemptId)
          return;
        const restoredAnswers = Object.fromEntries(
          (progress.answers || []).map((item) => [
            item.question_id,
            savedAnswerKeys(
              paper.questions.find((question) => question.id === item.question_id) || paper.questions[0],
              item.answer_text,
            ),
          ]),
        );
        setStartedAt(progress.startedAt);
        setSeconds(remainingSeconds(progress.startedAt, paper.duration, paper.endAt));
        setAnswers(restoredAnswers);
        setIndex(getResumeIndex(paper.questions, restoredAnswers));
      })
      .catch(() => {
        /* URL 已带 attemptId，恢复失败时保留当前答题页 */
      });
  }, [initialAttemptId, initialStage, name, paper.duration, paper.id, paper.questions, phone, practice]);

  useEffect(() => {
    if (practice || initialStage !== "answer" || !initialAttemptId) return;
    loadExamPaper(String(paper.id), { attemptId: initialAttemptId })
      .then((runtimePaper) => setActiveQuestions(runtimePaper.questions))
      .catch(() => {
        /* 首屏已经使用随机快照；加载失败时保留当前页面，不替换题目数据 */
      });
  }, [initialAttemptId, initialStage, paper.id, practice]);

  useEffect(() => {
    if (practice || initialStage !== "answer" || attemptId) return;
    if (!name || !/^1\d{10}$/.test(phone)) return;
    startPublicExam(paper.id, { name, phone })
      .then(async (started) => {
        const runtimePaper = await loadExamPaper(String(paper.id), { attemptId: started.attemptId });
        setActiveQuestions(runtimePaper.questions);
        const questions = runtimePaper.questions;
        const restoredAnswers = Object.fromEntries(
          (started.answers || []).map((item) => [
            item.question_id,
            savedAnswerKeys(
              questions.find((question) => question.id === item.question_id) || questions[0],
              item.answer_text,
            ),
          ]),
        );
        setAttemptId(started.attemptId);
        setAttemptNo(started.attemptNo || null);
        setStartedAt(started.startedAt);
        setSeconds(remainingSeconds(started.startedAt, paper.duration, paper.endAt));
        setAnswers(restoredAnswers);
        setIndex(getResumeIndex(questions, restoredAnswers));
        if (started.status === "SUBMITTED") {
          setResult({
            score: Number(started.score || 0),
            totalScore: Number(started.totalScore || paper.totalScore),
            passScore: Number(started.passScore || paper.passScore),
            passed: Boolean(started.passed),
          });
          setSubmitted(true);
          setRemainingAttempts(started.remainingAttempts || 0);
          setStage("result");
        }
      })
      .catch(() => {
        window.location.replace(`/kaoshi/${paper.id}`);
      });
  }, [attemptId, initialStage, name, paper.id, phone, practice]);
  useEffect(() => {
    if (!restored || submitted || stage === "result") return;
    sessionStorage.setItem(
      draftKey,
      JSON.stringify({
        stage,
        name,
        phone,
        attemptId,
        startedAt,
        index,
        answers,
        seconds,
        submitted: false,
      }),
    );
  }, [
    restored,
    submitted,
    stage,
    name,
    phone,
    attemptId,
    startedAt,
    index,
    answers,
    seconds,
    draftKey,
  ]);
  useEffect(() => {
    if (stage !== "answer" || submitted) return;
    const timer = window.setInterval(
      () =>
        setSeconds((s) => {
          if (s <= 1) {
            window.clearInterval(timer);
            return 0;
          }
          return s - 1;
        }),
      1000,
    );
    return () => window.clearInterval(timer);
  }, [stage, submitted]);
  const persistAnswer = async (questionId: number, selected: string[]) => {
    if (!attemptId || practice) return true;
    try {
      await savePublicAnswer(attemptId, questionId, selected);
      return true;
    } catch (error) {
      Toast.error(error instanceof Error ? error.message : "答案保存失败");
      return false;
    }
  };
  const choose = (key: string) => {
    setAnswers((current) => {
      const old = current[q.id] || [];
      const next =
        q.kind === "multi"
          ? old.includes(key)
            ? old.filter((x) => x !== key)
            : [...old, key]
          : [key];
      return { ...current, [q.id]: next };
    });
  };
  const beginExam = async () => {
    if (starting) return;
    if (!practice && (!name.trim() || !/^1\d{10}$/.test(phone))) {
      Toast.error("请填写正确的姓名和11位手机号");
      return;
    }
    setStarting(true);
    try {
      const now = Date.now();
      if (paper.taskStatus === "DISABLED" || paper.taskStatus === "ENDED" || (paper.endAt && now >= Date.parse(paper.endAt))) throw new Error("考试已结束，不能继续答题");
      if (paper.startAt && now < Date.parse(paper.startAt)) throw new Error("考试尚未开始");
      // 先检查重考资格；服务端 start 接口仍会再次校验，防止并发或绕过前端。
      const eligibility = await checkPublicExamEligibility(paper.id, {
        name,
        phone,
      });
      setRemainingAttempts(eligibility.remainingAttempts);
      // 续考和已用完次数的成绩跳转维持原逻辑；新答题实例必须先完成全部关联视频。
      if (eligibility.canStart && !eligibility.inProgress) {
        const requiredLearning = await loadExamLearning(paper.id, name, phone);
        if ((requiredLearning.videos.some(video => !video.completed) || requiredLearning.materials?.some(material => !material.completed))) {
          if (learningPage) setLearning(requiredLearning);
          else {
            const query = new URLSearchParams({ name: name.trim(), phone: phone.trim() });
            window.location.assign(`/kaoshi/${paper.id}/learning?${query}`);
          }
          return;
        }
      }
      const started = await startPublicExam(paper.id, { name, phone });
      const runtimePaper = await loadExamPaper(String(paper.id), { attemptId: started.attemptId });
      setActiveQuestions(runtimePaper.questions);
      const questions = runtimePaper.questions;
      const restoredAnswers = Object.fromEntries(
        (started.answers || []).map((item) => [
          item.question_id,
          savedAnswerKeys(
            questions.find((question) => question.id === item.question_id) || questions[0],
            item.answer_text,
          ),
        ]),
      );
      setAttemptId(started.attemptId);
      setAttemptNo(started.attemptNo || null);
      setStartedAt(started.startedAt);
      setSeconds(remainingSeconds(started.startedAt, paper.duration, paper.endAt));
      setAnswers(restoredAnswers);
      setIndex(getResumeIndex(questions, restoredAnswers));
      if (started.status === "SUBMITTED") {
        setResult({
          score: Number(started.score || 0),
          totalScore: Number(started.totalScore || paper.totalScore),
          passScore: Number(started.passScore || paper.passScore),
          passed: Boolean(started.passed),
        });
        setSubmitted(true);
        setRemainingAttempts(started.remainingAttempts || 0);
        sessionStorage.removeItem(draftKey);
        window.location.assign(
          withTraining(`/kaoshi/${paper.id}/result?attemptId=${started.attemptId}`),
        );
        return;
      }
      sessionStorage.setItem(
        draftKey,
        JSON.stringify({
          stage: "answer",
          name,
          phone,
          attemptId: started.attemptId,
          startedAt: started.startedAt,
          index: getResumeIndex(paper.questions, restoredAnswers),
          answers: restoredAnswers,
          seconds: remainingSeconds(started.startedAt, paper.duration, paper.endAt),
          submitted: false,
        }),
      );
      const query = new URLSearchParams({
        name,
        phone,
        // 将本次随机抽题实例传给答题页，首屏直接加载固定的随机快照。
        attemptId: String(started.attemptId),
      });
      window.location.assign(withTraining(`/kaoshi/${paper.id}/answer?${query.toString()}`));
    } catch (error) {
      Toast.error(error instanceof Error ? error.message : "无法开始考试");
    } finally {
      setStarting(false);
    }
  };
  const submitAnswers = async () => {
    if (!practice && (!name.trim() || !/^1\d{10}$/.test(phone))) {
      Toast.error("请填写正确的姓名和11位手机号");
      return;
    }
    const unansweredIndex = paper.questions.findIndex(
      (question) => !(answers[question.id] || []).length,
    );
    if (unansweredIndex >= 0 && (practice || seconds > 0)) {
      setIndex(unansweredIndex);
      Toast.error(`第 ${unansweredIndex + 1} 题未作答，请完成所有题目后再提交`);
      return;
    }
    if ((practice || seconds > 0) && !(await persistAnswer(q.id, answers[q.id] || []))) return;
    if (!practice)
      try {
        if (!attemptId) throw new Error("考试记录不存在，请重新开始考试");
        const submittedResult = await submitPublicAttempt(attemptId);
        setResult(submittedResult);
        setRemainingAttempts(submittedResult.remainingAttempts || 0);
        if (!practice) {
          window.location.assign(
            withTraining(`/kaoshi/${paper.id}/result?attemptId=${attemptId}`),
          );
          return;
        }
      } catch (error) {
      Toast.error(error instanceof Error ? error.message : "提交失败");
        return;
      }
    setSubmitted(true);
    setStage("result");
    sessionStorage.removeItem(draftKey);
    // 在线自测属于临时练习，不写入 localStorage，避免关闭浏览器后继续保留。
    // sessionStorage 仅在当前标签页会话内有效，关闭标签页/浏览器后由浏览器清理。
    const old = JSON.parse(sessionStorage.getItem("aiquestion:records") || "[]");
    sessionStorage.setItem(
      "aiquestion:records",
      JSON.stringify([
        {
          title: paper.title,
          score,
          passed: score >= paper.passScore,
          date: new Date().toLocaleString(),
          name: name || "练习",
        },
        ...old,
      ]),
    );
    // 清理旧版本遗留的持久化自测记录，避免升级后仍显示历史数据。
    localStorage.removeItem("aiquestion:records");
  };
  const submit = async () => {
    if (submitting) return;
    setSubmitting(true);
    try { await submitAnswers(); } finally { setSubmitting(false); setConfirmOpen(false); }
  };
  const requestSubmit = async () => {
    if (navigating || submitting) return;
    setNavigating(true);
    const saved = (practice || seconds > 0) ? await persistAnswer(q.id, answers[q.id] || []) : true;
    setNavigating(false);
    if (!saved) return;
    const missing = paper.questions.findIndex(question => !(answers[question.id] || []).length);
    if (missing >= 0 && (practice || seconds > 0)) {
      setIndex(missing); setCardOpen(false);
      Toast.warning(`第 ${missing + 1} 题未作答，请先完成`); return;
    }
    setCardOpen(false); setConfirmOpen(true);
  };
  if (stage === "info" && learningPage) {
    if (!learning) return <div className="flex flex-col gap-4 py-8 text-center"><p>{learningError || "正在恢复学习进度…"}</p>{learningError && <Button onClick={() => window.location.reload()}>重新加载</Button>}</div>;
    return <ExamTrainingMaterials key={learning.token} examId={paper.id} data={learning} starting={starting} onBack={() => window.location.assign(`/kaoshi/${paper.id}`)} onContinue={beginExam} />;
  }
  if (stage === "info") return <Layout.Content className={compact ? "h5-form" : "h5-form h5-content"}>
    <Typography.Title heading={3} className="h5-section-title !text-base !mt-5 !mb-3">填写考生信息</Typography.Title><p className="h5-muted">{practice ? "无需登录，答完即可查看解析。" : "用于记录学习与成绩，请准确填写。"}</p>
    {!practice && <><label>姓名 <span>*</span><Input aria-label="姓名" size="large" maxLength={100} value={name} onChange={setName} placeholder="请输入姓名" /></label><label>手机号 <span>*</span><Input aria-label="手机号" size="large" type="tel" maxLength={11} value={phone} onChange={value => setPhone(value.trim())} placeholder="请输入11位手机号" /></label></>}
    <div className="h5-hint"><IconClock /><span>如关联培训材料，请完成学习后再参加考试。已完成的学习可直接进入考试。</span></div>
    <div className="h5-bottom h5-form-actions"><Button block theme="solid" size="large" className="h5-primary" loading={starting} disabled={starting} onClick={beginExam}>{starting ? "正在检查考试记录…" : practice ? "开始自测" : "确认信息，继续"}<IconArrowRight /></Button></div>
  </Layout.Content>;
  if (stage === "answer" && !q) return <div className="h5-empty">正在加载本次考试题目…</div>;
  if (stage === "result") return <ExamResultView paper={paper} score={score} passed={result?.passed ?? score >= paper.passScore} answers={answers} showAnswers={initialShowAnswers} grades={initialGrades} candidateName={initialCandidateName || name} candidatePhone={initialCandidatePhone || phone} attemptNo={initialAttemptNo ?? attemptNo ?? undefined} startedAt={initialStartedAt} submittedAt={initialSubmittedAt} remainingAttempts={remainingAttempts} attemptId={resultAttemptId} details={details} practice={practice} />;
  const picked = answers[q.id] || [];
  const options = q.options?.length
    ? q.options
    : q.kind === "judge"
      ? [
          { key: "A", text: "正确" },
          { key: "B", text: "错误" },
        ]
      : [];
  const time = `${String(Math.floor(seconds / 60)).padStart(2, "0")}:${String(seconds % 60).padStart(2, "0")}`;

  const moveTo = async (target: number) => {
    if (navigating || submitting) return;
    setNavigating(true);
    try { if (await persistAnswer(q.id, picked)) { setIndex(target); setCardOpen(false); } }
    finally { setNavigating(false); }
  };
  const goNext = async () => {
    if (!picked.length) { Toast.warning("请先完成本题后再进入下一题"); return; }
    await moveTo(Math.min(paper.questions.length - 1, index + 1));
  };
  const answeredCount = paper.questions.filter(question => (answers[question.id] || []).length).length;
  const optionBody = (option: (typeof options)[number]) => <span className="h5-option-body"><span className="h5-option-key">{option.key}</span><span>{option.text}</span></span>;
  const answerCard = <><p className="h5-muted">已答 {answeredCount} / {paper.questions.length} 题，绿色为已答题目。</p><div className="h5-card-grid">{paper.questions.map((question, i) => <Button key={question.id} aria-label={`第 ${i + 1} 题${(answers[question.id] || []).length ? "，已答" : "，未答"}`} theme={(answers[question.id] || []).length ? "light" : "borderless"} className={`${(answers[question.id] || []).length ? "answered" : ""} ${i === index ? "current" : ""}`} disabled={navigating || submitting} onClick={() => moveTo(i)}>{i + 1}</Button>)}</div><Button block size="large" theme="solid" onClick={requestSubmit} disabled={navigating || submitting}>提交答卷</Button></>;
  return <Layout.Content className="h5-answer-layout">
    <aside className="h5-desktop-answer-card"><h2 className="h5-section-title">答题卡</h2>{answerCard}</aside>
    <div className="h5-answer-main">
    <div className="h5-exam-toolbar"><div className={`h5-timer ${seconds < 60 ? "urgent" : ""}`}><IconClock /><strong>{time}</strong><small>剩余时间</small></div><Button aria-label="打开答题卡" theme="borderless" className="h5-link" icon={<IconList />} onClick={() => setCardOpen(true)}>已答 {answeredCount}/{paper.questions.length}</Button></div>
    <Progress percent={(index + 1) / Math.max(1, paper.questions.length) * 100} showInfo={false} stroke="#60af91" style={{ height: 3 }} />
    <div className="h5-content h5-answer-content"><div className="h5-question-meta"><span className="h5-tag">{q.kind === "multi" ? "多选题" : q.kind === "judge" ? "判断题" : "单选题"}</span><span>本题 {q.score || 0} 分</span><b>{String(index + 1).padStart(2, "0")} <small>/ {String(paper.questions.length).padStart(2, "0")}</small></b></div>
      <h1 className="h5-question" id="exam-question-title">{q.title}</h1><p className="h5-muted">{q.kind === "multi" ? "请选择所有符合题意的选项" : "请选择一个正确答案"}</p>
      {q.kind === "multi" ? <CheckboxGroup aria-labelledby="exam-question-title" className="h5-answer-options" value={picked} disabled={navigating || submitting} onChange={values => setAnswers(current => ({ ...current, [q.id]: values.map(String) }))}>{options.map(option => <Checkbox key={option.key} value={option.key}>{optionBody(option)}</Checkbox>)}</CheckboxGroup> : <RadioGroup aria-labelledby="exam-question-title" className="h5-answer-options" value={picked[0]} disabled={navigating || submitting} onChange={e => choose(String(e.target.value))}>{options.map(option => <Radio key={option.key} value={option.key}>{optionBody(option)}</Radio>)}</RadioGroup>}
      <p className="h5-save"><IconTick />答案保留在当前会话，切题时同步保存</p>
    </div>
    <div className="h5-bottom"><div className="h5-actions"><Button style={{ flex: 1 }} size="large" className="h5-secondary" disabled={index === 0 || navigating || submitting} onClick={() => moveTo(Math.max(0, index - 1))}>上一题</Button>{index < paper.questions.length - 1 && (practice || seconds > 0) ? <Button style={{ flex: 1 }} size="large" theme="solid" className="h5-primary" loading={navigating} disabled={submitting} onClick={goNext}>下一题 <IconArrowRight /></Button> : <Button style={{ flex: 1 }} size="large" theme="solid" className="h5-primary" loading={submitting} disabled={navigating} onClick={requestSubmit}>提交答卷</Button>}</div><Button theme="borderless" className="h5-link" onClick={() => setCardOpen(true)}>查看答题卡 · 检查并交卷</Button></div>
    </div>
    <Modal bodyStyle={{ maxHeight: "calc(100dvh - 240px)", overflowY: "auto" }} title="答题卡" visible={cardOpen} className="h5-modal-root" modalContentClass="h5-modal" style={{ width: "min(440px, calc(100vw - 28px))" }} footer={null} onCancel={() => setCardOpen(false)}>{answerCard}</Modal>
    <Modal bodyStyle={{ maxHeight: "calc(100dvh - 240px)", overflowY: "auto" }} title="确认提交答卷？" visible={confirmOpen} className="h5-modal-root" modalContentClass="h5-modal" style={{ width: "min(440px, calc(100vw - 28px))" }} okText="确认提交" cancelText="继续检查" confirmLoading={submitting} cancelButtonProps={{ disabled: submitting }} closable={!submitting} maskClosable={!submitting} onCancel={() => { if (!submitting) setConfirmOpen(false); }} onOk={submit}><p className="h5-muted">已完成 {answeredCount} / {paper.questions.length} 道题目。提交后将结束本次考试，无法修改答案。</p>{seconds === 0 && <p className="h5-muted">考试时间已到，请提交当前答卷。</p>}</Modal>
  </Layout.Content>;
}
