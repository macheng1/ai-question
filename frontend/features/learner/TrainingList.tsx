"use client";
import { SurveyFeedbackModal } from "./SurveyFeedbackModal";
import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import {
  Banner,
  Button,
  Card,
  Input,
  Select,
  Spin,
  Tag,
  Table,
  Typography,
  Progress,
  Space,
} from "@douyinfe/semi-ui";
import { getLearnerTrainings, type LearnerTraining } from "./api";
import {
  learningLabels,
  examLabels,
  formatTime,
  formatDuration,
  learningAction,
  learningHref,
} from "./status";

export function TrainingList({
  surveys = false,
  exams = false,
}: {
  surveys?: boolean;
  exams?: boolean;
}) {
  const router = useRouter();
  const [surveyId, setSurveyId] = useState<string>();
  const [rows, setRows] = useState<LearnerTraining[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [keyword, setKeyword] = useState("");
  const [status, setStatus] = useState<string>();
  const [requirement, setRequirement] = useState<string>();
  const [reload, setReload] = useState(0);
  useEffect(() => {
    let active = true;
    setLoading(true);
    setError("");
    getLearnerTrainings()
      .then((data) => {
        if (active) setRows(data);
      })
      .catch((e) => {
        if (active) setError(e.message);
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [reload]);
  const visible = rows.filter(
    (row) =>
      (!surveys || row.surveyEnabled) &&
      (!exams || row.assessmentId) &&
      `${row.name} ${row.examName || ""}`.includes(keyword.trim()) &&
      (!requirement || row.requirementType === requirement) &&
      (!status || (exams ? row.examStatus : row.learningStatus) === status),
  );
  return (
    <div className="flex flex-col gap-5">
      <SurveyFeedbackModal id={surveyId} onClose={() => setSurveyId(undefined)} />
      <div className="flex flex-wrap items-center justify-between gap-3">
        <Typography.Title heading={2} className="!m-0 !text-[28px] !font-semibold !text-[#183f35]">
          {surveys ? "满意度评估" : exams ? "我的考试" : "我的培训"}
        </Typography.Title>
        <Button onClick={() => setReload((n) => n + 1)} loading={loading}>
          刷新
        </Button>
      </div>
      <Card bodyStyle={{ padding: 20 }}><div className="flex flex-wrap gap-3">
        <Input
          aria-label="搜索培训"
          placeholder="搜索培训或考试名称"
          value={keyword}
          onChange={setKeyword}
          className="!w-full sm:!w-auto sm:!min-w-60 sm:!flex-1"
        />
        <Select
          className="!w-full sm:!w-44"
          placeholder="全部学习要求"
          showClear
          value={requirement}
          onChange={(v) => setRequirement(v ? String(v) : undefined)}
          optionList={[
            { value: "REQUIRED", label: "必修" },
            { value: "ELECTIVE", label: "选修" },
          ]}
        />
        {!surveys && (
          <Select
          className="!w-full sm:!w-44"
            placeholder={exams ? "全部考试状态" : "全部学习状态"}
            showClear
            value={status}
            onChange={(v) => setStatus(v ? String(v) : undefined)}
            optionList={Object.entries(exams ? examLabels : learningLabels).map(
              ([value, label]) => ({ value, label }),
            )}
          />
        )}
      </div>
      </Card>
      {error && <Banner type="danger" description={error} />}
      {loading ? (
        <Spin />
      ) : surveys ? (
        <Card bodyStyle={{ padding: 0 }} className="overflow-hidden">
          <Table<LearnerTraining>
            rowKey="id"
            dataSource={visible}
            pagination={{ pageSize: 10, showTotal: true, style: { padding: "16px 20px", margin: 0 } }}
            className="[&_table]:!w-full [&_table]:!min-w-[900px] [&_table]:!table-fixed"
            scroll={{ x: 900 }}
            columns={[
              { title: "培训名称", dataIndex: "name", render: (name: string) => <span className="[overflow-wrap:anywhere] font-medium">{name}</span> },
              { title: "学习要求", width: 100, render: (_, row) => row.requirementType === "REQUIRED" ? "必修" : "选修" },
              { title: "学习进度", width: 130, render: (_, row) => `${row.progress}%` },
              { title: "学习完成时间", width: 190, render: (_, row) => formatTime(row.completedAt) },
              { title: "评估状态", width: 130, render: (_, row) => <Tag color={row.surveySubmitted ? "green" : "orange"}>{row.surveySubmitted ? "已提交" : "待填写"}</Tag> },
              { title: "操作", width: 140, render: (_, row) => <Space>
                <Button theme="borderless" onClick={() => row.surveySubmitted ? setSurveyId(String(row.id)) : router.push(`/training/${row.id}/survey?from=surveys`)}>{row.surveySubmitted ? "查看评估" : "填写评估"}</Button>
              </Space> },
            ]}
          />
        </Card>
      ) : !exams ? (
        <Card bodyStyle={{ padding: 0 }} className="overflow-hidden">
          <Table<LearnerTraining>
            rowKey="id"
            dataSource={visible}
            pagination={{ pageSize: 10, showTotal: true, style: { padding: "16px 20px", margin: 0 } }}
            className="[&_table]:!w-full [&_table]:!min-w-[1200px] [&_table]:!table-fixed [&_.semi-table-thead>tr>th]:whitespace-nowrap"
            scroll={{ x: 1200 }}
            columns={[
              {
                title: "培训名称",
                dataIndex: "name",
                render: (name: string, row) => <div className="min-w-0">
                  <p className="[overflow-wrap:anywhere] font-medium">{name}</p>
                  <p className="mt-1 text-xs text-slate-500">{row.assessmentId ? "培训 + 考试" : "独立培训"}</p>
                </div>,
              },
              { title: "学习要求", width: 100, render: (_, row) => <Tag color={row.requirementType === "REQUIRED" ? "blue" : "grey"}>{row.requirementType === "REQUIRED" ? "必修" : "选修"}</Tag> },
              { title: "学习状态", width: 120, render: (_, row) => <Tag color={row.learningStatus === "COMPLETED" ? "green" : "blue"}>{learningLabels[row.learningStatus]}</Tag> },
              { title: "学习进度", width: 180, render: (_, row) => <div>
                <Progress percent={row.progress} showInfo />
                <p className="mt-1 text-xs text-slate-500">已完成 {row.completedUnits}/{row.totalUnits} 个学习单元</p>
              </div> },
              { title: "有效学习时长", width: 140, render: (_, row) => formatDuration(row.studiedSeconds) },
              { title: "关联考试", width: 230, render: (_, row) => row.assessmentId ? <div>
                <p className="mb-2 [overflow-wrap:anywhere] text-sm">{row.examName}</p>
                <Tag color={row.examStatus === "PASSED" ? "green" : row.examStatus === "FAILED" ? "red" : "blue"}>{examLabels[row.examStatus || ""] || "待考试"}</Tag>
                <p className="mt-1 text-xs text-slate-500">已考 {row.attemptCount} 次{row.score != null ? ` · 成绩 ${row.score}/${row.totalScore}` : ""}</p>
              </div> : "—" },
              { title: "操作", width: 140, render: (_, row) =>
                <Button theme="borderless" onClick={() => router.push(`/learner/trainings/${row.id}`)}>查看详情</Button>
              },
            ]}
          />
        </Card>
      ) : (
        <Card bodyStyle={{ padding: 0 }} className="overflow-hidden">
          <Table<LearnerTraining>
            rowKey="id"
            dataSource={visible}
            pagination={{ pageSize: 10, showTotal: true, style: { padding: "16px 20px", margin: 0 } }}
            className="[&_table]:!w-full [&_table]:!min-w-[1250px] [&_table]:!table-fixed [&_.semi-table-thead>tr>th]:whitespace-nowrap"
            scroll={{ x: 1250 }}
            columns={[
              { title: "考试名称", dataIndex: "examName", render: (name: string) => <span className="[overflow-wrap:anywhere] font-medium">{name}</span> },
              { title: "所属培训", width: 170, render: (_, row) => <div>
                <p className="[overflow-wrap:anywhere]">{row.name}</p>
                <p className="mt-1 text-xs text-slate-500">{learningLabels[row.learningStatus]} · {row.progress}%</p>
              </div> },
              { title: "学习要求", width: 100, render: (_, row) => row.requirementType === "REQUIRED" ? "必修" : "选修" },
              { title: "考试时间", width: 210, render: (_, row) => <div className="text-sm leading-6 tabular-nums">
                <p>{formatTime(row.examStartAt)}</p>
                <p className="text-slate-500">至 {formatTime(row.examEndAt)}</p>
              </div> },
              { title: "考试状态", width: 140, render: (_, row) => <Tag color={row.examStatus === "PASSED" ? "green" : row.examStatus === "FAILED" ? "red" : "blue"}>{examLabels[row.examStatus || ""] || "待考试"}</Tag> },
              { title: "已考次数", width: 100, render: (_, row) => `${row.attemptCount} 次` },
              { title: "成绩", width: 100, render: (_, row) => row.score == null ? "—" : `${row.score}/${row.totalScore}` },
              { title: "操作", width: 230, render: (_, row) => <Space wrap>
                <Button theme="borderless" onClick={() => router.push(learningHref(row))}>{learningAction(row)}</Button>
                <Button theme="borderless" onClick={() => router.push(`/learner/trainings/${row.id}`)}>查看详情</Button>
                {row.canRetake && <Button theme="borderless" onClick={() => router.push(`/training/${row.id}?exam=1`)}>再次考试</Button>}
              </Space> },
            ]}
          />
        </Card>
      )}
    </div>
  );
}
