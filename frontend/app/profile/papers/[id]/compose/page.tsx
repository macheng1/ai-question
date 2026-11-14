"use client";

import { Layout, Card, Typography } from "@douyinfe/semi-ui";

import { IconArrowLeft, IconLock } from "@douyinfe/semi-icons";

import { useEffect, useMemo, useState } from "react";
import { formatQuestionOption, getQuestionDisplayOptions } from "@/lib/question-display";
import { useParams } from "next/navigation";
import "@douyinfe/semi-ui/react19-adapter";
import { Button, Input, Select, Spin, Toast, InputNumber } from "@douyinfe/semi-ui";
import { ProfileShell } from "@/components/layout/ProfileShell";
import { RandomComposePanel } from "@/features/papers/components/RandomComposePanel";
import {
  getManagedBanks,
  getBankQuestions,
  type QuestionItem,
} from "@/features/banks/management-api";
import {
  addPaperQuestion,
  getPaper,
  getPaperQuestions,
  removePaperQuestion,
  updatePaperQuestionScore,
  updatePaperStatus,
  type Paper,
} from "@/features/papers/api";

const typeLabel: Record<string, string> = {
  SINGLE: "单选",
  MULTIPLE: "多选",
  TRUE_FALSE: "判断",
  JUDGE: "判断",
  SHORT_ANSWER: "问答",
};

