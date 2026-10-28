"use client";
import { IconPlus } from "@/features/exam/components/ExamIcons";

import { useEffect, useState } from "react";
import Link from "next/link";
import "@douyinfe/semi-ui/react19-adapter";
import { Button, DatePicker, Input, Modal, Spin, Toast } from "@douyinfe/semi-ui";
import { PageShell } from "@/components/layout/PageShell";
import { ProfileSidebar } from "@/components/layout/ProfileSidebar";
import {
  deleteAssessment,
  getAssessment,
  getAssessments,
  updateAssessment,
  updateAssessmentStatus,
  type Assessment,
} from "@/features/assessments/api";
import { getPapers, type Paper } from "@/features/papers/api";
import { ArrangeAssessmentModal } from "@/features/assessments/components/ArrangeAssessmentModal";

const dateText = (value?: string) =>
  value ? value.replace("T", " ").slice(0, 16) : "-";
const stateOf = (item: Assessment) => {
  const now = Date.now();
  if (
    item.status === "ENDED" ||
    item.status === "DISABLED" ||
    now >= new Date(item.endAt).getTime()
  )
    return ["已结束", "bg-slate-100 text-slate-500"];
  if (now < new Date(item.startAt).getTime())
    return ["未开始", "bg-amber-50 text-amber-600"];
  return ["进行中", "bg-emerald-50 text-emerald-600"];
};
export default function AssessmentManagementPage() {
  const [items, setItems] = useState<Assessment[]>([]),
    [keyword, setKeyword] = useState(""),
    [start, setStart] = useState<string>(),
    [end, setEnd] = useState<string>(),
    [loading, setLoading] = useState(true),
    [selected, setSelected] = useState<Assessment | null>(null),
    [editing, setEditing] = useState<Assessment | null>(null),
    [confirm, setConfirm] = useState<{
      type: "end" | "delete";
      item: Assessment;
    } | null>(null),
    [confirming, setConfirming] = useState(false),
    [arrangeOpen, setArrangeOpen] = useState(false),
    [arrangePaper, setArrangePaper] = useState<Paper | null>(null),
    [papers, setPapers] = useState<Paper[]>([]);
  const load = async () => {
    setLoading(true);
    try {
      const page = await getAssessments({
        keyword: keyword.trim() || undefined,
        startAt: start,
        endAt: end,
      });
      setItems(page.records || []);
    } catch (e) {
      Toast.error(e instanceof Error ? e.message : "考试任务加载失败");
    } finally {
      setLoading(false);
    }
  };
  useEffect(() => {
    load();
  }, []);
  const openArrange = async () => {
    try {
      const page = await getPapers();
      setPapers(page.records || []);
      setArrangePaper(null);
      setArrangeOpen(true);
    } catch (e) {
      Toast.error(e instanceof Error ? e.message : "试卷加载失败");
    }
  };
  const forceEnd = (item: Assessment) => setConfirm({ type: "end", item });
  const remove = (item: Assessment) => setConfirm({ type: "delete", item });
  const confirmAction = async () => {
    if (!confirm) return;
    setConfirming(true);
    try {
      if (confirm.type === "end") {
        await updateAssessmentStatus(confirm.item.id, "ENDED");
        Toast.success("考试已结束");
      } else {
        await deleteAssessment(confirm.item.id);
        Toast.success("删除成功");
      }
      setConfirm(null);
      await load();
    } catch (e) {
      Toast.error(e instanceof Error ? e.message : "操作失败");
    } finally {
      setConfirming(false);
    }
  };
  const copyLink = async (item: Assessment) => {
    await navigator.clipboard?.writeText(
      `${window.location.origin}/kaoshi/${item.id}`,
    );
    Toast.success("考试链接已复制");
  };
  return (
    <PageShell>
      <main className="mx-auto flex min-h-[720px] w-[92%] max-w-[1160px] flex-col gap-6 py-10 sm:py-14 md:flex-row">
        <ProfileSidebar />
        <section className="min-w-0 flex-1">
          <div className="flex items-center justify-between">
            <div>
              <h1 className="text-2xl font-bold text-slate-800">考试管理</h1>
              <p className="mt-2 text-sm text-slate-500">
                管理已发布的考试任务、成绩和考生入口
              </p>
            </div>
            <Button theme="borderless"
              onClick={openArrange}
              className="rounded-lg bg-orange-500 px-4 py-2.5 text-sm font-semibold text-white"
            >
              <IconPlus aria-hidden="true" /> 安排考试
            </Button>
          </div>
          <div className="mt-6 flex flex-col gap-3 rounded-2xl border border-slate-200 bg-white p-4 lg:flex-row lg:items-center">
            <div className="flex flex-1 items-center gap-3">
              <DatePicker
                type="date"
                placeholder="年 / 月 / 日"
                dropdownClassName="assessment-datepicker-dropdown"
                onChange={(_, value) =>
                  setStart(value ? `${value}T00:00:00` : undefined)
                }
                className="flex-1"
              />
              <span className="text-sm text-slate-400">至</span>
              <DatePicker
                type="date"
                placeholder="年 / 月 / 日"
                dropdownClassName="assessment-datepicker-dropdown"
                onChange={(_, value) =>
                  setEnd(value ? `${value}T23:59:59` : undefined)
                }
                className="flex-1"
              />
            </div>
            <Input
              value={keyword}
              onChange={setKeyword}
              onEnterPress={load}
              placeholder="搜索考试名称、试卷名称"
              className="lg:flex-1"
            />
            <Button theme="solid" type="warning" onClick={load}>
              立即搜索
            </Button>
          </div>
          {loading ? (
            <div className="grid min-h-64 place-items-center">
              <Spin size="large" />
            </div>
          ) : (
            <div className="mt-5 space-y-4">
              {items.map((item) => {
                const [label, tone] = stateOf(item);
                return (
                  <article
                    key={item.id}
                    className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm"
                  >
                    <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
                      <div>
                        <div className="flex items-center gap-3">
                          <span
                            className={`rounded-full px-3 py-1 text-xs ${tone}`}
                          >
                            {label}
                          </span>
                          <h2 className="text-base font-bold text-slate-800">
                            {item.name}
                          </h2>
                        </div>
                        <p className="mt-1 text-xs text-slate-400">
                          试卷：{item.paperName || item.paperId}（
                          {item.paperMode === "RANDOM" ? "随机" : "固定"}）
                        </p>
                        <div className="mt-4 flex flex-wrap gap-x-5 gap-y-2 text-xs text-slate-400">
                          <span>{item.durationMinutes} 分钟</span>
                          <span>及格 {item.passScore ?? 60} 分</span>
                          <span>
                            {dateText(item.startAt)} ～ {dateText(item.endAt)}
                          </span>
                          <span>
                            {item.allowRetake
                              ? `最多答题 ${item.maxAttempts} 次（含首次）`
                              : "不可重考"}
                          </span>
                        </div>
                      </div>
                      <div className="flex flex-wrap items-center gap-4 text-sm">
                        <Button theme="borderless"
                          className="font-semibold text-slate-700"
                          onClick={() => setEditing(item)}
                        >
                          编辑
                        </Button>
                        <Link
                          className="text-blue-500 no-underline"
                          href={`/profile/assessments/${item.id}/scores`}
                        >
                          成绩
                        </Link>
                        {label === "已结束" && (
                          <Link
                            className="text-blue-500 no-underline"
                            href={`/profile/assessments/${item.id}/statistics`}
                          >
                            统计
                          </Link>
                        )}
                        <Button theme="borderless"
                          className="text-blue-500"
                          onClick={() => setSelected(item)}
                        >
                          二维码
                        </Button>
                        {label !== "已结束" && (
                          <Button theme="borderless"
                            className="text-orange-500"
                            onClick={() => forceEnd(item)}
                          >
                            强制结束考试
                          </Button>
                        )}
                        <Button theme="borderless"
                          className="text-red-500"
                          onClick={() => remove(item)}
                        >
                          删除
                        </Button>
                      </div>
                    </div>
                  </article>
                );
              })}
              {!items.length && (
                <div className="rounded-2xl border border-slate-200 bg-white py-24 text-center text-sm text-slate-400">
                  暂无考试任务
                </div>
              )}
            </div>
          )}
        </section>
      </main>
      <ExamInfoModal
        item={selected}
        onClose={() => setSelected(null)}
        onCopy={copyLink}
      />
      <EditAssessmentModal
        item={editing}
        onClose={() => setEditing(null)}
        onDone={() => {
          setEditing(null);
          load();
        }}
      />
      <ArrangeAssessmentModal
        paper={arrangePaper}
        papers={papers}
        visible={arrangeOpen}
        onClose={() => {
          setArrangeOpen(false);
          setArrangePaper(null);
        }}
        onDone={() => {
          setArrangeOpen(false);
          setArrangePaper(null);
          load();
        }}
        onSelectPaper={setArrangePaper}
      />
      <Modal
        visible={Boolean(confirm)}
        title={confirm?.type === "end" ? "强制结束考试" : "删除考试"}
        onCancel={() => setConfirm(null)}
        footer={
          <div className="flex justify-end gap-3">
            <Button theme="borderless" onClick={() => setConfirm(null)}>
              取消
            </Button>
            <Button
              theme="solid"
              type={confirm?.type === "delete" ? "danger" : "warning"}
              loading={confirming}
              onClick={confirmAction}
            >
              确定
            </Button>
          </div>
        }
      >
        <p className="text-sm leading-7 text-slate-600">
          {confirm?.type === "end"
            ? `确定强制结束考试“${confirm.item.name}”吗？结束后未交卷的考生将无法继续答题。`
            : `确定删除考试“${confirm?.item.name}”吗？删除后不可恢复。`}
        </p>
      </Modal>
    </PageShell>
  );
}

