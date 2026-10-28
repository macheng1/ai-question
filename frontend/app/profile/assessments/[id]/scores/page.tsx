"use client";

import { IconArrowLeft } from "@/features/exam/components/ExamIcons";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useParams } from "next/navigation";
import "@douyinfe/semi-ui/react19-adapter";
import { Table,  Checkbox, Spin, Tag, Toast  } from "@douyinfe/semi-ui";
import { PageShell } from "@/components/layout/PageShell";
import { ProfileSidebar } from "@/components/layout/ProfileSidebar";
import {
  getAssessment,
  getAssessmentRecords,
  getAssessmentStatistics,
  type Assessment,
} from "@/features/assessments/api";

type Statistics = {
  totalCount: number;
  averageScore: number;
  passedCount: number;
  totalScore: number;
};
type RecordItem = {
  id: number;
  paperName?: string;
  candidateName: string;
  phone?: string;
  score: number;
  totalScore: number;
  passed: boolean;
  attemptNo: number;
  startedAt?: string;
  submittedAt?: string;
  status: string;
};
const textDate = (value?: string) =>
  value ? value.replace("T", " ").slice(0, 16) : "-";
const durationText = (startedAt?: string, submittedAt?: string) => {
  if (!startedAt || !submittedAt) return "-";
  const seconds = Math.max(
    0,
    Math.floor(
      (new Date(submittedAt).getTime() - new Date(startedAt).getTime()) / 1000,
    ),
  );
  return `${Math.floor(seconds / 60)}分${seconds % 60}秒`;
};
const statusMeta = (record: RecordItem) => {
  if (record.status === "VOID") return { label: "作废", color: "grey" as const };
  if (record.status === "SUBMITTED") {
    return record.passed
      ? { label: "已通过", color: "green" as const }
      : { label: "未通过", color: "red" as const };
  }
  if (record.status === "IN_PROGRESS") {
    return { label: "答题中", color: "blue" as const };
  }
  if (record.status === "EXPIRED") {
    return { label: "已超时", color: "orange" as const };
  }
  return { label: "未交卷", color: "grey" as const };
};

