"use client";
import { Typography, Space, Table, Tag } from "@douyinfe/semi-ui";
import { Card } from "@douyinfe/semi-ui";

import { IconHelpCircleStroked, IconPlus } from "@douyinfe/semi-icons";

import { useEffect, useMemo, useState } from "react";
import Link from "next/link";
import "@douyinfe/semi-ui/react19-adapter";
import { Button, Input, Modal, Select, Toast, InputNumber, Tooltip } from "@douyinfe/semi-ui";
import { ProfileShell } from "@/components/layout/ProfileShell";
import {
  copyPaper,
  createPaper,
  deletePaper,
  getPapers,
  updatePaper,
  type Paper,
  type SavePaper,
} from "@/features/papers/api";

function statusHeader(label: string, tip: string) {
  return (
    <span className="inline-flex items-center gap-1 whitespace-nowrap">
      {label}
      <Tooltip trigger="click" content={tip}>
        <Button aria-label={label + "说明"} theme="borderless" type="tertiary" size="small" icon={<IconHelpCircleStroked />} />
      </Tooltip>
    </span>
  );
}

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
  const [search, setSearch] = useState("");
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [loading, setLoading] = useState(true);
  const [formOpen, setFormOpen] = useState(false);
  const [copyOpen, setCopyOpen] = useState(false);
  const [editing, setEditing] = useState<Paper | null>(null);
  const [copying, setCopying] = useState<Paper | null>(null);
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [mode, setMode] = useState("FIXED");
  const [totalScore, setTotalScore] = useState("100");
  const [passScore, setPassScore] = useState("60");
  const [duration, setDuration] = useState("60");
  const [saving, setSaving] = useState(false);

  const load = () =>
    getPapers()
      .then((page) => setPapers(page.records))
      .catch((error) =>
        Toast.error(error instanceof Error ? error.message : "试卷加载失败"),
      ).finally(() => setLoading(false));

  useEffect(() => {
    load();
  }, []);

  const visible = useMemo(
    () =>
      papers.filter((paper) => !search || paper.name.includes(search)),
    [papers, search],
  );

  const currentPage = Math.min(page, Math.max(1, Math.ceil(visible.length / pageSize)));
  const runSearch = () => { setSearch(keyword.trim()); setPage(1); };

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
    <ProfileShell>
          <div className="flex flex-wrap items-center justify-between gap-4">
            <div>
              <Typography.Title heading={1} className="!m-0 !text-[28px] !font-semibold !text-[#183f35]">试卷管理</Typography.Title>
              <p className="mt-2 text-sm text-slate-400">创建固定或随机试卷，在培训中安排考试。</p>
            </div>
            <Button theme="solid" type="primary" onClick={openCreate}><IconPlus aria-hidden="true" /> 创建试卷</Button>
          </div>
          <Card bodyStyle={{ padding: 0 }} className="!mt-5">
            <div className="flex flex-wrap gap-3 p-5">
              <Input className="min-w-[200px] flex-1" value={keyword} onChange={setKeyword} onEnterPress={runSearch} showClear placeholder="搜索试卷名称" />
              <Button theme="solid" type="primary" onClick={runSearch}>搜索</Button>
            </div>
          </Card>
          <Card bodyStyle={{ padding: 0 }} className="!mt-5 overflow-hidden">
            <Table
              rowKey="id"
              loading={loading}
              className="w-full [&_.semi-table-table]:!w-full [&_.semi-table-table]:!table-fixed"
              scroll={{ x: 1450 }}
              dataSource={visible.slice((currentPage - 1) * pageSize, currentPage * pageSize)}
              empty="暂无试卷"
              pagination={{ currentPage, pageSize, total: visible.length, showTotal: true, showSizeChanger: true, pageSizeOpts: [10, 20, 50], onPageChange: setPage, onPageSizeChange: size => { setPageSize(size); setPage(1); } }}
              columns={[
                { title: "试卷名称", dataIndex: "name", width: 240, render: (_: unknown, paper: Paper) => <div className="break-words font-medium">{paper.name}</div> },
                { title: "试卷描述", dataIndex: "description", render: (value: string) => <div className="break-words text-sm text-slate-500">{value || "—"}</div> },
                { title: "组卷方式", width: 120, render: (_: unknown, paper: Paper) => modeLabel(paper.mode) },
                { title: statusHeader("组卷状态", "选好题目或设置好抽题规则后，点击「完成组卷」。显示「已完成组卷」后，就可以用这张试卷安排考试。"), width: 140, render: (_: unknown, paper: Paper) => <Tag color={paper.status === "PUBLISHED" ? "green" : "orange"}>{paper.status === "PUBLISHED" ? "已完成组卷" : "未完成组卷"}</Tag> },
                { title: statusHeader("锁定状态", "试卷关联考试后会锁定。锁定后可以查看组卷。如需调整题目或抽题规则，可复制一份再修改。"), width: 130, render: (_: unknown, paper: Paper) => <Tag color={paper.hasAssessment ? "grey" : "green"}>{paper.hasAssessment ? "已锁定" : "未锁定"}</Tag> },
                { title: "题数", width: 80, render: (_: unknown, paper: Paper) => `${paper.questionCount || 0} 题` },
                { title: "时长", width: 100, render: (_: unknown, paper: Paper) => `${paper.durationMinutes} 分钟` },
                { title: "总分 / 及格分", width: 130, render: (_: unknown, paper: Paper) => `${paper.totalScore} / ${paper.passScore} 分` },
                { title: "操作", width: 300, fixed: "right", render: (_: unknown, paper: Paper) => <div className="flex flex-wrap items-center gap-1">
                  <Link href={`/profile/papers/${paper.id}/compose`} className="px-3 py-2 text-sm font-semibold text-blue-600 no-underline">{paper.hasAssessment ? "查看组卷" : "组卷"}</Link>
                  <Button theme="borderless" onClick={() => openEdit(paper)}>编辑</Button>
                  <Button theme="borderless" onClick={() => openCopy(paper)}>复制</Button>
                  <Button theme="borderless" type="danger" disabled={Boolean(paper.hasAssessment)} onClick={() => remove(paper)}>删除</Button>
                </div> },
              ]}
            />
          </Card>

      <Modal
        bodyStyle={{ maxHeight: "calc(100dvh - 240px)", overflowY: "auto" }}
        width="min(560px, calc(100vw - 24px))"
        title={editing ? "编辑试卷" : "创建试卷"}
        visible={formOpen}
        onCancel={() => setFormOpen(false)}
        footer={<Space><Button className="modal-cancel-button" onClick={() => setFormOpen(false)}>取消</Button><Button theme="solid" type="primary" loading={saving} onClick={save}>保存</Button></Space>}
      >
        <div className="flex flex-col gap-5 py-2">
          <div><label htmlFor="paper-name" className="mb-2 block text-sm font-medium">试卷名称 <span className="text-red-500">*</span></label><Input id="paper-name" value={name} onChange={setName} placeholder="请输入试卷名称" /></div>
          <div><label htmlFor="paper-description" className="mb-2 block text-sm font-medium">试卷描述</label><Input id="paper-description" value={description} onChange={setDescription} placeholder="请输入试卷描述（选填）" /></div>
          <div>
            <div className="mb-2 flex items-center gap-1 text-sm font-medium">
              <span id="paper-mode-label">组卷方式</span>
              <Tooltip trigger="click" content={
                <div className="space-y-1">
                  <p>固定组卷：手动选题，所有学员使用同一组题目。</p>
                  <p>随机组卷：选好题库，设置各题型的题数和每题分值。学员开始考试时，系统按规则抽题。</p>
                </div>
              }>
                <Button aria-label="组卷方式说明" theme="borderless" type="tertiary" size="small" icon={<IconHelpCircleStroked />} />
              </Tooltip>
            </div>
            <Select aria-labelledby="paper-mode-label" value={mode} onChange={(value) => setMode(String(value))} optionList={modeOptions} style={{ width: "100%" }} />
          </div>
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
            <div className="min-w-0"><label htmlFor="paper-total" className="mb-2 block text-sm font-medium">试卷总分</label><InputNumber id="paper-total" style={{ width: "100%" }} value={totalScore} onChange={value => setTotalScore(String(value ?? ""))} suffix="分" /></div>
            <div className="min-w-0"><label htmlFor="paper-duration" className="mb-2 block text-sm font-medium">考试时长</label><InputNumber id="paper-duration" style={{ width: "100%" }} value={duration} onChange={value => setDuration(String(value ?? ""))} suffix="分钟" /></div>
            <div className="min-w-0"><label htmlFor="paper-pass" className="mb-2 block text-sm font-medium">及格分数</label><InputNumber id="paper-pass" style={{ width: "100%" }} value={passScore} onChange={value => setPassScore(String(value ?? ""))} suffix="分" /></div>
          </div>
        </div>
      </Modal>
      <Modal
        bodyStyle={{ maxHeight: "calc(100dvh - 240px)", overflowY: "auto" }}
        width="min(560px, calc(100vw - 24px))"
        title="复制试卷"
        visible={copyOpen}
        onCancel={() => setCopyOpen(false)}
        footer={<Space><Button className="modal-cancel-button" onClick={() => setCopyOpen(false)}>取消</Button><Button theme="solid" type="primary" loading={saving} onClick={copy}>确认复制</Button></Space>}
      >
        <div className="flex flex-col gap-5 py-2">
          <div className="rounded-lg bg-slate-50 px-4 py-3 text-sm text-slate-500">来源试卷：<span className="break-words text-slate-700">{copying?.name}</span></div>
          <div><label htmlFor="paper-copy-name" className="mb-2 block text-sm font-medium">副本名称 <span className="text-red-500">*</span></label><Input id="paper-copy-name" value={name} onChange={setName} placeholder="请输入副本名称" /></div>
          <div><label htmlFor="paper-copy-description" className="mb-2 block text-sm font-medium">试卷描述</label><Input id="paper-copy-description" value={description} onChange={setDescription} placeholder="请输入试卷描述（选填）" /></div>
        </div>
      </Modal>
    </ProfileShell>
  );
}
