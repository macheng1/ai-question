"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { Empty, Spin, Tag } from "@douyinfe/semi-ui";
import "@douyinfe/semi-ui/react19-adapter";
import { PageShell } from "@/components/layout/PageShell";
import { ProfileSidebar } from "@/components/layout/ProfileSidebar";
import { getCurrentUser, type UserProfile } from "@/features/auth/api";
import { useAuthStore } from "@/features/auth/store";
import { getMyExamRecords, getMyMistakes } from "@/features/profile/api";
import type { MistakeRecord, ProfileExamRecord } from "@/features/exam/api";

type Props = { mode: "exams" | "mistakes" };

function formatDuration(seconds?: number) {
  if (!seconds && seconds !== 0) return "-";
  if (seconds < 60) return `${seconds}秒`;
  return `${Math.floor(seconds / 60)}分${seconds % 60}秒`;
}

/** 将接口返回的 ISO 时间统一显示为中国用户易读的本地时间。 */
function formatDateTime(value?: string) {
  if (!value) return "-";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;

  const parts = new Intl.DateTimeFormat("zh-CN", {
    timeZone: "Asia/Shanghai",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
    hour12: false,
  }).formatToParts(date);
  const get = (type: string) => parts.find((part) => part.type === type)?.value ?? "";
  return `${get("year")}-${get("month")}-${get("day")} ${get("hour")}:${get("minute")}:${get("second")}`;
}

/**
 * “我的考试”只展示每个考试任务最新的一次答题。
 * 后端仍保留并返回完整历史，成绩统计和重考记录不受影响。
 */
function latestExamRecords(records: ProfileExamRecord[]) {
  const latest = new Map<number, ProfileExamRecord>();
  for (const record of records) {
    const current = latest.get(record.taskId);
    if (!current || record.attemptNo > current.attemptNo ||
      (record.attemptNo === current.attemptNo && record.attemptId > current.attemptId)) {
      latest.set(record.taskId, record);
    }
  }
  return [...latest.values()].sort((a, b) => {
    const aTime = Date.parse(a.submittedAt || a.startedAt || "") || 0;
    const bTime = Date.parse(b.submittedAt || b.startedAt || "") || 0;
    return bTime - aTime || b.attemptId - a.attemptId;
  });
}

function normalizeQuestionType(type: string | undefined) {
  return String(type ?? "").trim().toUpperCase().replace(/[\s_-]/g, "");
}

function isJudgeType(type: string | undefined) {
  const normalized = normalizeQuestionType(type);
  return normalized === "TRUEFALSE" || normalized === "JUDGE" || String(type ?? "").includes("判断");
}

function answerSet(value: string | undefined, type: string) {
  const normalized = String(value ?? "").trim().toUpperCase();
  if (isJudgeType(type)) {
    if (normalized === "TRUE" || normalized === "A" || normalized.includes("正确")) return new Set(["A"]);
    if (normalized === "FALSE" || normalized === "B" || normalized.includes("错误")) return new Set(["B"]);
  }
  return new Set(normalized.split(/[,，、\s|]+/).map((item) => item.split(".")[0]).filter(Boolean));
}

function formatAnswer(value: string | undefined, type: string) {
  if (!String(value ?? "").trim()) return "未作答";
  const answers = answerSet(value, type);
  if (isJudgeType(type)) {
    if (answers.has("A")) return "A. 正确";
    if (answers.has("B")) return "B. 错误";
  }
  return [...answers].join("、") || "未作答";
}

function questionTypeLabel(type: string) {
  if (type === "MULTIPLE" || type === "MULTI" || type.includes("多选")) return "多选题";
  if (isJudgeType(type)) return "判断题";
  return "单选题";
}

function ProfileHeader({ user }: { user: UserProfile }) {
  return <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
    <div className="flex items-center gap-4"><div className="grid h-16 w-16 place-items-center rounded-full bg-blue-500 text-2xl font-bold text-white">{user.name?.slice(0, 1) || "用"}</div><div><h2 className="text-xl font-bold text-slate-800">{user.name}</h2><p className="text-sm text-slate-500">{user.roleCode === "ADMIN" ? "企业管理员" : "考生"}</p></div></div>
    <div className="mt-5 grid gap-3 sm:grid-cols-3"><Info label="手机号" value={user.phone} /><Info label="所属单位" value={user.unitName || "-"} /><Info label="地址" value={[user.province, user.city, user.district, user.address].filter(Boolean).join(" ") || "-"} /></div>
  </div>;
}
function Info({ label, value }: { label: string; value: string }) { return <div className="rounded-xl bg-slate-50 p-3"><p className="text-xs text-slate-400">{label}</p><p className="mt-2 text-sm text-slate-700">{value}</p></div>; }

