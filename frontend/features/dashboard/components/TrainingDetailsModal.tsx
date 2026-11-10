"use client";

import { useEffect, useState } from "react";
import { Banner, Button, Modal, Skeleton } from "@douyinfe/semi-ui";
import { getDashboard } from "../api";
import type { DashboardData, DashboardQuery } from "../types";
import { DashboardDetails } from "./DashboardDetails";

/** 独立维护明细筛选和分页，关闭时保留汇总列表状态。 */
export function TrainingDetailsModal({ initialQuery, title, onClose }: {
  initialQuery: DashboardQuery;
  title: string;
  onClose: () => void;
}) {
  const [query, setQuery] = useState(initialQuery);
  const [data, setData] = useState<DashboardData>();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [retry, setRetry] = useState(0);

  useEffect(() => {
    let active = true;
    setLoading(true);
    setError("");
    getDashboard(query)
      .then((value) => { if (active) setData(value); })
      .catch((e) => {
        if (active) {
          setData(undefined);
          setError(e instanceof Error ? e.message : "明细加载失败");
        }
      })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [query, retry]);

  return (
    <Modal
      visible
      title={title}
      width="min(1200px, calc(100vw - 24px))"
      style={{ top: 24 }}
      bodyStyle={{ maxHeight: "calc(100vh - 180px)", overflowY: "auto" }}
      onCancel={onClose}
      footer={<Button onClick={onClose}>关闭</Button>}
    >
      {error ? (
        <div className="space-y-3">
          <Banner type="danger" description={error} closeIcon={null} />
          <Button onClick={() => setRetry((value) => value + 1)}>重试</Button>
        </div>
      ) : data ? (
        <div className="min-w-0 space-y-4">
          <div className="flex flex-wrap gap-x-6 gap-y-2 text-sm text-slate-600">
            <span>应完成：{data.overview.assigned} 人次</span>
            <span>已完成：{data.overview.completed} 人次</span>
            <span>完成率：{data.overview.completionRate == null ? "—" : `${data.overview.completionRate}%`}</span>
          </div>
          <DashboardDetails
            data={data}
            query={query}
            loading={loading}
            hideTraining={Boolean(query.trainingId)}
            onChange={(value) => setQuery((previous) => ({ ...previous, ...value }))}
          />
        </div>
      ) : <Skeleton active loading placeholder={<Skeleton.Paragraph rows={6} />} />}
    </Modal>
  );
}
