"use client";

import { useEffect, useState } from "react";
import { useParams, useRouter } from "next/navigation";
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
import { PageShell } from "@/components/layout/PageShell";
import { createAssessment } from "@/features/assessments/api";
import { getPaper, type Paper } from "@/features/papers/api";

const localDateTime = (offsetHours: number) => {
  const date = new Date(Date.now() + offsetHours * 3600000);
  const pad = (value: number) => String(value).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
};
const displayDate = (value: string) => value.replace("T", " ");

export default function NewAssessmentPage() {
  const { paperId } = useParams<{ paperId: string }>();
  const router = useRouter();
  const [paper, setPaper] = useState<Paper | null>(null);
  const [name, setName] = useState("");
  const [startAt, setStartAt] = useState(localDateTime(0));
  const [endAt, setEndAt] = useState(localDateTime(24));
  const [duration, setDuration] = useState("60");
  const [passScore, setPassScore] = useState("60");
  const [scope, setScope] = useState("ALL");
  const [allowRetake, setAllowRetake] = useState(false);
  const [maxAttempts, setMaxAttempts] = useState("1");
  const [shuffle, setShuffle] = useState(true);
  const [showResult, setShowResult] = useState(true);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    getPaper(Number(paperId))
      .then((data) => {
        setPaper(data);
        setPassScore(String(data.passScore || 60));
        setDuration(String(data.durationMinutes || 60));
      })
      .catch((e) =>
        Toast.error(e instanceof Error ? e.message : "试卷加载失败"),
      )
      .finally(() => setLoading(false));
  }, [paperId]);
  const submit = async () => {
    if (!name.trim()) {
      Toast.error("请输入考试名称");
      return;
    }
    if (new Date(endAt) <= new Date(startAt)) {
      Toast.error("结束时间必须晚于开始时间");
      return;
    }
    setSaving(true);
    try {
      await createAssessment({
        name: name.trim(),
        paperId: Number(paperId),
        startAt,
        endAt,
        durationMinutes: Number(duration) || 60,
        candidateScope: scope,
        allowRetake,
        maxAttempts: allowRetake ? Math.max(1, Number(maxAttempts) || 1) : 1,
        shuffleQuestions: shuffle,
        shuffleOptions: shuffle,
        showResultImmediately: showResult,
      });
      Toast.success("考试安排成功");
      router.push("/profile/assessments");
    } catch (e) {
      Toast.error(e instanceof Error ? e.message : "安排考试失败");
    } finally {
      setSaving(false);
    }
  };
  if (loading)
    return (
      <PageShell>
        <div className="grid min-h-[720px] place-items-center">
          <Spin size="large" />
        </div>
      </PageShell>
    );
  return (
    <PageShell>
      <main className="min-h-[720px] bg-slate-100 py-10">
        <div className="mx-auto min-h-[650px] w-[92%] max-w-[1160px] rounded-2xl bg-white/70 p-8">
          <h1 className="text-2xl font-bold text-slate-800">试卷管理</h1>
          <div className="mt-6 h-14 rounded-xl border border-slate-200 bg-white" />
          <div className="mt-5 h-64 rounded-2xl border border-slate-200 bg-white p-6">
            <div className="h-5 w-40 rounded bg-slate-100" />
            <div className="mt-6 h-4 w-64 rounded bg-slate-100" />
          </div>
        </div>
        <Modal
          visible
          title="安排考试"
          width={520}
          onCancel={() => router.back()}
          footer={
            <div className="flex justify-end gap-8">
              <Button theme="borderless" onClick={() => router.back()}>
                取消
              </Button>
              <Button
                theme="solid"
                type="warning"
                loading={saving}
                onClick={submit}
              >
                保存
              </Button>
            </div>
          }
        >
          <div className="space-y-3">
            <div className="flex items-start justify-between rounded-xl bg-slate-50 px-4 py-3">
              <div>
                <p className="text-xs text-slate-400">试卷</p>
                <p className="mt-1 text-sm font-semibold text-slate-700">
                  {paper?.name}
                </p>
                <p className="mt-1 text-xs text-slate-400">
                  {paper?.questionCount || 0} 题 · 推荐{" "}
                  {paper?.durationMinutes || duration} 分钟 ·{" "}
                  {paper?.mode === "RANDOM" ? "随机组卷" : "固定组卷"}
                </p>
              </div>
              <div className="text-right">
                <b className="text-2xl text-teal-500">{paper?.totalScore}</b>
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
                  value={startAt}
                  onChange={setStartAt}
                />
              </label>
              <label>
                <span className="mb-1 block text-xs text-slate-500">
                  结束时间
                </span>
                <Input
                  type="datetime-local"
                  value={endAt}
                  onChange={setEndAt}
                />
              </label>
            </div>
            <div className="text-xs text-slate-400">
              考试时间：{displayDate(startAt)} ～ {displayDate(endAt)}
            </div>
            <Checkbox
              checked={shuffle}
              onChange={(event) => setShuffle(Boolean(event.target.checked))}
            >
              乱序（同一题型内部题目顺序打乱）
            </Checkbox>
            <div className="rounded-xl border border-slate-200">
              <div className="border-b border-slate-200 px-4 py-3 text-sm font-semibold text-slate-600">
                高级设置
              </div>
              <div className="space-y-4 p-4">
                <label>
                  <span className="mb-1 block text-xs text-slate-500">
                    及格分数
                  </span>
                  <Input
                    type="number"
                    value={passScore}
                    onChange={setPassScore}
                  />
                </label>
                <label>
                  <span className="mb-1 block text-xs text-slate-500">
                    参考范围
                  </span>
                  <Select
                    value={scope}
                    onChange={(value) => setScope(String(value))}
                    optionList={[
                      { label: "无限制（所有人均可参加）", value: "ALL" },
                      { label: "指定部门", value: "DEPARTMENT" },
                      { label: "指定人员", value: "USERS" },
                    ]}
                    className="w-full"
                  />
                </label>
                <div className="flex flex-wrap items-center gap-2">
                  <Checkbox
                    checked={allowRetake}
                    onChange={(event) =>
                      setAllowRetake(Boolean(event.target.checked))
                    }
                  >
                    允许重考
                  </Checkbox>
                  <span className="text-xs text-slate-400">
                    最多答题（含首次）
                  </span>
                  <Input
                    type="number"
                    value={maxAttempts}
                    disabled={!allowRetake}
                    onChange={setMaxAttempts}
                    className="w-20"
                  />
                  <span className="text-xs text-slate-400">次</span>
                </div>
                <Checkbox
                  checked={showResult}
                  onChange={(event) =>
                    setShowResult(Boolean(event.target.checked))
                  }
                >
                  交卷后可以立即查看答案
                </Checkbox>
              </div>
            </div>
          </div>
        </Modal>
      </main>
    </PageShell>
  );
}
