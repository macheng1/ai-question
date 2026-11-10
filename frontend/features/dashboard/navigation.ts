import type { DashboardTab } from "./types";

export type DashboardView = "overview" | DashboardTab;
export const dashboardTabs: { key: DashboardView; label: string }[] = [
  { key: "overview", label: "数据总览" },
  { key: "trainings", label: "培训汇总" },
  { key: "learners", label: "学员明细" },
  { key: "incomplete", label: "未完成名单" },
  { key: "exams", label: "考试成绩" },
  { key: "logs", label: "学习日志" },
];
