"use client";

import dynamic from "next/dynamic";
import { Empty } from "@douyinfe/semi-ui";
import type { AssessmentStatisticsDetail } from "@/features/assessments/api";

const VChart = dynamic(
  () => import("@visactor/react-vchart").then((module) => module.VChart),
  { ssr: false },
);

type ChartProps = {
  data: AssessmentStatisticsDetail;
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
    <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
      <h2 className="text-base font-bold text-slate-800">{title}</h2>
      <p className="mt-1 text-xs text-slate-400">{description}</p>
      <div className="mt-4 h-[280px]">{children}</div>
    </section>
  );
}

function EmptyChart() {
  return <Empty description="暂无统计数据" className="h-full justify-center" />;
}

export function StatisticsCharts({ data }: ChartProps) {
  const scoreValues = data.scoreDistribution.map((item) => ({
    label: item.label,
    count: Number(item.count || 0),
  }));
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
    <div className="grid gap-5 lg:grid-cols-2">
      <ChartCard title="成绩分布" description="各分数段人数统计（按最高分计）">
        {scoreValues.length ? (
          <VChart
            spec={{
              type: "bar",
              data: [{ id: "score", values: scoreValues }],
              xField: "label",
              yField: "count",
              color: ["#2563eb"],
              bar: { style: { cornerRadius: 6 } },
              axes: [
                { orient: "bottom", title: { visible: false } },
                { orient: "left", title: { text: "人数" } },
              ],
              label: { visible: true },
              legends: { visible: false },
            }}
            options={{ autoFit: true }}
          />
        ) : (
          <EmptyChart />
        )}
      </ChartCard>

      <ChartCard title="及格情况" description={`及格线 ${data.overview.passScore || 0} 分`}>
        {passValues.length ? (
          <VChart
            spec={{
              type: "pie",
              data: [{ id: "pass", values: passValues }],
              categoryField: "label",
              valueField: "count",
              outerRadius: 0.82,
              innerRadius: 0.58,
              color: ["#22c55e", "#ef4444"],
              legends: { visible: true, orient: "bottom" },
              label: { visible: true },
            }}
            options={{ autoFit: true }}
          />
        ) : (
          <EmptyChart />
        )}
      </ChartCard>

      <ChartCard title="交卷时间分布" description="考生交卷时间趋势">
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
                { orient: "left", title: { text: "交卷人数" } },
              ],
              legends: { visible: false },
            }}
            options={{ autoFit: true }}
          />
        ) : (
          <EmptyChart />
        )}
      </ChartCard>

      <ChartCard title="题型得分率" description="各题型平均正确率">
        {typeValues.length ? (
          <VChart
            spec={{
              type: "bar",
              data: [{ id: "types", values: typeValues }],
              xField: "label",
              yField: "value",
              color: ["#38bdf8"],
              bar: { style: { cornerRadius: 6 } },
              axes: [
                { orient: "bottom", title: { visible: false } },
                { orient: "left", title: { text: "正确率（%）" } },
              ],
              label: { visible: true },
              legends: { visible: false },
            }}
            options={{ autoFit: true }}
          />
        ) : (
          <EmptyChart />
        )}
      </ChartCard>
    </div>
  );
}