function ExamInfoModal({
  item,
  onClose,
  onCopy,
}: {
  item: Assessment | null;
  onClose: () => void;
  onCopy: (item: Assessment) => void;
}) {
  if (!item) return null;
  const link = `${typeof window !== "undefined" ? window.location.origin : ""}/kaoshi/${item.id}`;
  const qr = `https://api.qrserver.com/v1/create-qr-code/?size=240x240&data=${encodeURIComponent(link)}`;
  return (
    <Modal
      visible
      title="考试信息"
      onCancel={onClose}
      footer={
        <div className="flex justify-end gap-4">
          <Button theme="borderless" onClick={onClose}>
            关闭
          </Button>
          <Button
            theme="solid"
            type="warning"
            onClick={() => window.open(qr, "_blank")}
          >
            保存图片
          </Button>
        </div>
      }
    >
      <p className="mb-5 text-sm text-slate-500">可扫码或复制链接参加考试。</p>
      <div className="grid grid-cols-[90px_1fr] gap-y-3 text-sm">
        <span className="text-slate-500">考试名称</span>
        <b>{item.name}</b>
        <span className="text-slate-500">试卷名称</span>
        <span>{item.paperName || item.paperId}</span>
        <span className="text-slate-500">考试时间</span>
        <span>
          {dateText(item.startAt)} ～ {dateText(item.endAt)}
        </span>
        <span className="text-slate-500">考试时长</span>
        <span>{item.durationMinutes} 分钟</span>
      </div>
      <div className="my-5 flex justify-center">
        <img
          className="h-48 w-48 rounded-xl border border-slate-200 p-3"
          src={qr}
          alt="考试二维码"
        />
      </div>
      <div className="flex items-center gap-3">
        <Input value={link} readOnly className="flex-1" />
        <Button theme="borderless" onClick={() => onCopy(item)}>
          复制链接
        </Button>
      </div>
    </Modal>
  );
}

