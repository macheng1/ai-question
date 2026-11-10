"use client";
import { useEffect, useState } from "react";
import { Banner, Card, Empty, Spin, Typography } from "@douyinfe/semi-ui";
import { getMistakes, type Mistake } from "@/features/learner/api";
const answerLabel = (answer: string) =>
  answer === "TRUE" ? "正确" : answer === "FALSE" ? "错误" : answer || "未作答";

export default function Mistakes() {
  const [rows, setRows] = useState<Mistake[]>([]),
    [error, setError] = useState(""),
    [loading, setLoading] = useState(true);
  useEffect(() => {
    getMistakes()
      .then(setRows)
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false));
  }, []);
  return (
    <div className="flex flex-col gap-5">
      <Typography.Title heading={2}>我的错题</Typography.Title>
      <Typography.Text type="secondary">
        仅展示已公布成绩的考试错题。
      </Typography.Text>
      {error && <Banner type="danger" description={error} />}
      {loading ? (
        <Spin />
      ) : !rows.length ? (
        <Empty description="暂无错题" />
      ) : (
        rows.map((row) => (
          <Card key={row.id}>
            <Typography.Text type="secondary">{row.examName}</Typography.Text>
            <h3 className="my-4 whitespace-pre-wrap break-words font-semibold">
              {row.stem}
            </h3>
            <ul className="mb-4 space-y-2">
              {row.options.map((option) => (
                <li key={option.optionKey} className="whitespace-pre-wrap break-words">
                  {option.optionKey}. {option.optionText}
                </li>
              ))}
            </ul>
            <p className="mb-2 text-red-600">
              我的答案：{answerLabel(row.userAnswer)}
            </p>
            <p>正确答案：{answerLabel(row.correctAnswer)}</p>
            {row.analysis && (
              <p className="mt-3 whitespace-pre-wrap break-words text-slate-500">
                解析：{row.analysis}
              </p>
            )}
          </Card>
        ))
      )}
    </div>
  );
}
