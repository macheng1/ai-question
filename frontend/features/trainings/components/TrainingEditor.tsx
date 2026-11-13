"use client";
import { Typography, Space, Steps } from "@douyinfe/semi-ui";
import { Card } from "@douyinfe/semi-ui";
import {
  IconArrowLeft,
  IconArrowRight,
  IconPlus,
  IconHelpCircleStroked,
} from "@douyinfe/semi-icons";
import { useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import {
  Button,
  DatePicker,
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
import { loadCopyMaterials } from "./TrainingCopyMaterials";
import { TrainingVideos } from "./TrainingVideos";
import { TrainingExamSettings, emptyTrainingExam, validateTrainingExam } from "./TrainingExamSettings";
import { TrainingDraftMaterials, type DraftMaterial } from "./TrainingDraftMaterials";
import { apiClient } from "@/lib/api/client";
const labels = { RATING: "评分题", SINGLE: "单选题", TEXT: "意见题" };
const scopeKey = (item: Training) => JSON.stringify({
    assessmentId: item.assessmentId ?? null,
    type: item.audience?.type,
    ids: [...(item.audience?.ids || [])].sort((a, b) => a - b),
  });
export function TrainingEditor({ id, copyFrom, initialPaperId }: { id?: string; copyFrom?: string; initialPaperId?: number }) {
  const router = useRouter();
  const [originalAudience, setOriginalAudience] = useState<Training["audience"]>();
  const [savedEndAt, setSavedEndAt] = useState<string | null>(null);
  const [savedStartAt, setSavedStartAt] = useState<string | null>(null);
  const [clock, setClock] = useState(() => Date.now());
  useEffect(() => {
    const timer = window.setInterval(() => setClock(Date.now()), 1000);
    return () => window.clearInterval(timer);
  }, []);
  const ended = !!id && !!savedEndAt && Date.parse(savedEndAt) <= clock;
  const started = !!id && !!savedStartAt && Date.parse(savedStartAt) <= clock;
  const [materialsBusy, setMaterialsBusy] = useState(false);
  const [draftMaterials, setDraftMaterials] = useState<DraftMaterial[]>([]);
  const [training, setTraining] = useState<Training>({
    id: 0,
    exam: { ...emptyTrainingExam, enabled: !!initialPaperId, paperId: initialPaperId || null },
    audience: { type: "ALL", ids: [] },
    name: "",
    description: "",
    surveyEnabled: true,
    questions: [],
    version: 1,
  });
  const [loading, setLoading] = useState(Boolean(id || copyFrom));
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [audienceError, setAudienceError] = useState("正在检查学员资格，请稍候");
  const savedScope = useRef("");
  const sourceVersion = useRef(0);
  const savingRef = useRef(false);
  const [adding, setAdding] = useState<SurveyQuestion["type"] | null>(null);
  const addingRef = useRef(false);
  const [dirty, setDirty] = useState(false);
  const [step, setStep] = useState(0);
  const [preview, setPreview] = useState(false);
  useEffect(() => {
    let active = true;
    if (id || copyFrom)
      getTraining(id || copyFrom!)
        .then(async (saved) => {
          if (!active) return;
          sourceVersion.current = saved.version;
          if (copyFrom) {
            const copiedMaterials = await loadCopyMaterials(copyFrom);
            if (!active) return;
            setDraftMaterials(copiedMaterials);
            setTraining({ ...saved, id: 0, name: saved.name.slice(0, 156) + "（副本）",
              startAt: null, endAt: null, audience: { type: "", ids: [] },
              examLocked: false, assessmentId: null, exam: saved.exam ? { ...saved.exam, startAt: null, endAt: null } : { ...emptyTrainingExam }, purpose: "STANDALONE",
              responseCount: 0, learningLocked: false, submitted: false, version: 1 });
          } else { setOriginalAudience(saved.audience); setSavedEndAt(saved.endAt || null); setSavedStartAt(saved.startAt || null); savedScope.current = scopeKey(saved); setTraining(saved); }
        })
        .catch((e) => { if (active) setError(e.message); })
        .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [id, copyFrom]);
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
  const locked = started || (training.responseCount || 0) > 0;
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
  const validateTraining = () => {
    if (materialsBusy) { Toast.warning("正在识别材料，请稍候"); return false; }
    if (!training.audience?.type) { Toast.error("请选择学习范围"); setStep(0); return false; }
    if (!training.name.trim()) {
      Toast.error("请输入培训名称");
      setStep(0);
      return false;
    }
    if (!training.startAt || !training.endAt) { Toast.error("请填写培训开始和结束时间"); setStep(0); return false; }
    if (!Number.isFinite(Date.parse(training.startAt)) || !Number.isFinite(Date.parse(training.endAt)) || Date.parse(training.endAt) <= Date.parse(training.startAt)) {
      Toast.error("培训结束时间必须晚于开始时间");
      setStep(0);
      return false;
    }
    if (audienceError && (!training.id || savedScope.current !== scopeKey(training)
        || (training.audience?.type !== "ALL" && !training.audience?.ids.length))) {
      Toast.error(audienceError);
      setStep(0);
      return false;
    }
    return true;
  };
  const goToStep = (next: number) => {
    if (busy || loading || error || addingRef.current) return;
    if (next > step && !validateTraining()) return;
    if (next > 1 && next > step) {
      const examError = validateTrainingExam(training.exam ? { ...training.exam, startAt: training.startAt ?? null, endAt: training.endAt ?? null } : undefined);
      if (examError) { Toast.error(examError); setStep(1); return; }
    }
    setStep(next);
  };
  const save = async () => {
    if (addingRef.current || savingRef.current || busy || loading || error) return;
    if (!validateTraining()) return;
    const examError = validateTrainingExam(training.exam ? { ...training.exam, startAt: training.startAt ?? null, endAt: training.endAt ?? null } : undefined);
    if (examError) { Toast.error(examError); setStep(1); return; }
    if (training.surveyEnabled && !training.questions.length) {
      Toast.error("请至少添加一道评估题目");
      setStep(2);
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
      setStep(2);
      return;
    }
    savingRef.current = true;
    setBusy(true);
    try {
      const payload = { ...training, exam: training.exam ? { ...training.exam, startAt: training.startAt ?? null, endAt: training.endAt ?? null } : undefined };
      let prepared: Training | undefined;
      if (!training.id && !copyFrom && draftMaterials.length) {
        if (draftMaterials.some(item => item.kind === "materials" && !item.pages)) {
          Toast.error("请先打开材料的设置时长，完成页数识别"); setStep(0); return;
        }
        const data = new FormData();
        data.append("training", new Blob([JSON.stringify(payload)], { type: "application/json" }));
        draftMaterials.forEach(item => data.append(item.kind, item.file));
        data.append("materialTimings", new Blob([JSON.stringify(draftMaterials.filter(item => item.kind === "materials").map(item => item.pages))], { type: "application/json" }));
        prepared = await apiClient.post<Training>("/management/trainings/with-materials", data, { timeout: 600000 });
      }
      if (copyFrom && !training.id) {
        const data = new FormData();
        let uploadIndex = 0;
        const items = draftMaterials.map(item => {
          const reference = item.sourceId ? { sourceId: item.sourceId } : { uploadIndex: uploadIndex++ };
          if (!item.sourceId) data.append("files", item.file);
          return { kind: item.kind, ...reference, pages: item.pages };
        });
        data.append("draft", new Blob([JSON.stringify({ copy: { sourceVersion: sourceVersion.current, training: payload }, items })], { type: "application/json" }));
        prepared = await apiClient.post<Training>(`/management/trainings/${copyFrom}/copy-with-materials`, data, { timeout: 600000 });
      }
      const saved = prepared || await saveTraining(training.id || undefined, payload);
      savedScope.current = scopeKey(saved);
      setTraining(saved);
      setDirty(false);
      setDraftMaterials([]);
      Toast.success(copyFrom && !training.id ? "培训复制成功" : "培训已保存");
      router.replace("/profile/trainings");
    } catch (e) {
      Toast.error(e instanceof Error ? e.message : "保存失败");
    } finally {
      savingRef.current = false;
      setBusy(false);
    }
  };
  return (
    <ProfileShell>
      <Typography.Text link={{ href: "/profile/trainings" }} icon={<IconArrowLeft size="small" />} className="!inline-flex !min-h-8 !items-center !text-sm">返回培训列表</Typography.Text>
      <div className="my-5 flex flex-wrap items-center justify-between gap-3">
        <Typography.Title
          heading={1}
          className="!m-0 !text-[28px] !font-semibold !text-[#183f35]"
        >
          {copyFrom ? "复制培训" : id ? "编辑培训" : "新建培训"}
        </Typography.Title>
        {step === 2 && (
          <Button disabled={busy || loading || !!error || !!adding} onClick={() => setPreview(true)}>
            预览评估
          </Button>
        )}
      </div>
      {loading ? (
        <p>加载中…</p>
      ) : error ? (
        <p role="alert" className="text-red-600">
          {error}
        </p>
      ) : (
        <>
          <div className="mb-6 overflow-x-auto">
            <Steps current={step} onChange={goToStep} aria-label="培训创建步骤" className="min-w-[480px]">
              <Steps.Step title="培训信息" />
              <Steps.Step title="考试" />
              <Steps.Step title="满意度评估" />
            </Steps>
          </div>
          <fieldset disabled={busy} className="min-w-0">
            <div hidden={step !== 0}>
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
                  <div className="flex flex-col gap-2">
                    <span className="font-semibold">培训时间 *</span>
                    <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
                      <DatePicker
                        aria-label="培训开始时间" aria-required="true" placeholder="请选择开始时间"
                        type="dateTime" format="yyyy-MM-dd HH:mm"
                        showClear disabled={busy || started || !!training.examLocked} className="w-full"
                        value={training.startAt ? new Date(training.startAt) : undefined}
                        onChange={(_, text) => change({ startAt: typeof text === "string" && text ? text.replace(" ", "T") : null })}
                      />
                      <DatePicker
                        aria-label="培训结束时间" aria-required="true" placeholder="请选择结束时间"
                        type="dateTime" format="yyyy-MM-dd HH:mm"
                        showClear disabled={busy} className="w-full"
                        value={training.endAt ? new Date(training.endAt) : undefined}
                        onChange={(_, text) => change({ endAt: typeof text === "string" && text ? text.replace(" ", "T") : null })}
                      />
                    </div>
                  </div>
                  <TrainingAudience
                    trainingId={training.id || undefined}
                    onValidationChange={setAudienceError}
                    assessmentId={training.exam ? undefined : training.assessmentId}
                    locked={ended}
                    originalScope={id && (started || training.learningLocked || training.examLocked) ? originalAudience : undefined}
                    value={training.audience || { type: "ALL", ids: [] }}
                    disabled={busy || ended}
                    onChange={(audience) => change({ audience })}
                  />
                  {training.id > 0 ? (
                    <>
                      <TrainingVideos id={training.id} readOnly={started} />
                      <TrainingMaterials id={training.id} readOnly={started} />
                    </>
                  ) : (
                    <TrainingDraftMaterials onBusyChange={setMaterialsBusy} items={draftMaterials} disabled={busy} onChange={items => { setDraftMaterials(items); setDirty(true); }} />
                  )}
                </div>
              </Card>
            </div>
            <div hidden={step !== 1}>
              <Card bodyStyle={{ padding: 0 }}>
                <TrainingExamSettings value={training.exam || emptyTrainingExam} onChange={exam => change({ exam })}
                  disabled={busy} started={started} locked={started || !!training.examLocked} scopeLocked={started || !!training.learningLocked} />
              </Card>
            </div>
            <div hidden={step !== 2}>
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
                    <Tooltip trigger="click" position="topLeft" content="开启后请至少添加一道评估题。关闭评估只会关闭填写入口，现有题目与反馈会保留；培训开始或已有反馈后，题目内容和顺序不可修改。">
                      <Button theme="borderless" type="tertiary" size="small" icon={<IconHelpCircleStroked />} aria-label="满意度评估说明" />
                    </Tooltip>
                  </div>
                </Card>
                {locked && (
                  <p className="rounded-lg bg-amber-50 p-3 text-sm text-amber-800">
                    {started ? "培训已开始，评估题目不可修改。" : "已有反馈，题目内容和顺序已锁定。你仍可修改培训信息或关闭评估。"}
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
            </div>
          </fieldset>
          <div className="mt-6 flex flex-wrap items-center justify-between gap-3">
            <span className="text-sm text-slate-500">第 {step + 1} 步，共 3 步</span>
            <Space wrap>
              {step > 0 && <Button disabled={busy || !!adding} onClick={() => goToStep(step - 1)}>上一步</Button>}
              {step < 2 ? (
                <Button theme="solid" disabled={busy || !!adding} onClick={() => goToStep(step + 1)}>下一步</Button>
              ) : (
                <Button theme="solid" type="warning" loading={busy} disabled={!!adding} onClick={save}>
                  {copyFrom && !training.id ? "确认修改并复制" : "保存培训"}
                </Button>
              )}
            </Space>
          </div>
        </>
      )}
      <Modal
        bodyStyle={{ maxHeight: "calc(100vh - 180px)", overflowY: "auto", overflowX: "hidden", paddingRight: 16, paddingBottom: 24 }}
        title="满意度评估预览"
        visible={preview}
        onCancel={() => setPreview(false)}
        footer={null}
        style={{ width: "min(640px, 94vw)" }}
      >
        <div className="min-w-0 px-1 pt-1">
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
