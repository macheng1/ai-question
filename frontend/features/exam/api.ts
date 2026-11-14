import { confirmLearner } from "@/features/auth/learner-session";
import { apiClient } from "@/lib/api/client";
export function startPublicExam(
  id: string,
  data: { name: string; phone: string },
) {
  return apiClient.post<{
    attemptId: number;
    startedAt: string;
    status?: "IN_PROGRESS" | "PENDING_REVIEW" | "SUBMITTED";
    pendingReview?: boolean;
    submittedAt?: string;
    attemptNo?: number;
    maxAttempts?: number;
    remainingAttempts?: number;
    showAnswers?: boolean;
    score?: number;
    totalScore?: number;
    passScore?: number;
    passed?: boolean;
    answers: Array<{ question_id: number; answer_text: string }>;
  }>(`/public/exams/${id}/start`, data);
}
export async function checkPublicExamEligibility(
  id: string,
  data: { name: string; phone: string },
) {
  await confirmLearner("EXAM", Number(id), data.name, data.phone);
  return apiClient.get<{
    allowRetake: boolean;
    maxAttempts: number;
    attemptCount: number;
    remainingAttempts: number;
    inProgress: boolean;
    canStart: boolean;
    pendingReview?: boolean;
    pendingAttemptId?: number;
  }>(`/public/exams/${id}/eligibility`, data);
}
export function getPublicExamProgress(
  id: string,
  data: { name: string; phone: string },
) {
  return apiClient.get<{
    inProgress: boolean;
    attemptId?: number;
    startedAt?: string;
    answers?: Array<{ question_id: number; answer_text: string }>;
  }>(`/public/exams/${id}/progress`, data);
}
export function savePublicAnswer(
  attemptId: number,
  questionId: number,
  answer: string[],
) {
  return apiClient.put<void>(
    `/public/exams/attempts/${attemptId}/answers/${questionId}`,
    { answer: answer.join(",") },
  );
}
export function submitPublicAttempt(attemptId: number) {
  return apiClient.post<{
    pendingReview?: boolean;
    status?: string;
    score?: number | null;
    totalScore: number;
    passScore: number;
    passed?: boolean | null;
    attemptNo?: number;
    maxAttempts?: number;
    remainingAttempts?: number;
  }>(`/public/exams/attempts/${attemptId}/submit`, {});
}
export type ProfileExamRecord = {
  attemptId: number;
  taskId: number;
  examName: string;
  paperName?: string;
  score?: number;
  totalScore: number;
  passScore: number;
  passed?: boolean;
  status: "SUBMITTED" | "IN_PROGRESS" | "PENDING_REVIEW";
  attemptNo: number;
  startedAt?: string;
  submittedAt?: string;
  durationSeconds?: number;
  candidateName?: string;
  phone?: string;
};
export type MistakeOption = { key: string; text: string };
export type MistakeRecord = {
  attemptId: number;
  questionId: number;
  taskId: number;
  examName: string;
  bankName: string;
  questionType: string;
  stem: string;
  correctAnswer: string;
  analysis?: string;
  userAnswer: string;
  answeredAt?: string;
  options: MistakeOption[];
};
export function getPublicAttemptResult(attemptId: string | number) {
  return apiClient.get<{
    attemptId: number;
    taskId: number;
    attemptNo: number;
    pendingReview?: boolean;
    status?: string;
    score?: number | null;
    totalScore: number;
    passScore: number;
    passed?: boolean | null;
    startedAt?: string;
    submittedAt?: string;
    candidateName?: string;
    phone?: string;
    remainingAttempts?: number;
    showAnswers?: boolean;
    answers: Array<{
      questionId: number;
      answerText: string;
      isCorrect: boolean;
      score: number;
    }>;
  }>(`/public/exams/attempts/${attemptId}/result`);
}
