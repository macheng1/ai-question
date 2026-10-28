"use client";

import { AppIcon } from "@/components/ui/AppIcon";

import Link from "next/link";
import Image from "next/image";
import { useEffect, useState } from "react";
import { usePathname } from "next/navigation";
import { Button, Modal } from "@douyinfe/semi-ui";
import "@douyinfe/semi-ui/react19-adapter";
import { getCurrentUser, type UserProfile } from "@/features/auth/api";
import { useAuthStore } from "@/features/auth/store";

export function Header() {
  const [open, setOpen] = useState(false);
  const [mounted, setMounted] = useState(false);
  const [logoutOpen, setLogoutOpen] = useState(false);
  const pathname = usePathname();
  const user = useAuthStore((state) => state.user);
  const setSession = useAuthStore((state) => state.setSession);
  const clearSession = useAuthStore((state) => state.clearSession);

  useEffect(() => {
    setMounted(true);
    const token = useAuthStore.getState().accessToken;
    if (!token) return;
    getCurrentUser()
      .then((profile: UserProfile) => setSession({ phone: profile.phone, name: profile.name }, token))
      .catch(() => clearSession());
  }, [clearSession, setSession]);

  const close = () => setOpen(false);
  const logout = () => { setLogoutOpen(true); close(); };
  const confirmLogout = () => { clearSession(); setLogoutOpen(false); };

  const isActive = (href: string) => href === "/" ? pathname === href : pathname === href || pathname.startsWith(`${href}/`);
  const navClass = (href: string) => `relative whitespace-nowrap rounded-lg px-3 py-2 text-sm transition ${isActive(href) ? "font-bold text-[#239f99] after:absolute after:inset-x-3 after:-bottom-3 after:h-0.5 after:rounded-full after:bg-[#35c5bd]" : "font-medium text-slate-500 hover:bg-[#effbfb] hover:text-[#239f99]"}`;

  return <><header className="sticky top-0 z-40 border-b border-slate-200/80 bg-white/95 shadow-[0_1px_12px_rgba(15,23,42,.04)] backdrop-blur">
    <div className="relative mx-auto flex min-h-[72px] w-[92%] max-w-[1240px] items-center justify-between gap-3">
      <Link aria-label="返回首页" className="flex shrink-0 items-center gap-3 text-slate-800 no-underline" href="/">
        <Image src="/brand-logo.png" alt="智企云考 Logo" width={44} height={44} priority className="h-11 w-11 object-contain" />
        <span><strong className="block text-[15px] tracking-wide">智企云考</strong><small className="mt-1 block text-[10px] text-slate-400">企业培训考试，一站式管理</small></span>
      </Link>
      <Button className="rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-600 shadow-sm sm:hidden" onClick={() => setOpen(!open)} aria-label="打开导航"><AppIcon name="menu" /></Button>
      <nav className={`${open ? "flex" : "hidden"} absolute left-0 right-0 top-[72px] z-20 flex-col gap-1 border-b border-slate-200 bg-white p-4 shadow-xl sm:absolute sm:left-1/2 sm:right-auto sm:top-1/2 sm:flex sm:w-max sm:flex-row sm:items-center sm:gap-2 sm:whitespace-nowrap sm:border-0 sm:bg-transparent sm:p-0 sm:shadow-none sm:-translate-x-1/2 sm:-translate-y-1/2`}>
        <Link onClick={close} className={navClass("/")} href="/">首页</Link>
        <Link onClick={close} className={navClass("/banks")} href="/banks">题库大全</Link>
        <Link onClick={close} className={navClass("/self-test")} href="/self-test">在线自测</Link>
        <Link onClick={close} className={`${navClass("/paid-questions")} flex items-center gap-1.5`} href="/paid-questions">
          有偿出题<span className="rounded-full bg-[#ff7a00] px-1.5 py-0.5 text-[9px] font-bold leading-none text-white">HOT</span>
        </Link>
        <Link onClick={close} className={navClass("/news")} href="/news">公告资讯</Link>
        <Link onClick={close} className={navClass("/guide")} href="/guide">帮助手册</Link>
        {mounted && user ? <div className="group relative sm:hidden">
          <Link onClick={close} href="/profile" className={`${navClass("/profile")} flex items-center gap-2 sm:ml-2`}>
            <span className="grid h-7 w-7 place-items-center rounded-full bg-[#dff8f6] text-xs font-bold text-[#239f99]">{(user.name || user.phone).slice(0, 1)}</span>
            <span className="max-w-24 truncate">{user.name || user.phone}</span>
          </Link>
          <div className="absolute right-0 top-10 hidden w-32 rounded-lg border border-slate-200 bg-white p-1 shadow-lg group-hover:block">
            <Link onClick={close} href="/profile" className="block rounded-md px-3 py-2 text-xs text-slate-600 no-underline hover:bg-[#effbfb] hover:text-[#239f99]">个人中心</Link>
            <Button onClick={logout} className="block w-full rounded-md px-3 py-2 text-left text-xs text-slate-500 hover:bg-orange-50 hover:text-orange-600">退出登录</Button>
          </div>
        </div> : <Link onClick={close} className="rounded-lg bg-[#ff8500] px-4 py-2.5 text-center text-sm font-bold text-white shadow-sm shadow-orange-100 transition hover:bg-[#eb7600] sm:hidden" href="/login">登录/注册</Link>}
      </nav>
      {mounted && user ? <div className="group relative hidden shrink-0 sm:block">
        <Link onClick={close} href="/profile" className={`${navClass("/profile")} flex items-center gap-2 border-b-2 border-[#35c5bd] px-3 py-3`}>
          <span className="grid h-7 w-7 place-items-center rounded-full bg-[#dff8f6] text-xs font-bold text-[#239f99]">{(user.name || user.phone).slice(0, 1)}</span>
          <span className="max-w-28 truncate">{user.name || user.phone}</span>
        </Link>
        <div className="absolute right-0 top-12 hidden w-32 rounded-lg border border-slate-200 bg-white p-1 shadow-lg group-hover:block">
          <Link onClick={close} href="/profile" className="block rounded-md px-3 py-2 text-xs text-slate-600 no-underline hover:bg-[#effbfb] hover:text-[#239f99]">个人中心</Link>
          <Button onClick={logout} className="block w-full rounded-md px-3 py-2 text-left text-xs text-slate-500 hover:bg-orange-50 hover:text-orange-600">退出登录</Button>
        </div>
      </div> : <Link onClick={close} className="hidden rounded-lg bg-[#ff8500] px-4 py-2.5 text-center text-sm font-bold text-white shadow-sm shadow-orange-100 transition hover:bg-[#eb7600] sm:block" href="/login">登录/注册</Link>}
    </div>
  </header><Modal title="确认退出登录" visible={logoutOpen} onCancel={() => setLogoutOpen(false)} footer={<div className="flex justify-end gap-3"><Button className="modal-cancel-button" onClick={() => setLogoutOpen(false)}>取消</Button><Button theme="solid" type="warning" onClick={confirmLogout}>确认退出</Button></div>}>确定要退出当前账号吗？</Modal></>;
}
