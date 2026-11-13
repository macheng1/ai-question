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
      <Typography.Title heading={2} className="!m-0 !text-[28px] !font-semibold !text-[#183f35]">我的错题</Typography.Title>
      <Typography.Text type="secondary">
        仅展示已公布成绩的考试错题。
      </Typography.Text>
      {error && <Banner type="danger" description={error} />}
      {loading ? (
        <Spin />
      ) : !rows.length ? (
        <Card><Empty description="暂无错题" className="py-8" /></Card>
      ) : (
        rows.map((row) => (
          <Card key={row.id}>
            <Typography.Text type="secondary">{row.examName}</Typography.Text>
            <h3 className="my-4 whitespace-pre-wrap [overflow-wrap:anywhere] font-semibold">
              {row.stem}
            </h3>
            <ul className="mb-4 space-y-2">
              {row.options.map((option) => (
                <li key={option.optionKey} className="whitespace-pre-wrap [overflow-wrap:anywhere]">
                  {option.optionKey.trim().toUpperCase() === "TRUE"
                    ? "A. 正确"
                    : option.optionKey.trim().toUpperCase() === "FALSE"
                      ? "B. 错误"
                      : `${option.optionKey}. ${option.optionText}`}
                </li>
              ))}
            </ul>
            <p className="mb-2 text-red-600">
              我的答案：{answerLabel(row.userAnswer)}
            </p>
            <p>正确答案：{answerLabel(row.correctAnswer)}</p>
            {row.analysis && (
              <p className="mt-3 whitespace-pre-wrap [overflow-wrap:anywhere] text-slate-500">
                解析：{row.analysis}
              </p>
            )}
          </Card>
        ))
      )}
    </div>
  );
}
