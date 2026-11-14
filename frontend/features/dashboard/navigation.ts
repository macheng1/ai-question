import type { DashboardTab } from "./types";

export type DashboardView = "overview" | DashboardTab;
export const dashboardTabs: { key: DashboardView; label: string }[] = [
  { key: "overview", label: "数据总览" },
  { key: "trainings", label: "学时汇总" },
  { key: "learners", label: "学员明细" },
  { key: "incomplete", label: "未完成人员清单" },
  { key: "logs", label: "学习日志" },
];