export default function ComposePaperPage() {
  const params = useParams<{ id: string }>();
  const paperId = Number(params.id);
  const [paper, setPaper] = useState<Paper | null>(null);
  const [banks, setBanks] = useState<{ id: number; name: string }[]>([]);
  const [bankId, setBankId] = useState<number>();
  const [questions, setQuestions] = useState<QuestionItem[]>([]);
  const [selected, setSelected] = useState<QuestionItem[]>([]);
  // 已保存的题目快照，用于在点击“保存组卷/完成组卷”时计算差异。
  const [savedSelected, setSavedSelected] = useState<QuestionItem[]>([]);
  const [keyword, setKeyword] = useState("");
  const [filter, setFilter] = useState("ALL");
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!paperId) return;
    Promise.all([
      getPaper(paperId),
      getPaperQuestions(paperId),
    ])
      .then(([paperData, paperQuestions]) => {
        setPaper(paperData);
        setSelected(paperQuestions);
        setSavedSelected(paperQuestions);
      })
    .catch((e) =>
        Toast.error(e instanceof Error ? e.message : "组卷数据加载失败"),
      )
      .finally(() => setLoading(false));
  }, [paperId]);
  useEffect(() => {
    let cancelled = false;
    const loadBanks = async () => {
      try {
        const records = (await getManagedBanks()).records;
        if (!cancelled) {
          setBanks(records);
          setBankId(records[0]?.id);
        }
      } catch (e) {
        if (!cancelled) {
          setBanks([]);
          setBankId(undefined);
          Toast.error(e instanceof Error ? e.message : "题库加载失败");
        }
      }
    };
    loadBanks();
    return () => {
      cancelled = true;
    };
  }, []);
  useEffect(() => {
    let cancelled = false;
    if (!bankId) {
      setQuestions([]);
      return () => {
        cancelled = true;
      };
    }
    getBankQuestions(bankId)
      .then((data) => {
        if (!cancelled) setQuestions(data.records);
      })
      .catch((e) =>
        !cancelled && Toast.error(e instanceof Error ? e.message : "题目加载失败"),
      );
    return () => {
      cancelled = true;
    };
  }, [bankId]);

  const visibleQuestions = useMemo(
    () =>
      questions.filter(
        (item) =>
          (!keyword || item.stem.includes(keyword)) &&
          (filter === "ALL" || item.questionType === filter),
      ),
    [questions, keyword, filter],
  );
  const selectedIds = new Set(selected.map((item) => item.id));
  const locked = Boolean(paper?.hasAssessment);
  const score = selected.reduce(
    (sum, item) => sum + Number(item.score || 5),
    0,
  );
  const add = async (item: QuestionItem) => {
    if (locked) {
      Toast.info("试卷已有考试任务，组卷已锁定");
      return;
    }
    if (selectedIds.has(item.id)) {
      Toast.info("这道题已经加入试卷");
      return;
    }
    setSelected((current) => [...current, { ...item, score: 5 }]);
  };
  const remove = async (item: QuestionItem) => {
    if (locked) {
      Toast.info("试卷已有考试任务，组卷已锁定");
      return;
    }
    setSelected((current) => current.filter((question) => question.id !== item.id));
  };
  const changeScore = async (item: QuestionItem, value: string) => {
    if (locked) {
      Toast.info("试卷已有考试任务，组卷已锁定");
      return;
    }
    const next = Number(value);
    if (!Number.isFinite(next) || next <= 0) {
      Toast.error("题目分值必须大于 0");
      return;
    }
    setSelected((current) =>
      current.map((question) =>
        question.id === item.id ? { ...question, score: next } : question,
      ),
    );
  };
  const persistSelection = async () => {
    const savedById = new Map(savedSelected.map((item) => [item.id, item]));
    const selectedById = new Map(selected.map((item) => [item.id, item]));
    try {
      await Promise.all(
        savedSelected
          .filter((item) => !selectedById.has(item.id))
          .map((item) => removePaperQuestion(paperId, item.id)),
      );
      await Promise.all(
        selected
          .filter((item) => !savedById.has(item.id))
          .map((item) => addPaperQuestion(paperId, { questionId: item.id, score: Number(item.score || 5) })),
      );
      await Promise.all(
        selected
          .filter((item) => savedById.has(item.id) && Number(savedById.get(item.id)?.score || 5) !== Number(item.score || 5))
          .map((item) => updatePaperQuestionScore(paperId, item.id, Number(item.score || 5))),
      );
      const latest = await getPaperQuestions(paperId);
      setSelected(latest);
      setSavedSelected(latest);
      return true;
    } catch (e) {
      Toast.error(e instanceof Error ? e.message : "组卷保存失败");
      return false;
    }
  };
  const saveDraft = async () => {
    if (score !== Number(paper?.totalScore || 0)) {
      Toast.error(
        `当前题目总分为 ${score} 分，必须等于试卷设置的 ${paper?.totalScore || 0} 分`,
      );
      return;
    }
    if (await persistSelection()) Toast.success("组卷已保存");
  };
  const complete = async () => {
    if (score !== Number(paper?.totalScore || 0)) {
      Toast.error(
        `当前题目总分为 ${score} 分，必须等于试卷设置的 ${paper?.totalScore || 0} 分`,
      );
      return;
    }
    try {
      if (!(await persistSelection())) return;
      await updatePaperStatus(paperId, "PUBLISHED");
      Toast.success("试卷已完成");
      window.location.href = "/profile/papers";
    } catch (e) {
      Toast.error(e instanceof Error ? e.message : "操作失败");
    }
  };

  if (loading)
    return (
      <ProfileShell>
        <div className="grid min-h-[720px] place-items-center">
          <Spin size="large" />
        </div>
      </ProfileShell>
    );
  const isRandomPaper = paper?.mode?.toUpperCase() === "RANDOM";

  if (isRandomPaper) {
    return (
      <ProfileShell>
        <RandomComposePanel
          paper={paper}
          locked={Boolean(paper.hasAssessment)}
          onCompleted={() => { window.location.href = "/profile/papers"; }}
        />
      </ProfileShell>
    );
  }
  return (
    <ProfileShell>
      <Layout.Content className="min-w-0">
        <div className="flex flex-wrap items-center justify-between gap-4 border-b border-slate-200 bg-white px-5 py-3 sm:px-8">
          <div className="flex min-w-0 flex-wrap items-center gap-3 text-sm">
            <Typography.Text link={{ href: "/profile/papers" }} icon={<IconArrowLeft size="small" />} className="!inline-flex !min-h-8 !items-center !text-sm">返回</Typography.Text>
            <strong className="text-lg text-slate-800">{paper?.name}</strong>
            <span className="shrink-0 whitespace-nowrap rounded bg-blue-50 px-2 py-1 text-blue-600">
              {isRandomPaper ? "随机组卷" : "固定组卷"}
            </span>
            <span className="shrink-0 whitespace-nowrap rounded bg-orange-50 px-2 py-1 text-orange-600">
              {paper?.status === "PUBLISHED" ? "已完成组卷" : "未完成组卷"}
            </span>
            {locked && (
              <span className="shrink-0 whitespace-nowrap rounded bg-slate-200 px-2 py-1 text-slate-600">
                <IconLock aria-hidden="true" /> 已锁定
              </span>
            )}
          </div>
          <div className="flex flex-wrap items-center gap-3 text-sm text-slate-500">
            <span>已选 {selected.length} 题</span>
            <span>
              总分{" "}
              <b
                className={
                  score === Number(paper?.totalScore || 0)
                    ? "text-green-600"
                    : "text-orange-500"
                }
              >
                {score}
              </b>{" "}
              / 设置 {paper?.totalScore}
            </span>
            <Button
              theme="solid"
              type="warning"
              disabled={locked}
              onClick={saveDraft}
            >
              保存组卷
            </Button>
            <Button
              theme="solid"
              type="primary"
              disabled={locked}
              onClick={complete}
            >
              完成组卷
            </Button>
          </div>
        </div>
        <div className="grid grid-cols-2 gap-4 mt-5">
          <div className="min-w-0 col-span-2 lg:col-span-1"><Layout.Content className="overflow-hidden rounded-lg border border-slate-200 bg-white">
            <div className="border-b border-slate-100 px-5 py-4 text-base font-semibold text-slate-800">
              我的题库
            </div>
            <div className="flex flex-wrap gap-2 p-4">
              <Select
                value={bankId}
                onChange={(value) => setBankId(Number(value))}
                optionList={banks.map((bank) => ({
                  label: bank.name,
                  value: bank.id,
                }))}
                className="min-w-[220px] flex-1"
                placeholder="选择题库"
              />
              <Input
                value={keyword}
                onChange={setKeyword}
                placeholder="搜索题干..."
                className="min-w-[180px] flex-1"
              />
            </div>
            <div className="flex flex-wrap gap-2 border-b border-slate-100 px-4 pb-3">
              <Button
                theme={filter === "ALL" ? "solid" : "light"}
                type="primary"
                onClick={() => setFilter("ALL")}
              >
                全部
              </Button>
              <Button
                theme={filter === "SINGLE" ? "solid" : "light"}
                type="primary"
                onClick={() => setFilter("SINGLE")}
              >
                单选
              </Button>
              <Button
                theme={filter === "MULTIPLE" ? "solid" : "light"}
                type="primary"
                onClick={() => setFilter("MULTIPLE")}
              >
                多选
              </Button>
              <Button
                theme={filter === "TRUE_FALSE" ? "solid" : "light"}
                type="primary"
                onClick={() => setFilter("TRUE_FALSE")}
              >
                判断
              </Button>
              <Button theme={filter === "SHORT_ANSWER" ? "solid" : "light"} type="primary" onClick={() => setFilter("SHORT_ANSWER")}>
                问答
              </Button>
            </div>
            <div className="flex flex-col max-h-[720px] gap-2 overflow-y-auto bg-slate-50/40 p-2">
              {visibleQuestions.map((item, index) => (
                <button type="button"
                  aria-pressed={selectedIds.has(item.id)}
                  key={item.id}
                  disabled={locked}
                  onClick={() => add(item)}
                  className={`block h-auto w-full shrink-0 whitespace-normal rounded-lg border p-4 text-left transition-colors focus-visible:outline focus-visible:outline-2 focus-visible:outline-blue-500 ${
                    selectedIds.has(item.id)
                      ? "border-[#54d0ca] bg-[#e9fbfa]"
                      : "border-slate-100 bg-white"
                  } ${locked ? "cursor-not-allowed opacity-60" : "hover:bg-slate-50"}`}
                >
                  <div className="flex items-start justify-between gap-3 text-sm">
                    <span className="min-w-0 break-words font-medium leading-6 text-slate-800">
                      {index + 1}. {item.stem}
                    </span>
                    <span className="shrink-0 text-xs text-slate-500">
                      {typeLabel[item.questionType] || item.questionType}
                    </span>
                  </div>
                  <div className="mt-2 grid grid-cols-1 gap-2 break-words text-sm leading-6">
                    {item.questionType === "SHORT_ANSWER" && <div className="whitespace-pre-wrap text-slate-600">参考答案：{item.answerText}</div>}
                    {getQuestionDisplayOptions(item).map((option) => (
                      <div
                        key={option.optionKey}
                        className={option.correct ? "font-semibold text-green-600" : "text-slate-500"}
                      >
                        {formatQuestionOption(option)}
                      </div>
                    ))}
                  </div>
                </button>
              ))}
              {!visibleQuestions.length && (
                <div className="py-24 text-center text-sm text-slate-400">
                  暂无可选题目
                </div>
              )}
            </div>
          </Layout.Content></div>
          <div className="min-w-0 col-span-2 lg:col-span-1"><Layout.Content className="overflow-hidden rounded-lg border border-slate-200 bg-white">
            <div className="border-b border-slate-100 px-5 py-4 text-base font-semibold text-slate-800">
              已选题目 ({selected.length})
            </div>
            <div className="border-b border-slate-100 bg-slate-50 px-5 py-3 text-sm text-slate-500">
              从左侧点击题目添加 · 当前总分 {score} 分
            </div>
            <div className="max-h-[720px] overflow-y-auto bg-slate-50/40 p-3">
              {selected.map((item, index) => (
                <Card bodyStyle={{ padding: 0 }}
                  key={item.id}
                  className="!mb-3 last:!mb-0"
                ><div className="p-4">
                  <div className="flex items-start justify-between gap-3">
                    <div className="min-w-0 text-sm font-medium leading-6 text-slate-800">
                      <span className="mr-1 text-slate-400">#{index + 1}</span>
                      {item.stem}
                    </div>
                    <span className="shrink-0 text-xs text-slate-600">
                      {typeLabel[item.questionType] || item.questionType}
                    </span>
                  </div>
                  <div className="mt-2 grid grid-cols-1 gap-2 break-words text-sm leading-6">
                    {item.questionType === "SHORT_ANSWER" && <div className="whitespace-pre-wrap text-slate-600">参考答案：{item.answerText}</div>}
                    {getQuestionDisplayOptions(item).map((option) => (
                      <div
                        key={option.optionKey}
                        className={option.correct ? "font-semibold text-green-600" : "text-slate-500"}
                      >
                        {formatQuestionOption(option)}
                      </div>
                    ))}
                  </div>
                  <div className="mt-3 flex items-center justify-between gap-3 border-t border-slate-100 pt-3">
                    <div className="flex items-center gap-2 text-xs text-slate-400">
                      <span>分值</span>
                      <InputNumber

                        value={String(item.score || 5)}
                        onChange={(value) =>
                          setSelected((current) =>
                            current.map((question) =>
                              question.id === item.id
                                ? { ...question, score: Number(value) }
                                : question,
                            ),
                          )
                        }
                        onBlur={(event) => changeScore(item, event.target.value)}
                        disabled={locked}
                        className="w-24"
                      />
                      <span>分</span>
                    </div>
                    <Button
                      type="danger"
                      theme="borderless"
                      disabled={locked}
                      onClick={() => remove(item)}
                    >
                      移除
                    </Button>
                  </div>
                </div></Card>
              ))}
              {!selected.length && (
                <div className="py-24 text-center text-sm text-slate-400">
                  从左侧点击题目添加
                </div>
              )}
            </div>
          </Layout.Content></div>
        </div>
      </Layout.Content>
    </ProfileShell>
  );
}