function ExamTable({ records }: { records: ProfileExamRecord[] }) {
  return <div className="overflow-hidden rounded-xl border border-slate-200"><div className="grid grid-cols-5 bg-slate-50 px-5 py-3 text-xs text-slate-500"><span>考试名称</span><span>得分</span><span>是否及格</span><span>交卷时间</span><span>用时</span></div>{records.map((record) => <Link key={record.attemptId} href={record.status === "IN_PROGRESS" ? `/kaoshi/${record.taskId}/answer?attemptId=${record.attemptId}` : `/kaoshi/${record.taskId}/result?attemptId=${record.attemptId}`} className="grid grid-cols-5 items-center border-t border-slate-100 px-5 py-4 text-sm no-underline hover:bg-slate-50"><span className="text-slate-700">{record.examName}</span><span className={record.status === "IN_PROGRESS" ? "text-slate-500" : "font-semibold text-red-500"}>{record.status === "IN_PROGRESS" ? "-" : `${record.score ?? 0}`}</span><span><Tag color={record.status === "IN_PROGRESS" ? "blue" : record.passed ? "green" : "red"}>{record.status === "IN_PROGRESS" ? "答题中" : record.passed ? "合格" : "不合格"}</Tag></span><span className="text-slate-500">{formatDateTime(record.submittedAt)}</span><span className="text-slate-500">{formatDuration(record.durationSeconds)}</span></Link>)}</div>;
}

function MistakeCard({ item, index }: { item: MistakeRecord; index: number }) {
  const selected = answerSet(item.userAnswer, item.questionType); const correct = answerSet(item.correctAnswer, item.questionType);
  const options = item.options?.length ? item.options : isJudgeType(item.questionType) ? [{ key: "A", text: "正确" }, { key: "B", text: "错误" }] : [];
  return <article className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm"><div className="flex items-center gap-2"><span className="rounded bg-blue-50 px-2 py-1 text-xs text-blue-600">{questionTypeLabel(item.questionType)}</span><span className="text-xs text-slate-400">{item.bankName || item.examName}</span></div><h3 className="mt-4 text-base font-semibold leading-7 text-slate-800">{index + 1}. {item.stem}</h3>{options.length > 0 && <div className="mt-4 grid gap-2 sm:grid-cols-2">{options.map((option) => { const optionKey = option.key.toUpperCase(); const isCorrect = correct.has(optionKey); const isSelected = selected.has(optionKey); return <div key={option.key} className={`rounded-lg px-3 py-2 text-sm ${isCorrect ? "bg-green-50 text-green-700" : isSelected ? "bg-red-50 text-red-600" : "text-slate-600"}`}>{option.key}. {option.text}<span className="float-right text-xs">{isCorrect ? "正确答案" : isSelected ? "你的选择" : ""}</span></div>; })}</div>}<div className="mt-4 border-t border-slate-100 pt-3 text-sm"><p><span className="text-slate-500">你的答案：</span><span className="text-red-500">{formatAnswer(item.userAnswer, item.questionType)}</span><span className="ml-5 text-slate-500">正确答案：</span><span className="text-green-600">{formatAnswer(item.correctAnswer, item.questionType)}</span></p>{item.analysis && <p className="mt-3 rounded-lg bg-amber-50 px-3 py-2 text-slate-600">解析：{item.analysis}</p>}</div></article>;
}

export function ProfileRecordsPage({ mode }: Props) {
  const accessToken = useAuthStore((state) => state.accessToken);
  const [user, setUser] = useState<UserProfile | null>(null); const [records, setRecords] = useState<ProfileExamRecord[]>([]); const [mistakes, setMistakes] = useState<MistakeRecord[]>([]); const [loading, setLoading] = useState(true);
  useEffect(() => { if (!accessToken) { setLoading(false); return; } setLoading(true); Promise.all([getCurrentUser(), mode === "exams" ? getMyExamRecords() : getMyMistakes()]).then(([profile, data]) => { setUser(profile); if (mode === "exams") setRecords(latestExamRecords(data as ProfileExamRecord[])); else setMistakes(data as MistakeRecord[]); }).catch(() => undefined).finally(() => setLoading(false)); }, [mode, accessToken]);
  if (!accessToken) return <PageShell><main className="mx-auto flex min-h-[700px] w-[92%] items-center justify-center"><Link href="/login" className="rounded-lg bg-orange-500 px-6 py-3 text-white no-underline">请先登录</Link></main></PageShell>;
  return <PageShell><main className="mx-auto flex min-h-[720px] w-[92%] max-w-[1160px] flex-col gap-6 py-10 md:flex-row"><ProfileSidebar /><section className="min-w-0 flex-1"><ProfileHeader user={user || { userId: 0, name: "", phone: "" }} /><div className="mt-6 rounded-2xl border border-slate-200 bg-white p-5 shadow-sm"><div className="mb-5 flex items-center justify-between"><h1 className="text-xl font-bold text-slate-800">{mode === "exams" ? "我的考试" : "我的错题"}</h1><span className="text-sm text-slate-400">共 {mode === "exams" ? records.length : mistakes.length} 条记录</span></div>{loading ? <div className="flex justify-center py-16"><Spin /></div> : mode === "exams" ? records.length ? <ExamTable records={records} /> : <Empty description="暂无考试记录" /> : mistakes.length ? <div className="space-y-4">{mistakes.map((item, index) => <MistakeCard key={`${item.attemptId}-${item.questionId}`} item={item} index={index} />)}</div> : <Empty description="暂无错题" />}</div></section></main></PageShell>;
}
