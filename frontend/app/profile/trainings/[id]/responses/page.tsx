"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { useParams } from "next/navigation";
import { Banner, Button, Card, Table, Tag, Typography } from "@douyinfe/semi-ui";
import { IconArrowLeft } from "@douyinfe/semi-icons";
import { getTraining, getResponses, type Training, type SurveyResponse } from "@/features/trainings/api";
import { ProfileShell } from "@/components/layout/ProfileShell";
import { useCompactViewport } from "@/lib/hooks/useCompactViewport";

const dateText = (value: string) => Number.isFinite(Date.parse(value))
  ? new Date(value).toLocaleString("zh-CN", { timeZone: "Asia/Shanghai", hour12: false }) : "—";

export default function ResponsesPage() {
  const { id } = useParams<{ id: string }>();
  const compact = useCompactViewport();
  const [training, setTraining] = useState<Training>();
  const [items, setItems] = useState<SurveyResponse[]>([]);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);
  const [expanded, setExpanded] = useState<(string | number)[]>([]);
  const requestVersion = useRef(0);
  const load = useCallback(async () => {
    const version = ++requestVersion.current;
    setLoading(true); setError("");
    try {
      const [course, responses] = await Promise.all([getTraining(id), getResponses(id)]);
      if (version !== requestVersion.current) return;
      setTraining(course); setItems(responses);
    } catch (e) {
      if (version === requestVersion.current) setError(e instanceof Error ? e.message : "反馈加载失败，请重试");
    } finally { if (version === requestVersion.current) setLoading(false); }
  }, [id]);
  useEffect(() => { void load(); return () => { requestVersion.current += 1; }; }, [load]);
  const ratingQuestions = training?.questions.filter(q => q.type === "RATING") || [];

  return <ProfileShell>
    <Typography.Text link={{ href: "/profile/trainings" }} icon={<IconArrowLeft size="small" />} className="!inline-flex !min-h-8 !items-center !text-sm">返回培训列表</Typography.Text>
    <div className="mt-5 flex flex-wrap items-center justify-between gap-3">
      <Typography.Title heading={1} className="!m-0 !text-[28px] !font-semibold !text-[#183f35]">评估反馈</Typography.Title>
      <Button theme="borderless" loading={loading} onClick={() => void load()}>刷新数据</Button>
    </div>
    <p className="mt-2 break-words text-sm text-slate-500">{training?.name || "查看培训满意度评估与学员反馈"}</p>
    {error && <Banner className="mt-5" type="danger" description={error} />}
    {!!ratingQuestions.length && <div className="my-5 grid gap-4" style={{ gridTemplateColumns: "repeat(auto-fit, minmax(min(100%, 320px), 1fr))" }}>
      {ratingQuestions.map((q, index) => {
        const values = items.map(r => Number(r.answers[q.id])).filter(n => n >= 1 && n <= 5);
        const average = values.length ? (values.reduce((sum, value) => sum + value, 0) / values.length).toFixed(1) : "—";
        return <Card key={q.id} bodyStyle={{ padding: 20 }} className="min-w-0">
          <div className="flex items-start justify-between gap-3"><p className="min-w-0 break-words text-sm font-medium text-slate-700">{index + 1}. {q.title}</p><Tag className="shrink-0" type="light">评分题</Tag></div>
          <div className="mt-4 flex flex-wrap items-end justify-between gap-3">
            <div><p className="mb-1 text-xs text-slate-500">平均评分</p><span className="text-3xl font-semibold text-teal-700">{average}</span><span className="ml-2 text-sm text-slate-400">/ 5 分</span></div>
            <span className="text-sm text-slate-500">{values.length ? `${values.length} 人作答` : "暂无评分"}</span>
          </div>
        </Card>;
      })}
    </div>}
    <Card className="!mt-5 overflow-hidden" bodyStyle={{ padding: 0 }}>
      <div className="flex flex-wrap items-center justify-between gap-2 border-b border-solid border-slate-100 px-5 py-4">
        <div className="flex items-center gap-2"><strong className="text-base text-slate-800">反馈明细</strong><Tag type="light">{items.length} 人提交</Tag></div>
        <span className="text-xs text-slate-500">按提交时间倒序排列</span>
      </div>
      <Table<SurveyResponse> rowKey="id" loading={loading} dataSource={items} style={{ width: "100%" }} scroll={compact ? { x: 560 } : undefined}
        empty="暂无反馈，学员提交评估后将在这里显示"
        pagination={{ pageSize: 10, showTotal: !compact, style: { padding: "16px 20px", margin: 0 } }}
        expandedRowKeys={expanded} onExpandedRowsChange={rows => setExpanded((rows || []).flatMap(row => "id" in row ? [row.id] : []))}
        columns={[
          { title: "姓名", dataIndex: "respondentName", width: "35%" },
          { title: "提交时间", width: "45%", render: (_, row) => dateText(row.submittedAt) },
          { title: "操作", width: "20%", render: (_, row) => <Button theme="borderless" onClick={() => setExpanded(current => current.includes(row.id) ? current.filter(key => key !== row.id) : [...current, row.id])}>{expanded.includes(row.id) ? "收起反馈" : "查看反馈"}</Button> },
        ]}
        expandedRowRender={item => item && <div className="grid gap-4 px-5 py-4">
          {training?.questions.map((q, index) => <div key={q.id} className="min-w-0"><p className="break-words text-sm text-slate-500">{index + 1}. {q.title}</p><p className="mt-1 whitespace-pre-wrap break-words text-sm text-slate-800">{item.answers[q.id] || "未填写"}{q.type === "RATING" && item.answers[q.id] ? " 分" : ""}</p></div>)}
        </div>}
      />
    </Card>
  </ProfileShell>;
}
