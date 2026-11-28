import type { DashboardFilters, DashboardQuery } from "./types";

/** Reports summarize all matching training; paging and detail status are not report filters. */
export function reportFilters(query: DashboardQuery): DashboardFilters {
  const { start, end, trainingId, departmentId, employeeId, category } = query;
  return { start, end, trainingId, departmentId, employeeId, category };
}
