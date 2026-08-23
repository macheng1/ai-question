"use client";

import { useEffect, useMemo, useState } from "react";
import Link from "next/link";
import "@douyinfe/semi-ui/react19-adapter";
import { Button, Input, Modal, Select, Toast } from "@douyinfe/semi-ui";
import { PageShell } from "@/components/layout/PageShell";
import { ProfileSidebar } from "@/components/layout/ProfileSidebar";
import { ArrangeAssessmentModal } from "@/features/assessments/components/ArrangeAssessmentModal";
import {
  copyPaper,
  createPaper,
  deletePaper,
  getPapers,
  updatePaper,
  type Paper,
  type SavePaper,
} from "@/features/papers/api";

const modeOptions = [
  { label: "固定组卷", value: "FIXED" },
  { label: "随机组卷", value: "RANDOM" },
];

const modeLabel = (mode: string) =>
  mode.toUpperCase() === "RANDOM" ? "随机组卷" : "固定组卷";

/** 试卷管理入口。在线自测使用 /self-test，企业试卷管理使用本页面。 */
export default function PapersPage() {
  const [papers, setPapers] = useState<Paper[]>([]);
  const [keyword, setKeyword] = useState("");
  const [formOpen, setFormOpen] = useState(false);
  const [copyOpen, setCopyOpen] = useState(false);
  const [editing, setEditing] = useState<Paper | null>(null);
  const [copying, setCopying] = useState<Paper | null>(null);
  const [arrange, setArrange] = useState<Paper | null>(null);
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [mode, setMode] = useState("FIXED");
  const [totalScore, setTotalScore] = useState("100");
  const [passScore, setPassScore] = useState("60");
  const [duration, setDuration] = useState("60");
  const [saving, setSaving] = useState(false);

  const load = () =>
    getPapers(keyword)
      .then((page) => setPapers(page.records))
      .catch((error) =>
        Toast.error(error instanceof Error ? error.message : "试卷加载失败"),
      );

  useEffect(() => {
    load();
  }, []);

  const visible = useMemo(
    () =>
      papers.filter((paper) => !keyword || paper.name.includes(keyword)),
    [papers, keyword],
  );

  const resetForm = () => {
    setEditing(null);
    setName("");
    setDescription("");
    setMode("FIXED");
    setTotalScore("100");
    setPassScore("60");
    setDuration("60");
  };

  const openCreate = () => {
    resetForm();
    setFormOpen(true);
  };

  const openEdit = (paper: Paper) => {
    setEditing(paper);
    setName(paper.name);
    setDescription(paper.description || "");
    setMode(paper.mode?.toUpperCase() === "RANDOM" ? "RANDOM" : "FIXED");
    setTotalScore(String(paper.totalScore || 100));
    setPassScore(String(paper.passScore || 60));
    setDuration(String(paper.durationMinutes || 60));
    setFormOpen(true);
  };

  const save = async () => {
    if (!name.trim()) return Toast.error("请输入试卷名称");
    const data: SavePaper = {
      name: name.trim(),
      description: description.trim(),
      mode,
      totalScore: Number(totalScore) || 100,
      passScore: Number(passScore) || 60,
      durationMinutes: Number(duration) || 60,
    };
    setSaving(true);
    try {
      if (editing) await updatePaper(editing.id, data);
      else await createPaper(data);
      Toast.success(editing ? "试卷修改成功" : "试卷创建成功");
      setFormOpen(false);
      resetForm();
      load();
    } catch (error) {
      Toast.error(error instanceof Error ? error.message : "保存试卷失败");
    } finally {
      setSaving(false);
    }
  };

  const remove = (paper: Paper) => {
    if (paper.hasAssessment) return Toast.info("试卷已有考试任务，不能删除");
    Modal.confirm({
      title: "删除试卷",
      content: `确定删除试卷“${paper.name}”吗？`,
      onOk: async () => {
        try {
          await deletePaper(paper.id);
          Toast.success("试卷已删除");
          load();
        } catch (error) {
          Toast.error(error instanceof Error ? error.message : "删除试卷失败");
        }
      },
    });
  };

  const openCopy = (paper: Paper) => {
    setCopying(paper);
    setName(`${paper.name}_副本`);
    setDescription(paper.description || "");
    setCopyOpen(true);
  };

  const copy = async () => {
    if (!copying || !name.trim()) return Toast.error("请输入副本名称");
    setSaving(true);
    try {
      await copyPaper(copying.id, { name: name.trim(), description });
      Toast.success("试卷复制成功");
      setCopyOpen(false);
      load();
    } catch (error) {
      Toast.error(error instanceof Error ? error.message : "复制试卷失败");
    } finally {
      setSaving(false);
    }
  };

  return (
    <PageShell>
      <main className="mx-auto flex min-h-[720px] w-[92%] max-w-[1160px] flex-col gap-6 py-10 sm:py-14 md:flex-row">
        <ProfileSidebar />
        <section className="min-w-0 flex-1">
          <div className="flex items-center justify-between gap-4">
            <div>
              <h1 className="text-2xl font-bold text-slate-800">试卷管理</h1>
              <p className="mt-2 text-sm text-slate-400">创建固定或随机试卷，并安排考试。</p>
            </div>
            <Button theme="solid" type="warning" onClick={openCreate}>+ 创建试卷</Button>
          </div>
          <div className="mt-6 rounded-2xl border border-slate-200 bg-white p-4">
            <Input value={keyword} onChange={setKeyword} onEnterPress={load} showClear placeholder="搜索试卷名称..." />
          </div>
          <div className="mt-5 grid gap-4 lg:grid-cols-2">
            {visible.map((paper) => {
              const locked = Boolean(paper.hasAssessment);
              const completed = paper.status === "PUBLISHED";
              return (
                <article key={paper.id} className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
                  <div className="flex items-start justify-between gap-4">
                    <div>
                      <h2 className="text-lg font-bold text-slate-800">{paper.name}</h2>
                      <p className="mt-2 text-sm text-slate-500">{paper.description || "暂无描述"}</p>
                    </div>
                    <div className="flex shrink-0 flex-col items-end gap-2 text-xs">
                      <span className="rounded-md bg-blue-50 px-2 py-1 text-blue-600">{modeLabel(paper.mode)}</span>
                      <span className={`rounded-md px-2 py-1 ${completed ? "bg-emerald-50 text-emerald-600" : "bg-orange-50 text-orange-600"}`}>{completed ? "已完成组卷" : "未完成组卷"}</span>
                      <span className="rounded-md bg-slate-100 px-2 py-1 text-slate-500">{locked ? "🔒 已锁定" : "私有"}</span>
                    </div>
                  </div>
                  <div className="mt-6 flex flex-wrap gap-x-5 gap-y-2 text-sm text-slate-500">
                    <span>▤ {paper.questionCount || 0} 题</span><span>◷ {paper.durationMinutes} 分钟</span><span>◎ {paper.totalScore} 分</span>
                  </div>
                  <div className="mt-5 flex flex-wrap items-center gap-3 border-t border-slate-100 pt-4 text-sm">
                    <Link href={`/profile/papers/${paper.id}/compose`} className={`rounded-lg px-4 py-2 font-semibold no-underline ${locked ? "bg-slate-100 text-slate-400" : "bg-blue-50 text-blue-600"}`}>组卷{locked ? "（已锁定）" : ""}</Link>
                    <button className="text-slate-700" onClick={() => setArrange(paper)} disabled={!completed}>安排考试</button>
                    <button className="text-slate-600" onClick={() => openEdit(paper)}>编辑</button>
                    <button className="text-slate-600" onClick={() => openCopy(paper)}>复制</button>
                    <button className="text-red-500" onClick={() => remove(paper)}>删除</button>
                  </div>
                </article>
              );
            })}
            {!visible.length && <div className="col-span-full py-20 text-center text-sm text-slate-400">暂无试卷</div>}
          </div>
        </section>
      </main>
      <Modal title={editing ? "编辑试卷" : "创建试卷"} visible={formOpen} onCancel={() => setFormOpen(false)} footer={<div className="flex justify-end gap-3"><Button className="modal-cancel-button" onClick={() => setFormOpen(false)}>取消</Button><Button theme="solid" type="warning" loading={saving} onClick={save}>保存</Button></div>}>
        <div className="space-y-4 py-2"><Input value={name} onChange={setName} placeholder="试卷名称" /><Input value={description} onChange={setDescription} placeholder="描述（可选）" /><Select value={mode} onChange={(value) => setMode(String(value))} optionList={modeOptions} className="w-full" /><div className="grid grid-cols-3 gap-3"><Input type="number" value={totalScore} onChange={setTotalScore} prefix="总分" /><Input type="number" value={duration} onChange={setDuration} prefix="时长" /><Input type="number" value={passScore} onChange={setPassScore} prefix="及格" /></div></div>
      </Modal>
      <Modal title="复制试卷" visible={copyOpen} onCancel={() => setCopyOpen(false)} footer={<div className="flex justify-end gap-3"><Button className="modal-cancel-button" onClick={() => setCopyOpen(false)}>取消</Button><Button theme="solid" type="warning" loading={saving} onClick={copy}>复制</Button></div>}><div className="space-y-4 py-2"><p className="text-sm text-slate-500">来源：{copying?.name}</p><Input value={name} onChange={setName} placeholder="副本名称" /><Input value={description} onChange={setDescription} placeholder="描述（可选）" /></div></Modal>
      <ArrangeAssessmentModal paper={arrange} visible={Boolean(arrange)} onClose={() => setArrange(null)} onDone={load} />
    </PageShell>
  );
}
