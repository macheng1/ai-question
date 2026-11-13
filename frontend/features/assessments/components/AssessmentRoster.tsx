"use client";
import { Typography, Space } from "@douyinfe/semi-ui";
import { Layout } from "@douyinfe/semi-ui";

import { useEffect, useState } from "react";
import "@douyinfe/semi-ui/react19-adapter";
import { Banner, Button, Select, Spin, Table, Tag } from "@douyinfe/semi-ui";
import { getAssessmentRoster, type AssessmentRosterRow } from "../api";
import { useCompactViewport } from "@/lib/hooks/useCompactViewport";

export function AssessmentRoster({ taskId, scope, refreshVersion = 0 }: { taskId: number; scope?: string; refreshVersion?: number }) {
  const compact = useCompactViewport();
  const [rows, setRows] = useState<AssessmentRosterRow[]>([]);
  const [department, setDepartment] = useState<number>();
  const [status, setStatus] = useState("ALL");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [reload, setReload] = useState(0);
  useEffect(() => {
    if (!scope || scope === "ALL") return;
    let active = true;
    setLoading(true);
    setError("");
    getAssessmentRoster(taskId).then(data => { if (active) setRows(data); })
      .catch(e => { if (active) setError(e instanceof Error ? e.message : "名单加载失败"); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [taskId, scope, reload, refreshVersion]);
  if (!scope || scope === "ALL") return null;
  const departments = Array.from(new Map(rows.map(r => [r.departmentId, r.departmentName])).entries());
  const scoped = rows.filter(r => !department || r.departmentId === department);
  const submitted = scoped.filter(r => r.submitted).length;
  const visible = scoped.filter(r => status === "ALL" || (status === "SUBMITTED" ? r.submitted : !r.submitted));
  return <Layout.Content className="flex flex-col mt-5 gap-4 rounded-2xl border border-slate-200 bg-white p-5">
    <div className="flex flex-wrap items-center justify-between gap-3"><Typography.Title heading={2} className="!text-lg !font-bold !text-slate-800">应考名单</Typography.Title><Button size="small" onClick={() => setReload(n => n + 1)}>刷新名单</Button></div>
    {loading ? <Spin /> : error ? <Banner type="danger" description={error} /> : <>
      <Space wrap >
        <Select aria-label="名单部门筛选" placeholder="全部部门" showClear value={department} onChange={v => setDepartment(v == null ? undefined : Number(v))} optionList={departments.map(([value, label]) => ({ value, label }))} style={{ minWidth: 160 }} />
        <Select aria-label="交卷状态筛选" value={status} onChange={v => setStatus(String(v))} optionList={[{ label: "全部状态", value: "ALL" }, { label: "已考（已交卷）", value: "SUBMITTED" }, { label: "未考（未交卷）", value: "PENDING" }]} style={{ minWidth: 160 }} />
      </Space>
      <div className="grid grid-cols-3 gap-3">{[["应考", scoped.length], ["已考", submitted], ["未考", scoped.length - submitted]].map(([label, count]) => <div key={label} className="rounded-xl bg-slate-50 p-3"><p className="text-xs text-slate-500">{label}</p><p className="mt-1 text-xl font-bold text-teal-700">{count}<span className="ml-1 text-xs font-normal">人</span></p></div>)}</div>
      <p className="text-xs text-slate-500">按保存时的名单统计；至少交卷一次记为已考，重考不重复计人数。学习中、答题中均计入未交卷。</p>
      {!rows.length ? <Banner type="warning" description="关联培训尚未配置学习名单，请在培训管理中设置学习范围。" /> : <Table<AssessmentRosterRow> rowKey="employeeId" dataSource={visible} pagination={{ pageSize: 10 }} style={{ width: "100%" }} scroll={compact ? { x: 560 } : undefined} columns={[
        { title: "姓名", dataIndex: "candidateName", width: "25%" }, { title: "手机号", dataIndex: "phone", width: "25%" }, { title: "部门（安排时）", dataIndex: "departmentName", width: "30%" },
        { title: "状态", width: "20%", render: (_, row) => <Tag color={row.submitted ? "green" : "grey"}>{row.submitted ? "已交卷" : "未交卷"}</Tag> },
      ]} />}
    </>}
  </Layout.Content>;
}
