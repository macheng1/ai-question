"use client";
import { useCompactViewport } from "@/lib/hooks/useCompactViewport";
import { Typography } from "@douyinfe/semi-ui";
import { Card } from "@douyinfe/semi-ui";
import { IconPlus, IconChevronDown, IconHelpCircleStroked } from "@douyinfe/semi-icons";
import { useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import "@douyinfe/semi-ui/react19-adapter";
import { Button, Input, Modal, Toast, Table, Select, Tag, Dropdown, Tooltip } from "@douyinfe/semi-ui";
import { trainingCategories, getTrainings, endTraining, type Training } from "@/features/trainings/api";
import { ProfileShell } from "@/components/layout/ProfileShell";
import { TrainingReminderModal } from "@/features/trainings/components/TrainingReminderModal";
export default function TrainingsPage() {
  const router = useRouter();
  const compact = useCompactViewport();
  const [reminder, setReminder] = useState<Training | null>(null);
  const [openMenu, setOpenMenu] = useState<number | null>(null);
  const [entry, setEntry] = useState<Training | null>(null);
  const [qrFailed, setQrFailed] = useState(false);
  const entryLinkField = useRef<HTMLDivElement>(null);
  const entryLink = entry && typeof window !== "undefined" ? `${window.location.origin}/training/${entry.id}` : "";
  const qrUrl = `https://api.qrserver.com/v1/create-qr-code/?size=240x240&data=${encodeURIComponent(entryLink)}`;
  const copying = useRef(false);
  const copyEntryLink = async () => {
    if (copying.current || !entryLink) return;
    copying.current = true;
    const toastId = "training-entry-copy";
    try {
      // Run inside the click gesture, before any asynchronous permission request.
      // The input must stay inside the modal so its focus trap does not steal selection.
      const input = entryLinkField.current?.querySelector("input");
      if (input) {
        input.focus();
        input.select();
        input.setSelectionRange(0, input.value.length);
        try {
          if (document.execCommand("copy")) {
            Toast.success({ id: toastId, content: "链接已复制" });
            return;
          }
        } catch {
          // Try the newer API when the synchronous method is unavailable.
        }
      }
      if (navigator.clipboard?.writeText) {
        try {
          await navigator.clipboard.writeText(entryLink);
          Toast.success({ id: toastId, content: "链接已复制" });
          return;
        } catch {
          // Leave the visible link selected for manual copying.
        }
      }
      input?.focus();
      input?.select();
      Toast.warning({ id: toastId, content: "链接已选中，请按 Ctrl+C（Mac 为 ⌘C）或长按复制" });
    } finally {
      copying.current = false;
    }
  };
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    const refreshTime = () => setNow(Date.now());
    const timer = window.setInterval(refreshTime, 30_000);
    window.addEventListener("focus", refreshTime);
    return () => {
      window.clearInterval(timer);
      window.removeEventListener("focus", refreshTime);
    };
  }, []);
  const [items, setItems] = useState<Training[]>([]);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [keyword, setKeyword] = useState("");
  const [category, setCategory] = useState<string>();
  const [requirement, setRequirement] = useState<string>();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const load = () => { setLoading(true); setError(""); getTrainings().then(setItems).catch(e => setError(e.message)).finally(() => setLoading(false)); };
  useEffect(() => { load(); }, []);
  const stopTraining = (item: Training) => Modal.confirm({
    title: item.assessmentId ? "结束培训和考试" : "结束培训",
    content: item.assessmentId
      ? `确认结束“${item.name}”的培训和考试？进行中的答卷将结算，学习记录和成绩保留。结束后可回看材料，不再记录学习进度，也不能参加考试。`
      : `确认结束“${item.name}”的培训？学习记录保留，结束后可回看材料，不再记录学习进度。`,
    okText: item.assessmentId ? "结束培训和考试" : "结束培训", cancelText: "取消",
    onOk: async () => { try { await endTraining(item.id); Toast.success(item.assessmentId ? "培训和考试已结束" : "培训已结束"); load(); } catch (e) { Toast.error(e instanceof Error ? e.message : "结束失败"); throw e; } },
  });
  const visible = items.filter(item => item.name.includes(keyword.trim()) && (!category || item.category === category) && (!requirement || item.requirementType === requirement));
  const currentPage = Math.min(page, Math.max(1, Math.ceil(visible.length / pageSize)));
  const pageItems = visible.slice((currentPage - 1) * pageSize, currentPage * pageSize);
  return <ProfileShell><div className="flex flex-wrap items-center justify-between gap-4"><div><Typography.Title heading={1} className="!m-0 !text-[28px] !font-semibold !text-[#183f35]">培训管理</Typography.Title><p className="mt-2 text-sm text-slate-500">统一管理学习、考试与满意度评估</p></div><div className="flex items-center gap-3"><Button className="shrink-0" theme="solid" icon={<IconPlus />} onClick={() => router.push("/profile/trainings/new")}>新建培训</Button></div></div>
  <Card bodyStyle={{ padding: 0 }} className="!my-5"><div className="grid gap-4 p-5 sm:grid-cols-2 xl:grid-cols-[200px_200px_minmax(0,1fr)]"><Select aria-label="分类筛选" placeholder="全部分类" showClear value={category} optionList={trainingCategories} onChange={v => { setCategory(v ? String(v) : undefined); setPage(1); }} /><Select aria-label="学习要求筛选" placeholder="全部学习要求" showClear value={requirement} optionList={[{value:"REQUIRED",label:"必修"},{value:"ELECTIVE",label:"选修"}]} onChange={v => { setRequirement(v ? String(v) : undefined); setPage(1); }} /><Input placeholder="搜索培训名称" value={keyword} onChange={value => { setKeyword(value); setPage(1); }} /></div></Card>
  {loading ? <p>加载中…</p> : error ? <div role="alert">{error}<Button onClick={load}>重试</Button></div> : !visible.length ? <Card bodyStyle={{ padding: 0 }} className="text-slate-500"><div className="p-12 text-center">{items.length ? "没有匹配的培训" : "还没有培训，点击右上角新建"}</div></Card> : <Card bodyStyle={{ padding: 0 }} className="overflow-x-auto"><Table<Training> rowKey="id" dataSource={pageItems} pagination={{ total: visible.length, style: { padding: "16px 20px", margin: 0, boxSizing: "border-box" }, currentPage, pageSize, showTotal: !compact, showSizeChanger: !compact, pageSizeOpts: [10, 20, 50], onPageChange: setPage, onPageSizeChange: size => { setPageSize(size); setPage(1); } }} scroll={{ x: 1200 }} columns={[
    { title: "培训名称", dataIndex: "name", width: 260, render: value => <span className="break-words">{value}</span> },
    { title: <span className="inline-flex items-center gap-1">培训状态<Tooltip trigger="click" position="bottomLeft" content={<div className="max-w-[320px] text-sm leading-6"><strong>培训状态说明</strong><ul className="mt-2 list-disc space-y-1 pl-4"><li>未开始：还没到开始时间，暂不能学习。</li><li>进行中：已开始，可以参加学习。</li><li>已结束：培训已结束。</li></ul></div>}><Button aria-label="培训状态说明" theme="borderless" type="tertiary" size="small" icon={<IconHelpCircleStroked />} /></Tooltip></span>, width: 150, className: "whitespace-nowrap", render: (_, item) => {
      if (item.endAt && now >= new Date(item.endAt).getTime()) return <Tag color="blue">已结束</Tag>;
      if (item.startAt && now < new Date(item.startAt).getTime()) return <Tag color="orange">未开始</Tag>;
      if (item.startAt || item.endAt) return <Tag color="green">进行中</Tag>;
      return <Tag color="grey">未设置</Tag>;
    } },
    { title: "分类", width: 110, className: "whitespace-nowrap", render: (_, item) => trainingCategories.find(c => c.value === item.category)?.label || "未分类" },
    { title: "学习要求", width: 100, className: "whitespace-nowrap", render: (_, item) => <Tag color={item.requirementType === "ELECTIVE" ? "cyan" : "blue"}>{item.requirementType === "ELECTIVE" ? "选修" : "必修"}</Tag> },
    { title: "评估题数", width: 100, className: "whitespace-nowrap", render: (_, item) => item.questions.length },
    { title: "评估状态", width: 110, className: "whitespace-nowrap", render: (_, item) => <Tag color={item.surveyEnabled ? "green" : "grey"}>{item.surveyEnabled ? "已开启" : "已关闭"}</Tag> },
    { title: "反馈", width: 80, className: "whitespace-nowrap", dataIndex: "responseCount" },
    { title: "操作", width: 320, fixed: compact ? undefined : "right", className: "whitespace-nowrap", render: (_, item) => <div className="flex items-center gap-2 whitespace-nowrap">
      <Button theme="borderless" size="large" onClick={() => router.push(`/profile/trainings/${item.id}/edit`)}>编辑</Button>
      <Button theme="borderless" size="large" onClick={() => { setQrFailed(false); setEntry(item); }}>培训入口</Button>
      <Dropdown trigger="click" position="bottomRight" visible={openMenu === item.id} onVisibleChange={visible => setOpenMenu(visible ? item.id : null)} render={<Dropdown.Menu>
        <Dropdown.Item onClick={() => { setOpenMenu(null); router.push(`/profile/trainings/${item.id}/copy`); }}>复制</Dropdown.Item>
        <Dropdown.Item onClick={() => { setOpenMenu(null); router.push(`/profile/trainings/${item.id}/learning-records`); }}>培训统计</Dropdown.Item>
        {item.assessmentId && <Dropdown.Item onClick={() => { setOpenMenu(null); router.push(`/profile/assessments/${item.assessmentId}/statistics`); }}>考试统计</Dropdown.Item>}
        <Dropdown.Item disabled={!!item.examShared || !!(item.startAt && now < Date.parse(item.startAt)) || !!(item.endAt && now >= Date.parse(item.endAt) && (!item.assessmentId || item.examStatus === "DISABLED" || item.examStatus === "ENDED" || (item.examEndAt && now >= Date.parse(item.examEndAt))))} onClick={() => { setOpenMenu(null); stopTraining(item); }}>{item.assessmentId ? "结束培训和考试" : "结束培训"}</Dropdown.Item>
        <Dropdown.Item onClick={() => { setOpenMenu(null); setReminder(item); }}>一键提醒</Dropdown.Item>
        <Dropdown.Item onClick={() => router.push(`/profile/trainings/${item.id}/responses`)}>查看反馈</Dropdown.Item>
      </Dropdown.Menu>}><Button theme="borderless" size="large" icon={<IconChevronDown />} iconPosition="right">更多</Button></Dropdown>
    </div> }
  ]} /></Card>}
  <Modal visible={!!entry} title="培训入口" width="min(480px, calc(100vw - 24px))" onCancel={() => setEntry(null)} bodyStyle={{ maxHeight: "calc(100dvh - 240px)", overflowY: "auto" }} footer={<div className="flex flex-wrap justify-end gap-2">
    <Button onClick={() => setEntry(null)}>关闭</Button>
    <Button onClick={() => window.open(entryLink, "_blank", "noopener,noreferrer")}>打开培训</Button>
    <Button theme="solid" disabled={qrFailed} onClick={() => window.open(qrUrl, "_blank", "noopener,noreferrer")}>保存图片</Button>
  </div>}>
    {entry && <div className="flex flex-col gap-4">
      <Typography.Text strong className="break-words">{entry.name}</Typography.Text>
      <Typography.Text type="secondary">扫码进入培训，也可以复制链接发送给学员。</Typography.Text>
      <div className="flex min-h-60 items-center justify-center">
        {qrFailed ? <Typography.Text type="danger">二维码加载失败，请关闭后重试，或复制培训链接。</Typography.Text> : <img src={qrUrl} alt={`${entry.name}培训二维码`} width={240} height={240} onError={() => setQrFailed(true)} />}
      </div>
      <div ref={entryLinkField} className="flex items-center gap-2">
        <Input aria-label="培训链接" value={entryLink} readOnly onFocus={event => event.target.select()} style={{ flex: 1, minWidth: 0 }} />
        <Button className="shrink-0" onClick={copyEntryLink}>复制链接</Button>
      </div>
    </div>}
  </Modal>
  {reminder && <TrainingReminderModal training={reminder} onClose={() => setReminder(null)} />}
  </ProfileShell>;
}
