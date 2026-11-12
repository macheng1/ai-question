"use client";
import { create } from "zustand";
import { persist, createJSONStorage } from "zustand/middleware";
import { usePathname } from "next/navigation";
import { TOKEN_KEY, LEARNER_TOKEN_KEY } from "@/lib/api/http";
import type { UserProfile } from "./api";

type User = Pick<UserProfile, "userId" | "phone" | "name" | "accountType" | "enterpriseId">;
type AuthState = {
  user: User | null;
  accessToken: string | null;
  setSession: (user: User, accessToken: string) => void;
  clearSession: () => void;
};

function clearLearnerDrafts() {
  for (let i = sessionStorage.length - 1; i >= 0; i--) {
    const key = sessionStorage.key(i);
    if (key?.startsWith("aiquestion:exam-")) sessionStorage.removeItem(key);
  }
}

function createSessionStore(name: string, tokenKey: string) {
  return create<AuthState>()(persist((set, get) => ({
    user: null,
    accessToken: null,
    setSession: (user, accessToken) => {
      if (typeof window !== "undefined") {
        if (tokenKey === LEARNER_TOKEN_KEY && get().user?.userId !== user.userId) clearLearnerDrafts();
        window.localStorage.setItem(tokenKey, accessToken);
      }
      set({ user, accessToken });
    },
    clearSession: () => {
      if (typeof window !== "undefined") {
        window.localStorage.removeItem(tokenKey);
        if (tokenKey === LEARNER_TOKEN_KEY) clearLearnerDrafts();
      }
      set({ user: null, accessToken: null });
    },
  }), { name, storage: createJSONStorage(() => localStorage) }));
}

export const useAuthStore = createSessionStore("aiquestion-auth", TOKEN_KEY);
export const useLearnerStore = createSessionStore("aiquestion-learner-auth", LEARNER_TOKEN_KEY);

/** 共用导航根据所在区域读取身份，不能在缺少学员身份时回退到管理员。 */
export function useCurrentSession() {
  const pathname = usePathname();
  const admin = useAuthStore();
  const learner = useLearnerStore();
  return pathname.startsWith("/learner") ? learner : admin;
}
