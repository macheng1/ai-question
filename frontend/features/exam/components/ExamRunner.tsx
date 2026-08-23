"use client";
import { useEffect, useState } from "react";
import "@douyinfe/semi-ui/react19-adapter";
import { Toast } from "@douyinfe/semi-ui";
import type { ExamPaper } from "../types";
import { loadExamPaper } from "../load-paper";
import {
  checkPublicExamEligibility,
  getPublicExamProgress,
  savePublicAnswer,
  startPublicExam,
  submitPublicAttempt,
} from "../api";

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

const formatResultDate = (value?: string) => {
  if (!value) return "-";
  return value.replace("T", " ").slice(0, 16);
};

const formatResultDuration = (startedAt?: string, submittedAt?: string) => {
  if (!startedAt || !submittedAt) return "-";
  const duration = Math.max(0, Date.parse(submittedAt) - Date.parse(startedAt));
  if (!Number.isFinite(duration)) return "-";
  const seconds = Math.floor(duration / 1000);
  const minutes = Math.floor(seconds / 60);
  const remainingSeconds = seconds % 60;
  return minutes ? `${minutes}分${remainingSeconds}秒` : `${remainingSeconds}秒`;
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
    if (practice || initialStage !== "info") return;
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
            `/kaoshi/${paper.id}/answer?${query.toString()}`,
          );
        })
        .catch(() => {
          /* 进行中检查失败时仍保留正常的考试入口 */
        });
    } catch {
      /* ignore an invalid persisted session */
    }
  }, [initialStage, paper.id, practice]);
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
        /* 页面仍可展示，提交时会提示考试记录不存在 */
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
      // 先检查重考资格；服务端 start 接口仍会再次校验，防止并发或绕过前端。
      const eligibility = await checkPublicExamEligibility(paper.id, {
        name,
        phone,
      });
      setRemainingAttempts(eligibility.remainingAttempts);
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
          `/kaoshi/${paper.id}/result?attemptId=${started.attemptId}`,
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
      window.open(
        `/kaoshi/${paper.id}/answer?${query.toString()}`,
        "_blank",
        "noopener,noreferrer",
      );
    } catch (error) {
      Toast.error(error instanceof Error ? error.message : "无法开始考试");
    } finally {
      setStarting(false);
    }
  };
  const submit = async () => {
    if (!practice && (!name.trim() || !/^1\d{10}$/.test(phone))) {
      Toast.error("请填写正确的姓名和11位手机号");
      return;
    }
    const unansweredIndex = paper.questions.findIndex(
      (question) => !(answers[question.id] || []).length,
    );
    if (unansweredIndex >= 0) {
      setIndex(unansweredIndex);
      Toast.error(`第 ${unansweredIndex + 1} 题未作答，请完成所有题目后再提交`);
      return;
    }
    if (!(await persistAnswer(q.id, answers[q.id] || []))) return;
    if (!practice)
      try {
        if (!attemptId) throw new Error("考试记录不存在，请重新开始考试");
        const submittedResult = await submitPublicAttempt(attemptId);
        setResult(submittedResult);
        setRemainingAttempts(submittedResult.remainingAttempts || 0);
        if (!practice) {
          window.location.assign(
            `/kaoshi/${paper.id}/result?attemptId=${attemptId}`,
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
  if (stage === "info")
    return (
      <section
        className={
          compact
            ? ""
            : "mt-8 max-w-xl rounded-2xl border border-slate-200 bg-white p-7 shadow-sm"
        }
      >
        <h3 className="text-sm font-bold text-slate-700">填写信息</h3>
        <p className="mt-2 text-sm text-slate-500">
          {practice
            ? "无需登录，答完即可查看解析。"
            : "请填写姓名和手机号，交卷后可查询成绩。"}
        </p>
        {!practice && (
          <div className="mt-4 space-y-4">
            <label className="block text-xs font-medium text-slate-600">
              姓名 <span className="text-red-500">*</span>
              <input
                className="mt-2 w-full rounded-lg border border-slate-200 bg-slate-50 px-4 py-3 text-sm outline-none focus:border-teal-400"
                value={name}
                onChange={(e) => setName(e.target.value)}
                placeholder="请输入姓名"
              />
            </label>
            <label className="block text-xs font-medium text-slate-600">
              手机号 <span className="text-red-500">*</span>
              <input
                className="mt-2 w-full rounded-lg border border-slate-200 bg-slate-50 px-4 py-3 text-sm outline-none focus:border-teal-400"
                value={phone}
                onChange={(e) => setPhone(e.target.value)}
                placeholder="请输入11位手机号"
              />
            </label>
            <label className="block text-xs font-medium text-slate-600">
              工种/职务{" "}
              <span className="font-normal text-slate-400">（选填）</span>
              <input
                className="mt-2 w-full rounded-lg border border-slate-200 px-4 py-3 text-sm outline-none focus:border-teal-400"
                placeholder="请输入工种或职务（选填）"
              />
            </label>
          </div>
        )}
        <button
          className="mt-5 w-full rounded-lg bg-[#ff8500] px-5 py-3 text-sm font-bold text-white disabled:cursor-not-allowed disabled:opacity-60"
          onClick={beginExam}
          disabled={starting}
        >
          {starting ? "正在检查考试记录…" : `开始${practice ? "自测" : "考试"}`}
        </button>
      </section>
    );
  if (stage === "answer" && !q)
    return <div className="mx-auto mt-8 max-w-3xl rounded-2xl border border-slate-200 bg-white p-10 text-center text-sm text-slate-400">正在加载本次考试题目...</div>;
  const optionsFor = (question: (typeof paper.questions)[number]) =>
    question.options?.length
      ? question.options
      : question.kind === "judge"
        ? [
            { key: "A", text: "正确" },
            { key: "B", text: "错误" },
          ]
        : [];
  const answerLabel = (
    question: (typeof paper.questions)[number],
    keys: string[],
  ) =>
    keys
      .map((key) => {
        const option = optionsFor(question).find((item) => item.key === key);
        return option ? `${option.key}. ${option.text}` : key;
      })
      .join("、");
  const isAnswerCorrect = (
    question: (typeof paper.questions)[number],
    pickedKeys: string[],
  ) => {
    const correctKeys = question.answer || [];
    if (!pickedKeys.length || !correctKeys.length) return false;
    if (question.kind === "multi") {
      const picked = new Set(pickedKeys);
      return correctKeys.every((key) => picked.has(key));
    }
    return (
      [...pickedKeys].sort().join(",") === [...correctKeys].sort().join(",")
    );
  };
  const correctCount = paper.questions.filter((question) =>
    isAnswerCorrect(question, answers[question.id] || []),
  ).length;
  const showAnswers = initialShowAnswers;
  if (stage === "result")
    return (
      <div className="result-print-root mx-auto mt-4 max-w-3xl space-y-3 pb-6 sm:mt-8 sm:space-y-4 sm:pb-10">
        <section className="result-print-card rounded-xl border border-slate-200 bg-white px-4 py-6 text-center shadow-sm sm:rounded-2xl sm:px-8 sm:py-7">
          <h1 className="break-words text-xl font-bold leading-8 text-slate-800">{paper.title}</h1>
          <p className="mt-2 break-words text-sm text-slate-500">{paper.subtitle}</p>
          {(initialCandidateName || initialCandidatePhone) && (
            <p className="mt-2 break-words text-sm text-slate-600">
              {initialCandidateName || "匿名考生"} · {initialCandidatePhone || "-"}
            </p>
          )}
          <p className="mt-1 text-xs text-slate-400">
            第 {initialAttemptNo || 1} 次答题
          </p>
          <div className="mt-4 text-5xl font-bold text-slate-800 sm:text-6xl">
            {score}
            <small className="ml-2 text-lg font-medium text-slate-400">
              分
            </small>
          </div>
          <p className="mt-2 text-sm text-slate-500">
            满分 {paper.totalScore}
            {showAnswers && <> · 答对 {correctCount}/{paper.questions.length} 题</>}
          </p>
          {(initialStartedAt || initialSubmittedAt) && (
            <p className="mt-2 text-xs text-slate-400">
              开始：{formatResultDate(initialStartedAt)} · 交卷：
              {formatResultDate(initialSubmittedAt)} · 用时：
              {formatResultDuration(initialStartedAt, initialSubmittedAt)}
            </p>
          )}
          <p
            className={`mt-3 font-semibold ${score >= paper.passScore ? "text-emerald-600" : "text-orange-600"}`}
          >
            {score >= paper.passScore ? "恭喜，通过考试" : "本次未达到及格线"}
          </p>
        </section>
        {showAnswers && (
        <section className="result-print-card rounded-xl border border-slate-200 bg-white p-4 shadow-sm sm:rounded-2xl sm:p-7">
          <h2 className="text-lg font-bold text-slate-800">答题详情</h2>
          <div className="mt-4 divide-y divide-slate-100">
            {paper.questions.map((question, questionIndex) => {
              const pickedKeys = answers[question.id] || [];
              const correct = isAnswerCorrect(question, pickedKeys);
              return (
                <article
                  key={question.id}
                  className="result-print-question py-5 first:pt-2 last:pb-2"
                >
                  <div className="flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between sm:gap-3">
                    <div className="min-w-0 break-words text-sm font-medium leading-6 text-slate-800">
                      <span className="mr-2 text-slate-400">
                        {questionIndex + 1}.
                      </span>
                      <span className="mr-2 rounded bg-blue-50 px-2 py-1 text-xs text-blue-600">
                        {question.kind === "multi"
                          ? "多选"
                          : question.kind === "judge"
                            ? "判断"
                            : "单选"}
                      </span>
                      {question.title}
                    </div>
                    <span
                      className={`shrink-0 text-sm font-semibold ${correct ? "text-emerald-600" : "text-red-500"}`}
                    >
                      {correct ? "✓" : "×"} {correct ? question.score || 0 : 0}{" "}
                      分
                    </span>
                  </div>
                  <div className="mt-3 grid gap-2 break-words text-sm text-slate-600 sm:grid-cols-2">
                    {optionsFor(question).map((option) => (
                      <div
                        key={option.key}
                        className={
                          question.answer.includes(option.key)
                            ? "font-medium text-emerald-600"
                            : pickedKeys.includes(option.key)
                              ? "font-medium text-red-500"
                              : "text-slate-600"
                        }
                      >
                        {option.key}. {option.text}
                      </div>
                    ))}
                  </div>
                  <p className="mt-2 text-xs text-slate-500">
                    答案：
                    {pickedKeys.length
                      ? answerLabel(question, pickedKeys)
                      : "未作答"}
                  </p>
                  <p className="mt-1 text-xs font-medium text-emerald-600">
                    正确答案：
                    {answerLabel(question, question.answer || []) ||
                      "暂无答案"}
                  </p>
                  <div className="mt-2 break-words rounded-lg bg-amber-50 px-3 py-2 text-xs leading-6 text-slate-600">
                    <span className="font-medium text-slate-700">解析：</span>
                    {question.analysis || "暂无解析"}
                  </div>
                </article>
              );
            })}
          </div>
        </section>
        )}
        <div className="flex flex-col justify-center gap-3 sm:flex-row sm:flex-wrap">
          <button
            type="button"
            onClick={() => window.print()}
            className="print-hidden w-full rounded-lg bg-[#ff8500] px-6 py-3 text-sm font-bold text-white sm:w-auto"
          >
            打印成绩
          </button>
          {remainingAttempts > 0 && (
            <a
              className="print-hidden w-full rounded-lg bg-[#ff8500] px-6 py-3 text-center text-sm font-bold text-white no-underline sm:w-auto"
              href={`/kaoshi/${paper.id}`}
            >
              重做（还可 {remainingAttempts} 次）
            </a>
          )}
          <a
            className="print-hidden w-full rounded-lg border border-slate-200 bg-white px-6 py-3 text-center text-sm font-bold text-slate-600 no-underline sm:w-auto"
            href="/"
          >
            回到首页
          </a>
          <a
            className="print-hidden w-full rounded-lg border border-slate-200 bg-white px-6 py-3 text-center text-sm font-bold text-slate-600 no-underline sm:w-auto"
            href="/profile"
          >
            查看个人中心
          </a>
        </div>
      </div>
    );
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

  const goPrevious = async () => {
    if (!(await persistAnswer(q.id, picked))) return;
    setIndex((current) => Math.max(0, current - 1));
  };

  const goNext = async () => {
    if (!picked.length) {
      Toast.error("请先完成本题后再进入下一题");
      return;
    }
    if (!(await persistAnswer(q.id, picked))) return;
    setIndex((current) =>
      Math.min(paper.questions.length - 1, current + 1),
    );
  };
  return (
    <section className="mx-auto mt-4 max-w-3xl sm:mt-8">
      <div className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm sm:rounded-2xl sm:p-8">
        <div className="flex justify-between border-b border-slate-100 pb-4 text-sm text-slate-500">
          <span>
            第 {index + 1} / {paper.questions.length} 题
            {attemptNo ? ` · 第 ${attemptNo} 次答题` : ""}
          </span>
          <b className="text-[#ff8500]">剩余 {time}</b>
        </div>
        <div className="mt-5 sm:mt-7">
          <h3 className="break-words text-lg font-bold leading-8 text-slate-800 sm:text-xl sm:leading-9">
            {q.title}
          </h3>
          <p className="mt-2 text-xs text-[#2db9b0]">
            {q.kind === "multi"
              ? "多选题"
              : q.kind === "judge"
                ? "判断题"
                : "单选题"}
          </p>
        </div>
        <div className="mt-4 grid gap-3 sm:mt-5">
          {options.map((option) => (
            <button
              key={option.key}
              onClick={() => choose(option.key)}
              className={`flex min-h-14 w-full items-start gap-3 rounded-xl border p-3 text-left text-sm leading-6 sm:items-center sm:p-4 sm:text-base ${picked.includes(option.key) ? "border-teal-400 bg-teal-50" : "border-slate-200 bg-white"}`}
            >
              <span
                className={`grid h-7 w-7 place-items-center rounded-full text-xs ${picked.includes(option.key) ? "bg-[#2db9b0] text-white" : "bg-slate-100 text-slate-500"}`}
              >
                {option.key}
              </span>
              {option.text}
            </button>
          ))}
        </div>
        <div className="mt-6 grid grid-cols-2 gap-3 sm:mt-7 sm:flex sm:justify-between">
          <button
            className="w-full rounded-lg border border-teal-100 px-3 py-3 text-sm font-bold text-teal-700 disabled:opacity-40 sm:w-auto sm:px-5"
            disabled={index === 0}
            onClick={goPrevious}
          >
            上一题
          </button>
          {index < paper.questions.length - 1 ? (
            <button
              className="w-full rounded-lg bg-[#ff8500] px-3 py-3 text-sm font-bold text-white sm:w-auto sm:px-5"
              onClick={goNext}
            >
              下一题
            </button>
          ) : (
            <button
              className="w-full rounded-lg bg-[#ff8500] px-3 py-3 text-sm font-bold text-white sm:w-auto sm:px-5"
              onClick={submit}
            >
              提交试卷
            </button>
          )}
        </div>
      </div>
    </section>
  );
}
