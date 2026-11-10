import { apiClient } from "@/lib/api/client";
import type { DashboardData, DashboardOptions, DashboardQuery } from "./types";
export const getDashboardOptions = () =>
  apiClient.get<DashboardOptions>("/management/dashboard/options");
export const getDashboard = (query: DashboardQuery) =>
  apiClient.get<DashboardData>("/management/dashboard", query);
