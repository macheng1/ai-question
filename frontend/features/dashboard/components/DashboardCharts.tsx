"use client";
import { StatHint } from "./StatHint";
import dynamic from "next/dynamic";
import { Button, Card, Empty, Progress, Spin } from "@douyinfe/semi-ui";
import type { DashboardData, DashboardStatus } from "../types";
const VChart = dynamic(
  () => import("@visactor/react-vchart").then((module) => module.VChart),
  {
    ssr: false,
    loading: () => (
      <div className="flex h-full items-center justify-center">
        <Spin />
      </div>
    ),
  },
);

export function DashboardCharts({
  data,
  onStatus,
  onDepartment,
}: {
  data: DashboardData;
  onStatus: (status: DashboardStatus) => void;
  onDepartment: (id: number) => void;
}) {
  const distribution = [
    {
      name: "已完成",
      count: data.overview.completed,
      status: "COMPLETED" as const,
      color: "#28856c",
    },
    {
      name: "进行中",
      count: data.overview.inProgress,
      status: "IN_PROGRESS" as const,
      color: "#e8a13c",
    },
    {
      name: "未开始",
      count: data.overview.notStarted,
      status: "NOT_STARTED" as const,
      color: "#aebdb8",
    },
  ];
  return (
    <div className="grid gap-4 xl:grid-cols-3">
      <Card title={<span className="inline-flex items-center gap-1">培训完成分布<StatHint label="培训完成分布" text="没有学习或答题记录算未开始，已达到培训要求算已完成，其余算进行中。" /></span>} className="min-w-0">
        {data.overview.assigned === 0 ? (
          <Empty className="py-12" description="暂无培训名单" />
        ) : (
          <>
            <div
              className="h-[200px]"
              role="img"
              aria-label={distribution
                .map((item) => `${item.name}${item.count}人次`)
                .join("，")}
            >
              <VChart
                spec={{
                  type: "pie",
                  animation: false,
                  data: [{ id: "distribution", values: distribution }],
                  categoryField: "name",
                  valueField: "count",
                  innerRadius: 0.65,
                  outerRadius: 0.9,
                  color: distribution.map((item) => item.color),
                  legends: { visible: false },
                  label: { visible: false },
                }}
                options={{ autoFit: true }}
              />
            </div>
            <div className="flex flex-wrap justify-center gap-1">
              {distribution.map((item) => (
                <Button
                  key={item.name}
                  theme="borderless"
                  onClick={() => onStatus(item.status)}
                  style={{ color: item.color }}
                >
                  {item.name} {item.count}
                </Button>
              ))}
            </div>
          </>
        )}
        <p className="mt-2 text-xs text-slate-500">
          关联考试未通过的已学习人员计入进行中
        </p>
      </Card>
      <Card title={<span className="inline-flex items-center gap-1">部门完成情况<StatHint label="部门完成情况" text="按培训分配时的部门统计，完成率为本部门已完成人次 ÷ 应完成人次。" /></span>} className="min-w-0">
        {data.departments.length === 0 ? (
          <Empty className="py-12" description="暂无部门数据" />
        ) : (
          <div className="max-h-[252px] overflow-y-auto pr-1">
            {data.departments.map((item) => (
              <div className="mb-4" key={item.id}>
                <div className="mb-1 flex items-center justify-between gap-2">
                  <Button
                    theme="borderless"
                    size="small"
                    style={{ color: "#246c58", maxWidth: "65%" }}
                    onClick={() => onDepartment(item.id)}
                  >
                    <span className="truncate">{item.name}</span>
                  </Button>
                  <span className="whitespace-nowrap text-xs text-slate-500">
                    {item.completed} / {item.assigned} · {item.rate}%
                  </span>
                </div>
                <Progress
                  percent={item.rate}
                  showInfo={false}
                  stroke="#28856c"
                />
              </div>
            ))}
          </div>
        )}
        <p className="mt-2 text-xs text-slate-500">
          点击部门筛选 · 按分配时部门统计
        </p>
      </Card>
      <Card title={<span className="inline-flex items-center gap-1">培训完成趋势<StatHint label="培训完成趋势" text="按完成日期统计近 30 天每天的完成人次。有考试时，取学完和通过考试中较晚的一天。" /></span>} className="min-w-0">
        {data.trend.every((item) => item.count === 0) ? (
          <Empty className="py-12" description="最近 30 天暂无完成记录" />
        ) : (
          <div
            className="h-[240px]"
            role="img"
            aria-label="最近30天培训完成人次趋势"
          >
            <VChart
              spec={{
                type: "line",
                animation: false,
                data: [
                  {
                    id: "trend",
                    values: data.trend.map((item) => ({
                      ...item,
                      label: item.date.slice(5),
                    })),
                  },
                ],
                xField: "label",
                yField: "count",
                color: ["#28856c"],
                point: { visible: true },
                axes: [
                  { orient: "bottom", label: { autoHide: true } },
                  {
                    orient: "left",
                    min: 0,
                    title: { visible: true, text: "人次" },
                    tick: { tickCount: 4 },
                  },
                ],
                legends: { visible: false },
              }}
              options={{ autoFit: true }}
            />
          </div>
        )}
        <p className="mt-2 text-xs text-slate-500">
          所选课程最近 30 天的当前已完成人次
        </p>
      </Card>
    </div>
  );
}
