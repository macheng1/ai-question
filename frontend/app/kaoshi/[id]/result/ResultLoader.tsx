"use client";
import { Layout } from "@douyinfe/semi-ui";

import { useEffect, useState } from "react";
import { getPublicAttemptResult } from "@/features/exam/api";
import { ExamRunner } from "@/features/exam/components/ExamRunner";
import { readSavedAnswer, type ExamPaper } from "@/features/exam/types";

type ResultLoaderProps = {
  paper: ExamPaper;
  attemptId: string;
  details?: boolean;
};

/**
 * 结果页只负责按答题记录加载数据，具体结果展示仍复用考试组件，
 * 保证正式考试和练习模式的详情样式、答案映射保持一致。
 */
export function ResultLoader({ paper, attemptId, details = false }: ResultLoaderProps) {
  const [data, setData] = useState<{
    result: {
      score: number;
      totalScore: number;
      passScore: number;
      passed: boolean;
    };
    answers: Record<number, string[]>;
    remainingAttempts: number;
    candidateName: string;
    phone: string;
    attemptNo: number;
    startedAt?: string;
    submittedAt?: string;
    showAnswers: boolean;
    grades: { questionId: number; isCorrect: boolean; score: number }[];
  } | null>(null);
  const [pendingReview, setPendingReview] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    let active = true;
    getPublicAttemptResult(attemptId)
      .then((response) => {
        if (!active) return;
        if (String(response.taskId) !== String(paper.id)) throw new Error("答题记录与本场考试不匹配");
        if (response.pendingReview || response.status === "PENDING_REVIEW") {
          setPendingReview(true);
          return;
        }
        const answers = Object.fromEntries(
          (response.answers || []).map((item) => {
            const question = paper.questions.find((q) => q.id === item.questionId);
            return [item.questionId, readSavedAnswer(question?.kind, item.answerText)];
          }),
        );
        setData({
          result: {
            score: Number(response.score || 0),
            totalScore: Number(response.totalScore || paper.totalScore),
            passScore: Number(response.passScore || paper.passScore),
            passed: Boolean(response.passed),
          },
          answers,
          remainingAttempts: Number(response.remainingAttempts || 0),
          candidateName: response.candidateName || "",
          phone: response.phone || "",
          attemptNo: Number(response.attemptNo || 1),
          startedAt: response.startedAt,
          submittedAt: response.submittedAt,
          showAnswers: response.showAnswers !== false,
          grades: response.answers || [],
        });
      })
      .catch((reason) => {
        if (active) {
          setError(
            reason instanceof Error ? reason.message : "成绩详情加载失败",
          );
        }
      });
    return () => {
      active = false;
    };
  }, [attemptId, paper]);

  if (error) {
    return (
      <Layout.Content className="mx-auto mt-8 max-w-3xl rounded-2xl border border-red-100 bg-white p-8 text-center text-sm text-red-500 shadow-sm">
        {error}
      </Layout.Content>
    );
  }

  if (pendingReview) return (
    <section className="h5-content text-center">
      <h1 className="h5-section-title">待批阅</h1>
      <p className="h5-muted my-4">《{paper.title}》已交卷，教师完成批阅后可查看成绩。</p>
      <a className="h5-link" href="/learner/exams">返回我的考试</a>
    </section>
  );

  if (!data) {
    return (
      <Layout.Content className="mx-auto mt-8 max-w-3xl rounded-2xl border border-slate-200 bg-white p-8 text-center text-sm text-slate-400 shadow-sm">
        正在加载成绩详情…
      </Layout.Content>
    );
  }

  return (
    <ExamRunner
      paper={paper}
      initialStage="result"
      initialResult={data.result}
      initialAnswers={data.answers}
      initialRemainingAttempts={data.remainingAttempts}
      initialCandidateName={data.candidateName}
      initialCandidatePhone={data.phone}
      initialAttemptNo={data.attemptNo}
      initialStartedAt={data.startedAt}
      initialSubmittedAt={data.submittedAt}
      initialShowAnswers={data.showAnswers}
      allowRestore={false}
      details={details}
      initialGrades={data.grades}
      resultAttemptId={attemptId}
    />
  );
}
