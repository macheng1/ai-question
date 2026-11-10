"use client";
import { Typography, Space } from "@douyinfe/semi-ui";
import { Card } from "@douyinfe/semi-ui";
import {
  IconArrowLeft,
  IconArrowRight,
  IconPlus,
  IconHelpCircleStroked,
} from "@douyinfe/semi-icons";
import { useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import {
  Button,
  DatePicker,
  Banner,
  Input,
  TextArea,
  Select,
  Checkbox,
  Radio,
  Modal,
  Switch,
  Toast,
  Tooltip,
} from "@douyinfe/semi-ui";
import {
  trainingCategories,
  createSurveyQuestion,
  getTraining,
  saveTraining,
  type Training,
  type SurveyQuestion,
} from "../api";
import { ProfileShell } from "@/components/layout/ProfileShell";
import { SurveyForm } from "./SurveyForm";
import { TrainingAudience } from "./TrainingAudience";
import { TrainingMaterials } from "./TrainingMaterials";
import { TrainingVideos } from "./TrainingVideos";
import { getAssessments, type Assessment } from "@/features/assessments/api";
const labels = { RATING: "评分题", SINGLE: "单选题", TEXT: "意见题" };
export function TrainingEditor({ id }: { id?: string }) {
  const router = useRouter();
  const [assessments, setAssessments] = useState<Assessment[]>([]);
  useEffect(() => {
    getAssessments()
      .then((r) => setAssessments(r.records))
      .catch((e) => setError(e.message));
  }, []);
  const [training, setTraining] = useState<Training>({
    id: 0,
    audience: { type: "ALL", ids: [] },
    name: "",
    description: "",
    surveyEnabled: true,
    questions: [],
    version: 1,
  });
  const [loading, setLoading] = useState(Boolean(id));
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [adding, setAdding] = useState<SurveyQuestion["type"] | null>(null);
  const addingRef = useRef(false);
  const [dirty, setDirty] = useState(false);
  const [tab, setTab] = useState("content");
  const [preview, setPreview] = useState(false);
  useEffect(() => {
    if (id)
      getTraining(id)
        .then(setTraining)
        .catch((e) => setError(e.message))
        .finally(() => setLoading(false));
  }, [id]);
  useEffect(() => {
    const warn = (event: BeforeUnloadEvent) => {
      if (dirty) event.preventDefault();
    };
    const leave = (event: MouseEvent) => {
      const link =
        event.target instanceof Element
          ? (event.target.closest("a[href]") as HTMLAnchorElement | null)
          : null;
      if (
        !dirty ||
        !link ||
        link.target === "_blank" ||
        event.metaKey ||
        event.ctrlKey ||
        event.shiftKey ||
        event.altKey ||
        link.href === window.location.href
      )
        return;
      event.preventDefault();
      event.stopPropagation();
      Modal.confirm({
        title: "离开编辑页",
        content: "有未保存的修改，确定离开吗？",
        okText: "离开",
        cancelText: "继续编辑",
        onOk: () => {
          window.removeEventListener("beforeunload", warn);
          if (link.origin === window.location.origin)
            router.push(link.pathname + link.search + link.hash);
          else window.location.assign(link.href);
        },
      });
    };
    window.addEventListener("beforeunload", warn);
    document.addEventListener("click", leave, true);
    return () => {
      window.removeEventListener("beforeunload", warn);
      document.removeEventListener("click", leave, true);
    };
  }, [dirty, router]);
  const change = (patch: Partial<Training>) => {
    setTraining((old) => ({ ...old, ...patch }));
    setDirty(true);
  };
  const locked = (training.responseCount || 0) > 0;
  const assessmentEndAt = assessments.find(item => item.id === training.assessmentId)?.endAt;
  const endsAfterExam = Boolean(training.endAt && assessmentEndAt &&
    new Date(training.endAt).getTime() > new Date(assessmentEndAt).getTime());
  const updateQuestion = (index: number, patch: Partial<SurveyQuestion>) =>
    change({
      questions: training.questions.map((q, i) =>
        i === index ? { ...q, ...patch } : q,
      ),
    });
  const add = async (type: SurveyQuestion["type"]) => {
    if (addingRef.current || locked || busy || training.questions.length >= 50)
      return;
    addingRef.current = true;
    setAdding(type);
    try {
      const question = await createSurveyQuestion(type);
      setTraining((old) => ({
        ...old,
        questions: [...old.questions, question],
      }));
      setDirty(true);
    } catch (e) {
      Toast.error(e instanceof Error ? e.message : "添加题目失败，请重试");
    } finally {
      addingRef.current = false;
      setAdding(null);
    }
  };
  const move = (index: number, offset: number) => {
    const questions = [...training.questions];
    [questions[index], questions[index + offset]] = [
      questions[index + offset],
      questions[index],
    ];
    change({ questions });
  };
  const save = async () => {
    if (addingRef.current || busy) return;
    if (training.startAt && training.endAt && new Date(training.endAt).getTime() <= new Date(training.startAt).getTime()) {
      Toast.error("培训结束时间必须晚于开始时间");
      return;
    }
    if (!training.name.trim()) {
      Toast.error("请输入培训名称");
      setTab("content");
      return;
    }
    if (training.surveyEnabled && !training.questions.length) {
      Toast.error("请至少添加一道评估题目");
      setTab("survey");
      return;
    }
    if (
      training.questions.some(
        (q) =>
          !q.title.trim() ||
          (q.type === "SINGLE" &&
            (q.options.length < 2 ||
              q.options.some((o) => !o.trim()) ||
              new Set(q.options.map((o) => o.trim())).size !==
                q.options.length)),
      )
    ) {
      Toast.error("请填写题目标题，单选题至少两个不同的非空选项");
      setTab("survey");
      return;
    }
    setBusy(true);
    try {
      const saved = await saveTraining(training.id || undefined, training);
      setTraining(saved);
      setDirty(false);
      Toast.success("培训评估已保存");
      if (!id) router.replace(`/profile/trainings/${saved.id}/edit`);
    } catch (e) {
      Toast.error(e instanceof Error ? e.message : "保存失败");
    } finally {
      setBusy(false);
    }
  };
  return (
    <ProfileShell>
      <Link href="/profile/trainings" className="text-sm text-teal-700">
        {<IconArrowLeft />} 返回培训列表
      </Link>
      <div className="my-5 flex flex-wrap items-center justify-between gap-3">
        <Typography.Title
          heading={1}
          className="!m-0 !text-[28px] !font-semibold !text-[#183f35]"
        >
          {id ? "编辑培训" : "新建培训"}
        </Typography.Title>
        <Space wrap>
          <Button
            disabled={loading || !!error || !!adding}
            onClick={() => setPreview(true)}
          >
            预览评估
          </Button>
          <Button
            theme="solid"
            type="warning"
            loading={busy}
            disabled={loading || !!error || !!adding}
            onClick={save}
          >
            保存
          </Button>
        </Space>
      </div>
      {loading ? (
        <p>加载中…</p>
      ) : error ? (
        <p role="alert" className="text-red-600">
          {error}
        </p>
      ) : (
        <>
          <div className="mb-5 flex gap-6 border-b border-slate-200">
            {[
              ["content", "培训信息"],
              ["survey", "满意度评估"],
            ].map(([key, label]) => (
              <Button
                theme="borderless"
                key={key}
                onClick={() => setTab(key)}
                className={`pb-3 text-sm font-semibold ${tab === key ? "border-b-2 border-teal-500 text-teal-700" : "text-slate-500"}`}
              >
                {label}
              </Button>
            ))}
          </div>
          <fieldset disabled={busy} className="min-w-0">
            {tab === "content" ? (
              <Card bodyStyle={{ padding: 0 }} className="">
                <div className="flex flex-col gap-6 p-6">
                  <label className="block">
                    <span className="mb-2 block font-semibold">培训名称 *</span>
                    <Input
                      disabled={busy}
                      maxLength={160}
                      value={training.name}
                      onChange={(name) => change({ name })}
                      placeholder="例如：新员工入职培训"
                    />
                  </label>
                  <label className="block">
                    <span className="mb-2 block font-semibold">培训简介</span>
                    <TextArea
                      disabled={busy}
                      maxLength={2000}
                      rows={5}
                      value={training.description}
                      onChange={(description) =>
                        change({ description: String(description) })
                      }
                      style={{ width: "100%" }}
                      placeholder="介绍本次培训的内容和目的"
                    />
                  </label>
                  <div className="grid gap-6 sm:grid-cols-2">
                    <div>
                      <span className="mb-2 block font-semibold">课程分类</span>
                      <Select
                        aria-label="课程分类"
                        motion={false}
                        style={{ width: "100%" }}
                        disabled={busy}
                        value={training.category || "UNCATEGORIZED"}
                        optionList={trainingCategories}
                        onChange={(value) =>
                          change({ category: String(value) })
                        }
                      />
                    </div>
                    <div>
                      <span className="mb-2 block font-semibold">学习要求</span>
                      <Radio.Group
                        aria-label="学习要求"
                        disabled={busy}
                        value={training.requirementType || "REQUIRED"}
                        onChange={(e) =>
                          change({ requirementType: e.target.value })
                        }
                      >
                        <Radio value="REQUIRED">必修</Radio>
                        <Radio value="ELECTIVE">选修</Radio>
                      </Radio.Group>
                    </div>
                  </div>
                  <div>
                    <div className="mb-2 flex items-center gap-1">
                      <span className="font-semibold">关联考试（选填）</span>
                      <Tooltip content="不关联考试时仅进行培训；关联后完成学习才能参加考试。已有学习或考试记录后，关联考试和学习范围不能修改。">
                        <Button
                          theme="borderless"
                          type="tertiary"
                          size="small"
                          icon={<IconHelpCircleStroked />}
                          aria-label="关联考试说明"
                        />
                      </Tooltip>
                    </div>
                    <Select
                      aria-label="关联考试"
                      motion={false}
                      style={{ width: "100%" }}
                      showClear
                      filter
                      disabled={busy || training.learningLocked}
                      placeholder="不关联考试（仅培训）"
                      value={
                        training.assessmentId
                          ? String(training.assessmentId)
                          : undefined
                      }
                      onChange={(value) =>
                        change({ assessmentId: value ? Number(value) : null })
                      }
                      optionList={assessments.map((a) => ({
                        value: String(a.id),
                        label: a.name,
                      }))}
                    />
                  </div>
                  <div className="flex flex-col gap-2">
                    <span className="font-semibold">培训时间</span>
                    <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
                      <DatePicker
                        aria-label="培训开始时间" placeholder="请选择开始时间"
                        type="dateTime" format="yyyy-MM-dd HH:mm"
                        showClear disabled={busy} className="w-full"
                        value={training.startAt ? new Date(training.startAt) : undefined}
                        onChange={(_, text) => change({ startAt: typeof text === "string" && text ? text.replace(" ", "T") : null })}
                      />
                      <DatePicker
                        aria-label="培训结束时间" placeholder="请选择结束时间"
                        type="dateTime" format="yyyy-MM-dd HH:mm"
                        showClear disabled={busy} className="w-full"
                        value={training.endAt ? new Date(training.endAt) : undefined}
                        onChange={(_, text) => change({ endAt: typeof text === "string" && text ? text.replace(" ", "T") : null })}
                      />
                    </div>
                    {endsAfterExam && <Banner type="warning" description="培训结束时间晚于考试结束时间，员工学完时考试可能已经关闭，请核对时间安排。" />}
                  </div>
                  <TrainingAudience
                    value={training.audience || { type: "ALL", ids: [] }}
                    disabled={busy || training.learningLocked}
                    onChange={(audience) => change({ audience })}
                  />
                  {training.id > 0 ? (
                    <>
                      <TrainingVideos id={training.id} />
                      <TrainingMaterials id={training.id} />
                    </>
                  ) : (
                    <p>请先保存培训，再上传培训材料。</p>
                  )}
                </div>
              </Card>
            ) : (
              <div className="flex flex-col gap-4">
                <Card bodyStyle={{ padding: 0 }} className="">
                  <div className="flex items-center gap-3 p-4">
                    <Switch
                      disabled={busy}
                      checked={training.surveyEnabled}
                      onChange={(surveyEnabled) => change({ surveyEnabled })}
                      aria-label="开启满意度评估"
                    />
                    <span>开启满意度评估</span>
                  </div>
                </Card>
                {!training.surveyEnabled && (
                  <p className="text-sm text-slate-500">
                    已关闭填写入口，现有题目与反馈会保留。
                  </p>
                )}
                {locked && (
                  <p className="rounded-lg bg-amber-50 p-3 text-sm text-amber-800">
                    已有反馈，题目内容和顺序已锁定。你仍可修改培训信息或关闭评估。
                  </p>
                )}
                <fieldset
                  disabled={locked || busy}
                  className="flex flex-col gap-4"
                >
                  {training.questions.map((q, index) => (
                    <Card bodyStyle={{ padding: 0 }} key={q.id} className="">
                      <div className="flex flex-col gap-4 p-5">
                        <div className="flex flex-wrap items-center justify-between gap-2">
                          <strong>
                            {index + 1}. {labels[q.type]}
                          </strong>
                          <div className="flex flex-wrap items-center gap-3 text-sm">
                            <label className="flex items-center gap-2">
                              <Checkbox
                                disabled={locked || busy}
                                checked={q.required}
                                onChange={(e) =>
                                  updateQuestion(index, {
                                    required: !!e.target.checked,
                                  })
                                }
                              />
                              必填
                            </label>
                            <Button
                              theme="borderless"
                              disabled={locked || busy || index === 0}
                              className="text-teal-700 disabled:text-slate-300"
                              onClick={() => move(index, -1)}
                            >
                              上移
                            </Button>
                            <Button
                              theme="borderless"
                              disabled={
                                locked ||
                                busy ||
                                index === training.questions.length - 1
                              }
                              className="text-teal-700 disabled:text-slate-300"
                              onClick={() => move(index, 1)}
                            >
                              下移
                            </Button>
                            <Button
                              disabled={locked || busy}
                              theme="borderless"
                              className="text-red-500"
                              onClick={() =>
                                change({
                                  questions: training.questions.filter(
                                    (_, i) => i !== index,
                                  ),
                                })
                              }
                            >
                              删除
                            </Button>
                          </div>
                        </div>
                        <Input
                          disabled={locked || busy}
                          aria-label={`第${index + 1}题标题`}
                          maxLength={300}
                          placeholder="请输入题目标题"
                          value={q.title}
                          onChange={(title) => updateQuestion(index, { title })}
                        />
                        {q.type === "RATING" && (
                          <p className="text-sm text-slate-500">
                            1 分非常不满意 {<IconArrowRight />} 5 分非常满意
                          </p>
                        )}
                        {q.type === "TEXT" && (
                          <div className="rounded-lg bg-slate-50 p-3 text-sm text-slate-400">
                            填写人可输入最多1000字意见
                          </div>
                        )}
                        {q.type === "SINGLE" && (
                          <div className="flex flex-col gap-2">
                            {q.options.map((option, i) => (
                              <div
                                key={i}
                                className="flex min-w-0 items-center gap-2"
                              >
                                <span className="text-slate-400">
                                  <Radio
                                    disabled
                                    checked={false}
                                    aria-label="选项"
                                  />
                                </span>
                                <Input
                                  disabled={locked || busy}
                                  style={{ flex: 1, minWidth: 0 }}
                                  aria-label={`选项${i + 1}`}
                                  maxLength={200}
                                  value={option}
                                  onChange={(value) =>
                                    updateQuestion(index, {
                                      options: q.options.map((o, n) =>
                                        n === i ? value : o,
                                      ),
                                    })
                                  }
                                />
                                <Button
                                  theme="borderless"
                                  disabled={
                                    locked || busy || q.options.length <= 2
                                  }
                                  className="shrink-0 text-sm text-red-500 disabled:text-slate-300"
                                  onClick={() =>
                                    updateQuestion(index, {
                                      options: q.options.filter(
                                        (_, n) => n !== i,
                                      ),
                                    })
                                  }
                                >
                                  移除
                                </Button>
                              </div>
                            ))}
                            <Button
                              disabled={
                                locked || busy || q.options.length >= 20
                              }
                              onClick={() =>
                                updateQuestion(index, {
                                  options: [...q.options, ""],
                                })
                              }
                            >
                              添加选项
                            </Button>
                          </div>
                        )}
                      </div>
                    </Card>
                  ))}
                  {!training.questions.length && (
                    <div className="rounded-xl border border-dashed border-slate-300 p-10 text-center text-slate-500">
                      还没有题目，请添加评分、单选或意见题。
                    </div>
                  )}
                  <Space wrap>
                    {(["RATING", "SINGLE", "TEXT"] as const).map((type) => (
                      <Button
                        key={type}
                        loading={adding === type}
                        disabled={
                          locked ||
                          busy ||
                          !!adding ||
                          training.questions.length >= 50
                        }
                        onClick={() => add(type)}
                      >
                        {<IconPlus />} {labels[type]}
                      </Button>
                    ))}
                  </Space>
                </fieldset>
              </div>
            )}
          </fieldset>
        </>
      )}
      <Modal
        bodyStyle={{ maxHeight: "calc(100dvh - 240px)", overflowY: "auto" }}
        title="满意度评估预览"
        visible={preview}
        onCancel={() => setPreview(false)}
        footer={null}
        style={{ width: "min(640px, 94vw)" }}
      >
        <div className="max-h-[70vh] overflow-y-auto p-1">
          <Typography.Title heading={2} className="!mb-4 !text-xl !font-bold">
            {training.name || "培训名称"}
          </Typography.Title>
          <SurveyForm
            key={JSON.stringify(training)}
            training={training}
            preview
          />
        </div>
      </Modal>
    </ProfileShell>
  );
}
