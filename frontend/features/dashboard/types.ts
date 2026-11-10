export type DashboardTab =
  "trainings" | "learners" | "incomplete" | "exams" | "logs";
export type DashboardStatus =
  "ALL" | "NOT_STARTED" | "IN_PROGRESS" | "COMPLETED" | "OVERDUE";
export type DashboardFilters = {
  start?: string;
  end?: string;
  trainingId?: number;
  departmentId?: number;
  category?: string;
};
export type DashboardQuery = DashboardFilters & {
  tab: DashboardTab;
  status: DashboardStatus;
  page: number;
  pageSize: number;
};
export type DashboardOptions = {
  trainings: { id: number; name: string }[];
  departments: { id: number; name: string }[];
};
export type DashboardRow = {
  id?: number;
  name: string;
  trainingId?: number;
  trainingName?: string;
  assessmentId?: number;
  employeeId?: number;
  phone?: string;
  departmentName?: string;
  sessionId?: number;
  assigned?: number;
  completed: number | boolean;
  completionRate?: number | null;
  studiedHours: number;
  overdue: number | boolean;
  overdueReasons?: string[];
  status?: string;
  learningStatus?: string;
  progress?: number;
  examStatus?: string;
  examName?: string;
  attemptCount?: number;
  attemptId?: number;
  score?: number | null;
  totalScore?: number;
  durationSeconds?: number | null;
  startedAt?: string;
  completedAt?: string;
  submittedAt?: string;
  missingDuration?: number;
};
export type DashboardData = {
  overview: {
    trainingCount: number;
    assigned: number;
    completed: number;
    notStarted: number;
    inProgress: number;
    studiedHours: number;
    examAssigned: number;
    passed: number;
    overdue: number;
    missingDuration: number;
    completionRate: number | null;
    passRate: number | null;
  };
  departments: {
    id: number;
    name: string;
    assigned: number;
    completed: number;
    rate: number;
  }[];
  trend: { date: string; count: number }[];
  rows: DashboardRow[];
  total: number;
  page: number;
  pageSize: number;
  updatedAt: string;
};
