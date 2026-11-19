"use client";

import { useEffect, useState } from "react";
import { Button, Pagination, Sidebar, Spin } from "@douyinfe/semi-ui";
import { showConsultationError } from "./showConsultationError";
import { apiClient } from "@/lib/api/client";
import type { DashboardData, DashboardFilters } from "@/features/dashboard/types";

export type RosterQuery = DashboardFilters & { tab: string; status: string };
const statuses: Record<string,string> = { NOT_STARTED: "未开始", IN_PROGRESS: "进行中", COMPLETED: "已完成" };

export function ConsultationRoster({ query, close }: { query: RosterQuery; close: () => void }) {
  const [page, setPage] = useState(1);
  const [data, setData] = useState<DashboardData>();
  const [loadFailed, setLoadFailed] = useState(false);
  const [retry, setRetry] = useState(0);
  useEffect(() => {
    const abort = new AbortController();
    setData(undefined);
    setLoadFailed(false);
    apiClient.get<DashboardData>("/management/dashboard", { ...query, page, pageSize: 10 }, { signal: abort.signal })
      .then(result => { if (!abort.signal.aborted) setData(result); })
      .catch(e => { if (!abort.signal.aborted) { setLoadFailed(true); showConsultationError(e, "名单加载失败"); } });
    return () => abort.abort();
  }, [query, page, retry]);
  return <Sidebar.Container visible title="学员明细" showClose onCancel={close}
    style={{ width: "100%", minWidth: 0 }}>
    <div className="space-y-3 p-4">
      {loadFailed ? <Button onClick={() => setRetry(value => value + 1)}>重新加载</Button>
        : !data ? <Spin/> : <>
          <p>共 {data.total} 人次</p>
          {data.rows.map(row => <div key={row.trainingId + "-" + row.employeeId} className="border-b border-slate-100 pb-2">
            <strong>{row.name}</strong> · {row.departmentName || "未填写部门"}
            <div className="text-sm text-slate-600">{row.trainingName} · {statuses[row.status || ""] || "—"}</div>
          </div>)}
          {data.total > 10 && <Pagination size="small" currentPage={data.page} pageSize={10} total={data.total} onPageChange={setPage}/>}
        </>}
    </div>
  </Sidebar.Container>;
}
