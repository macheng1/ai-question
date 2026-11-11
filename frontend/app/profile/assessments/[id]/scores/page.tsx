"use client";
import { Typography } from "@douyinfe/semi-ui";
import { Card } from "@douyinfe/semi-ui";

import { IconArrowLeft } from "@douyinfe/semi-icons";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useParams } from "next/navigation";
import "@douyinfe/semi-ui/react19-adapter";
import { Table, Select, Checkbox, Spin, Tag, Toast } from "@douyinfe/semi-ui";
import { ProfileShell } from "@/components/layout/ProfileShell";
import {
  getAssessment,
  getAssessmentRecords,
  getAssessmentStatistics,
  type Assessment,
} from "@/features/assessments/api";

import { AssessmentRoster } from "@/features/assessments/components/AssessmentRoster";

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
  departmentId?: number;
  departmentName?: string;
  score: number;
  totalScore: number;
  passed: boolean;
  attemptNo: number;
  startedAt?: string;
  submittedAt?: string;
  status: string;
};
const textDate = (value?: string) =>
  value && Number.isFinite(Date.parse(value))
    ? new Date(value).toLocaleString("zh-CN", { timeZone: "Asia/Shanghai", hour12: false })
    : "-";
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
  const [department, setDepartment] = useState<number>();
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
      <ProfileShell>
        <div className="grid min-h-[720px] place-items-center">
          <Spin size="large" />
        </div>
      </ProfileShell>
    );
  const passRate = stats.totalCount
    ? Math.round((stats.passedCount / stats.totalCount) * 100)
    : 0;

  const departments = Array.from(new Map(records.filter(r => r.departmentId).map(r => [r.departmentId!, r.departmentName || "未关联部门"])).entries());
  const visibleRecords = records.filter(r => department == null || r.departmentId === department);
  return (
    <ProfileShell>
          <Typography.Text link={{ href: "/profile/assessments" }} icon={<IconArrowLeft size="small" />} className="!inline-flex !min-h-8 !items-center !text-sm">返回考试管理</Typography.Text>
          <div className="mt-5">
            <Typography.Title heading={1} className="!m-0 !text-[28px] !font-semibold !text-[#183f35]">成绩统计</Typography.Title>
            <p className="mt-2 text-sm text-slate-500">
              查看“{task?.paperName || "试卷"}”的成绩与交卷记录
            </p>
          </div>
          <Card bodyStyle={{ padding: 0 }} className="!mt-5"><div className="p-4">
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
          </div></Card>
          <AssessmentRoster taskId={taskId} scope={task?.candidateScope} />
          <div className="mt-5 flex flex-wrap items-center gap-3"><span className="text-sm font-semibold">成绩记录</span><Select aria-label="成绩部门筛选" placeholder="全部部门" showClear value={department} onChange={v => setDepartment(v == null ? undefined : Number(v))} optionList={departments.map(([value, label]) => ({ value, label }))} style={{ minWidth: 180 }} /><span className="text-xs text-slate-500">上方成绩汇总为全场数据</span></div>
          <Card bodyStyle={{ padding: 0 }} className="!mt-5 overflow-x-auto">
            <div className="min-w-[1080px]">
              <Table empty="暂无成绩记录" dataSource={visibleRecords} rowKey="id" pagination={false} scroll={{x: 1080}} columns={[{title: "序号", render: (_value: unknown, _record: (typeof records)[number], index: number) => (<span>{index + 1}</span>)},
{title: "考试", render: (_value: unknown, record: (typeof records)[number]) => (<span>{record.paperName || task?.paperName || "-"}</span>)},
{title: "姓名", render: (_value: unknown, record: (typeof records)[number]) => (<span>
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
{title: "部门", render: (_value: unknown, record: RecordItem) => record.departmentName || "未关联部门"},
{title: "得分", render: (_value: unknown, record: (typeof records)[number]) => (<span
                    className={`font-semibold ${record.status === "VOID" ? "text-slate-400" : "text-slate-700"}`}
                  >
                    {record.status === "IN_PROGRESS"
                      ? "-"
                      : (record.score ?? 0)}
                  </span>)},
{title: "状态", render: (_value: unknown, record: (typeof records)[number]) => (<span>
                    <Tag color={statusMeta(record).color} type="light" size="small">
                      {statusMeta(record).label}
                    </Tag>
                  </span>)},
{title: "次数", render: (_value: unknown, record: (typeof records)[number]) => (<span>
                    {record.attemptNo === 1
                      ? "首次"
                      : `第 ${record.attemptNo} 次`}
                  </span>)},
{title: "用时", render: (_value: unknown, record: (typeof records)[number]) => (<span>
                    {durationText(record.startedAt, record.submittedAt)}
                  </span>)},
{title: "交卷时间", render: (_value: unknown, record: (typeof records)[number]) => (<span>{textDate(record.submittedAt)}</span>)},
{title: "操作", render: (_value: unknown, record: (typeof records)[number]) => (record.status !== "IN_PROGRESS" && (
                    <Link
                      href={`/kaoshi/${taskId}/result?attemptId=${record.id}`}
                      target="_blank"
                      className="text-left text-teal-600 no-underline hover:text-teal-700"
                    >
                      查看
                    </Link>
                  ))}]} />
            </div>
          </Card>

    </ProfileShell>
  );
}
