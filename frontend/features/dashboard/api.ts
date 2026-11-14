import { apiClient } from "@/lib/api/client";
import type { DashboardData, DashboardOptions, DashboardQuery } from "./types";
export const getDashboardOptions = () =>
  apiClient.get<DashboardOptions>("/management/dashboard/options");
export const getDashboard = (query: DashboardQuery) =>
  apiClient.get<DashboardData>("/management/dashboard", query);

export const exportDashboardRows = (query: DashboardQuery) => {
  const { start, end, trainingId, departmentId, category, status } = query;
  const roster = query.tab === "incomplete";
  return apiClient.download(roster ? "/management/dashboard/incomplete/export" : "/management/dashboard/logs/export", {
    params: { start, end, trainingId, departmentId, category, status },
    filename: roster ? "培训人员名单.xlsx" : "学习日志.xlsx",
  });
};
