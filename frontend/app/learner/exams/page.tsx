"use client";
import { useEffect, useState } from "react";
import { Banner, Table, Typography, Tabs, TabPane } from "@douyinfe/semi-ui";
import { getExamRecords, type ExamRecord } from "@/features/learner/api";
import { TrainingList } from "@/features/learner/TrainingList";
export default function Exams() {
  const [rows, setRows] = useState<ExamRecord[]>([]),
    [error, setError] = useState(""),
    [loading, setLoading] = useState(true);
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
          <Typography.Title heading={2}>考试记录</Typography.Title>
          {error && <Banner type="danger" description={error} />}
          <Table<ExamRecord>
            rowKey="id"
            loading={loading}
            dataSource={rows}
            pagination={{ pageSize: 10 }}
            scroll={{ x: 880 }}
            columns={[
              { title: "考试名称", dataIndex: "name", width: 260 },
              { title: "次数", dataIndex: "attemptNo", width: 70 },
              {
                title: "成绩",
                width: 110,
                render: (_, row) =>
                  row.status !== "SUBMITTED"
                    ? "未交卷"
                    : row.score === null
                      ? "暂未公布"
                      : `${row.score}/${row.totalScore}`,
              },
              {
                title: "结果",
                width: 100,
                render: (_, row) =>
                  row.status !== "SUBMITTED"
                    ? "答题中"
                    : row.passed === null
                      ? "暂未公布"
                      : row.passed
                        ? "通过"
                        : "未通过",
              },
              { title: "提交时间", dataIndex: "submittedAt", width: 200 },
              {
                title: "考试用时",
                width: 140,
                render: (_, row) =>
                  row.durationSeconds == null
                    ? "—"
                    : `${Math.floor(row.durationSeconds / 60)}分${row.durationSeconds % 60}秒`,
              },
            ]}
          />
        </div>
      </TabPane>
    </Tabs>
  );
}
