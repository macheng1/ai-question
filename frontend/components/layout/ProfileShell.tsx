"use client";
import type { ReactNode } from "react";
import { Layout } from "@douyinfe/semi-ui";
import { PageShell } from "./PageShell";
import { ProfileSidebar } from "./ProfileSidebar";

/** Shared workspace geometry keeps navigation and content aligned across pages. */
export function ProfileShell({ children }: { children: ReactNode }) {
  return (
    <PageShell>
      <Layout.Content className="mx-auto flex min-h-screen w-[96%] max-w-[1800px] flex-col gap-6 py-6 md:flex-row">
        <ProfileSidebar />
        <Layout.Content className="min-w-0 flex-1">{children}</Layout.Content>
      </Layout.Content>
    </PageShell>
  );
}
