"use client";
import { useState } from "react";
import { OverdueEmailDialog } from "./OverdueEmailDialog";
import { apiClient } from "@/lib/api/client";
import { Checkbox, Modal } from "@douyinfe/semi-ui";
import { IconDownload } from "@douyinfe/semi-icons";
import { exportDashboardRows } from "../api";
import { useCompactViewport } from "@/lib/hooks/useCompactViewport";
import { formatStudyDuration, formatDuration } from "@/lib/duration";
import {
  Button,
  Card,
  Select,
  Space,
  Table,
  Tag,
  Tooltip,
  Toast,
} from "@douyinfe/semi-ui";
import type { ColumnProps } from "@douyinfe/semi-ui/lib/es/table/interface";
import type {
  DashboardData,
  DashboardQuery,
  DashboardRow,
  DashboardStatus,
} from "../types";
import { StatHint } from "./StatHint";
import { dashboardTabs } from "../navigation";
const labels: Record<string, string> = {
  NOT_STARTED: "未开始",
  IN_PROGRESS: "进行中",
  COMPLETED: "已完成",
  PASSED: "已通过",
  FAILED: "未通过",
  PENDING_REVIEW: "待批阅",
  NONE: "无考试",
};
const colors = {
  NOT_STARTED: "grey",
  IN_PROGRESS: "blue",
  COMPLETED: "green",
  PASSED: "green",
  FAILED: "red",
  PENDING_REVIEW: "orange",
  NONE: "grey",
} as const;
function summaryHeader(label: string, tip: string) {
  return <span className="inline-flex items-center gap-1 whitespace-nowrap">
    {label}<StatHint label={label} text={tip} />
  </span>;
}
function statusTag(status?: string) {
  const key = status as keyof typeof colors;
  return <Tag color={colors[key] || "grey"}>{labels[key] || "—"}</Tag>;
}
function date(value?: string) {
  return value
    ? new Date(value).toLocaleString("zh-CN", {
        timeZone: "Asia/Shanghai",
        hour12: false,
      })
    : "—";
}
export function DashboardDetails({
  data,
  query,
  loading,
  onChange,
  onTraining,
  hideTraining = false,
}: {
  data: DashboardData;
  query: DashboardQuery;
  loading: boolean;
  onChange: (change: Partial<DashboardQuery>) => void;
  onTraining?: (id: number) => void;
  hideTraining?: boolean;
}) {
  const compact = useCompactViewport();
  const [exporting, setExporting] = useState(false);
  const overdueList = query.tab === "learners" && query.status === "OVERDUE";
  const [selected, setSelected] = useState<string[]>([]);
  const [selecting, setSelecting] = useState(false);
  const [emailOpen, setEmailOpen] = useState(false);
  const { start, end, trainingId, departmentId, category } = query;
  const filters = { start, end, trainingId, departmentId, category };
  const [notifyFailures, setNotifyFailures] = useState<{ employeeId: number; name: string; reason: string }[]>([]);
  const [notifying, setNotifying] = useState(false);
  async function notifyLearners() {
    if (notifying || !selected.length) return;
    setNotifying(true);
    try {
      const result = await apiClient.post<{ sent: number; missingEmail: number; failed: number; unsentKeys: string[]; failures: { employeeId: number; name: string; reason: string }[] }>(
        "/management/dashboard/overdue/notify", { ...filters, records: selected }, { timeout: 0 });
      setSelected(result.unsentKeys);
      setNotifyFailures(result.failures);
      if (!result.unsentKeys.length) Toast.success(`发送成功，已通知 ${result.sent} 名学员`);
      else Toast.warning(`已通知 ${result.sent} 人，缺少有效邮箱 ${result.missingEmail} 人，发送失败 ${result.failed} 人`);
    } catch (e) { Toast.error(e instanceof Error ? e.message : "发送失败"); }
    finally { setNotifying(false); }
  }
  async function selectAllOverdue() {
    setSelecting(true);
    try {
      setSelected(await apiClient.get<string[]>("/management/dashboard/overdue/keys", filters));
    } catch (e) { Toast.error(e instanceof Error ? e.message : "获取名单失败"); }
    finally { setSelecting(false); }
  }
  async function handleExport() {
    setExporting(true);
    try {
      await exportDashboardRows(query);
    } catch (error) {
      Toast.error(error instanceof Error ? error.message : "导出失败，请重试");
    } finally {
      setExporting(false);
    }
  }
  const training: ColumnProps<DashboardRow> = {
    title: "培训名称",
    dataIndex: "trainingName",
    width: 240,
    render: (_, row) => onTraining ? (
      <Button
        theme="borderless"
        style={{ color: "#246c58", maxWidth: "100%" }}
        onClick={() => onTraining?.(row.trainingId!)}
      >
        <span className="truncate">{row.trainingName}</span>
      </Button>
    ) : row.trainingName,
  };
  const columns: ColumnProps<DashboardRow>[] =
    query.tab === "trainings"
      ? [
          { title: "培训名称", dataIndex: "name", width: 240 },
          {
            title: summaryHeader("类型", "本场关联了考试就显示“培训+考试”，否则显示“独立培训”。"),
            width: 120,
            render: (_, row) => (
              <Tag color={row.assessmentId ? "cyan" : "grey"}>
                {row.assessmentId ? "培训+考试" : "独立培训"}
              </Tag>
            ),
          },
          { title: summaryHeader("应完成人次", "本场培训名单中的学员，每人计一次。"), dataIndex: "assigned", width: 130 },
          { title: summaryHeader("已完成人次", "统计已完成学习的学员。有关联考试时，最近一次考试还须通过。"), dataIndex: "completed", width: 130 },
          {
            title: summaryHeader("完成率", "本场已完成人次 ÷ 应完成人次。名单为空时显示“—”。"),
            width: 100,
            render: (_, row) =>
              row.completionRate == null ? "—" : `${row.completionRate}%`,
          },
          {
            title: summaryHeader("有效学时", "合计本场学员看视频和阅读材料的有效时间，按时、分、秒显示。"),
            width: 130,
            render: (_, row) => formatStudyDuration(row.studiedHours),
          },
          {
            title: summaryHeader("逾期未完成", "学习逾期未完成或考试逾期未通过，每人只计一次。待批阅不算考试逾期。"),
            width: 120,
            render: (_, row) => (
              <Tag color={row.overdue ? "orange" : "grey"}>
                {Number(row.overdue)} 人次
              </Tag>
            ),
          },
          {
            title: "操作",
            width: 120,
            fixed: compact ? undefined : "right",
            render: (_, row) => (
              <Button
                theme="borderless"
                style={{ color: "#246c58" }}
                onClick={() => onTraining?.(row.id!)}
              >
                查看明细
              </Button>
            ),
          },
        ]
      : query.tab === "learnerHours" ? [
          { title: "学员姓名", dataIndex: "name", width: 200 },
          { title: "手机号", dataIndex: "phone", width: 180 },
          { title: summaryHeader("培训场次", "该学员在当前筛选范围内被安排的培训场数。"), dataIndex: "assigned", width: 150 },
          { title: summaryHeader("有效学时", "合计该学员在这些培训中看视频和阅读材料的有效时间。"), width: 160,
            render: (_, row) => formatStudyDuration(row.studiedHours) },
        ] : [
          { title: "姓名", dataIndex: "name", width: 110 },
          { title: "手机号", dataIndex: "phone", width: 140 },
          { title: "部门", dataIndex: "departmentName", width: 140 },
          ...(hideTraining ? [] : [training]),
          ...(query.tab === "exams"
            ? [
                { title: "考试", dataIndex: "examName", width: 200 },
                {
                  title: "考试状态",
                  width: 110,
                  render: (_: unknown, row: DashboardRow) =>
                    statusTag(row.examStatus),
                },
                {
                  title: "成绩",
                  width: 100,
                  render: (_: unknown, row: DashboardRow) =>
                    row.score == null
                      ? "—"
                      : `${row.score} / ${row.totalScore}`,
                },
                { title: "答题次数", dataIndex: "attemptCount", width: 100 },
                {
                  title: "考试用时",
                  width: 110,
                  render: (_: unknown, row: DashboardRow) =>
                    row.durationSeconds == null
                      ? "—"
                      : formatDuration(row.durationSeconds),
                },
                {
                  title: "交卷时间",
                  width: 190,
                  render: (_: unknown, row: DashboardRow) =>
                    date(row.submittedAt),
                },
              ]
            : [
                {
                  title: "整体状态",
                  width: 110,
                  render: (_: unknown, row: DashboardRow) =>
                    statusTag(row.status),
                },
                {
                  title: "学习进度",
                  width: 100,
                  render: (_: unknown, row: DashboardRow) =>
                    `${row.progress || 0}%`,
                },
                {
                  title: "有效学时",
                  width: 130,
                  render: (_: unknown, row: DashboardRow) => (
                    <Space spacing={4}>
                      {formatStudyDuration(row.studiedHours)}
                      {Boolean(row.missingDuration) && (
                        <Tooltip content="旧记录部分时长缺失，仅汇总已记录时长">
                          <Tag color="orange">缺失</Tag>
                        </Tooltip>
                      )}
                    </Space>
                  ),
                },
                {
                  title: "考试状态",
                  width: 110,
                  render: (_: unknown, row: DashboardRow) =>
                    statusTag(row.examStatus),
                },
                {
                  title: "逾期情况",
                  width: 200,
                  render: (_: unknown, row: DashboardRow) =>
                    row.overdue ? (
                      <Space wrap>
                        {row.overdueReasons?.map((reason) => (
                          <Tag key={reason} color="orange">
                            {reason}
                          </Tag>
                        ))}
                      </Space>
                    ) : (
                      "—"
                    ),
                },
                ...(query.tab === "logs"
                  ? [
                      {
                        title: "开始学习",
                        width: 190,
                        render: (_: unknown, row: DashboardRow) =>
                          date(row.startedAt),
                      },
                      {
                        title: "整体完成",
                        width: 190,
                        render: (_: unknown, row: DashboardRow) =>
                          date(row.completedAt),
                      },
                      {
                        title: "考试分数",
                        width: 100,
                        render: (_: unknown, row: DashboardRow) =>
                          row.score ?? "—",
                      },
                      {
                        title: "答题次数",
                        dataIndex: "attemptCount",
                        width: 100,
                      },
                      {
                        title: "考试用时",
                        width: 110,
                        render: (_: unknown, row: DashboardRow) =>
                          row.durationSeconds == null
                            ? "—"
                            : formatDuration(row.durationSeconds),
                      },
                    ]
                  : []),
              ]),
        ];
  return (
    <Card bodyStyle={{ padding: 0 }} className="min-w-0">
      <Modal title="未发送成功的学员" visible={notifyFailures.length > 0} onCancel={() => setNotifyFailures([])} footer={<Button onClick={() => setNotifyFailures([])}>关闭</Button>}>
        <div className="max-h-96 overflow-auto"><table className="w-full text-left text-sm"><thead><tr><th>学员</th><th>原因</th></tr></thead><tbody>{notifyFailures.map(item => <tr key={item.employeeId}><td className="py-2 pr-3">{item.name}</td><td>{item.reason}</td></tr>)}</tbody></table></div>
      </Modal>
      <div className="flex flex-wrap items-center justify-between gap-3 p-4">
        <div>
          <span className="inline-flex items-center gap-1">
            <strong>
              {query.tab === "learnerHours" ? "学时汇总 · 按学员" : dashboardTabs.find((tab) => tab.key === query.tab)?.label}
            </strong>
            {query.tab === "logs" && (
              <StatHint
                label="学习日志"
                text="学员进入培训并产生学习记录后，就会出现在这里。每位学员每场培训显示一行，汇总学习进度和有效学时。"
              />
            )}
            {query.tab === "incomplete" && (
              <StatHint
                label="未完成人员清单"
                text="列出已开始学习或答题、尚未完成培训的学员。学完全部材料（无材料时确认完成）即完成学习。有关联考试时，最近一次考试还须及格。满意度评估单独统计。"
              />
            )}
          </span>
          <span className="ml-3 text-xs text-slate-500">
            {hideTraining
              ? "当前培训的学员学习及考试完成情况"
              : query.tab === "learnerHours" ? "每位学员一行，合计多场培训的有效学时"
              : query.tab === "exams"
              ? "每名员工每场培训展示最后一次答卷"
              : "同一员工参加多场培训，分别统计"}
          </span>
        </div>
        <Space wrap>
        {overdueList && <>
          <Button loading={selecting} disabled={loading || notifying || selecting || !data.total} onClick={selectAllOverdue}>选择全部 {data.total} 条</Button>
          {selected.length > 0 && <Button disabled={selecting || notifying} onClick={() => setSelected([])}>清空选择</Button>}
          <Button theme="solid" loading={notifying} disabled={!selected.length || loading || notifying || selecting} onClick={notifyLearners}>通知学员（{selected.length}）</Button>
          <Button disabled={!selected.length || loading || notifying || selecting} onClick={() => setEmailOpen(true)}>转发名单</Button>
        </>}
        {(query.tab === "logs" || query.tab === "incomplete") && (
          <Button icon={<IconDownload />} loading={exporting} disabled={loading || data.total === 0} onClick={handleExport}>
            导出 Excel
          </Button>
        )}
        {(query.tab === "learners" || query.tab === "exams") && (
          <Select
            aria-label="明细状态"
            value={query.status}
            style={{ width: 160 }}
            onChange={(value) =>
              onChange({ status: value as DashboardStatus, page: 1 })
            }
            optionList={[
              { value: "ALL", label: "全部状态" },
              { value: "NOT_STARTED", label: "未开始" },
              { value: "IN_PROGRESS", label: "进行中" },
              { value: "COMPLETED", label: "已完成" },
              { value: "OVERDUE", label: "逾期未完成" },
            ]}
          />
        )}
        </Space>
      </div>
      <Table<DashboardRow>
        className="[&_.semi-table-pagination-outer]:!m-0 [&_.semi-table-pagination-outer]:flex-wrap [&_.semi-table-pagination-outer]:gap-3 [&_.semi-table-pagination-outer]:!px-4 [&_.semi-table-pagination-outer]:!py-4"
        columns={overdueList ? [{
          title: "选择", width: 64, render: (_: unknown, row: DashboardRow) => {
            const key = row.trainingId + "-" + row.employeeId;
            return <Checkbox aria-label={`选择 ${row.name} · ${row.trainingName}`} disabled={loading || notifying || selecting}
              checked={selected.includes(key)} onChange={e => setSelected(prev =>
                e.target.checked ? [...prev, key] : prev.filter(item => item !== key))} />;
          },
        }, ...columns] : columns}
        dataSource={data.rows}
        loading={loading}
        rowKey={(row) =>
          String(row?.id ?? `${row?.trainingId}-${row?.employeeId}`)
        }
        empty="暂无符合条件的数据"
        scroll={{
          x: `max(100%, ${columns.reduce(
            (sum, column) => sum + Number(column.width || 100),
            0,
          )}px)`,
        }}
        pagination={{
          total: data.total,
          currentPage: data.page,
          pageSize: data.pageSize,
          showSizeChanger: !compact,
          pageSizeOpts: [10, 20, 50],
          onPageChange: (page) => onChange({ page }),
          onPageSizeChange: (pageSize) => onChange({ page: 1, pageSize }),
        }}
      />
      {query.tab !== "incomplete" && query.tab !== "logs" && (
        <div className="border-t border-solid border-slate-100 px-4 py-3 text-xs leading-6 text-slate-500">
          <p className="m-0">
            <span className="font-medium text-slate-600">完成口径：</span>
            独立培训完成全部材料；关联考试的培训需同时通过考试（按最后一次答卷）。满意度评估单独统计。
          </p>
          <p className="m-0">未完成人员清单仅统计已开始学习或答题、尚未完成培训的学员。</p>
        </div>
      )}
      {overdueList && emailOpen && <OverdueEmailDialog records={selected} filters={filters} onClose={() => setEmailOpen(false)} />}
    </Card>
  );
}
