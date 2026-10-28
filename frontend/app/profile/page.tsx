"use client";

import { AppIcon } from "@/components/ui/AppIcon";

import { useEffect, useMemo, useState } from "react";
import Link from "next/link";
import "@douyinfe/semi-ui/react19-adapter";
import { Toast, Button, Input, TextArea } from "@douyinfe/semi-ui";
import { PageShell } from "@/components/layout/PageShell";
import { ProfileSidebar } from "@/components/layout/ProfileSidebar";
import {
  changePassword,
  getCurrentUser,
  updateProfile,
  type UserProfile,
} from "@/features/auth/api";
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
  const [dialog, setDialog] = useState<"profile" | "password" | null>(null);
  const [saving, setSaving] = useState(false);
  const [profileForm, setProfileForm] = useState({ name: "", unitName: "", address: "" });
  const [passwordForm, setPasswordForm] = useState({ currentPassword: "", newPassword: "", confirmPassword: "" });

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
  const openProfileDialog = () => {
    setProfileForm({
      name: user?.name ?? sessionUser?.name ?? "",
      unitName: user?.unitName ?? "",
      address: user?.address ?? "",
    });
    setDialog("profile");
  };
  const openPasswordDialog = () => {
    setPasswordForm({ currentPassword: "", newPassword: "", confirmPassword: "" });
    setDialog("password");
  };
  const saveProfile = async () => {
    if (!profileForm.name.trim() || !profileForm.unitName.trim()) {
      Toast.error("请填写用户名和企业名称");
      return;
    }
    setSaving(true);
    try {
      const updated = await updateProfile({
        name: profileForm.name.trim(),
        unitName: profileForm.unitName.trim(),
        address: profileForm.address.trim(),
      });
      setUser(updated);
      const auth = useAuthStore.getState();
      if (auth.accessToken) auth.setSession({ phone: updated.phone, name: updated.name }, auth.accessToken);
      Toast.success("用户信息已更新");
      setDialog(null);
    } catch (error) {
      Toast.error(error instanceof Error ? error.message : "保存失败，请稍后重试");
    } finally {
      setSaving(false);
    }
  };
  const savePassword = async () => {
    if (!passwordForm.currentPassword || passwordForm.newPassword.length < 6) {
      Toast.error("请输入当前密码和至少 6 位的新密码");
      return;
    }
    if (passwordForm.newPassword !== passwordForm.confirmPassword) {
      Toast.error("两次输入的新密码不一致");
      return;
    }
    setSaving(true);
    try {
      await changePassword(passwordForm);
      Toast.success("密码已修改");
      setDialog(null);
    } catch (error) {
      Toast.error(error instanceof Error ? error.message : "修改失败，请稍后重试");
    } finally {
      setSaving(false);
    }
  };
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
                  <AppIcon name="book" />
                </span>
                <span className="text-xl text-slate-400"><AppIcon name="arrowRight" /></span>
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
                  <AppIcon name="file" />
                </span>
                <span className="text-xl text-slate-400"><AppIcon name="arrowRight" /></span>
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
                  <AppIcon name="star" />
                </span>
                <span className="text-xl text-slate-400"><AppIcon name="arrowRight" /></span>
              </div>
              <h3 className="mt-4 font-bold text-slate-800">我的收藏</h3>
              <p className="mt-2 text-xs text-slate-400">我收藏的公开题库</p>
            </Link>
          </div>
          <h2 className="mt-7 text-base font-bold text-slate-600">账号设置</h2>
          <div className="mt-4 flex flex-col gap-4 rounded-xl border border-blue-400 bg-white p-5 shadow-sm sm:flex-row sm:items-center sm:justify-between">
            <div className="flex items-center gap-3">
              <span className="text-xl text-blue-500"><AppIcon name="user" /></span>
              <div>
                <b className="block text-sm text-slate-800">账号设置</b>
                <span className="mt-1 block text-xs text-slate-400">
                  {displayName} · {user?.phone || sessionUser?.phone || ""}
                </span>
              </div>
            </div>
            <div className="flex gap-5 text-sm">
              <Button
                onClick={openProfileDialog}
                className="text-slate-600 hover:text-blue-600"
              >
                修改用户信息
              </Button>
              <Button
                onClick={openPasswordDialog}
                className="text-slate-600 hover:text-blue-600"
              >
                修改密码
              </Button>
            </div>
          </div>
        </section>
      </main>
      {dialog && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 p-4"
          onMouseDown={() => !saving && setDialog(null)}
        >
          <div
            role="dialog"
            aria-modal="true"
            className="w-full max-w-lg rounded-2xl bg-white p-6 shadow-2xl"
            onMouseDown={(event) => event.stopPropagation()}
          >
            <div className="flex items-center justify-between">
              <h2 className="text-lg font-bold text-slate-800">
                {dialog === "profile" ? "修改用户信息" : "修改密码"}
              </h2>
              <Button
                aria-label="关闭"
                className="text-2xl leading-none text-slate-400 hover:text-slate-700"
                onClick={() => !saving && setDialog(null)}
              >
                <AppIcon name="close" />
              </Button>
            </div>
            {dialog === "profile" ? (
              <div className="mt-6 space-y-4">
                <label className="block text-sm font-medium text-slate-700">
                  用户名
                  <Input
                    value={profileForm.name}
                    onChange={(value) => setProfileForm({ ...profileForm, name: value })}
                    className="mt-2 w-full rounded-lg border border-slate-200 px-3 py-2.5 outline-none transition focus:border-teal-400 focus:ring-2 focus:ring-teal-100"
                    maxLength={80}
                  />
                </label>
                <label className="block text-sm font-medium text-slate-700">
                  企业名称
                  <Input
                    value={profileForm.unitName}
                    onChange={(value) => setProfileForm({ ...profileForm, unitName: value })}
                    className="mt-2 w-full rounded-lg border border-slate-200 px-3 py-2.5 outline-none transition focus:border-teal-400 focus:ring-2 focus:ring-teal-100"
                    maxLength={160}
                  />
                </label>
                <label className="block text-sm font-medium text-slate-700">
                  地址（选填）
                  <TextArea
                    value={profileForm.address}
                    onChange={(value) => setProfileForm({ ...profileForm, address: value })}
                    className="mt-2 min-h-24 w-full resize-y rounded-lg border border-slate-200 px-3 py-2.5 outline-none transition focus:border-teal-400 focus:ring-2 focus:ring-teal-100"
                    maxLength={255}
                  />
                </label>
              </div>
            ) : (
              <div className="mt-6 space-y-4">
                {([
                  ["当前密码", "currentPassword"],
                  ["新密码", "newPassword"],
                  ["确认新密码", "confirmPassword"],
                ] as const).map(([label, key]) => (
                  <label key={key} className="block text-sm font-medium text-slate-700">
                    {label}
                    <Input
                      type="password"
                      value={passwordForm[key]}
                      onChange={(value) => setPasswordForm({ ...passwordForm, [key]: value })}
                      className="mt-2 w-full rounded-lg border border-slate-200 px-3 py-2.5 outline-none transition focus:border-teal-400 focus:ring-2 focus:ring-teal-100"
                      maxLength={64}
                    />
                  </label>
                ))}
              </div>
            )}
            <div className="mt-7 flex justify-end gap-3">
              <Button
                className="rounded-lg border border-slate-200 px-5 py-2.5 text-sm text-slate-600 hover:bg-slate-50"
                onClick={() => setDialog(null)}
                disabled={saving}
              >
                取消
              </Button>
              <Button
                className="rounded-lg bg-[#f97316] px-5 py-2.5 text-sm font-bold text-white hover:bg-[#ea580c] disabled:cursor-not-allowed disabled:opacity-60"
                onClick={dialog === "profile" ? saveProfile : savePassword}
                disabled={saving}
              >
                {saving ? "保存中..." : "保存"}
              </Button>
            </div>
          </div>
        </div>
      )}
    </PageShell>
  );
}
