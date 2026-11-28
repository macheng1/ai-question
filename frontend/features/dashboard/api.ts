import { apiClient } from "@/lib/api/client";
import type { DashboardData, DashboardOptions, DashboardQuery } from "./types";
import { reportFilters } from "./report";
export const getDashboardOptions = () =>
  apiClient.get<DashboardOptions>("/management/dashboard/options");
export const getDashboard = (query: DashboardQuery) =>
  apiClient.get<DashboardData>("/management/dashboard", query);

export const exportDashboardReport = (query: DashboardQuery) =>
  apiClient.download("/management/dashboard/report/export", {
    params: reportFilters(query),
    filename: query.employeeId == null ? "企业培训报告.pdf" : "个人培训报告.pdf",
    config: { timeout: 120_000 },
  });

export const exportDashboardRows = (query: DashboardQuery) => {
  const { start, end, trainingId, departmentId, employeeId, category, status } = query;
  const roster = query.tab === "incomplete";
  return apiClient.download(roster ? "/management/dashboard/incomplete/export" : "/management/dashboard/logs/export", {
    params: { start, end, trainingId, departmentId, employeeId, category, status },
    filename: roster ? "未完成人员清单.xlsx" : "学习日志.xlsx",
  });
};
