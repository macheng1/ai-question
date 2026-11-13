"use client";
import { use, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import {
  Banner,
  Button,
  Card,
  Progress,
  Space,
  Spin,
  Table,
  Tag,
  Typography,
} from "@douyinfe/semi-ui";
import { SurveyFeedbackModal } from "@/features/learner/SurveyFeedbackModal";
import { IconArrowLeft } from "@douyinfe/semi-icons";
import {
  getLearnerTrainingDetail,
  type LearnerTrainingDetail,
  type LearningMaterialRecord,
  type ExamRecord,
} from "@/features/learner/api";
import {
  formatTime,
  formatDuration,
  learningLabels,
  examLabels,
  learningAction,
  learningHref,
} from "@/features/learner/status";

export default function TrainingDetail({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = use(params);
  const router = useRouter();
  const [data, setData] = useState<LearnerTrainingDetail>();
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);
  const [reload, setReload] = useState(0);
  useEffect(() => {
    let active = true;
    setLoading(true);
    setError("");
    getLearnerTrainingDetail(id)
      .then((d) => {
        if (active) setData(d);
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
  }, [id, reload]);
  const [surveyOpen, setSurveyOpen] = useState(false);
  const row = data?.training;
  return (
    <div className="flex min-w-0 flex-col gap-5">
      <SurveyFeedbackModal id={surveyOpen ? id : undefined} onClose={() => setSurveyOpen(false)} />
      <div className="flex justify-between gap-3">
        <Button
          icon={<IconArrowLeft />}
          onClick={() => router.push("/learner")}
        >
          我的培训
        </Button>
        <Button loading={loading} onClick={() => setReload((n) => n + 1)}>
          刷新
        </Button>
      </div>
      {error ? (
        <Banner type="danger" description={error} />
      ) : loading ? (
        <Spin />
      ) : (
        row &&
        data && (
          <>
            <Card>
              <div className="flex flex-col gap-4">
                <Typography.Title
                  heading={3}
                  className="!m-0 !break-words !text-[var(--brand-700,#17483e)]"
                >
                  {row.name}
                </Typography.Title>
                <Space wrap>
                  <Tag
                    color={
                      row.learningStatus === "COMPLETED" ? "green" : "blue"
                    }
                  >
                    {learningLabels[row.learningStatus]}
                  </Tag>
                  <Tag>
                    {row.requirementType === "ELECTIVE" ? "选修" : "必修"}
                  </Tag>
                  {row.assessmentId && (
                    <Tag>{examLabels[row.examStatus || ""]}</Tag>
                  )}
                </Space>
                {row.description && (
                  <p className="whitespace-pre-wrap break-words text-sm leading-6 text-slate-500">
                    {row.description}
                  </p>
                )}
                <div className="rounded-lg bg-[var(--semi-color-fill-0)] p-4">
                  <div className="mb-3 flex items-center justify-between text-sm">
                    <span>学习进度</span>
                    <span className="font-semibold tabular-nums">
                      {row.progress}%
                    </span>
                  </div>
                  <Progress percent={row.progress} />
                  <div className="mt-4 grid gap-4 sm:grid-cols-2">
                    <DetailValue
                      label="有效学习时长"
                      value={formatDuration(row.studiedSeconds)}
                    />
                    <DetailValue
                      label="已完成学习单元"
                      value={`${row.completedUnits} / ${row.totalUnits}（视频、文档页）`}
                    />
                  </div>
                </div>
                <div className="grid gap-4 border-t border-[var(--semi-color-border)] pt-4 sm:grid-cols-3">
                  <DetailValue
                    label="分配时间"
                    value={formatTime(row.assignedAt)}
                  />
                  <DetailValue
                    label="开始学习"
                    value={formatTime(row.startedAt)}
                  />
                  <DetailValue
                    label="学习完成"
                    value={formatTime(row.completedAt)}
                  />
                </div>
                <Space wrap>
                  <Button
                    theme="solid"
                    onClick={() => router.push(learningHref(row))}
                  >
                    {learningAction(row)}
                  </Button>
                  {row.canRetake && (
                    <Button onClick={() => router.push(`/training/${row.id}?exam=1`)}>
                      再次考试
                    </Button>
                  )}
                  {row.surveyEnabled && (
                    <Button
                      onClick={() => row.surveySubmitted ? setSurveyOpen(true) : router.push(`/training/${row.id}/survey?from=detail`)}
                    >
                      {row.surveySubmitted ? "查看评估" : "填写满意度评估"}
                    </Button>
                  )}
                </Space>
              </div>
            </Card>
            <Card title="材料学习明细" bodyStyle={{ padding: 0 }}>
              <Table<LearningMaterialRecord>
                rowKey={(item) =>
                  item ? `${item.materialType}-${item.id}` : ""
                }
                dataSource={data.materials}
                pagination={false}
                className="[&_.semi-table-thead>tr>th]:whitespace-nowrap [&_.semi-table-tbody>tr>td]:align-middle [&_table]:!w-full [&_table]:!table-fixed"
                scroll={{ x: 900 }}
                columns={[
                  {
                    title: "材料",
                    dataIndex: "title",
                    render: (title: string) => (
                      <span className="block break-all text-sm leading-6">
                        {title}
                      </span>
                    ),
                  },
                  {
                    title: "进度",
                    width: 90,
                    render: (_, item) =>
                      `${item.completedUnits}/${item.totalUnits}`,
                  },
                  {
                    title: "有效学习时长",
                    width: 140,
                    render: (_, item) => formatDuration(item.studiedSeconds),
                  },
                  {
                    title: "状态",
                    width: 110,
                    render: (_, item) => (
                      <Tag
                        color={
                          item.completedUnits === item.totalUnits
                            ? "green"
                            : "grey"
                        }
                      >
                        {item.completedUnits === item.totalUnits
                          ? "已完成"
                          : item.studiedSeconds > 0
                            ? "学习中"
                            : "未开始"}
                      </Tag>
                    ),
                  },
                  {
                    title: "完成时间",
                    width: 190,
                    render: (_, item) => formatTime(item.completedAt),
                  },
                ]}
              />
            </Card>
            {row.assessmentId && (
              <Card title="关联考试">
                <div className="flex flex-col gap-4">
                  <Typography.Title heading={4}>
                    {row.examName}
                  </Typography.Title>
                  <div className="grid gap-4 rounded-lg bg-[var(--semi-color-fill-0)] p-4 sm:grid-cols-2 xl:grid-cols-4">
                    <DetailValue
                      label="考试状态"
                      value={examLabels[row.examStatus || ""] || "—"}
                    />
                    <DetailValue
                      label="开始时间"
                      value={formatTime(row.examStartAt)}
                    />
                    <DetailValue
                      label="结束时间"
                      value={formatTime(row.examEndAt)}
                    />
                    <DetailValue
                      label="答题次数"
                      value={`已考 ${row.attemptCount} 次 · 剩余 ${row.remainingAttempts || 0} 次`}
                    />
                  </div>
                  <Typography.Text type="secondary">
                    每次考试分别保留记录。成绩是否公布、能否补考均按考试设置执行。
                  </Typography.Text>
                  <Table<ExamRecord>
                    rowKey="id"
                    dataSource={data.attempts}
                    pagination={{ pageSize: 5 }}
                    className="[&_.semi-table-thead>tr>th]:whitespace-nowrap [&_table]:!w-full [&_table]:!table-fixed"
                    scroll={{ x: 800 }}
                    columns={[
                      { title: "次数", dataIndex: "attemptNo", width: 70 },
                      {
                        title: "结果",
                        width: 110,
                        render: (_, a) =>
                          a.status !== "SUBMITTED"
                            ? "答题中"
                            : a.passed == null
                              ? "暂未公布"
                              : a.passed
                                ? "通过"
                                : "未通过",
                      },
                      {
                        title: "成绩",
                        width: 100,
                        render: (_, a) =>
                          a.score == null ? "—" : `${a.score}/${a.totalScore}`,
                      },
                      {
                        title: "开始时间",
                        width: 180,
                        render: (_, a) => formatTime(a.startedAt),
                      },
                      {
                        title: "交卷时间",
                        width: 180,
                        render: (_, a) => formatTime(a.submittedAt),
                      },
                      {
                        title: "用时",
                        width: 130,
                        render: (_, a) =>
                          a.durationSeconds == null
                            ? "—"
                            : formatDuration(a.durationSeconds),
                      },
                    ]}
                  />
                </div>
              </Card>
            )}
          </>
        )
      )}
    </div>
  );
}

function DetailValue({ label, value }: { label: string; value: string }) {
  return (
    <div className="min-w-0">
      <p className="mb-1 text-xs text-[var(--semi-color-text-2)]">{label}</p>
      <p className="break-words text-sm font-medium leading-6 tabular-nums">
        {value}
      </p>
    </div>
  );
}
