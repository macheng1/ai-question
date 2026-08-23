"use client";

import { useEffect, useMemo, useState } from "react";
import Link from "next/link";
import "@douyinfe/semi-ui/react19-adapter";
import { Toast } from "@douyinfe/semi-ui";
import { PageShell } from "@/components/layout/PageShell";
import { ProfileSidebar } from "@/components/layout/ProfileSidebar";
import { getCurrentUser, type UserProfile } from "@/features/auth/api";
import { getManagedBanks } from "@/features/banks/management-api";
import { getPapers } from "@/features/papers/api";
import { useAuthStore } from "@/features/auth/store";

export default function ProfilePage() {
  const sessionUser = useAuthStore((state) => state.user);
  const [user, setUser] = useState<UserProfile | null>(null);
  const [bankCount, setBankCount] = useState(0);
  const [questionCount, setQuestionCount] = useState(0);
  const [paperCount, setPaperCount] = useState(0);
  const [mounted, setMounted] = useState(false);

  useEffect(() => {
    setMounted(true);
    const token = useAuthStore.getState().accessToken;
    if (!token) return;
    getCurrentUser()
      .then((profile) => setUser(profile))
      .catch(() => undefined);
    getManagedBanks()
      .then((banks) => {
        setBankCount(banks.total || banks.records.length);
        setQuestionCount(
          banks.records.reduce(
            (sum, item) => sum + (item.questionCount || 0),
            0,
          ),
        );
      })
      .catch(() => undefined);
    getPapers()
      .then((papers) => setPaperCount(papers.total || papers.records.length))
      .catch(() => undefined);
  }, []);

  const displayName =
    user?.name || sessionUser?.name || sessionUser?.phone || "游客用户";
  const stats = useMemo(
    () => [
      ["题库数量", bankCount],
      ["题目数量", questionCount],
      ["试卷数量", paperCount],
    ],
    [bankCount, questionCount, paperCount],
  );
  if (!mounted)
    return (
      <PageShell>
        <main className="mx-auto min-h-[700px] w-[92%] max-w-[1160px] py-16" />
      </PageShell>
    );
  if (!sessionUser && !useAuthStore.getState().accessToken)
    return (
      <PageShell>
        <main className="mx-auto flex min-h-[700px] w-[92%] max-w-[1160px] items-center justify-center">
          <div className="rounded-2xl border border-slate-200 bg-white p-10 text-center shadow-sm">
            <h1 className="text-2xl font-bold text-slate-800">请先登录</h1>
            <p className="mt-2 text-sm text-slate-500">
              登录后可以查看考试记录和个人数据
            </p>
            <Link
              className="mt-6 inline-block rounded-lg bg-[#f97316] px-6 py-3 text-sm font-bold text-white no-underline"
              href="/login"
            >
              登录 / 注册
            </Link>
          </div>
        </main>
      </PageShell>
    );

  return (
    <PageShell>
      <main className="mx-auto flex min-h-[720px] w-[92%] max-w-[1160px] flex-col gap-6 py-10 sm:py-14 md:flex-row">
        <ProfileSidebar />
        <section className="min-w-0 flex-1">
          <h1 className="text-2xl font-bold text-slate-800">概览</h1>
          <div className="mt-6 grid gap-4 sm:grid-cols-3">
            {stats.map(([label, value]) => (
              <div
                key={label as string}
                className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm"
              >
                <p className="text-xs text-slate-400">{label}</p>
                <strong className="mt-2 block text-3xl font-bold text-blue-500">
                  {value}
                </strong>
              </div>
            ))}
          </div>
          <h2 className="mt-7 text-base font-bold text-slate-600">个人中心</h2>
          <div className="mt-4 grid gap-4 lg:grid-cols-3">
            <Link
              href="/profile/exams"
              className="rounded-2xl bg-white p-5 no-underline shadow-[0_8px_30px_rgba(15,23,42,.04)] transition hover:-translate-y-0.5 hover:shadow-lg"
            >
              <div className="flex items-center justify-between">
                <span className="grid h-10 w-10 place-items-center rounded-xl bg-blue-50 text-lg text-blue-600">
                  ▧
                </span>
                <span className="text-xl text-slate-400">→</span>
              </div>
              <h3 className="mt-4 font-bold text-slate-800">我的考试</h3>
              <p className="mt-2 text-xs text-slate-400">
                查看我的考试记录与成绩
              </p>
            </Link>
            <Link
              href="/profile/mistakes"
              className="rounded-2xl bg-white p-5 no-underline shadow-[0_8px_30px_rgba(15,23,42,.04)] transition hover:-translate-y-0.5 hover:shadow-lg"
            >
              <div className="flex items-center justify-between">
                <span className="grid h-10 w-10 place-items-center rounded-xl bg-red-50 text-lg text-red-500">
                  ▤
                </span>
                <span className="text-xl text-slate-400">→</span>
              </div>
              <h3 className="mt-4 font-bold text-slate-800">我的错题</h3>
              <p className="mt-2 text-xs text-slate-400">
                回顾答错的题目，针对性复习
              </p>
            </Link>
            <Link
              href="/profile/favorites"
              className="rounded-2xl bg-white p-5 no-underline shadow-[0_8px_30px_rgba(15,23,42,.04)] transition hover:-translate-y-0.5 hover:shadow-lg"
            >
              <div className="flex items-center justify-between">
                <span className="grid h-10 w-10 place-items-center rounded-xl bg-orange-50 text-lg text-orange-500">
                  ☆
                </span>
                <span className="text-xl text-slate-400">→</span>
              </div>
              <h3 className="mt-4 font-bold text-slate-800">我的收藏</h3>
              <p className="mt-2 text-xs text-slate-400">我收藏的公开题库</p>
            </Link>
          </div>
          <h2 className="mt-7 text-base font-bold text-slate-600">账号设置</h2>
          <div className="mt-4 flex flex-col gap-4 rounded-xl border border-blue-400 bg-white p-5 shadow-sm sm:flex-row sm:items-center sm:justify-between">
            <div className="flex items-center gap-3">
              <span className="text-xl text-blue-500">♢</span>
              <div>
                <b className="block text-sm text-slate-800">账号设置</b>
                <span className="mt-1 block text-xs text-slate-400">
                  {displayName} · {user?.phone || sessionUser?.phone || ""}
                </span>
              </div>
            </div>
            <div className="flex gap-5 text-sm">
              <button
                onClick={() => Toast.info("用户名修改功能即将开放")}
                className="text-slate-600 hover:text-blue-600"
              >
                修改用户名
              </button>
              <button
                onClick={() => Toast.info("密码修改功能即将开放")}
                className="text-slate-600 hover:text-blue-600"
              >
                修改密码
              </button>
            </div>
          </div>
        </section>
      </main>
    </PageShell>
  );
}
