"use client";

import { useEffect, useState } from "react";
import { Banner, Button, Modal, Space, Table, Toast, Typography } from "@douyinfe/semi-ui";
import { getReminderPreview, sendReminders, type Training, type ReminderPreview, type ReminderRecipient, type ReminderResult } from "../api";

const dateText = (value: string | null) => value ? value.replace("T", " ").slice(0, 16) : "未设置";

export function TrainingReminderModal({ training, onClose }: { training: Training; onClose: () => void }) {
  const [preview, setPreview] = useState<ReminderPreview>();
  const [selected, setSelected] = useState<number[]>([]);
  const [loading, setLoading] = useState(true);
  const [sending, setSending] = useState(false);
  const [error, setError] = useState("");
  const [results, setResults] = useState<ReminderResult[]>();
  const [reload, setReload] = useState(0);
  useEffect(() => {
    let active = true;
    setLoading(true); setError(""); setResults(undefined); setSelected([]);
    getReminderPreview(training.id).then(data => {
      if (!active) return;
      setPreview(data);
      setSelected(data.recipients.filter(person => person.selectable).slice(0, 50).map(person => person.employeeId));
    }).catch(e => { if (active) setError(e instanceof Error ? e.message : "名单加载失败"); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [training.id, reload]);

  const send = async () => {
    if (!preview || sending || !selected.length) return;
    setSending(true); setError("");
    try {
      const response = await sendReminders(training.id, preview.requestId, selected);
      setResults(response);
      const sent = response.filter(result => result.status === "SENT").length;
      Toast.info(`邮件服务已接收 ${sent} 封，请查看下方逐人结果`);
    } catch (e) { setError(e instanceof Error ? e.message : "发送失败，请重试；同一次请求不会重复发送"); }
    finally { setSending(false); }
  };

  return <Modal visible title={`一键提醒 · ${training.name}`} width="min(960px, calc(100vw - 24px))" maskClosable={!sending} closable={!sending} onCancel={() => { if (!sending) onClose(); }} bodyStyle={{ maxHeight: "65dvh", overflowY: "auto" }} footer={<Space wrap>
    <Button disabled={sending} onClick={onClose}>关闭</Button>
    <Button disabled={sending || loading} onClick={() => setReload(value => value + 1)}>刷新名单</Button>
    <Button theme="solid" loading={sending} disabled={loading || !preview?.mailConfigured || !selected.length || selected.length > 50 || !!results} onClick={send}>发送提醒（{selected.length} 人）</Button>
  </Space>}>
    <div className="flex flex-col gap-4">
      <Banner type="info" closeIcon={null} description={<div className="space-y-2">
        <strong>逾期未完成规则</strong>
        <p>培训：超过培训结束时间，仍未完成全部材料学习（无材料培训需确认完成）。</p>
        <p>考试：超过关联考试结束时间，尚未参加、未交卷或最后一次成绩未通过。</p>
        <p>培训与考试使用相同起止时间，未开始学习的员工也包含在内；满意度评估不计入。同一员工的多个原因合并为一封邮件。</p>
        <p>结束后可回看材料，不再记录学习进度或参加考试；需要重新学习或考试，请联系管理员安排新培训。</p>
      </div>} />
      {preview && <Space wrap><Typography.Text>培训结束：{dateText(preview.trainingEndAt)}</Typography.Text><Typography.Text>考试结束：{dateText(preview.examEndAt)}</Typography.Text></Space>}
      {preview && !preview.trainingEndAt && <Banner type="warning" description="历史培训未设置结束时间，无法判定培训逾期，请联系管理员核查。" />}
      {preview && !preview.mailConfigured && <Banner type="warning" description="邮件服务未配置完整，请配置发件邮箱和平台访问地址后发送。" />}
      {error && <Banner type="danger" description={error} />}
      <p className="text-sm text-slate-500">每次最多选择 50 人。缺少有效邮箱的员工不可选；发送前再次检查完成情况。刷新名单后再次发送会产生新的提醒。</p>
      <Table<ReminderRecipient> rowKey="employeeId" loading={loading} dataSource={preview?.recipients || []} pagination={{ pageSize: 10 }} scroll={{ x: 880 }} empty="当前没有逾期未完成的员工" rowSelection={{ selectedRowKeys: selected, onChange: keys => setSelected((keys || []).map(Number)), getCheckboxProps: row => ({ disabled: sending || !!results || !row.selectable }) }} columns={[
        { title: "姓名", dataIndex: "name", width: 100 },
        { title: "部门", dataIndex: "department", width: 120 },
        { title: "邮箱", width: 220, render: (_, row) => <span className="break-all">{row.email || "未填写"}{!row.selectable && <Typography.Text type="danger">（邮箱无效）</Typography.Text>}</span> },
        { title: "未完成原因", width: 180, render: (_, row) => row.reasons.join("、") },
        { title: "上次发送", width: 160, render: (_, row) => row.lastSentAt ? dateText(row.lastSentAt) : "未发送" },
      ]} />
      {results && <Table<ReminderResult> rowKey="employeeId" pagination={false} dataSource={results} scroll={{ x: 500 }} columns={[
        { title: "姓名", width: 120, render: (_, row) => row.name || preview?.recipients.find(person => person.employeeId === row.employeeId)?.name || "已移出范围" },
        { title: "发送结果", width: 100, render: (_, row) => ({ SENT: "已发送", FAILED: "失败", SKIPPED: "已跳过" })[row.status] },
        { title: "说明", dataIndex: "message", width: 280 },
      ]} />}
    </div>
  </Modal>;
}
