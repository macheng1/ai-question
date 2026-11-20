"use client";

import { useRef, useState } from "react";
import { Button, Modal, Toast } from "@douyinfe/semi-ui";
import { apiClient } from "@/lib/api/client";
import { OverdueEmailDialog } from "@/features/dashboard/components/OverdueEmailDialog";
import type { DashboardFilters } from "@/features/dashboard/types";
import { showConsultationError } from "./showConsultationError";

const endpoint = "/management/dashboard/overdue";
type Failure = { employeeId: number; name: string; reason: string };
type Notification = { sent: number; missingEmail: number; failed: number; unsentKeys: string[]; failures: Failure[] };

export function OverdueChatActions({ filters }: { filters: DashboardFilters }) {
  const locked = useRef(false);
  const [busy, setBusy] = useState(false);
  const [dialog, setDialog] = useState<"notify" | "forward" | null>(null);
  const [records, setRecords] = useState<string[]>([]);
  const [failures, setFailures] = useState<Failure[]>([]);

  async function perform(action: () => Promise<unknown>) {
    if (locked.current) return;
    locked.current = true;
    setBusy(true);
    try {
      await action();
    } catch (error) {
      showConsultationError(error, "操作失败，请重试");
    } finally {
      locked.current = false;
      setBusy(false);
    }
  }

  function prepare(mode: "notify" | "forward") {
    void perform(async () => {
      const keys = await apiClient.get<string[]>(endpoint + "/keys", filters);
      if (!keys.length) { Toast.info("当前范围没有逾期记录"); return; }
      setRecords(keys);
      setFailures([]);
      setDialog(mode);
    });
  }

  function notify() {
    void perform(async () => {
      const result = await apiClient.post<Notification>(endpoint + "/notify",
        { ...filters, records }, { timeout: 0 });
      if (result.unsentKeys.length) {
        setRecords(result.unsentKeys);
        setFailures(result.failures);
        Toast.warning(`已通知 ${result.sent} 人，缺少有效邮箱 ${result.missingEmail} 人，发送失败 ${result.failed} 人`);
      } else {
        Toast.success(`已通知 ${result.sent} 名学员`);
        setDialog(null);
      }
    });
  }

  return <>
    <Button size="small" disabled={busy} onClick={() => void perform(() =>
      apiClient.download(endpoint + "/export", { params: filters, filename: "逾期未完成人员清单.xlsx" })
    )}>下载 Excel</Button>
    <Button size="small" disabled={busy} onClick={() => prepare("notify")}>通知学员</Button>
    <Button size="small" disabled={busy} onClick={() => prepare("forward")}>转发名单</Button>
    {dialog === "forward" && <OverdueEmailDialog filters={filters} records={records} onClose={() => setDialog(null)}/>}
    {dialog === "notify" && <Modal title="通知逾期学员" width="min(448px, calc(100vw - 24px))"
      bodyStyle={{ maxHeight: "60dvh", overflowY: "auto", overflowWrap: "anywhere" }} visible maskClosable={false} closable={!busy}
      closeOnEsc={!busy} onCancel={() => setDialog(null)} cancelText="取消" okText={failures.length ? "重试未发送项" : "发送"}
      confirmLoading={busy} cancelButtonProps={{ disabled: busy }} onOk={notify}>
      <p>将向 {new Set(records.map(key => key.split("-")[1])).size} 名学员发送提醒，共 {records.length} 条逾期记录。每人只收到自己的记录。</p>
      {failures.length > 0 && <ul className="max-h-64 overflow-auto">{failures.map(item =>
        <li key={item.employeeId}>{item.name} · {item.reason}</li>)}</ul>}
    </Modal>}
  </>;
}
