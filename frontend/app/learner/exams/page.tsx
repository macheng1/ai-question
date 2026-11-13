"use client";
import Link from "next/link";
import { useEffect, useState } from "react";
import { Banner, Card, Table, Tag, Typography, Tabs, TabPane } from "@douyinfe/semi-ui";
import { getExamRecords, getLearnerTrainings, type ExamRecord } from "@/features/learner/api";
import { TrainingList } from "@/features/learner/TrainingList";
const dateTime = (value?: string) => value && Number.isFinite(Date.parse(value))
  ? new Intl.DateTimeFormat("zh-CN", {timeZone:"Asia/Shanghai",year:"numeric",month:"2-digit",day:"2-digit",hour:"2-digit",minute:"2-digit",second:"2-digit",hourCycle:"h23"}).format(new Date(value)).replaceAll("/", "-") : "—";
export default function Exams() {
  const [rows, setRows] = useState<ExamRecord[]>([]),
    [error, setError] = useState(""),
    [loading, setLoading] = useState(true);
  const [trainingIds, setTrainingIds] = useState<Record<number, number>>({});
  useEffect(() => {
    getLearnerTrainings().then(items => setTrainingIds(Object.fromEntries(items.filter(item => item.assessmentId).map(item => [item.assessmentId!, item.id])))).catch(() => {});
  }, []);
  useEffect(() => {
    getExamRecords()
      .then(setRows)
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false));
  }, []);
  return (
    <Tabs type="line" defaultActiveKey="tasks">
      <TabPane tab="考试任务" itemKey="tasks">
        <div className="pt-5">
          <TrainingList exams />
        </div>
      </TabPane>
      <TabPane tab="答题历史" itemKey="history">
        <div className="flex flex-col gap-5 pt-5">
          <Typography.Title heading={2} className="!m-0 !text-[28px] !font-semibold !text-[#183f35]">考试记录</Typography.Title>
          {error && <Banner type="danger" description={error} />}
          <Card bodyStyle={{padding:0}} className="overflow-hidden">
          <Table<ExamRecord>
            style={{width:"100%"}}
            className="[&_table]:!w-full [&_table]:!table-fixed"
            rowKey="id"
            loading={loading}
            dataSource={rows}
            pagination={{ pageSize: 10, showTotal: true, style: {padding:"16px 20px",margin:0} }}
            scroll={{ x: 880 }}
            columns={[
              { title: "操作", width: 110, render: (_, row) => row.status === "SUBMITTED" ? <Link href={`/kaoshi/${row.taskId}/result?attemptId=${row.id}${trainingIds[row.taskId] ? `&trainingId=${trainingIds[row.taskId]}` : ""}`}>查看结果</Link> : "—" },
              { title: "考试名称", dataIndex: "name", width: "30%", render: (_,row) => <span className="break-all">{row.name}</span> },
              { title: "次数", dataIndex: "attemptNo", width: "10%", className:"whitespace-nowrap" },
              {
                title: "成绩",
                width: "13%", className:"whitespace-nowrap",
                render: (_, row) =>
                  row.status !== "SUBMITTED"
                    ? "未交卷"
                    : row.score === null
                      ? "暂未公布"
                      : `${row.score}/${row.totalScore}`,
              },
              {
                title: "结果",
                width: "12%", className:"whitespace-nowrap",
                render: (_, row) =>
                  row.status !== "SUBMITTED"
                    ? "答题中"
                    : row.passed === null
                      ? "暂未公布"
                      : row.passed
                        ? <Tag color="green">通过</Tag>
                        : <Tag color="orange">未通过</Tag>,
              },
              { title: "提交时间", width: "22%", className:"whitespace-nowrap", render:(_,row)=>dateTime(row.submittedAt) },
              {
                title: "考试用时",
                width: "13%", className:"whitespace-nowrap",
                render: (_, row) =>
                  row.durationSeconds == null
                    ? "—"
                    : `${Math.floor(row.durationSeconds / 60)}分${row.durationSeconds % 60}秒`,
              },
            ]}
          />
          </Card>
        </div>
      </TabPane>
    </Tabs>
  );
}
