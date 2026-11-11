"use client";
import { useState } from "react";
import { IconDownload } from "@douyinfe/semi-icons";
import { exportDashboardLogs } from "../api";
import { useCompactViewport } from "@/lib/hooks/useCompactViewport";
import { formatStudyHours } from "../format";
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
import { dashboardTabs } from "../navigation";
const labels: Record<string, string> = {
  NOT_STARTED: "未开始",
  IN_PROGRESS: "进行中",
  COMPLETED: "已完成",
  PASSED: "已通过",
  FAILED: "未通过",
  NONE: "无考试",
};
const colors = {
  NOT_STARTED: "grey",
  IN_PROGRESS: "blue",
  COMPLETED: "green",
  PASSED: "green",
  FAILED: "red",
  NONE: "grey",
} as const;
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
  async function handleExport() {
    setExporting(true);
    try {
      await exportDashboardLogs(query);
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
            title: "类型",
            width: 120,
            render: (_, row) => (
              <Tag color={row.assessmentId ? "cyan" : "grey"}>
                {row.assessmentId ? "培训+考试" : "独立培训"}
              </Tag>
            ),
          },
          { title: "应完成人次", dataIndex: "assigned", width: 110 },
          { title: "已完成人次", dataIndex: "completed", width: 110 },
          {
            title: "完成率",
            width: 100,
            render: (_, row) =>
              row.completionRate == null ? "—" : `${row.completionRate}%`,
          },
          {
            title: "有效学时",
            width: 110,
            render: (_, row) => `${formatStudyHours(row.studiedHours)} 小时`,
          },
          {
            title: "逾期未完成",
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
      : [
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
                      : `${Math.round((row.durationSeconds / 60) * 10) / 10} 分钟`,
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
                      {formatStudyHours(row.studiedHours)} 小时
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
                            : `${Math.round((row.durationSeconds / 60) * 10) / 10} 分钟`,
                      },
                    ]
                  : []),
              ]),
        ];
  return (
    <Card bodyStyle={{ padding: 0 }} className="min-w-0">
      <div className="flex flex-wrap items-center justify-between gap-3 p-4">
        <div>
          <strong>
            {dashboardTabs.find((tab) => tab.key === query.tab)?.label}
          </strong>
          <span className="ml-3 text-xs text-slate-500">
            {hideTraining
              ? "当前培训的学员学习及考试完成情况"
              : query.tab === "exams"
              ? "每名员工每场培训展示最后一次答卷"
              : "同一员工参加多场培训，分别统计"}
          </span>
        </div>
        <Space wrap>
        {query.tab === "logs" && (
          <Button icon={<IconDownload />} loading={exporting} disabled={loading || data.total === 0} onClick={handleExport}>
            导出 Excel
          </Button>
        )}
        {query.tab !== "trainings" && (
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
              ...(query.tab === "incomplete"
                ? []
                : [{ value: "COMPLETED", label: "已完成" }]),
              { value: "OVERDUE", label: "逾期未完成" },
            ]}
          />
        )}
        </Space>
      </div>
      <Table<DashboardRow>
        className="[&_.semi-table-pagination-outer]:!m-0 [&_.semi-table-pagination-outer]:flex-wrap [&_.semi-table-pagination-outer]:gap-3 [&_.semi-table-pagination-outer]:!px-4 [&_.semi-table-pagination-outer]:!py-4"
        columns={columns}
        dataSource={data.rows}
        loading={loading}
        rowKey={(row) =>
          String(row?.id ?? `${row?.trainingId}-${row?.employeeId}`)
        }
        empty="暂无符合条件的数据"
        scroll={{
          x: columns.reduce(
            (sum, column) => sum + Number(column.width || 100),
            0,
          ),
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
      <div className="border-t border-solid border-slate-100 px-4 py-3 text-xs leading-6 text-slate-500">
        <p className="m-0">
          <span className="font-medium text-slate-600">完成口径：</span>
          独立培训完成全部材料；关联考试的培训需同时通过考试（按最后一次答卷）。满意度评估单独统计。
        </p>
        <p className="m-0">未完成名单包含未开始及进行中，逾期人员可按状态筛选。</p>
      </div>
    </Card>
  );
}
