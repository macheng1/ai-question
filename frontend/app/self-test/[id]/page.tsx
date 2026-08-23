"use client";

import { useParams } from "next/navigation";
import { PageShell } from "@/components/layout/PageShell";
import { OnlineSelfTest } from "@/features/self-test/components/OnlineSelfTest";

export default function PublicSelfTestDetailPage() {
  const params = useParams<{ id: string }>();
  return <PageShell><OnlineSelfTest selfTestId={Number(params.id)} /></PageShell>;
}