function EditAssessmentModal({
  item,
  onClose,
  onDone,
}: {
  item: Assessment | null;
  onClose: () => void;
  onDone: () => void;
}) {
  const [name, setName] = useState(""),
    [start, setStart] = useState(""),
    [end, setEnd] = useState(""),
    [saving, setSaving] = useState(false);
  useEffect(() => {
    if (item) {
      setName(item.name);
      setStart(item.startAt?.slice(0, 16) || "");
      setEnd(item.endAt?.slice(0, 16) || "");
    }
  }, [item]);
  if (!item) return null;
  const save = async () => {
    if (!name.trim()) {
      Toast.error("请输入考试名称");
      return;
    }
    setSaving(true);
    try {
      const detail = await getAssessment(item.id);
      await updateAssessment(item.id, {
        name: name.trim(),
        paperId: detail.paperId,
        startAt: start,
        endAt: end,
        durationMinutes: detail.durationMinutes,
        candidateScope: detail.candidateScope,
        allowRetake: detail.allowRetake,
        maxAttempts: detail.maxAttempts,
        shuffleQuestions: detail.shuffleQuestions,
        shuffleOptions: detail.shuffleOptions,
        showResultImmediately: detail.showResultImmediately,
      });
      Toast.success("保存成功");
      onDone();
    } catch (e) {
      Toast.error(e instanceof Error ? e.message : "保存失败");
    } finally {
      setSaving(false);
    }
  };
  return (
    <Modal
      visible
      title="编辑考试"
      onCancel={onClose}
      footer={
        <div className="flex justify-end gap-3">
          <Button className="modal-cancel-button" onClick={onClose}>取消</Button>
          <Button theme="solid" type="warning" loading={saving} onClick={save}>
            保存
          </Button>
        </div>
      }
    >
      <div className="space-y-4">
        <Input value={name} onChange={setName} placeholder="考试名称" />
        <div className="grid grid-cols-2 gap-3">
          <Input type="datetime-local" value={start} onChange={setStart} />
          <Input type="datetime-local" value={end} onChange={setEnd} />
        </div>
      </div>
    </Modal>
  );
}
