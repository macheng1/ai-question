"use client";

import { apiClient } from "@/lib/api/client";
import type { AuthResult, UserProfile } from "./api";
import { useLearnerStore } from "./store";

export function getLearnerProfile() {
  return apiClient.get<UserProfile>("/learner/me");
}

/** 入口校验由后端执行，学员身份不覆盖管理员会话。 */
export async function confirmLearner(type: "TRAINING" | "EXAM", id: number, name: string, phone: string) {
  const result = await apiClient.post<AuthResult>("/learner/session", { type, id, name: name.trim(), phone: phone.trim() });
  useLearnerStore.getState().setSession(result, result.token.accessToken);
  window.localStorage.setItem("aiquestion-learner-entry", type === "TRAINING" ? `/training/${id}` : `/kaoshi/${id}`);
  return result;
}

export function learnerEntry() {
  if (typeof window === "undefined") return null;
  const entry = window.localStorage.getItem("aiquestion-learner-entry");
  return entry && /^\/(training|kaoshi)\/\d+$/.test(entry) ? entry : null;
}
