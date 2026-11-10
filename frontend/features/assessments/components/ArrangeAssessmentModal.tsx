"use client";


import { useEffect, useState } from "react";
import "@douyinfe/semi-ui/react19-adapter";
import { Button, Checkbox, DatePicker, Input, Modal, Select, Spin, Toast, InputNumber } from "@douyinfe/semi-ui";
import { createAssessment } from "@/features/assessments/api";
import { getPaper, type Paper } from "@/features/papers/api";

const localDateTime = (hours: number) => {
  const d = new Date(Date.now() + hours * 3600000),
    p = (n: number) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}T${p(d.getHours())}:${p(d.getMinutes())}`;
};
export function ArrangeAssessmentModal({
  paper,
  papers = [],
  visible,
  onClose,
  onDone,
  onSelectPaper,
}: {
  paper: Paper | null;
  papers?: Paper[];
  visible: boolean;
  onClose: () => void;
  onDone: () => void;
  onSelectPaper?: (paper: Paper | null) => void;
}) {
  const [detail, setDetail] = useState<Paper | null>(paper),
    [name, setName] = useState(""),
    [start, setStart] = useState(localDateTime(0)),
    [end, setEnd] = useState(localDateTime(24)),
    [duration, setDuration] = useState("60"),
    [shuffle, setShuffle] = useState(true),
    [retake, setRetake] = useState(false),
    [attempts, setAttempts] = useState("1"),
    [result, setResult] = useState(true),
    [loading, setLoading] = useState(false);
  useEffect(() => {
    if (!visible) return;
    if (!paper) {
      setDetail(null);
      setName("");
      return;
    }
    setDetail(paper);
    setName("");
    setDuration(String(paper.durationMinutes || 60));
    setStart(localDateTime(0));
    setEnd(localDateTime(24));
    getPaper(paper.id).then(setDetail);
  }, [visible, paper]);
  const save = async () => {
    if (!name.trim()) {
      Toast.error("请输入考试名称");
      return;
    }
    if (new Date(end) <= new Date(start)) {
      Toast.error("结束时间必须晚于开始时间");
      return;
    }
    setLoading(true);
    try {
      await createAssessment({
        name: name.trim(),
        paperId: paper!.id,
        startAt: start,
        endAt: end,
        durationMinutes: Number(duration) || 60,
        allowRetake: retake,
        maxAttempts: retake ? Math.max(1, Number(attempts) || 1) : 1,
        shuffleQuestions: shuffle,
        shuffleOptions: shuffle,
        showResultImmediately: result,
      });
      Toast.success("考试安排成功");
      onClose();
      onDone();
    } catch (e) {
      Toast.error(e instanceof Error ? e.message : "安排考试失败");
    } finally {
      setLoading(false);
    }
  };
  return (
    <Modal bodyStyle={{ maxHeight: "calc(100dvh - 240px)", overflowY: "auto" }}
      modalContentClass="arrange-modal"
      visible={visible}
      width="min(520px, calc(100vw - 24px))"
      title="安排考试"
      onCancel={onClose}
      footer={
        <div className="flex justify-end gap-8">
          <Button onClick={onClose}>
            取消
          </Button>
          <Button
            theme="solid"
            type="primary"
            loading={loading}
            disabled={!paper || !detail}
            onClick={save}
          >
            保存
          </Button>
        </div>
      }
    >
      <div className="flex flex-col gap-4">
        {!detail ? (
          papers.length ? (
            <div className="flex flex-col gap-4">
              <label className="block">
                <span className="mb-1 block text-xs text-slate-500">
                  选择试卷
                </span>
                <Select
                  placeholder="请选择试卷"
                  optionList={papers.map((item) => ({
                    label: item.name,
                    value: item.id,
                  }))}
                  onChange={(value) =>
                    onSelectPaper?.(
                      papers.find((item) => item.id === Number(value)) || null,
                    )
                  }
                  className="w-full"
                />
              </label>
              <p className="text-sm text-slate-400">
                请选择一份已完成组卷的试卷后继续安排考试。
              </p>
            </div>
          ) : (
            <div className="grid min-h-20 place-items-center">
              <Spin />
            </div>
          )
        ) : (
          <>
            <div className="flex items-start justify-between rounded-xl bg-slate-50 px-4 py-3">
              <div>
                <p className="text-xs text-slate-400">试卷</p>
                <p className="mt-1 text-sm font-semibold">{detail.name}</p>
                <p className="mt-1 text-xs text-slate-400">
                  推荐 {detail.durationMinutes} 分钟 ·{" "}
                  {detail.mode === "RANDOM" ? "随机组卷" : "固定组卷"}
                </p>
              </div>
              <div className="text-right">
                <b className="text-2xl text-teal-500">{detail.totalScore}</b>
                <p className="text-xs text-slate-400">满分</p>
              </div>
            </div>
            <Input
              value={name}
              onChange={setName}
              placeholder="考试名称，比如XX单位考试"
            />
            <div className="grid grid-cols-2 gap-4 ">
              <div className="min-w-0 col-span-2 sm:col-span-1"><label>
                <span className="mb-1 block text-xs text-slate-500">
                  开始时间
                </span>
                <DatePicker type="dateTime" format="yyyy-MM-dd HH:mm" className="w-full" value={start ? new Date(start) : undefined} onChange={(_, text) => setStart(typeof text === "string" ? text.replace(" ", "T") : "")} />
              </label></div>
              <div className="min-w-0 col-span-2 sm:col-span-1"><label>
                <span className="mb-1 block text-xs text-slate-500">
                  结束时间
                </span>
                <DatePicker type="dateTime" format="yyyy-MM-dd HH:mm" className="w-full" value={end ? new Date(end) : undefined} onChange={(_, text) => setEnd(typeof text === "string" ? text.replace(" ", "T") : "")} />
              </label></div>
            </div>
            <Checkbox
              checked={shuffle}
              onChange={(e) => setShuffle(Boolean(e.target.checked))}
            >
              乱序（同一题型内部题目顺序打乱）
            </Checkbox>
            <div className="rounded-xl border border-slate-200">
              <div className="border-b border-slate-200 px-4 py-3 text-sm font-semibold text-slate-600">
                高级设置
              </div>
              <div className="flex flex-col gap-4 p-4">
                <label>
                  <span className="mb-1 block text-xs text-slate-500">
                    及格分数
                  </span>
                  <InputNumber value={detail?.passScore ?? 60} disabled />
                  <p className="mt-1 text-xs text-slate-500">沿用试卷及格分数，请在组卷前于试卷中设置。</p>
                </label>
                <div className="flex min-h-8 flex-wrap items-center gap-2">
                  <Checkbox
                    checked={retake}
                    onChange={(e) => setRetake(Boolean(e.target.checked))}
                  >
                    允许重考
                  </Checkbox>
                  <span className="text-xs text-slate-400">
                    最多答题（含首次）
                  </span>
                  <InputNumber

                    disabled={!retake}
                    value={attempts}
                    onChange={value => setAttempts(String(value ?? ""))}
                    className="w-20 shrink-0"
                    style={{ width: 80 }}
                  />
                  <span className="text-xs text-slate-400">次</span>
                </div>
                <Checkbox
                  checked={result}
                  onChange={(e) => setResult(Boolean(e.target.checked))}
                >
                  交卷后可以立即查看答案
                </Checkbox>
              </div>
            </div>
          </>
        )}
      </div>
    </Modal>
  );
}
