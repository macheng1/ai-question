"use client";
import { Typography, Space } from "@douyinfe/semi-ui";
import { Layout, Card } from "@douyinfe/semi-ui";

import { IconArrowLeft } from "@douyinfe/semi-icons";

import { useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { useParams } from "next/navigation";
import "@douyinfe/semi-ui/react19-adapter";
import { Button, Checkbox, Spin, Tag, Toast } from "@douyinfe/semi-ui";
import { ProfileShell } from "@/components/layout/ProfileShell";
import {
  getAssessmentStatisticsDetail,
  type AssessmentStatisticsDetail,
} from "@/features/assessments/api";
import { AssessmentRoster } from "@/features/assessments/components/AssessmentRoster";
import { StatisticsCharts } from "@/features/assessments/components/StatisticsCharts";

const dateText = (value?: string) =>
  value && Number.isFinite(Date.parse(value))
    ? new Date(value).toLocaleString("zh-CN", { timeZone: "Asia/Shanghai", hour12: false })
    : "-";

const durationText = (seconds?: number) => {
  const total = Math.max(0, Number(seconds || 0));
  return `${Math.floor(total / 60)}分${Math.round(total % 60)}秒`;
};

const cardToneClass: Record<string, string> = {
  blue: "text-blue-600",
  purple: "text-purple-600",
  green: "text-green-600",
  orange: "text-orange-600",
  red: "text-red-600",
  teal: "text-teal-600",
};

export default function AssessmentStatisticsPage() {
  const { id } = useParams<{ id: string }>();
  const taskId = Number(id);
  const [data, setData] = useState<AssessmentStatisticsDetail | null>(null);
  const [includeRetake, setIncludeRetake] = useState(false);
  const [loading, setLoading] = useState(true);

  const load = async () => {
    if (!taskId) return;
    setLoading(true);
    try {
      setData(await getAssessmentStatisticsDetail(taskId, includeRetake));
    } catch (error) {
      Toast.error(error instanceof Error ? error.message : "统计数据加载失败");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
  }, [taskId, includeRetake]);

  const overview = data?.overview;
  const cards = useMemo(
    () => [
      [includeRetake ? "实考次数" : "实考人数", `${Number(overview?.participantCount || 0)} ${includeRetake ? "次" : "人"}`, "blue"],
      ["平均分", `${Number(overview?.averageScore || 0).toFixed(0)} 分`, "purple"],
      ["及格率", `${Number(overview?.passRate || 0).toFixed(0)}%`, "green"],
      ["最高分", `${Number(overview?.highestScore || 0).toFixed(0)} 分`, "orange"],
      ["最低分", `${Number(overview?.lowestScore || 0).toFixed(0)} 分`, "red"],
      ["平均用时", durationText(overview?.averageDurationSeconds), "teal"],
    ],
    [includeRetake, overview],
  );

  return (
    <ProfileShell>
          <div className="flex items-center justify-between gap-4">
            <div>
              <Link
                href={`/profile/assessments`}
                className="text-sm font-semibold text-blue-600 no-underline"
              >
                <IconArrowLeft aria-hidden="true" /> 返回考试管理
              </Link>
              <Typography.Title heading={1} className="!mt-5 !m-0 !text-[28px] !font-semibold !text-[#183f35]">考试统计</Typography.Title>
            </div>
            <Button theme="borderless" onClick={() => load()} loading={loading}>
              刷新数据
            </Button>
          </div>

          {loading && !data ? (
            <div className="grid min-h-[520px] place-items-center">
              <Spin size="large" />
            </div>
          ) : data ? (
            <>
              <Layout.Content className="mt-5 rounded-2xl border border-blue-200 bg-white p-5 shadow-sm">
                <div className="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
                  <div>
                    <Space wrap >
                      <Typography.Title heading={2} className="!text-lg !font-bold !text-slate-800">{data.task.name}</Typography.Title>
                      <Tag type="light">{data.task.status === "ENDED" || (data.task.endAt && Date.parse(data.task.endAt) <= Date.now()) ? "已结束" : data.task.status === "DISABLED" ? "已停用" : data.task.status === "DRAFT" ? "草稿" : data.task.startAt && Date.parse(data.task.startAt) > Date.now() ? "未开始" : "进行中"}</Tag>
                    </Space>
                    <p className="mt-2 text-sm text-slate-500">
                      试卷：{data.task.paperName || data.task.paperId} · {dateText(data.task.startAt)} ～ {dateText(data.task.endAt)} · 时长 {data.task.durationMinutes} 分钟 · 满分 {data.overview.totalScore} 分 · 及格 {data.overview.passScore} 分
                    </p>
                  </div>
                  <Checkbox checked={includeRetake} onChange={(event) => setIncludeRetake(Boolean(event.target.checked))}>
                    包含重考记录
                  </Checkbox>
                </div>
              </Layout.Content>

              <AssessmentRoster taskId={taskId} scope={data.task.candidateScope} />
              <div className="mt-5 grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-6">
                {cards.map(([label, value, tone]) => (
                  <Card bodyStyle={{ padding: 0 }} key={label} className=""><div className="p-4">
                    <p className="text-xs text-slate-400">{label}</p>
                    <p className={`mt-2 text-xl font-bold ${cardToneClass[tone]}`}>{value}</p>
                  </div></Card>
                ))}
              </div>

              <div className="mt-5">
                <StatisticsCharts data={data} />
              </div>

              <Layout.Content className="mt-5 rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
                <Typography.Title heading={2} className="!text-base !font-bold !text-slate-800">易错题 TOP 5</Typography.Title>
                <p className="mt-1 text-xs text-slate-400">正确率最低的题目</p>
                <div className="flex flex-col mt-4 gap-5">
                  {data.difficultQuestions.map((question, index) => (
                    <div key={question.questionId}>
                      <div className="flex items-start justify-between gap-3 text-sm">
                        <div className="min-w-0 text-slate-700">
                          <span className="mr-3 inline-grid h-6 w-6 place-items-center rounded-md bg-red-50 text-xs text-red-500">{index + 1}</span>
                          <Tag color="blue" type="light" size="small">{question.type}</Tag>
                          <span className="ml-3">{question.title}</span>
                        </div>
                        <span className="shrink-0 text-xs text-red-500">{Number(question.correctRate || 0).toFixed(0)}%</span>
                      </div>
                      <div className="mt-3 h-1.5 overflow-hidden rounded-full bg-slate-100">
                        <div className="h-full rounded-full bg-red-400" style={{ width: `${Math.min(100, Math.max(0, Number(question.correctRate || 0)))}%` }} />
                      </div>
                    </div>
                  ))}
                  {!data.difficultQuestions.length && <p className="py-10 text-center text-sm text-slate-400">暂无题目统计数据</p>}
                </div>
              </Layout.Content>
            </>
          ) : null}

    </ProfileShell>
  );
}
