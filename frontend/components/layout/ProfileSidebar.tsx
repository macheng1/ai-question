"use client";

import Link from "next/link";
import { useState } from "react";
import { usePathname } from "next/navigation";
import "@douyinfe/semi-ui/react19-adapter";
import { Button, Modal, Toast } from "@douyinfe/semi-ui";
import { useAuthStore } from "@/features/auth/store";

const menu = [
  { label: "概览", href: "/profile" },
  { label: "题库管理", href: "/profile/banks" },
  { label: "试卷管理", href: "/profile/papers" },
  { label: "培训管理", href: "/profile/trainings" },
  { label: "考试管理", href: "/profile/assessments" },
];

/**
 * 个人中心及管理页面共用的侧边导航。
 *
 * 集中处理菜单路径匹配、当前项高亮、退出登录和移动端导航，
 * 避免各页面分别维护一套几乎相同的侧边栏导致行为不一致。
 */
export function ProfileSidebar() {
  const pathname = usePathname();
  const clearSession = useAuthStore((state) => state.clearSession);
  const [logoutOpen, setLogoutOpen] = useState(false);

  const isActive = (href: string) =>
    href === "/profile"
      ? pathname === href
      : pathname === href || pathname.startsWith(`${href}/`);

  const logout = () => {
    setLogoutOpen(true);
  };

  const confirmLogout = () => {
    clearSession();
    setLogoutOpen(false);
    Toast.success("已退出登录");
    window.location.href = "/";
  };

  const links = (mobile = false) =>
    menu.map((item) => {
      const active = isActive(item.href);
      return (
        <Link
          key={item.label}
          href={item.href}
          aria-current={active ? "page" : undefined}
          className={
            mobile
              ? `shrink-0 rounded-lg px-3 py-2 text-sm font-semibold no-underline ${active ? "bg-[#2db9b0] text-white shadow-sm" : "bg-white text-slate-600"}`
              : `mb-1 block rounded-xl px-4 py-3 text-sm font-semibold no-underline transition ${active ? "bg-[#e8faf9] text-[#17645f]" : "text-slate-600 hover:bg-[#effbfb] hover:text-[#239f99]"}`
          }
        >
          {item.label}
        </Link>
      );
    });

  return (
    <>
      <aside className="hidden w-56 shrink-0 rounded-2xl border border-slate-200/80 bg-white p-3 shadow-[0_10px_35px_rgba(15,23,42,.06)] md:block">
        {links()}
        <div className="my-2 border-t border-slate-100" />
        <Button
          htmlType="button"
          onClick={logout}
          className="block w-full rounded-xl px-4 py-3 text-left text-sm font-semibold text-slate-500 hover:bg-orange-50 hover:text-orange-600"
        >
          退出登录
        </Button>
      </aside>
      <nav
        aria-label="个人中心导航"
        className="flex w-full gap-2 overflow-x-auto pb-1 md:hidden"
      >
        {links(true)}
        <Button
          htmlType="button"
          onClick={logout}
          className="shrink-0 rounded-lg bg-white px-3 py-2 text-sm font-semibold text-slate-500"
        >
          退出登录
        </Button>
      </nav>
      <Modal title="确认退出登录" visible={logoutOpen} onCancel={() => setLogoutOpen(false)} footer={<div className="flex justify-end gap-3"><Button className="modal-cancel-button" onClick={() => setLogoutOpen(false)}>取消</Button><Button theme="solid" type="warning" onClick={confirmLogout}>确认退出</Button></div>}>确定要退出当前账号吗？</Modal>
    </>
  );
}
