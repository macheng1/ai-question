"use client";

import { useRef, useState } from "react";
import { Banner, Button, Input, Modal, Toast } from "@douyinfe/semi-ui";
import { apiClient } from "@/lib/api/client";
import type { DashboardFilters } from "../types";

export function OverdueEmailDialog({ records, filters, onClose }: {
  records: string[]; filters: DashboardFilters; onClose: () => void;
}) {
  const [manual, setManual] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const sending = useRef(false);
  const recipients = Array.from(new Set(manual.split(/[\s,;，；]+/).map(s => s.trim().toLowerCase()).filter(Boolean)));
  async function send() {
    if (sending.current || !recipients.length) return;
    if (recipients.some(email => !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email))) {
      setError("请检查收件邮箱格式"); return;
    }
    sending.current = true;
    setBusy(true);
    setError("");
    try {
      const failed = await apiClient.post<string[]>("/management/dashboard/overdue/email", { ...filters, records, recipients }, { timeout: 0 });
      if (!failed.length) { Toast.success("发送成功"); onClose(); }
      else {
        setManual(failed.join("；"));
        setError(`发送成功 ${recipients.length - failed.length} 个，失败 ${failed.length} 个。已保留失败地址，可重试。`);
      }
    } catch (e) { setError(e instanceof Error ? e.message : "发送失败"); }
    finally { sending.current = false; setBusy(false); }
  }
  return <Modal title="发送逾期未完成人员清单" visible onCancel={onClose} closable={!busy}
    maskClosable={false} closeOnEsc={!busy} footer={<>
      <Button disabled={busy} onClick={onClose}>取消</Button>
      <Button theme="solid" loading={busy} disabled={!recipients.length || busy} onClick={send}>发送</Button>
    </>}>
    <div className="space-y-4">
      <p>已选 {new Set(records.map(key => key.split("-")[1])).size} 名学员，共 {records.length} 条培训记录。</p>
      <Input aria-label="手动填写收件邮箱" value={manual} disabled={busy} onChange={setManual}
        placeholder="手动填写邮箱，多个地址用逗号或分号分隔" />
      {error && <Banner type="danger" description={error} closeIcon={null} />}
    </div>
  </Modal>;
}
