"use client";
import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { Banner, Spin } from "@douyinfe/semi-ui";
import { apiClient } from "@/lib/api/client";
import { PageShell } from "@/components/layout/PageShell";

/** 兼容旧培训链接，所有考前学习统一由考试入口处理。 */
export function Learning({ id }: { id: string }) {
  const router = useRouter();
  const [error, setError] = useState("");
  useEffect(() => {
    const controller = new AbortController();
    apiClient.get<{ assessmentId?: number }>(`/public/trainings/${id}/exam`, undefined, { signal: controller.signal })
      .then(data => { if (data.assessmentId) router.replace(`/kaoshi/${data.assessmentId}`); else setError("此培训尚未关联考试，请联系培训管理员。"); })
      .catch(e => { if (!controller.signal.aborted) setError(e.message); });
    return () => controller.abort();
  }, [id, router]);
  return <PageShell><main className="mx-auto max-w-xl px-4 py-16">{error ? <Banner type="warning" description={error} closeIcon={null} /> : <Spin tip="正在进入培训考试…" />}</main></PageShell>;
}
