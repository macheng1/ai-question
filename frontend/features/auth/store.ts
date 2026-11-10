import { create } from "zustand";
import { persist, createJSONStorage } from "zustand/middleware";
import { TOKEN_KEY } from "@/lib/api/http";

import type { UserProfile } from "./api";
type User = Pick<
  UserProfile,
  "userId" | "phone" | "name" | "accountType" | "enterpriseId"
>;
type AuthState = {
  user: User | null;
  accessToken: string | null;
  setSession: (user: User, accessToken: string) => void;
  clearSession: () => void;
};

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      user: null,
      accessToken: null,
      setSession: (user, accessToken) => {
        if (typeof window !== "undefined")
          window.localStorage.setItem(TOKEN_KEY, accessToken);
        set({ user, accessToken });
      },
      clearSession: () => {
        if (typeof window !== "undefined")
          window.localStorage.removeItem(TOKEN_KEY);
        set({ user: null, accessToken: null });
      },
    }),
    { name: "aiquestion-auth", storage: createJSONStorage(() => localStorage) },
  ),
);
