import { apiClient } from "@/lib/api/client";
import type { PageResult } from "@/features/banks/types";
import type { QuestionItem } from "@/features/banks/management-api";

export type Paper = {
  id: number;
  name: string;
  description?: string;
  mode: string;
  totalScore: number;
  passScore: number;
  durationMinutes: number;
  status: string;
  questionCount?: number;
  hasAssessment?: boolean;
};
export type SavePaper = {
  name: string;
  description?: string;
  mode: string;
  totalScore: number;
  passScore: number;
  durationMinutes: number;
};
export function getPapers(keyword?: string) {
  return apiClient.get<PageResult<Paper>>("/management/papers", {
    pageNo: 1,
    pageSize: 50,
    keyword,
  });
}
export function getPaper(id: number) {
  return apiClient.get<Paper>(`/management/papers/${id}`);
}
export function createPaper(data: SavePaper) {
  return apiClient.post<Paper>("/management/papers", data);
}
export function updatePaper(id: number, data: SavePaper) {
  return apiClient.put<Paper>(`/management/papers/${id}`, data);
}
export function deletePaper(id: number) {
  return apiClient.delete<void>(`/management/papers/${id}`);
}
export function updatePaperStatus(id: number, value: string) {
  return apiClient.patch<Paper>(`/management/papers/${id}/status`, undefined, {
    params: { value },
  });
}
export function copyPaper(
  id: number,
  data: { name: string; description?: string },
) {
  return apiClient.post<Paper>(`/management/papers/${id}/copy`, data);
}
export function getPaperQuestions(id: number) {
  return apiClient.get<QuestionItem[]>(`/management/papers/${id}/questions`);
}
export function addPaperQuestion(
  id: number,
  data: { questionId: number; score?: number },
) {
  return apiClient.post<QuestionItem>(
    `/management/papers/${id}/questions`,
    data,
  );
}
export function removePaperQuestion(id: number, questionId: number) {
  return apiClient.delete<void>(
    `/management/papers/${id}/questions/${questionId}`,
  );
}
export function updatePaperQuestionScore(
  id: number,
  questionId: number,
  score: number,
) {
  return apiClient.patch<QuestionItem>(
    `/management/papers/${id}/questions/${questionId}/score`,
    undefined,
    { params: { value: score } },
  );
}

export type RandomConfig = {
  id?: number;
  bankId: number;
  bankName: string;
  singleCount: number;
  singleScore: number;
  multipleCount: number;
  multipleScore: number;
  trueFalseCount: number;
  trueFalseScore: number;
  availableSingleCount: number;
  availableMultipleCount: number;
  availableTrueFalseCount: number;
};
export function getRandomConfig(id: number) {
  return apiClient.get<RandomConfig[]>(`/management/papers/${id}/random-config`);
}
export function addRandomBank(id: number, bankId: number) {
  return apiClient.post<void>(`/management/papers/${id}/random-config/banks`, { bankId });
}
export function removeRandomBank(id: number, bankId: number) {
  return apiClient.delete<void>(`/management/papers/${id}/random-config/banks/${bankId}`);
}
export function saveRandomConfig(id: number, items: Omit<RandomConfig, "id" | "bankName" | "availableSingleCount" | "availableMultipleCount" | "availableTrueFalseCount">[]) {
  return apiClient.put<void>(`/management/papers/${id}/random-config`, items);
}
