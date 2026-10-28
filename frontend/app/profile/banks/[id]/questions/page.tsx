"use client";

import { IconArrowLeft, IconPlus } from "@/features/exam/components/ExamIcons";

import { useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { useParams } from "next/navigation";
import "@douyinfe/semi-ui/react19-adapter";
import { Table,  Button, Input as SemiInput, Modal, Select, TextArea as SemiTextArea, Toast, Upload  } from "@douyinfe/semi-ui";
import { PageShell } from "@/components/layout/PageShell";
import { ProfileSidebar } from "@/components/layout/ProfileSidebar";
import {
  createQuestion,
  deleteQuestion,
  getBankQuestions,
  getManagedBanks,
  getQuestion,
  updateQuestion,
  importQuestions,
  getQuestionImportTemplate,
  type QuestionItem,
} from "@/features/banks/management-api";

const Input = Object.assign(SemiInput, { TextArea: SemiTextArea });

type OptionForm = { optionKey: string; optionText: string; correct: boolean };
const defaultOptions = (): OptionForm[] =>
  ["A", "B", "C", "D"].map((optionKey) => ({
    optionKey,
    optionText: "",
    correct: false,
  }));
const difficultyLabel = (value?: string) =>
  ({ EASY: "简单", MEDIUM: "中等", HARD: "困难" })[value || ""] || value || "-";
const answerLabel = (value?: string) =>
  value?.toUpperCase() === "TRUE"
    ? "正确"
    : value?.toUpperCase() === "FALSE"
      ? "错误"
      : value || "-";

export default function QuestionsPage() {
  const params = useParams<{ id: string }>(),
    bankId = Number(params.id);
  const [questions, setQuestions] = useState<QuestionItem[]>([]),
    [bankName, setBankName] = useState("题库"),
    [keyword, setKeyword] = useState(""),
    [open, setOpen] = useState(false),
    [importOpen, setImportOpen] = useState(false),
    [importFile, setImportFile] = useState<File | null>(null),
    [importLoading, setImportLoading] = useState(false),
    [editingId, setEditingId] = useState<number | null>(null),
    [type, setType] = useState("SINGLE"),
    [difficulty, setDifficulty] = useState("MEDIUM"),
    [stem, setStem] = useState(""),
    [answer, setAnswer] = useState(""),
    [analysis, setAnalysis] = useState(""),
    [score, setScore] = useState("1"),
    [options, setOptions] = useState<OptionForm[]>(defaultOptions()),
    [loading, setLoading] = useState(false);
  const load = () =>
    getBankQuestions(bankId)
      .then((page) =>
        setQuestions(
          page.records.map((item) => ({
            ...item,
            difficulty: difficultyLabel(item.difficulty),
            answerText: answerLabel(item.answerText),
          })),
        ),
      )
      .catch((e) =>
        Toast.error(e instanceof Error ? e.message : "题目加载失败"),
      );
  useEffect(() => {
    if (!bankId) return;
    load();
    getManagedBanks()
      .then((page) =>
        setBankName(
          page.records.find((item) => item.id === bankId)?.name || "题库",
        ),
      )
      .catch(() => undefined);
  }, [bankId]);
  const visible = useMemo(
    () => questions.filter((item) => !keyword || item.stem.includes(keyword)),
    [questions, keyword],
  );
  const questionType =
    type === "TRUE_FALSE"
      ? "判断题"
      : type === "MULTIPLE"
        ? "多选题"
        : "单选题";
  const reset = () => {
    setEditingId(null);
    setType("SINGLE");
    setDifficulty("MEDIUM");
    setStem("");
    setAnswer("");
    setAnalysis("");
    setScore("1");
    setOptions(defaultOptions());
    setOpen(false);
  };
  const edit = async (id: number) => {
    try {
      const item = await getQuestion(id);
      setEditingId(id);
      setType(item.questionType);
      setDifficulty(item.difficulty || "MEDIUM");
      setStem(item.stem);
      setAnswer(item.answerText || "");
      setAnalysis(item.analysis || "");
      setScore(String(item.score || 1));
      setOptions(
        item.options?.length
          ? item.options.map((option) => ({
              optionKey: option.optionKey,
              optionText: option.optionText,
              correct: Boolean(option.correct),
            }))
          : defaultOptions(),
      );
      setOpen(true);
    } catch (e) {
      Toast.error(e instanceof Error ? e.message : "题目加载失败");
    }
  };
  const save = async () => {
    if (!stem.trim()) {
      Toast.error("请输入题干");
      return;
    }
    if (
      type !== "TRUE_FALSE" &&
      options.some((item) => !item.optionText.trim())
    ) {
      Toast.error("请填写完整选项");
      return;
    }
    setLoading(true);
    try {
      const selectedOptions =
        type === "TRUE_FALSE"
          ? []
          : options.map((item, index) => ({ ...item, sortNo: index }));
      const correct =
        type === "TRUE_FALSE"
          ? answer
          : selectedOptions
              .filter((item) => item.correct)
              .map((item) => item.optionKey)
              .join(",");
      const payload = {
        bankId,
        questionType: type,
        stem: stem.trim(),
        answerText: correct,
        analysis,
        difficulty,
        score: Number(score) || 0,
        options: selectedOptions,
      };
      if (editingId) {
        const { bankId: _, ...updatePayload } = payload;
        await import("@/features/banks/management-api").then((api) =>
          api.updateQuestion(editingId, updatePayload),
        );
      } else await createQuestion(payload);
      Toast.success(editingId ? "题目修改成功" : "题目创建成功");
      reset();
      load();
    } catch (e) {
      Toast.error(e instanceof Error ? e.message : "保存失败");
    } finally {
      setLoading(false);
    }
  };
  const remove = (id: number) =>
    Modal.confirm({
      title: "删除题目",
      content: "确定删除这道题目吗？删除后不可恢复。",
      onOk: async () => {
        try {
          await deleteQuestion(id);
          Toast.success("题目已删除");
          load();
        } catch (e) {
          Toast.error(e instanceof Error ? e.message : "删除失败");
        }
      },
    });
  const importTemplate = async () => {
    if (!importFile) {
      Toast.error("请选择 .xlsx 文件");
      return;
    }
    setImportLoading(true);
    try {
      const result = await importQuestions(bankId, importFile);
      Toast.success(
        `导入完成：新增 ${result.imported} 题，跳过 ${result.skipped} 题`,
      );
      if (result.errors.length)
        Toast.warning(`有 ${result.errors.length} 行未导入，请查看提示`);
      setImportFile(null);
      setImportOpen(false);
      load();
    } catch (e) {
      Toast.error(e instanceof Error ? e.message : "导入失败");
    } finally {
      setImportLoading(false);
    }
  };
  const setOption = (
    index: number,
    key: keyof OptionForm,
    value: string | boolean,
  ) =>
    setOptions((current) =>
      current.map((item, itemIndex) =>
        itemIndex === index ? { ...item, [key]: value } : item,
      ),
    );
  return (
    <PageShell>
      <main className="mx-auto flex min-h-[720px] w-[92%] max-w-[1160px] flex-col gap-6 py-10 sm:py-14 md:flex-row">
        <ProfileSidebar />
        <section className="min-w-0 flex-1">
          <Link
            href="/profile/banks"
            className="text-sm font-semibold text-blue-600 no-underline"
          >
            <IconArrowLeft aria-hidden="true" /> 返回题库管理
          </Link>
          <h1 className="mt-5 text-2xl font-bold text-slate-800">
            题库管理 -- 题目管理
          </h1>
          <section className="mt-6 rounded-2xl border border-blue-200 bg-white p-5">
            <div className="flex flex-wrap items-center gap-3 border-b border-slate-100 pb-4 text-sm text-slate-500">
              当前题库：<b className="text-slate-800">{bankName}</b>
              <span className="rounded-full bg-slate-100 px-3 py-1">
                共 {questions.length} 题
              </span>
            </div>
            <div className="mt-4 flex flex-col gap-3 sm:flex-row sm:items-center">
              <Input
                value={keyword}
                onChange={setKeyword}
                placeholder="搜索题干..."
                className="sm:w-80"
              />
              <span className="flex-1" />
              <Button
                theme="borderless"
                onClick={() => Toast.info("智能出题功能即将开放")}
              >
                智能生题
              </Button>
              <Button
                theme="borderless"
                onClick={() => Toast.info("AI 导入试题功能即将开放")}
              >
                AI导入试题
              </Button>
              <Button theme="borderless" onClick={() => setImportOpen(true)}>
                模板导入试题
              </Button>
              <Button
                theme="solid"
                type="warning"
                onClick={() => setOpen(true)}
              >
                <IconPlus aria-hidden="true" /> 新增题目
              </Button>
            </div>
          </section>
          <div className="mt-5 overflow-hidden rounded-2xl border border-slate-200 bg-white">
            <Table dataSource={visible} rowKey="id" pagination={false} scroll={{x: 850}} columns={[{title: "ID", render: (_value: unknown, item: (typeof visible)[number], index: number) => (<span>{item.id}</span>)},
{title: "题型", render: (_value: unknown, item: (typeof visible)[number], index: number) => (<span>
                  <b className="rounded-full bg-blue-50 px-2 py-1 text-xs text-blue-600">
                    {item.questionType === "MULTIPLE"
                      ? "多选"
                      : item.questionType === "TRUE_FALSE"
                        ? "判断"
                        : "单选"}
                  </b>
                </span>)},
{title: "题干", render: (_value: unknown, item: (typeof visible)[number], index: number) => (<span className="truncate">{item.stem}</span>)},
{title: "难度", render: (_value: unknown, item: (typeof visible)[number], index: number) => (<span>{item.difficulty || "-"}</span>)},
{title: "答案", render: (_value: unknown, item: (typeof visible)[number], index: number) => (<span>{item.answerText || "-"}</span>)},
{title: "操作", render: (_value: unknown, item: (typeof visible)[number], index: number) => (<span className="flex gap-3">
                  <Button theme="borderless"
                    onClick={() => edit(item.id)}
                    className="text-blue-500"
                  >
                    编辑
                  </Button>
                  <Button theme="borderless"
                    onClick={() => remove(item.id)}
                    className="text-red-500"
                  >
                    删除
                  </Button>
                </span>)}]} />
            {!visible.length && (
              <div className="py-12 text-center text-sm text-slate-400">
                暂无题目，点击右上角新增
              </div>
            )}
          </div>
        </section>
      </main>
      <Modal
        title={
          <span>
            {editingId ? "编辑题目" : "新增题目"}
            <small className="ml-3 text-xs font-normal text-slate-400">
              当前题库：{bankName}
            </small>
          </span>
        }
        visible={open}
        onCancel={reset}
        width={700}
        footer={
          <div className="flex justify-end gap-3">
            <Button className="modal-cancel-button" onClick={reset}>取消</Button>
            <Button
              theme="solid"
              type="warning"
              loading={loading}
              onClick={save}
            >
              保存
            </Button>
          </div>
        }
      >
        <div className="space-y-4 py-2">
          <div className="grid gap-3 sm:grid-cols-2">
            <Select
              value={type}
              onChange={(value) => setType(String(value))}
              optionList={[
                { label: "单选题", value: "SINGLE" },
                { label: "多选题", value: "MULTIPLE" },
                { label: "判断题", value: "TRUE_FALSE" },
              ]}
              className="w-full"
            />
            <Select
              value={difficulty}
              onChange={(value) => setDifficulty(String(value))}
              optionList={[
                { label: "简单", value: "EASY" },
                { label: "中等", value: "MEDIUM" },
                { label: "困难", value: "HARD" },
              ]}
              className="w-full"
            />
          </div>
          <Input.TextArea
            value={stem}
            onChange={setStem}
            autosize={{ minRows: 3, maxRows: 6 }}
            placeholder="题干内容"
          />
          {type !== "TRUE_FALSE" && (
            <div className="rounded-xl border border-slate-200 p-4">
              <div className="mb-3 flex justify-between text-sm text-slate-600">
                <span>选项</span>
                <Button theme="borderless"
                  onClick={() =>
                    setOptions([
                      ...options,
                      {
                        optionKey: String.fromCharCode(65 + options.length),
                        optionText: "",
                        correct: false,
                      },
                    ])
                  }
                  className="text-blue-500"
                >
                  <IconPlus aria-hidden="true" /> 添加选项
                </Button>
              </div>
              {options.map((option, index) => (
                <div key={index} className="mb-2 flex items-center gap-3">
                  <span className="w-5 text-center text-sm text-slate-400">
                    {option.optionKey}
                  </span>
                  <Input
                    value={option.optionText}
                    onChange={(value) => setOption(index, "optionText", value)}
                    placeholder={`选项 ${option.optionKey}`}
                    className="flex-1"
                  />
                  <Button theme="borderless"
                    onClick={() =>
                      setOptions(
                        options.filter((_, itemIndex) => itemIndex !== index),
                      )
                    }
                    className="text-red-500"
                  >
                    删除
                  </Button>
                </div>
              ))}
            </div>
          )}
          <div>
            <p className="mb-2 text-xs text-slate-500">正确选项</p>
            {type === "TRUE_FALSE" ? (
              <Select
                value={answer}
                onChange={(value) => setAnswer(String(value))}
                optionList={[
                  { label: "正确", value: "TRUE" },
                  { label: "错误", value: "FALSE" },
                ]}
                className="w-full"
              />
            ) : (
              <div className="flex gap-2">
                {options.map((option, index) => (
                  <Button
                    key={option.optionKey}
                    theme={option.correct ? "solid" : "light"}
                    type={option.correct ? "primary" : "tertiary"}
                    onClick={() =>
                      setOptions(
                        options.map((item, itemIndex) => ({
                          ...item,
                          correct:
                            type === "MULTIPLE"
                              ? itemIndex === index
                                ? !item.correct
                                : item.correct
                              : itemIndex === index,
                        })),
                      )
                    }
                  >
                    {option.optionKey}
                  </Button>
                ))}
              </div>
            )}
          </div>
          <Input.TextArea
            value={analysis}
            onChange={setAnalysis}
            autosize={{ minRows: 2, maxRows: 4 }}
            placeholder="题目解析（可选）"
          />
          <div className="grid gap-3 sm:grid-cols-2">
            <Input
              value={score}
              onChange={setScore}
              type="number"
              prefix="分值"
            />
            <Input placeholder="标签（可选）" onChange={() => undefined} />
          </div>
        </div>
      </Modal>
      <Modal
        title="模板导入试题"
        visible={importOpen}
        onCancel={() => {
          setImportOpen(false);
          setImportFile(null);
        }}
        okText="确定导入"
        cancelText="取消"
        confirmLoading={importLoading}
        onOk={async () => {
          if (!importFile) {
            Toast.error("请选择 .xlsx 文件");
            return;
          }
          setImportLoading(true);
          try {
            const result = await importQuestions(bankId, importFile);
            Toast.success(
              `导入完成：新增 ${result.imported} 题，跳过 ${result.skipped} 题`,
            );
            if (result.errors.length)
              Toast.warning(`有 ${result.errors.length} 行未导入，请查看提示`);
            setImportOpen(false);
            setImportFile(null);
            load();
          } catch (e) {
            Toast.error(e instanceof Error ? e.message : "导入失败");
          } finally {
            setImportLoading(false);
          }
        }}
      >
        <div className="space-y-3 py-2">
          <p className="text-sm text-slate-500">
            请先下载模板，按模板填写后上传 .xlsx 文件。
          </p>
          <Button
            theme="borderless"
            type="primary"
            onClick={() => getQuestionImportTemplate()}
          >
            下载导入模板
          </Button>
          <Upload accept=".xlsx" limit={1} action="" uploadTrigger="custom" beforeUpload={({ file }) => { setImportFile(file.fileInstance || null); return { shouldUpload: false }; }} onRemove={() => setImportFile(null)}><Button>选择导入文件</Button></Upload>
        </div>
      </Modal>
    </PageShell>
  );
}
