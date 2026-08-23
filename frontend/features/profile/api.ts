import { apiClient } from "@/lib/api/client";
import type { MistakeRecord, ProfileExamRecord } from "@/features/exam/api";

export function getMyExamRecords() {
  return apiClient.get<ProfileExamRecord[]>("/profile/records");
}

export function getMyMistakes() {
  return apiClient.get<MistakeRecord[]>("/profile/mistakes");
}
