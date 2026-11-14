"use client";
import { Typography, Space } from "@douyinfe/semi-ui";
import { Layout, Card } from "@douyinfe/semi-ui";

import { IconArrowLeft, IconHelpCircleStroked } from "@douyinfe/semi-icons";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useParams } from "next/navigation";
import "@douyinfe/semi-ui/react19-adapter";
import { Button, Checkbox, Spin, Tag, Toast, Tooltip } from "@douyinfe/semi-ui";
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
  const total = Math.round(Math.max(0, Number(seconds || 0)));
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
  const [appliedRetake, setAppliedRetake] = useState(false);
  const [loading, setLoading] = useState(true);

  const requestVersion = useRef(0);
  const [refreshVersion, setRefreshVersion] = useState(0);
  const load = useCallback(async () => {
    if (!taskId) return;
    const version = ++requestVersion.current;
    setLoading(true);
    try {
      const result = await getAssessmentStatisticsDetail(taskId, includeRetake);
      if (version === requestVersion.current) { setData(result); setAppliedRetake(includeRetake); setRefreshVersion(value => value + 1); }
    } catch (error) {
      if (version === requestVersion.current) Toast.error(error instanceof Error ? error.message : "统计数据加载失败");
    } finally {
      if (version === requestVersion.current) setLoading(false);
    }
  }, [taskId, includeRetake]);

  useEffect(() => {
    void load();
    return () => { requestVersion.current += 1; };
  }, [load]);

  const overview = data?.overview;
  const cards = useMemo(
    () => [
      [appliedRetake ? "实考次数" : "实考人数", `${Number(overview?.participantCount || 0)} ${appliedRetake ? "次" : "人"}`, "blue"],
      ["平均分", overview?.averageScore == null ? "—" : `${Number(overview.averageScore).toFixed(1)} 分`, "purple"],
      ["及格率", overview?.passRate == null ? "—" : `${Number(overview.passRate).toFixed(1)}%`, "green"],
      ["最高分", overview?.highestScore == null ? "—" : `${Number(overview.highestScore).toFixed(1)} 分`, "orange"],
      ["最低分", overview?.lowestScore == null ? "—" : `${Number(overview.lowestScore).toFixed(1)} 分`, "red"],
      ["平均用时", durationText(overview?.averageDurationSeconds), "teal"],
    ],
    [appliedRetake, overview],
  );

  return (
    <ProfileShell>
          <div className="flex items-center justify-between gap-4">
            <div>
              <Typography.Text link={{ href: "/profile/trainings" }} icon={<IconArrowLeft size="small" />} className="!inline-flex !min-h-8 !items-center !text-sm">返回培训管理</Typography.Text>
              <div className="mt-5 flex items-center gap-2"><Typography.Title heading={1} className="!m-0 !text-[28px] !font-semibold !text-[#183f35]">考试统计</Typography.Title>
                <Tooltip trigger="click" position="bottomLeft" content={<div className="max-h-[65vh] max-w-[380px] overflow-y-auto text-sm leading-6">
                  <strong>考试统计规则</strong>
                  <ul className="mt-2 list-disc space-y-1 pl-4">
                    <li>只统计本培训关联的考试，不合并使用同一试卷的其他考试。</li>
                    <li>应考按已保存的名单计算；至少交卷一次算已考，重考不重复计人数。尚未交卷算未考。</li>
                    <li>默认每人取最后一次交卷。勾选“包含重考记录”后按全部交卷次数统计。待批阅计入交卷人数、次数和用时。</li>
                    <li>分数和题目分析只统计已出分的答卷。及格率为及格记录数 ÷ 已出分记录数。</li>
                    <li>平均用时为开始答题至交卷的平均时长。成绩分布按得分占满分的百分比分段。</li>
                    <li>题型正确率和易错题按答对题数计算，未答按错误计；随机试卷只统计实际抽到的题目。</li>
                    <li>部门筛选只影响应考名单及其人数，状态筛选只影响名单列表；不影响下方成绩和图表。点击“刷新数据”更新统计和名单。</li>
                  </ul>
                </div>}><Button aria-label="考试统计规则说明" theme="borderless" type="tertiary" size="small" icon={<IconHelpCircleStroked />} /></Tooltip>
              </div>
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
                  <Checkbox disabled={loading} checked={includeRetake} onChange={(event) => setIncludeRetake(Boolean(event.target.checked))}>
                    包含重考记录
                  </Checkbox>
                </div>
              </Layout.Content>

              <AssessmentRoster taskId={taskId} scope={data.task.candidateScope} refreshVersion={refreshVersion} />
              <div className="mt-5 grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-6">
                {cards.map(([label, value, tone]) => (
                  <Card bodyStyle={{ padding: 0 }} key={label} className=""><div className="p-4">
                    <p className="text-xs text-slate-400">{label}</p>
                    <p className={`mt-2 text-xl font-bold ${cardToneClass[tone]}`}>{value}</p>
                  </div></Card>
                ))}
              </div>

              <div className="mt-5">
                <StatisticsCharts data={data} includeRetake={appliedRetake} />
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
