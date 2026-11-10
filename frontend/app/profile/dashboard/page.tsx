"use client";
import { ProfileShell } from "@/components/layout/ProfileShell";
import { Dashboard } from "@/features/dashboard/components/Dashboard";

export default function DashboardPage() {
  return (
    <ProfileShell>
      <Dashboard />
    </ProfileShell>
  );
}
