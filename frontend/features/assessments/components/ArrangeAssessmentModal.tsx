"use client";

import { useEffect, useState } from "react";
import "@douyinfe/semi-ui/react19-adapter";
import {
  Button,
  Checkbox,
  Input,
  Modal,
  Select,
  Spin,
  Toast,
} from "@douyinfe/semi-ui";
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
    [pass, setPass] = useState("60"),
    [scope, setScope] = useState("ALL"),
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
    setPass(String(paper.passScore || 60));
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
        candidateScope: scope,
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
    <Modal
      modalContentClass="arrange-modal"
      visible={visible}
      width={520}
      title="安排考试"
      onCancel={onClose}
      footer={
        <div className="flex justify-end gap-8">
          <Button className="modal-cancel-button" onClick={onClose}>
            取消
          </Button>
          <Button
            theme="solid"
            type="warning"
            loading={loading}
            disabled={!paper || !detail}
            onClick={save}
          >
            保存
          </Button>
        </div>
      }
    >
      <div className="arrange-form">
        {!detail ? (
          papers.length ? (
            <div className="space-y-4">
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
            <div className="grid grid-cols-2 gap-3">
              <label>
                <span className="mb-1 block text-xs text-slate-500">
                  开始时间
                </span>
                <Input
                  type="datetime-local"
                  value={start}
                  onChange={setStart}
                />
              </label>
              <label>
                <span className="mb-1 block text-xs text-slate-500">
                  结束时间
                </span>
                <Input type="datetime-local" value={end} onChange={setEnd} />
              </label>
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
              <div className="arrange-advanced-fields p-4">
                <label>
                  <span className="mb-1 block text-xs text-slate-500">
                    及格分数
                  </span>
                  <Input type="number" value={pass} onChange={setPass} />
                </label>
                <label>
                  <span className="mb-1 block text-xs text-slate-500">
                    参考范围
                  </span>
                  <Select
                    value={scope}
                    onChange={(v) => setScope(String(v))}
                    optionList={[
                      { label: "无限制（所有人均可参加）", value: "ALL" },
                      { label: "指定部门", value: "DEPARTMENT" },
                      { label: "指定人员", value: "USERS" },
                    ]}
                    className="w-full"
                  />
                </label>
                <div className="flex min-h-8 items-center gap-2 whitespace-nowrap">
                  <Checkbox
                    checked={retake}
                    onChange={(e) => setRetake(Boolean(e.target.checked))}
                  >
                    允许重考
                  </Checkbox>
                  <span className="text-xs text-slate-400">
                    最多答题（含首次）
                  </span>
                  <Input
                    type="number"
                    disabled={!retake}
                    value={attempts}
                    onChange={setAttempts}
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
