import { apiClient } from "@/lib/api/client";
export type SurveyQuestion = {
  id: string;
  type: "RATING" | "SINGLE" | "TEXT";
  title: string;
  required: boolean;
  options: string[];
};
export const trainingCategories = [
  { value: "UNCATEGORIZED", label: "未分类" },
  { value: "ONBOARDING", label: "入职培训" },
  { value: "SAFETY", label: "安全培训" },
  { value: "POLICY", label: "制度培训" },
];
export type TrainingAudienceScope = {
  type: "ALL" | "DEPARTMENT" | "USERS";
  ids: number[];
};
export type Training = {
  audience?: TrainingAudienceScope;
  purpose?: "STANDALONE" | "EXAM";
  learningLocked?: boolean;
  category?: string;
  requirementType?: "REQUIRED" | "ELECTIVE";
  id: number;
  assessmentId?: number | null;
  startAt?: string | null;
  endAt?: string | null;
  name: string;
  description: string;
  surveyEnabled: boolean;
  questions: SurveyQuestion[];
  version: number;
  responseCount?: number;
  submitted?: boolean;
};
export type SaveTraining = Omit<
  Training,
  "id" | "responseCount" | "submitted" | "version"
> & { version?: number };
export type SurveyResponse = {
  id: number;
  respondentName: string;
  submittedAt: string;
  answers: Record<string, string>;
};
export const getTrainings = () =>
  apiClient.get<Training[]>("/management/trainings");
export const getTraining = (id: string | number) =>
  apiClient.get<Training>(`/management/trainings/${id}`);
export const saveTraining = (id: number | undefined, data: SaveTraining) =>
  id
    ? apiClient.put<Training>(`/management/trainings/${id}`, data)
    : apiClient.post<Training>("/management/trainings", data);
export const deleteTraining = (id: number) =>
  apiClient.delete<void>(`/management/trainings/${id}`);
export const getResponses = (id: string) =>
  apiClient.get<SurveyResponse[]>(`/management/trainings/${id}/responses`);
export const getSurvey = (id: string) =>
  apiClient.get<Training>(`/trainings/${id}/survey`);
export const submitSurvey = (
  id: number,
  version: number,
  answers: Record<string, string>,
) => apiClient.post<void>(`/trainings/${id}/survey`, { version, answers });

export const createSurveyQuestion = (type: SurveyQuestion["type"]) =>
  apiClient.post<SurveyQuestion>("/management/trainings/question-drafts", {
    type,
  });

export type ReminderRecipient = { employeeId: number; name: string; department: string; email: string; reasons: string[]; selectable: boolean; lastSentAt: string | null };
export type ReminderPreview = { requestId: string; trainingEndAt: string | null; examEndAt: string | null; mailConfigured: boolean; recipients: ReminderRecipient[] };
export type ReminderResult = { employeeId: number; name: string; status: "SENT" | "FAILED" | "SKIPPED"; message: string };
export const getReminderPreview = (id: number) => apiClient.get<ReminderPreview>(`/management/trainings/${id}/reminders`);
export const sendReminders = (id: number, requestId: string, employeeIds: number[]) => apiClient.post<ReminderResult[]>(`/management/trainings/${id}/reminders`, { requestId, employeeIds }, { timeout: 900000 });

export const copyTraining = (id: number) => apiClient.post<Training>(`/management/trainings/${id}/copy`, undefined, { timeout: 900000 });

export const getTrainingExcludedEmployees = (assessmentId: number, trainingId?: number) =>
  apiClient.get<number[]>("/management/trainings/audience-eligibility", { assessmentId, trainingId });
