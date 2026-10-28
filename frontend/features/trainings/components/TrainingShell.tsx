import type { ReactNode } from "react";
import { PageShell } from "@/components/layout/PageShell";
import { ProfileSidebar } from "@/components/layout/ProfileSidebar";
export function TrainingShell({ children }: { children: ReactNode }) {
  return <PageShell><main className="mx-auto flex min-h-[720px] w-[92%] max-w-[1160px] flex-col gap-6 py-10 sm:py-14 md:flex-row"><ProfileSidebar /><section className="min-w-0 flex-1">{children}</section></main></PageShell>;
}
