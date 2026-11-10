"use client";
import { use, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import {
  Banner,
  Button,
  Card,
  Descriptions,
  Progress,
  Space,
  Spin,
  Table,
  Tag,
  Typography,
} from "@douyinfe/semi-ui";
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
  const row = data?.training;
  return (
    <div className="flex min-w-0 flex-col gap-5">
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
                <Typography.Title heading={2} className="!break-words">
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
                <p className="whitespace-pre-wrap break-words leading-6">
                  {row.description}
                </p>
                <Progress percent={row.progress} showInfo />
                <Descriptions
                  row
                  data={[
                    { key: "分配时间", value: formatTime(row.assignedAt) },
                    { key: "开始学习", value: formatTime(row.startedAt) },
                    { key: "学习完成", value: formatTime(row.completedAt) },
                    {
                      key: "有效学习时长",
                      value: formatDuration(row.studiedSeconds),
                    },
                    {
                      key: "学习单元",
                      value: `${row.completedUnits} / ${row.totalUnits}（视频、文档页）`,
                    },
                  ]}
                />
                <Space wrap>
                  <Button
                    theme="solid"
                    onClick={() => router.push(`/training/${row.id}`)}
                  >
                    {learningAction(row)}
                  </Button>
                  {row.canRetake && (
                    <Button onClick={() => router.push(`/training/${row.id}`)}>
                      再次考试
                    </Button>
                  )}
                  {row.surveyEnabled && (
                    <Button
                      onClick={() => router.push(`/training/${row.id}/survey`)}
                    >
                      {row.surveySubmitted ? "查看评估" : "填写满意度评估"}
                    </Button>
                  )}
                </Space>
              </div>
            </Card>
            <Card title="材料学习明细" bodyStyle={{ padding: 0 }}>
              <Table<LearningMaterialRecord>
                rowKey={(item) => item ? `${item.materialType}-${item.id}` : ""}
                dataSource={data.materials}
                pagination={false}
                scroll={{ x: 660 }}
                columns={[
                  { title: "材料", dataIndex: "title", width: 220 },
                  {
                    title: "进度",
                    width: 120,
                    render: (_, item) =>
                      `${item.completedUnits}/${item.totalUnits}`,
                  },
                  {
                    title: "有效学习时长",
                    width: 150,
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
                  <Descriptions
                    row
                    data={[
                      { key: "状态", value: examLabels[row.examStatus || ""] },
                      {
                        key: "考试时间",
                        value: `${formatTime(row.examStartAt)} 至 ${formatTime(row.examEndAt)}`,
                      },
                      {
                        key: "答题次数",
                        value: `已考 ${row.attemptCount} 次，剩余 ${row.remainingAttempts || 0} 次`,
                      },
                    ]}
                  />
                  <Typography.Text type="secondary">
                    每次考试分别保留记录。成绩是否公布、能否补考均按考试设置执行。
                  </Typography.Text>
                  <Table<ExamRecord>
                    rowKey="id"
                    dataSource={data.attempts}
                    pagination={{ pageSize: 5 }}
                    scroll={{ x: 740 }}
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
