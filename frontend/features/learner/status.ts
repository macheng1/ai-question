import type { LearnerTraining } from "./api";

export const learningLabels = {
  NOT_STARTED: "未开始",
  IN_PROGRESS: "学习中",
  COMPLETED: "学习完成",
} as const;
export const examLabels: Record<string, string> = {
  NOT_STARTED: "考试未开始",
  WAITING_LEARNING: "待完成培训",
  READY: "待考试",
  IN_PROGRESS: "答题中",
  PASSED: "已通过",
  FAILED: "未通过",
  PENDING_REVIEW: "待批阅",
  RESULT_PENDING: "结果待公布",
  ENDED: "考试已结束",
};
export const formatTime = (value?: string) =>
  value ? new Date(value).toLocaleString("zh-CN", { hour12: false }) : "—";
export const formatDuration = (seconds: number) =>
  `${Math.floor(seconds / 60)}分${Math.floor(seconds % 60)}秒`;
export function isPendingReview(row: { pendingReview?: boolean; examStatus?: string; status?: string }) {
  return Boolean(row.pendingReview || row.status === "PENDING_REVIEW" || row.examStatus === "PENDING_REVIEW");
}

export function learnerExamStatus(row: LearnerTraining) {
  return isPendingReview(row) ? "PENDING_REVIEW" : row.examStatus || "";
}

export function learningAction(row: LearnerTraining) {
  if (row.trainingEnded || isPendingReview(row)) return "回看培训";
  if (row.learningStatus === "NOT_STARTED") return "开始学习";
  if (row.learningStatus === "IN_PROGRESS") return "继续学习";
  if (row.examStatus === "READY") return "进入考试";
  if (row.examStatus === "IN_PROGRESS") return "继续考试";
  return "回看培训";
}

export function learningHref(row: LearnerTraining) {
  const mode = row.trainingEnded || isPendingReview(row) ? "review" : row.learningStatus !== "COMPLETED" ? "resume" :
    ["READY", "IN_PROGRESS"].includes(row.examStatus || "") ? "exam" : "review";
  return `/training/${row.id}?${mode}=1`;
}