export default function AssessmentScoresPage() {
  const { id } = useParams<{ id: string }>();
  const taskId = Number(id);
  const [task, setTask] = useState<Assessment | null>(null);
  const [stats, setStats] = useState<Statistics>({
    totalCount: 0,
    averageScore: 0,
    passedCount: 0,
    totalScore: 0,
  });
  const [records, setRecords] = useState<RecordItem[]>([]);
  const [includeRetake, setIncludeRetake] = useState(false);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!taskId) return;
    setLoading(true);
    Promise.all([
      getAssessment(taskId),
      getAssessmentStatistics(taskId, includeRetake),
      getAssessmentRecords(taskId, includeRetake),
    ])
      .then(([assessment, summary, list]) => {
        setTask(assessment);
        setStats(summary);
        setRecords(list);
      })
      .catch((error) =>
        Toast.error(error instanceof Error ? error.message : "成绩加载失败"),
      )
      .finally(() => setLoading(false));
  }, [taskId, includeRetake]);

  if (loading)
    return (
      <PageShell>
        <div className="grid min-h-[720px] place-items-center">
          <Spin size="large" />
        </div>
      </PageShell>
    );
  const passRate = stats.totalCount
    ? Math.round((stats.passedCount / stats.totalCount) * 100)
    : 0;

  return (
    <PageShell>
      <main className="mx-auto flex min-h-[720px] w-[92%] max-w-[1160px] flex-col gap-6 py-10 sm:py-14 md:flex-row">
        <ProfileSidebar />
        <section className="min-w-0 flex-1">
          <Link
            href="/profile/assessments"
            className="text-sm font-semibold text-blue-600 no-underline"
          >
            <IconArrowLeft aria-hidden="true" /> 返回考试管理
          </Link>
          <div className="mt-5">
            <h1 className="text-2xl font-bold text-slate-800">成绩统计</h1>
            <p className="mt-2 text-sm text-slate-500">
              查看“{task?.paperName || "试卷"}”的成绩与交卷记录
            </p>
          </div>
          <div className="mt-6 rounded-2xl border border-slate-200 bg-white p-4">
            <div className="flex flex-col gap-3 lg:flex-row lg:items-center">
              <div className="min-w-40 px-1 py-2 text-sm font-semibold text-slate-700">
                {task?.paperName || "当前试卷"}
              </div>
              <label className="flex items-center gap-2 text-sm text-slate-600">
                <Checkbox
                  checked={includeRetake}
                  onChange={(event) => setIncludeRetake(Boolean(event.target.checked))}
                />
                包含重考记录
              </label>
              <span className="text-sm text-slate-500">
                平均分{" "}
                <b className="text-2xl text-slate-800">
                  {Number(stats.averageScore || 0).toFixed(0)}
                </b>
              </span>
              <span className="text-sm text-slate-500">
                及格人数{" "}
                <b className="text-xl text-emerald-600">{stats.passedCount}</b>{" "}
                / {stats.totalCount}（{passRate}%）
              </span>
            </div>
            <p className="mt-3 text-right text-xs text-slate-400">
              {includeRetake
                ? "当前显示全部考试次数"
                : "当前显示每位考生最新一次考试记录"}
            </p>
          </div>
          <div className="mt-5 overflow-x-auto rounded-2xl border border-slate-200 bg-white">
            <div className="min-w-[1080px]">
              <Table dataSource={records} rowKey="id" pagination={false} scroll={{x: 1080}} columns={[{title: "序号", render: (_value: unknown, record: (typeof records)[number], index: number) => (<span>{index + 1}</span>)},
{title: "考试", render: (_value: unknown, record: (typeof records)[number], index: number) => (<span>{record.paperName || task?.paperName || "-"}</span>)},
{title: "姓名", render: (_value: unknown, record: (typeof records)[number], index: number) => (<span>
                    <b className="block font-normal text-slate-700">
                      {record.candidateName || record.phone || "匿名考生"}
                    </b>
                    <small className="text-xs text-slate-400">
                      {record.phone || "-"}
                    </small>
                    {includeRetake && (
                      <small className="block text-xs text-slate-400">
                        （第{record.attemptNo}次）
                      </small>
                    )}
                  </span>)},
{title: "得分", render: (_value: unknown, record: (typeof records)[number], index: number) => (<span
                    className={`font-semibold ${record.status === "VOID" ? "text-slate-400" : "text-slate-700"}`}
                  >
                    {record.status === "IN_PROGRESS"
                      ? "-"
                      : (record.score ?? 0)}
                  </span>)},
{title: "状态", render: (_value: unknown, record: (typeof records)[number], index: number) => (<span>
                    <Tag color={statusMeta(record).color} type="light" size="small">
                      {statusMeta(record).label}
                    </Tag>
                  </span>)},
{title: "次数", render: (_value: unknown, record: (typeof records)[number], index: number) => (<span>
                    {record.attemptNo === 1
                      ? "首次"
                      : `第 ${record.attemptNo} 次`}
                  </span>)},
{title: "用时", render: (_value: unknown, record: (typeof records)[number], index: number) => (<span>
                    {durationText(record.startedAt, record.submittedAt)}
                  </span>)},
{title: "交卷时间", render: (_value: unknown, record: (typeof records)[number], index: number) => (<span>{textDate(record.submittedAt)}</span>)},
{title: "操作", render: (_value: unknown, record: (typeof records)[number], index: number) => (record.status !== "IN_PROGRESS" && (
                    <Link
                      href={`/kaoshi/${taskId}/result?attemptId=${record.id}`}
                      target="_blank"
                      className="text-left text-teal-600 no-underline hover:text-teal-700"
                    >
                      查看
                    </Link>
                  ))}]} />
              {!records.length && (
                <div className="py-24 text-center text-sm text-slate-400">
                  暂无成绩记录
                </div>
              )}
            </div>
          </div>
        </section>
      </main>
    </PageShell>
  );
}
