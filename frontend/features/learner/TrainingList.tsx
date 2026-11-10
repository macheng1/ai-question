"use client";
import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import {
  Banner,
  Button,
  Card,
  Empty,
  Input,
  Select,
  Spin,
  Tag,
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
} from "./status";

export function TrainingList({
  surveys = false,
  exams = false,
}: {
  surveys?: boolean;
  exams?: boolean;
}) {
  const router = useRouter();
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
      <div className="flex items-center justify-between gap-3">
        <Typography.Title heading={2}>
          {surveys ? "满意度评估" : exams ? "我的考试" : "我的培训"}
        </Typography.Title>
        <Button onClick={() => setReload((n) => n + 1)} loading={loading}>
          刷新
        </Button>
      </div>
      <div className="flex flex-wrap gap-3">
        <Input
          aria-label="搜索培训"
          placeholder="搜索培训或考试名称"
          value={keyword}
          onChange={setKeyword}
          className="!w-full sm:!w-72"
        />
        <Select
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
      {error && <Banner type="danger" description={error} />}
      {loading ? (
        <Spin />
      ) : !visible.length ? (
        <Empty description="暂无符合条件的任务" />
      ) : (
        <div className="grid gap-4 xl:grid-cols-2">
          {visible.map((row) => (
            <Card key={row.id}>
              <div className="flex h-full flex-col gap-4">
                <Space wrap>
                  <Tag
                    color={row.requirementType === "REQUIRED" ? "blue" : "grey"}
                  >
                    {row.requirementType === "REQUIRED" ? "必修" : "选修"}
                  </Tag>
                  <Tag>{row.assessmentId ? "培训 + 考试" : "独立培训"}</Tag>
                  <Tag
                    color={
                      row.learningStatus === "COMPLETED" ? "green" : "blue"
                    }
                  >
                    {learningLabels[row.learningStatus]}
                  </Tag>
                  {surveys && (
                    <Tag color={row.surveySubmitted ? "green" : "orange"}>
                      {row.surveySubmitted ? "已提交" : "待填写"}
                    </Tag>
                  )}
                </Space>
                <Typography.Title heading={4} className="!break-words">
                  {exams ? row.examName : row.name}
                </Typography.Title>
                <p className="line-clamp-2 break-words text-sm leading-6 text-slate-500">
                  {exams
                    ? `所属培训：${row.name}`
                    : row.description || "请进入培训查看学习内容"}
                </p>
                <div>
                  <div className="mb-2 flex justify-between text-sm">
                    <span>学习进度</span>
                    <span>{row.progress}%</span>
                  </div>
                  <Progress percent={row.progress} />
                  <p className="mt-2 text-xs text-slate-500">
                    有效学习 {formatDuration(row.studiedSeconds)} · 已完成{" "}
                    {row.completedUnits}/{row.totalUnits} 个学习单元
                  </p>
                </div>
                {row.assessmentId && (
                  <div className="rounded-md bg-[var(--semi-color-fill-0)] p-3 text-sm leading-6">
                    <div className="flex flex-wrap items-center gap-2">
                      <span className="break-words">{row.examName}</span>
                      <Tag
                        color={
                          row.examStatus === "PASSED"
                            ? "green"
                            : row.examStatus === "FAILED"
                              ? "red"
                              : "blue"
                        }
                      >
                        {examLabels[row.examStatus || ""] || "待考试"}
                      </Tag>
                      {row.canRetake && <Tag color="orange">可补考 / 重考</Tag>}
                    </div>
                    <p className="mt-2 text-xs text-slate-500">
                      {formatTime(row.examStartAt)} 至{" "}
                      {formatTime(row.examEndAt)}
                    </p>
                    <p>
                      已考 {row.attemptCount} 次
                      {row.score != null
                        ? ` · 成绩 ${row.score}/${row.totalScore}`
                        : ""}
                    </p>
                  </div>
                )}
                <Space wrap className="mt-auto">
                  {surveys ? (
                    <Button
                      theme="solid"
                      onClick={() => router.push(`/training/${row.id}/survey`)}
                    >
                      {row.surveySubmitted ? "查看评估" : "填写评估"}
                    </Button>
                  ) : (
                    <Button
                      theme="solid"
                      onClick={() => router.push(`/training/${row.id}`)}
                    >
                      {learningAction(row)}
                    </Button>
                  )}
                  <Button
                    onClick={() => router.push(`/learner/trainings/${row.id}`)}
                  >
                    查看详情
                  </Button>
                  {!surveys && row.canRetake && (
                    <Button onClick={() => router.push(`/training/${row.id}`)}>
                      再次考试
                    </Button>
                  )}
                </Space>
              </div>
            </Card>
          ))}
        </div>
      )}
    </div>
  );
}
