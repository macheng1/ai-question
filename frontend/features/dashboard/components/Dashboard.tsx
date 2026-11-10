"use client";
import { formatStudyHours } from "../format";
import { useEffect, useState } from "react";
import {
  Banner,
  Button,
  Card,
  DatePicker,
  Empty,
  Select,
  Skeleton,
  Space,
  Tabs,
  TabPane,
  Tooltip,
  Typography,
} from "@douyinfe/semi-ui";
import {
  IconCalendar,
  IconUserGroup,
  IconTickCircle,
  IconClock,
  IconCheckList,
  IconAlertTriangle,
  IconRefresh,
  IconHelpCircle,
} from "@douyinfe/semi-icons";
import { getDashboard, getDashboardOptions } from "../api";
import type {
  DashboardData,
  DashboardFilters,
  DashboardOptions,
  DashboardQuery,
  DashboardStatus,
} from "../types";
import { trainingCategories } from "@/features/trainings/api";
import { DashboardCharts } from "./DashboardCharts";
import { dashboardTabs, type DashboardView } from "../navigation";
import { DashboardDetails } from "./DashboardDetails";
import { TrainingDetailsModal } from "./TrainingDetailsModal";

const initialQuery: DashboardQuery = {
  tab: "trainings",
  status: "ALL",
  page: 1,
  pageSize: 10,
};
function dateText(date: Date) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
}
export function Dashboard() {
  const [options, setOptions] = useState<DashboardOptions>({
    trainings: [],
    departments: [],
  });
  const [optionsError, setOptionsError] = useState("");
  const [draft, setDraft] = useState<DashboardFilters>({});
  const [view, setView] = useState<DashboardView>("overview");
  const [query, setQuery] = useState<DashboardQuery>(initialQuery);
  const [data, setData] = useState<DashboardData>();
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);
  const [refresh, setRefresh] = useState(0);
  const [detailQuery, setDetailQuery] = useState<DashboardQuery>();
  useEffect(() => {
    let active = true;
    getDashboardOptions()
      .then((value) => {
        if (active) {
          setOptions(value);
          setOptionsError("");
        }
      })
      .catch((e) => {
        if (active)
          setOptionsError(e instanceof Error ? e.message : "筛选选项加载失败");
      });
    return () => {
      active = false;
    };
  }, [refresh]);
  useEffect(() => {
    let active = true;
    setLoading(true);
    setError("");
    getDashboard(query)
      .then((value) => {
        if (active) setData(value);
      })
      .catch((e) => {
        if (active) {
          setError(e instanceof Error ? e.message : "数据加载失败");
          setData(undefined);
        }
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [query, refresh]);
  const change = (value: Partial<DashboardQuery>) => {
    if (value.tab === "learners") {
      setDetailQuery({ ...query, ...value, tab: "learners", page: 1 });
      return;
    }
    if (value.tab) setView(value.tab);
    setQuery((previous) => ({ ...previous, ...value }));
  };
  const selectView = (key: string) => {
    const next = dashboardTabs.find((tab) => tab.key === key)?.key;
    if (!next) return;
    setView(next);
    setQuery((previous) => ({
      ...previous,
      tab: next === "overview" ? "trainings" : next,
      status: "ALL",
      page: 1,
    }));
  };
  const training = (id: number) => {
    setDetailQuery({ ...query, trainingId: id, tab: "learners", status: "ALL", page: 1 });
  };
  const status = (value: DashboardStatus) =>
    change({
      tab: value === "OVERDUE" ? "incomplete" : "learners",
      status: value,
      page: 1,
    });
  const overview = data?.overview;
  const metrics = overview
    ? [
        {
          label: "培训数量",
          value: String(overview.trainingCount),
          unit: "场",
          detail: "当前筛选范围",
          icon: <IconCalendar />,
          action: () => change({ tab: "trainings", status: "ALL", page: 1 }),
        },
        {
          label: "应完成人次",
          value: String(overview.assigned),
          unit: "人次",
          detail: "员工 × 培训名单",
          icon: <IconUserGroup />,
          action: () => status("ALL"),
        },
        {
          label: "培训完成率",
          value:
            overview.completionRate == null
              ? "—"
              : `${overview.completionRate}%`,
          unit: "",
          detail: `已完成 ${overview.completed} / 应完成 ${overview.assigned}`,
          icon: <IconTickCircle />,
          action: () => status("COMPLETED"),
        },
        {
          label: "有效学习时长",
          value: formatStudyHours(overview.studiedHours),
          unit: "小时",
          detail: "累计有效学时",
          icon: <IconClock />,
          action: () => change({ tab: "logs", status: "ALL", page: 1 }),
        },
        {
          label: "考试通过率",
          value: overview.passRate == null ? "—" : `${overview.passRate}%`,
          unit: "",
          detail: `通过 ${overview.passed} / 应考 ${overview.examAssigned}`,
          icon: <IconCheckList />,
          action: () => change({ tab: "exams", status: "ALL", page: 1 }),
        },
        {
          label: "逾期未完成",
          value: String(overview.overdue),
          unit: "人次",
          detail: "培训 / 考试逾期去重",
          icon: <IconAlertTriangle />,
          action: () => status("OVERDUE"),
          warning: true,
        },
      ]
    : [];
  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div>
          <Typography.Title heading={2} style={{ color: "#183f35", margin: 0 }}>
            数据驾驶舱
          </Typography.Title>
          <p className="mt-2 text-sm text-slate-500">企业培训与考核数据总览</p>
        </div>
        <Space wrap>
          <Typography.Text type="tertiary" size="small">
            {data
              ? `更新于 ${new Date(data.updatedAt).toLocaleTimeString("zh-CN", { hour12: false })}`
              : ""}
          </Typography.Text>
          <Button
            icon={<IconRefresh />}
            loading={loading}
            onClick={() => setRefresh((value) => value + 1)}
          >
            刷新
          </Button>
        </Space>
      </div>
      <div className="overflow-x-auto">
        <Tabs style={{ minWidth: 560 }} activeKey={view} onChange={selectView}>
          {dashboardTabs.filter((tab) => tab.key !== "learners").map((tab) => (
            <TabPane key={tab.key} itemKey={tab.key} tab={tab.label} />
          ))}
        </Tabs>
      </div>
      <Card>
        <div className="grid items-end gap-4 sm:grid-cols-2 xl:grid-cols-[1.5fr_1fr_1fr_1fr_auto]">
          <div className="min-w-0">
            <div className="mb-2 flex items-center gap-1 text-sm">
              培训时间
              <Tooltip content="按培训开始日期筛选，未设置开始时间的培训按创建日期筛选；指标为所选课程的累计数据。">
                <IconHelpCircle tabIndex={0} aria-label="培训时间统计说明" />
              </Tooltip>
            </div>
            <DatePicker
              type="dateRange"
              format="yyyy-MM-dd"
              style={{ width: "100%" }}
              value={
                draft.start && draft.end
                  ? [
                      new Date(`${draft.start}T00:00:00`),
                      new Date(`${draft.end}T00:00:00`),
                    ]
                  : undefined
              }
              onChange={(value) => {
                if (
                  Array.isArray(value) &&
                  value[0] instanceof Date &&
                  value[1] instanceof Date
                )
                  setDraft((previous) => ({
                    ...previous,
                    start: dateText(value[0] as Date),
                    end: dateText(value[1] as Date),
                  }));
                else
                  setDraft((previous) => ({
                    ...previous,
                    start: undefined,
                    end: undefined,
                  }));
              }}
            />
          </div>
          <div className="min-w-0">
            <div className="mb-2 text-sm">培训名称</div>
            <Select
              aria-label="培训名称"
              style={{ width: "100%" }}
              filter
              showClear
              placeholder="全部培训"
              value={draft.trainingId}
              optionList={options.trainings.map((item) => ({
                value: item.id,
                label: item.name,
              }))}
              onChange={(value) =>
                setDraft((previous) => ({
                  ...previous,
                  trainingId: typeof value === "number" ? value : undefined,
                }))
              }
            />
          </div>
          <div className="min-w-0">
            <div className="mb-2 text-sm">部门</div>
            <Select
              aria-label="部门"
              style={{ width: "100%" }}
              filter
              showClear
              placeholder="全部部门"
              value={draft.departmentId}
              optionList={options.departments.map((item) => ({
                value: item.id,
                label: item.name,
              }))}
              onChange={(value) =>
                setDraft((previous) => ({
                  ...previous,
                  departmentId: typeof value === "number" ? value : undefined,
                }))
              }
            />
          </div>
          <div className="min-w-0">
            <div className="mb-2 text-sm">课程分类</div>
            <Select
              aria-label="课程分类"
              style={{ width: "100%" }}
              showClear
              placeholder="全部分类"
              value={draft.category}
              optionList={trainingCategories}
              onChange={(value) =>
                setDraft((previous) => ({
                  ...previous,
                  category: typeof value === "string" ? value : undefined,
                }))
              }
            />
          </div>
          <Space>
            <Button
              theme="solid"
              type="primary"
              loading={loading}
              onClick={() =>
                setQuery({
                  ...draft,
                  tab: query.tab,
                  status: query.status,
                  page: 1,
                  pageSize: query.pageSize,
                })
              }
            >
              查询
            </Button>
            <Button
              disabled={loading}
              onClick={() => {
                setDraft({});
                setQuery({
                  ...initialQuery,
                  tab: query.tab,
                  pageSize: query.pageSize,
                });
              }}
            >
              重置
            </Button>
          </Space>
        </div>
      </Card>
      {optionsError && (
        <Banner
          type="warning"
          description={`筛选选项加载失败：${optionsError}，请点击刷新重试。`}
        />
      )}
      {error && (
        <Banner
          type="danger"
          description={error}
          closeIcon={null}
          fullMode={false}
        >
          <Button onClick={() => setRefresh((value) => value + 1)}>重试</Button>
        </Banner>
      )}
      {loading ? (
        <Skeleton
          loading
          placeholder={
            <div className="space-y-5">
              <Skeleton.Paragraph rows={3} />
              <Skeleton.Paragraph rows={8} />
            </div>
          }
        />
      ) : data ? (
        <>
          {data.overview.missingDuration > 0 && (
            <Banner
              type="warning"
              description={`${data.overview.missingDuration} 条学习记录存在旧学时缺失，累计学时仅包含已记录部分。`}
            />
          )}
          {view === "overview" ? (
            <>
              <div className="grid grid-cols-2 gap-3 lg:grid-cols-3 2xl:grid-cols-6">
                {metrics.map((metric) => (
                  <Card key={metric.label} bodyStyle={{ padding: 16 }}>
                    <div className="mb-2 flex items-center gap-2 text-sm">
                      <span
                        style={{
                          color: metric.warning ? "#d97706" : "#28856c",
                        }}
                      >
                        {metric.icon}
                      </span>
                      {metric.label}
                    </div>
                    <Button
                      theme="borderless"
                      aria-label={`查看${metric.label}明细`}
                      onClick={metric.action}
                      style={{
                        color: metric.warning ? "#d97706" : "#183f35",
                        height: "auto",
                        padding: "4px 0",
                      }}
                    >
                      <span className="text-2xl font-semibold">
                        {metric.value}
                      </span>
                      <span className="ml-1 text-xs">{metric.unit}</span>
                    </Button>
                    <p className="mt-2 text-xs text-slate-500">
                      {metric.detail}
                    </p>
                  </Card>
                ))}
              </div>
              <DashboardCharts
                data={data}
                onStatus={status}
                onDepartment={(id) => {
                  change({
                    departmentId: id,
                    tab: "learners",
                    status: "ALL",
                    page: 1,
                  });
                }}
              />
            </>
          ) : (
            <DashboardDetails
              data={data}
              query={query}
              loading={loading}
              onChange={change}
              onTraining={training}
            />
          )}
        </>
      ) : !error ? (
        <Empty description="暂无数据" />
      ) : null}
      {detailQuery && (
        <TrainingDetailsModal
          initialQuery={detailQuery}
          title={detailQuery.trainingId
            ? `培训明细 · ${options.trainings.find((item) => item.id === detailQuery.trainingId)?.name || "学员完成情况"}`
            : "学员完成情况"}
          onClose={() => setDetailQuery(undefined)}
        />
      )}
    </div>
  );
}
