import { apiClient } from "@/lib/api/client";

export type SaveAssessment = {
  name: string;
  paperId: number;
  startAt: string;
  endAt: string;
  durationMinutes: number;
  allowRetake?: boolean;
  maxAttempts?: number;
  shuffleQuestions?: boolean;
  shuffleOptions?: boolean;
  showResultImmediately?: boolean;
};
export type Assessment = {
  id: number;
  name: string;
  paperId: number;
  paperName?: string;
  paperMode?: string;
  questionCount?: number;
  passScore?: number;
  startAt: string;
  endAt: string;
  durationMinutes: number;
  candidateScope?: string;
  allowRetake?: boolean;
  maxAttempts?: number;
  shuffleQuestions?: boolean;
  shuffleOptions?: boolean;
  showResultImmediately?: boolean;
  status: string;
};
export function createAssessment(data: SaveAssessment) {
  return apiClient.post<Assessment>("/management/assessments", data);
}
export function getAssessments(
  params: { keyword?: string; startAt?: string; endAt?: string } = {},
) {
  return apiClient.get<{
    records: Assessment[];
    total: number;
    pageNo: number;
    pageSize: number;
    pages: number;
  }>("/management/assessments", { pageNo: 1, pageSize: 50, ...params });
}
export function getAssessment(id: number) {
  return apiClient.get<Assessment>(`/management/assessments/${id}`);
}
export function updateAssessment(id: number, data: SaveAssessment) {
  return apiClient.put<Assessment>(`/management/assessments/${id}`, data);
}
export function updateAssessmentStatus(id: number, value: string) {
  return apiClient.patch<Assessment>(
    `/management/assessments/${id}/status`,
    undefined,
    { params: { value } },
  );
}
export function deleteAssessment(id: number) {
  return apiClient.delete<void>(`/management/assessments/${id}`);
}
export function getAssessmentStatistics(id: number, includeRetake = false) {
  return apiClient.get<{
    totalCount: number;
    averageScore?: number | null;
    gradedCount: number;
    passedCount: number;
    totalScore: number;
  }>(`/management/assessments/${id}/statistics`, { includeRetake });
}
export type AssessmentStatisticsDetail = {
  task: Assessment;
  overview: {
    participantCount: number;
    averageScore?: number | null;
    passRate?: number | null;
    highestScore?: number | null;
    lowestScore?: number | null;
    averageDurationSeconds: number;
    totalScore: number;
    passScore: number;
  };
  scoreDistribution: Array<{ label: string; count: number }>;
  passDistribution: Array<{ label: string; count: number }>;
  submissionTrend: Array<{ label: string; count: number }>;
  typeScore: Array<{
    type: string;
    score: number;
    fullScore: number;
    correctRate: number;
  }>;
  difficultQuestions: Array<{
    questionId: number;
    title: string;
    type: string;
    answeredCount: number;
    correctRate: number;
  }>;
};
export function getAssessmentStatisticsDetail(
  id: number,
  includeRetake = false,
) {
  return apiClient.get<AssessmentStatisticsDetail>(
    `/management/assessments/${id}/statistics/detail`,
    { includeRetake },
  );
}
export function getAssessmentRecords(id: number, includeRetake = false) {
  return apiClient.get<
    Array<{
      id: number;
      candidateName: string;
      phone?: string;
      departmentId?: number;
      departmentName?: string;
      score?: number | null;
      totalScore: number;
      passed?: boolean | null;
      attemptNo: number;
      startedAt?: string;
      submittedAt?: string;
      status: string;
    }>
  >(`/management/assessments/${id}/records`, { includeRetake });
}

export type AssessmentRosterRow = {
  employeeId: number;
  departmentId: number;
  departmentName: string;
  candidateName: string;
  phone: string;
  submitted: boolean;
};
export function getAssessmentRoster(id: number) {
  return apiClient.get<AssessmentRosterRow[]>(
    `/management/assessments/${id}/roster`,
  );
}
