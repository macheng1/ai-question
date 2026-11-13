"use client";
import { formatPercentage, formatStatistic } from "../statistics-format";
import { Typography } from "@douyinfe/semi-ui";
import { Layout } from "@douyinfe/semi-ui";

import dynamic from "next/dynamic";
import { Empty } from "@douyinfe/semi-ui";
import type { AssessmentStatisticsDetail } from "@/features/assessments/api";

const VChart = dynamic(
  () => import("@visactor/react-vchart").then((module) => module.VChart),
  { ssr: false },
);

type ChartProps = {
  data: AssessmentStatisticsDetail;
  includeRetake?: boolean;
};

function ChartCard({
  title,
  description,
  children,
}: {
  title: string;
  description: string;
  children: React.ReactNode;
}) {
  return (
    <Layout.Content className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
      <Typography.Title heading={2} className="!text-base !font-bold !text-slate-800">{title}</Typography.Title>
      <p className="mt-1 text-xs text-slate-400">{description}</p>
      <div className="mt-4 h-[280px]">{children}</div>
    </Layout.Content>
  );
}

function EmptyChart() {
  return <Empty description="暂无统计数据" className="h-full justify-center" />;
}

export function StatisticsCharts({ data, includeRetake = false }: ChartProps) {
  const scoreRanges = ["0-59", "60-69", "70-79", "80-89", "90-100"];
  const scoreLabels = ["0–<60", "60–<70", "70–<80", "80–<90", "90–100"];
  const scoreValues = scoreRanges.map((range, index) => ({
    label: scoreLabels[index],
    count: Number(data.scoreDistribution.find((item) => item.label === range)?.count || 0),
  }));
  data.scoreDistribution.filter((item) => !scoreRanges.includes(item.label)).forEach((item) => {
    scoreValues.push({ label: item.label, count: Number(item.count || 0) });
  });
  const passValues = data.passDistribution.map((item) => ({
    label: item.label,
    count: Number(item.count || 0),
  }));
  const trendValues = data.submissionTrend.map((item) => ({
    label: item.label,
    count: Number(item.count || 0),
  }));
  const typeValues = data.typeScore.map((item) => ({
    label: item.type,
    value: Number(item.correctRate || 0),
  }));

  return (
    <div className="grid grid-cols-2 gap-4 ">
      <div className="min-w-0 col-span-2 lg:col-span-1"><ChartCard title="成绩分布" description={includeRetake ? "按得分占满分的百分比分段，统计所有已交卷次数；柱高表示次数" : "按得分占满分的百分比分段，每人取最后一次已交卷成绩；柱高表示人数"}>
        {data.scoreDistribution.length ? (
          <VChart
            spec={{
              type: "bar",
              data: [{ id: "score", values: scoreValues }],
              xField: "label",
              yField: "count",
              color: ["#2563eb"],
              barMaxWidth: 56,
              bar: { style: { cornerRadius: 6 } },
              axes: [
                { orient: "bottom", title: { visible: false } },
                { orient: "left", min: 0, tick: { noDecimals: true }, title: { visible: true, text: includeRetake ? "次数" : "人数" } },
              ],
              label: { visible: true },
              legends: { visible: false },
            }}
            options={{ autoFit: true }}
          />
        ) : (
          <EmptyChart />
        )}
      </ChartCard></div>

      <div className="min-w-0 col-span-2 lg:col-span-1"><ChartCard title="及格情况" description={`及格线 ${formatStatistic(data.overview.passScore || 0)} 分`}>
        {passValues.length ? (
          <VChart
            spec={{
              type: "pie",
              data: [{ id: "pass", values: passValues }],
              categoryField: "label",
              valueField: "count",
              outerRadius: 0.82,
              innerRadius: 0.58,
              color: { type: "ordinal", domain: ["已通过", "未通过"], range: ["#22c55e", "#ef4444"] },
              legends: { visible: true, orient: "bottom" },
              label: { visible: true },
            }}
            options={{ autoFit: true }}
          />
        ) : (
          <EmptyChart />
        )}
      </ChartCard></div>

      <div className="min-w-0 col-span-2 lg:col-span-1"><ChartCard title="交卷时间分布" description={includeRetake ? "按交卷时间统计全部已交卷次数" : "按每人最后一次已交卷时间统计人数"}>
        {trendValues.length ? (
          <VChart
            spec={{
              type: "line",
              data: [{ id: "trend", values: trendValues }],
              xField: "label",
              yField: "count",
              line: { style: { lineWidth: 3 } },
              point: { visible: true, style: { fill: "#38bdf8" } },
              axes: [
                { orient: "bottom", title: { visible: false } },
                { orient: "left", min: 0, tick: { noDecimals: true }, title: { visible: true, text: includeRetake ? "交卷次数" : "交卷人数" } },
              ],
              legends: { visible: false },
            }}
            options={{ autoFit: true }}
          />
        ) : (
          <EmptyChart />
        )}
      </ChartCard></div>

      <div className="min-w-0 col-span-2 lg:col-span-1"><ChartCard title="题型正确率" description="答对题数 ÷ 该题型总题数，未答按错误计">
        {typeValues.length ? (
          <VChart
            spec={{
              type: "bar",
              data: [{ id: "types", values: typeValues }],
              xField: "label",
              yField: "value",
              color: ["#38bdf8"],
              barMaxWidth: 56,
              bar: { style: { cornerRadius: 6 } },
              axes: [
                { orient: "bottom", title: { visible: false } },
                { orient: "left", type: "linear", min: 0, max: 100, label: { formatMethod: formatPercentage }, title: { visible: true, text: "正确率" } },
              ],
              label: { visible: true, formatMethod: formatPercentage },
              tooltip: {
                mark: {
                  title: { value: (datum) => String(datum?.label ?? "") },
                  content: [{ key: "正确率", value: (datum) => formatPercentage(datum?.value) }],
                },
                dimension: {
                  title: { value: (datum) => String(datum?.label ?? "") },
                  content: [{ key: "正确率", value: (datum) => formatPercentage(datum?.value) }],
                },
              },
              legends: { visible: false },
            }}
            options={{ autoFit: true }}
          />
        ) : (
          <EmptyChart />
        )}
      </ChartCard></div>
    </div>
  );
}
