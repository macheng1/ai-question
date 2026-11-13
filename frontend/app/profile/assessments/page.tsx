"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { Button, Card, Input, Tag, Typography } from "@douyinfe/semi-ui";
import { getAssessments, type Assessment } from "@/features/assessments/api";
import { ProfileShell } from "@/components/layout/ProfileShell";

/** Compatibility entry for historical exam records; new exams are configured in trainings. */
export default function HistoricalAssessmentsPage() {
  const router = useRouter();
  const [items, setItems] = useState<Assessment[]>([]);
  const [keyword, setKeyword] = useState("");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const load = async (search = "") => {
    setLoading(true); setError("");
    try { setItems((await getAssessments({ keyword: search.trim() || undefined })).records); }
    catch (e) { setError(e instanceof Error ? e.message : "历史考试加载失败"); }
    finally { setLoading(false); }
  };
  useEffect(() => { void load(); }, []);
  return <ProfileShell>
    <Button theme="borderless" onClick={() => router.push("/profile/trainings")}>返回培训管理</Button>
    <Typography.Title heading={1} className="!my-5 !text-[28px] !font-semibold !text-[#183f35]">历史考试</Typography.Title>
    <p className="mb-5 text-sm text-slate-500">保留历史考试及成绩。新考试和考试设置统一在培训管理中处理。</p>
    <div className="mb-5 flex gap-3"><Input aria-label="搜索历史考试" placeholder="搜索考试名称、试卷名称" value={keyword} onChange={setKeyword} onEnterPress={() => load(keyword)} /><Button disabled={loading} onClick={() => load(keyword)}>搜索</Button></div>
    {loading ? <p>加载中…</p> : error ? <p role="alert">{error}<Button onClick={() => load(keyword)}>重试</Button></p> : <div className="flex flex-col gap-4">
      {items.map(item => <Card key={item.id}>
        <div className="flex flex-wrap items-center justify-between gap-4">
          <div><Typography.Title heading={3} className="!m-0 !text-lg">{item.name}</Typography.Title><p className="mt-2 text-sm text-slate-500">试卷：{item.paperName}</p><p className="mt-2 text-sm text-slate-500">{item.startAt.replace("T", " ").slice(0, 16)} 至 {item.endAt.replace("T", " ").slice(0, 16)}</p></div>
          <div className="flex flex-wrap items-center gap-2"><Tag>历史数据</Tag><Button theme="borderless" onClick={() => router.push(`/profile/assessments/${item.id}/scores`)}>成绩</Button><Button theme="borderless" onClick={() => router.push(`/profile/assessments/${item.id}/statistics`)}>统计</Button></div>
        </div>
      </Card>)}
      {!items.length && <Card>没有匹配的历史考试</Card>}
    </div>}
  </ProfileShell>;
}
