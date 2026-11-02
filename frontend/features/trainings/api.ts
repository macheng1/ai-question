import { apiClient } from "@/lib/api/client";
export type SurveyQuestion = { id: string; type: "RATING" | "SINGLE" | "TEXT"; title: string; required: boolean; options: string[] };
export type Training = { id: number; assessmentId?: number | null; name: string; description: string; surveyEnabled: boolean; questions: SurveyQuestion[]; version: number; responseCount?: number; submitted?: boolean };
export type SaveTraining = Omit<Training, "id" | "responseCount" | "submitted" | "version"> & { version?: number };
export type SurveyResponse = { id: number; respondentName: string; submittedAt: string; answers: Record<string, string> };
export const getTrainings = () => apiClient.get<Training[]>("/management/trainings");
export const getTraining = (id: string | number) => apiClient.get<Training>(`/management/trainings/${id}`);
export const saveTraining = (id: number | undefined, data: SaveTraining) => id ? apiClient.put<Training>(`/management/trainings/${id}`, data) : apiClient.post<Training>("/management/trainings", data);
export const deleteTraining = (id: number) => apiClient.delete<void>(`/management/trainings/${id}`);
export const getResponses = (id: string) => apiClient.get<SurveyResponse[]>(`/management/trainings/${id}/responses`);
export const getSurvey = (id: string) => apiClient.get<Training>(`/trainings/${id}/survey`);
export const submitSurvey = (id: number, version: number, answers: Record<string, string>) => apiClient.post<void>(`/trainings/${id}/survey`, { version, answers });

export const createSurveyQuestion = (type: SurveyQuestion["type"]) => apiClient.post<SurveyQuestion>("/management/trainings/question-drafts", { type });
