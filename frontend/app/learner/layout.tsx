import type { ReactNode } from "react";
import { AuthGuard } from "@/components/auth/AuthGuard";
import { ProfileShell } from "@/components/layout/ProfileShell";
export default function LearnerLayout({ children }: { children: ReactNode }) {
  return (
    <AuthGuard accountType="EMPLOYEE">
      <ProfileShell>{children}</ProfileShell>
    </AuthGuard>
  );
}
