"use client";
import { Typography, Space, Tag, Banner } from "@douyinfe/semi-ui";
import { Layout, Card } from "@douyinfe/semi-ui";

import { IconArrowLeft, IconPlus, IconDownload, IconUpload } from "@douyinfe/semi-icons";

import { useEffect, useCallback, useRef, useState } from "react";
import { useParams } from "next/navigation";
import "@douyinfe/semi-ui/react19-adapter";
import { Table, Button, Input, Modal, Select, TextArea, Toast, Upload, InputNumber } from "@douyinfe/semi-ui";
import { ProfileShell } from "@/components/layout/ProfileShell";
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
  type QuestionImportResult,
} from "@/features/banks/management-api";

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
  const [pageNo, setPageNo] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [total, setTotal] = useState(0);
  const [listLoading, setListLoading] = useState(false);
  const [search, setSearch] = useState("");
  const [uploadVersion, setUploadVersion] = useState(0);
  const [importResult, setImportResult] = useState<QuestionImportResult | null>(null);
  const requestId = useRef(0);
  const load = useCallback(async () => {
    const currentRequest = ++requestId.current;
    setListLoading(true);
    try {
      const page = await getBankQuestions(bankId, pageNo, pageSize, search);
      if (currentRequest !== requestId.current) return;
      if (pageNo > 1 && !page.records.length) { setPageNo(pageNo - 1); return; }
      setQuestions(page.records);
      setTotal(page.total);
    } catch (e) {
      if (currentRequest === requestId.current) Toast.error(e instanceof Error ? e.message : "题目加载失败");
    } finally {
      if (currentRequest === requestId.current) setListLoading(false);
    }
  }, [bankId, pageNo, pageSize, search]);
  useEffect(() => {
    if (!bankId) return;
    getManagedBanks()
      .then((page) =>
        setBankName(
          page.records.find((item) => item.id === bankId)?.name || "题库",
        ),
      )
      .catch(() => undefined);
  }, [bankId]);
  useEffect(() => { if (bankId) void load(); }, [bankId, load]);
  const runSearch = () => { setPageNo(1); setSearch(keyword.trim()); };
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
  const hasOptions = type === "SINGLE" || type === "MULTIPLE";
  const save = async () => {
    if (!stem.trim()) {
      Toast.error("请输入题干");
      return;
    }
    if (
      hasOptions &&
      options.some((item) => !item.optionText.trim())
    ) {
      Toast.error("请填写完整选项");
      return;
    }
    if (type === "SHORT_ANSWER" && !answer.trim()) return Toast.error("请填写问答题参考答案");
    setLoading(true);
    try {
      const selectedOptions =
        !hasOptions
          ? []
          : options.map((item, index) => ({ ...item, sortNo: index }));
      const correct =
        !hasOptions
          ? answer
          : selectedOptions
              .filter((item) => item.correct)
              .map((item) => item.optionKey)
              .join(",");
      const payload = {
        questionType: type,
        stem: stem.trim(),
        answerText: correct,
        analysis: type === "SHORT_ANSWER" ? "" : analysis,
        difficulty,
        score: Number(score) || 0,
        options: selectedOptions,
      };
      if (editingId) {
        await updateQuestion(editingId, payload);
      } else await createQuestion({ ...payload, bankId });
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
    <ProfileShell>
          <Typography.Text link={{ href: "/profile/banks" }} icon={<IconArrowLeft size="small" />} className="!inline-flex !min-h-8 !items-center !text-sm">返回题库管理</Typography.Text>
          <Typography.Title heading={1} className="!mt-5 !m-0 !text-[28px] !font-semibold !text-[#183f35]">
            题库管理 -- 题目管理
          </Typography.Title>
          <Layout.Content className="mt-6 rounded-2xl border border-blue-200 bg-white p-5">
            <div className="flex flex-wrap items-center gap-3 border-b border-slate-100 pb-4 text-sm text-slate-500">
              当前题库：<b className="text-slate-800">{bankName}</b>
              <span className="rounded-full bg-slate-100 px-3 py-1">
                共 {total} 题
              </span>
            </div>
            <div className="mt-4 flex flex-col gap-3 sm:flex-row sm:items-center">
              <Input
                value={keyword}
                onChange={setKeyword}
                onEnterPress={runSearch}
                showClear
                placeholder="搜索题干..."
                className="w-full sm:!w-80"
              />
              <Button onClick={runSearch}>查询</Button>
              <span className="flex-1" />
              <Button theme="borderless" onClick={() => { setImportResult(null); setImportFile(null); setImportOpen(true); }}>
                模板导入试题
              </Button>
              <Button
                theme="solid"
                onClick={() => setOpen(true)}
              >
                <IconPlus aria-hidden="true" /> 新增题目
              </Button>
            </div>
          </Layout.Content>
          <Card bodyStyle={{ padding: 0 }} className="!mt-5 overflow-hidden">
            <Table
              style={{ width: "100%" }} loading={listLoading}
              empty={search ? "没有匹配的题目" : "暂无题目，点击右上角新增或导入"}
              dataSource={questions} rowKey="id"
              pagination={{ currentPage: pageNo, pageSize, total, showSizeChanger: true, pageSizeOpts: [10, 20, 50],
                onPageChange: setPageNo,
                onPageSizeChange: (size) => { setPageSize(size); setPageNo(1); } }}
              scroll={{ x: 960 }}
              columns={[
                { title: "ID", dataIndex: "id", width: 90 },
                { title: "题型", dataIndex: "questionType", width: 100,
                  render: (value: string) => <Tag color={value === "SHORT_ANSWER" ? "green" : value === "MULTIPLE" ? "violet" : value === "TRUE_FALSE" ? "cyan" : "blue"}>{value === "SHORT_ANSWER" ? "问答" : value === "MULTIPLE" ? "多选" : value === "TRUE_FALSE" ? "判断" : "单选"}</Tag> },
                { title: "题干", dataIndex: "stem", render: (value: string) => <div className="whitespace-normal break-words leading-6">{value}</div> },
                { title: "难度", dataIndex: "difficulty", width: 90, render: difficultyLabel },
                { title: "答案", dataIndex: "answerText", width: 120, render: (value: string, item: QuestionItem) => <span className="whitespace-pre-wrap break-words">{item.questionType === "SHORT_ANSWER" ? value : answerLabel(value)}</span> },
                { title: "操作", width: 160, fixed: "right",
                  render: (_value: unknown, item: QuestionItem) => <Space spacing={4}>
                    <Button theme="borderless" onClick={() => edit(item.id)}>编辑</Button>
                    <Button theme="borderless" type="danger" onClick={() => remove(item.id)}>删除</Button>
                  </Space> },
              ]}
            />
          </Card>

      <Modal bodyStyle={{ maxHeight: "calc(100dvh - 240px)", overflowY: "auto" }}
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
        width="min(700px, calc(100vw - 24px))"
        footer={
          <Space wrap >
            <Button className="modal-cancel-button" onClick={reset}>取消</Button>
            <Button
              theme="solid"
              type="warning"
              loading={loading}
              onClick={save}
            >
              保存
            </Button>
          </Space>
        }
      >
        <div className="flex flex-col gap-4 py-2">
          <div className="grid grid-cols-2 gap-4 ">
            <div className="min-w-0 col-span-2 sm:col-span-1"><Select
              value={type}
              onChange={(value) => { setType(String(value)); setAnswer(""); }}
              optionList={[
                { label: "单选题", value: "SINGLE" },
                { label: "多选题", value: "MULTIPLE" },
                { label: "判断题", value: "TRUE_FALSE" },
                { label: "问答题", value: "SHORT_ANSWER" },
              ]}
              className="w-full"
            /></div>
            <div className="min-w-0 col-span-2 sm:col-span-1"><Select
              value={difficulty}
              onChange={(value) => setDifficulty(String(value))}
              optionList={[
                { label: "简单", value: "EASY" },
                { label: "中等", value: "MEDIUM" },
                { label: "困难", value: "HARD" },
              ]}
              className="w-full"
            /></div>
          </div>
          <TextArea
            value={stem}
            onChange={setStem}
            autosize={{ minRows: 3, maxRows: 6 }}
            placeholder="题干内容"
          />
          {hasOptions && (
            <div className="rounded-xl border border-slate-200 p-4">
              <div className="mb-3 flex justify-between text-sm text-slate-600">
                <span>选项</span>
                <Button theme="borderless" disabled={options.length >= 26}
                  onClick={() =>
                    setOptions([
                      ...options,
                      {
                        optionKey: Array.from({ length: 26 }, (_, i) => String.fromCharCode(65 + i)).find(key => !options.some(option => option.optionKey === key))!,
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
            <p className="mb-2 text-xs text-slate-500">{type === "SHORT_ANSWER" ? "参考答案" : "正确选项"}</p>
            {type === "SHORT_ANSWER" ? (
              <TextArea value={answer} onChange={setAnswer} autosize={{ minRows: 3, maxRows: 8 }} placeholder="填写参考答案或评分要点" />
            ) : type === "TRUE_FALSE" ? (
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
              <Space wrap >
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
              </Space>
            )}
          </div>
          {type !== "SHORT_ANSWER" && (
            <TextArea
              value={analysis}
              onChange={setAnalysis}
              autosize={{ minRows: 2, maxRows: 4 }}
              placeholder="题目解析（可选）"
            />
          )}
          <div className="grid grid-cols-2 gap-4 ">
            <div className="min-w-0 col-span-2 sm:col-span-1"><InputNumber
              value={score}
              onChange={value => setScore(String(value ?? ""))}

              prefix="分值"
            /></div>
          </div>
        </div>
      </Modal>
      <Modal bodyStyle={{ maxHeight: "calc(100dvh - 240px)", overflowY: "auto" }} width="min(560px, calc(100vw - 24px))"
        title="模板导入试题"
        visible={importOpen}
        onCancel={() => {
          setImportOpen(false);
          setImportFile(null);
        }}
        okText="确定导入"
        cancelText={importResult ? "关闭" : "取消"}
        confirmLoading={importLoading}
        okButtonProps={{ disabled: !importFile || importLoading }}
        cancelButtonProps={{ disabled: importLoading }}
        closable={!importLoading}
        maskClosable={!importLoading}
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
            setImportResult(result);
            setUploadVersion((version) => version + 1);
            setImportFile(null);
            load();
          } catch (e) {
            Toast.error(e instanceof Error ? e.message : "导入失败");
          } finally {
            setImportLoading(false);
          }
        }}
      >
        <div className="flex flex-col gap-5 py-2">
          <div className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-slate-200 p-4">
            <div className="min-w-0">
              <Typography.Text strong>1. 下载并填写模板</Typography.Text>
              <div className="mt-1 text-xs leading-5 text-slate-500">支持单选、多选、判断和问答题，请先替换示例题。</div>
            </div>
            <Button icon={<IconDownload />} disabled={importLoading}
              onClick={() => { getQuestionImportTemplate().catch((e) => Toast.error(e instanceof Error ? e.message : "模板下载失败")); }}>
              下载模板
            </Button>
          </div>
          <div className="min-w-0">
            <div className="mb-3"><Typography.Text strong>2. 上传题目文件</Typography.Text></div>
            <Upload
              key={`${importOpen}-${uploadVersion}`}
              style={{ width: "100%" }}
              disabled={importLoading}
              accept=".xlsx"
              limit={1}
              action=""
              uploadTrigger="custom"
              draggable
              dragIcon={<IconUpload size="extra-large" />}
              dragMainText="点击选择文件，或拖拽文件到此处"
              dragSubText="仅支持 .xlsx 格式，每次导入一个文件"
              onAcceptInvalid={() => Toast.error("请选择 .xlsx 格式的文件")}
              onExceed={() => Toast.warning("每次只能导入一个文件，请先移除已选文件")}
              onChange={({ fileList }) => setImportFile(fileList[0]?.fileInstance || null)}
              onRemove={() => setImportFile(null)}
            />
            <div className="mt-3 text-xs leading-5 text-slate-500">
              重复题干自动跳过；有效行正常导入，失败行会显示原因，修正后可再次导入。
            </div>
          </div>
          {importResult && <Banner type={importResult.errors.length ? "warning" : "success"}
            title={`新增 ${importResult.imported} 题，重复跳过 ${importResult.skipped} 题，失败 ${importResult.errors.length} 行`}
            description={importResult.errors.length ? <div className="max-h-48 overflow-y-auto">{importResult.errors.map((error, index) => <div key={index}>{error}</div>)}</div> : "导入完成，可关闭窗口查看题目。"} />}
        </div>
      </Modal>
    </ProfileShell>
  );
}
