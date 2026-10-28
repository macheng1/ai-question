"use client";
import { IconPlus } from "@douyinfe/semi-icons";
import { useEffect, useState } from "react";
import Link from "next/link";
import "@douyinfe/semi-ui/react19-adapter";
import { Button, Input, Modal, Toast, Table } from "@douyinfe/semi-ui";
import { getTrainings, deleteTraining, type Training } from "@/features/trainings/api";
import { TrainingShell } from "@/features/trainings/components/TrainingShell";
export default function TrainingsPage() {
  const [items, setItems] = useState<Training[]>([]);
  const [keyword, setKeyword] = useState("");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const load = () => { setLoading(true); setError(""); getTrainings().then(setItems).catch(e => setError(e.message)).finally(() => setLoading(false)); };
  useEffect(() => { load(); }, []);
  const remove = (item: Training) => Modal.confirm({ title: "删除培训", content: `确定删除“${item.name}”吗？此操作不可恢复。`, onOk: async () => { try { await deleteTraining(item.id); Toast.success("已删除"); load(); } catch(e) { Toast.error(e instanceof Error ? e.message : "删除失败"); } } });
  const visible = items.filter(item => item.name.includes(keyword.trim()));
  return <TrainingShell><div className="flex items-center justify-between"><div><h1 className="text-2xl font-bold text-slate-800">培训管理</h1><p className="mt-2 text-sm text-slate-500">维护培训信息和满意度评估，查看参与反馈</p></div><Link href="/profile/trainings/new" className="rounded-lg bg-orange-500 px-4 py-2.5 text-sm font-semibold text-white">{<IconPlus />} 新建培训</Link></div>
  <div className="my-6 rounded-xl border border-slate-200 bg-white p-4"><Input placeholder="搜索培训名称" value={keyword} onChange={setKeyword} /></div>
  {loading ? <p>加载中…</p> : error ? <div role="alert">{error}<Button onClick={load}>重试</Button></div> : !visible.length ? <div className="rounded-2xl border border-slate-200 bg-white p-12 text-center text-slate-500">{items.length ? "没有匹配的培训" : "还没有培训，点击右上角新建"}</div> : <div className="overflow-x-auto rounded-2xl border border-slate-200 bg-white"><Table<Training> rowKey="id" dataSource={visible} pagination={false} scroll={{ x: 760 }} columns={[
    { title: "培训名称", dataIndex: "name", width: 200 },
    { title: "评估题数", render: (_, item) => item.questions.length },
    { title: "评估状态", render: (_, item) => <span className={item.surveyEnabled ? "text-teal-700" : "text-slate-400"}>{item.surveyEnabled ? "已开启" : "已关闭"}</span> },
    { title: "反馈", dataIndex: "responseCount" },
    { title: "操作", width: 300, render: (_, item) => <div className="flex flex-wrap gap-3 text-teal-700"><Link href={`/profile/trainings/${item.id}/edit`}>编辑 / 预览</Link><Link href={`/profile/trainings/${item.id}/responses`}>查看反馈</Link>{item.surveyEnabled && <Link href={`/training/${item.id}/survey`}>填写评估</Link>}<Button theme="borderless" type="danger" disabled={!!item.responseCount} onClick={() => remove(item)}>删除</Button></div> }
  ]} /></div>}</TrainingShell>;
}
