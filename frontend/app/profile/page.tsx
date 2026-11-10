"use client";
import { useEffect, useState } from "react";
import { Banner, Card, Typography } from "@douyinfe/semi-ui";
import { ProfileShell } from "@/components/layout/ProfileShell";
import { apiClient } from "@/lib/api/client";
export default function Overview() {
  const [data, setData] = useState<{
    trainingCount: number;
    employeeCount: number;
    examCount: number;
  }>();
  const [error, setError] = useState("");
  useEffect(() => {
    apiClient
      .get<{ trainingCount: number; employeeCount: number; examCount: number }>(
        "/management/overview",
      )
      .then(setData)
      .catch((e) => setError(e.message));
  }, []);
  return (
    <ProfileShell>
      <Typography.Title heading={2} style={{ color: "#183f35", margin: 0 }}>管理概览</Typography.Title>
      <p className="my-3 text-slate-500">查看本企业培训、员工与考试情况</p>
      {error && <Banner type="danger" description={error} />}
      <div className="mt-6 grid gap-4 sm:grid-cols-3">
        {[
          ["培训数量", data?.trainingCount],
          ["员工数量", data?.employeeCount],
          ["考试数量", data?.examCount],
        ].map(([label, count]) => (
          <Card key={label}>
            <Typography.Text type="secondary">{label}</Typography.Text>
            <p className="mt-4 text-3xl font-semibold">{count ?? "—"}</p>
          </Card>
        ))}
      </div>
    </ProfileShell>
  );
}
