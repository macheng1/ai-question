"use client";
import { useEffect, useState } from "react";
import { Banner, Card, Table, Tag, Typography, Button } from "@douyinfe/semi-ui";
import {
  getLearningRecords,
  type LearningRecord,
} from "@/features/learner/api";
import { useRouter } from "next/navigation";
import { formatTime } from "@/features/learner/status";
export default function Records() {
  const router = useRouter();
  const [rows, setRows] = useState<LearningRecord[]>([]),
    [error, setError] = useState(""),
    [loading, setLoading] = useState(true);
  useEffect(() => {
    getLearningRecords()
      .then(setRows)
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false));
  }, []);
  return (
    <div className="flex flex-col gap-5">
      <Typography.Title heading={2} className="!m-0 !text-[28px] !font-semibold !text-[#183f35]">培训记录</Typography.Title>
      <Typography.Text type="secondary">
        展示已记录的材料学习进度，学习完成不代表考试通过。
      </Typography.Text>
      {error && <Banner type="danger" description={error} />}
      <Card bodyStyle={{ padding: 0 }} className="overflow-hidden">
      <Table<LearningRecord>
        className="[&_table]:!w-full [&_table]:!min-w-[1120px] [&_table]:!table-fixed"
        rowKey="id"
        loading={loading}
        dataSource={rows}
        pagination={{ pageSize: 10, showTotal: true, style: { padding: "16px 20px", margin: 0 } }}
        scroll={{ x: 1120 }}
        columns={[
          { title: "培训名称", dataIndex: "name", render: (name: string) => <span className="[overflow-wrap:anywhere]">{name}</span> },
          {
            title: "开始学习",
            render: (_, row) => formatTime(row.startedAt),
            width: 190,
            className: "whitespace-nowrap tabular-nums",
          },
          {
            title: "完成时间",
            render: (_, row) => formatTime(row.completedAt),
            width: 190,
            className: "whitespace-nowrap tabular-nums",
          },
          {
            title: "有效学习时长",
            width: 160,
            render: (_, row) =>
              `${Math.floor(row.studiedSeconds / 60)}分${Math.floor(row.studiedSeconds % 60)}秒`,
          },
          {
            title: "材料学习状态",
            width: 160,
            render: (_, row) => (
              <Tag color={row.completed ? "green" : "blue"}>
                {row.completed ? "已完成" : "学习中"}
              </Tag>
            ),
          },
          {
            title: "操作",
            render: (_, row) => (
              <Button
                theme="borderless"
                onClick={() =>
                  router.push(`/learner/trainings/${row.trainingId}`)
                }
              >
                查看详情
              </Button>
            ),
            width: 130,
          },
        ]}
      />
      </Card>
    </div>
  );
}
