"use client";

import { useEffect, useState, type ReactNode } from "react";
import { useRouter } from "next/navigation";
import { useAuthStore } from "@/features/auth/store";

/**
 * Protects pages that belong to the signed-in personal center.
 *
 * The access token is stored in the browser, so the guard waits until the
 * client has mounted before deciding whether to render the protected page.
 * Guests are sent back to the public home page instead of seeing a partial
 * personal-center layout.
 */
export function AuthGuard({ children }: { children: ReactNode }) {
  const router = useRouter();
  const [checked, setChecked] = useState(false);

  useEffect(() => {
    const token = useAuthStore.getState().accessToken;
    if (!token) {
      router.replace("/");
      return;
    }
    setChecked(true);
  }, [router]);

  if (!checked) return null;
  return children;
}
