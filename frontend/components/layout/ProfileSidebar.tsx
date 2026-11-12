"use client";
import { useState } from "react";
import { usePathname, useRouter } from "next/navigation";
import { Button, Modal, Nav, Select, Space, Toast } from "@douyinfe/semi-ui";
import {
  IconAlertCircle, IconBook, IconCalendar, IconExit, IconFile,
  IconHistogram, IconHistory, IconLayers, IconLikeThumb,
  IconUserGroup,
} from "@douyinfe/semi-icons";
import { useCurrentSession } from "@/features/auth/store";

import { learnerEntry } from "@/features/auth/learner-session";
import { accountMenus, accountHome } from "@/features/auth/navigation";

const menuIcons = {
  "/profile/dashboard": IconHistogram,
  "/profile/trainings": IconBook,
  "/profile/banks": IconLayers,
  "/profile/papers": IconFile,
  "/profile/assessments": IconCalendar,
  "/profile/organization": IconUserGroup,
  "/learner": IconBook,
  "/learner/records": IconHistory,
  "/learner/exams": IconCalendar,
  "/learner/mistakes": IconAlertCircle,
  "/learner/surveys": IconLikeThumb,
};

/**
 * 个人中心及管理页面共用的侧边导航。
 *
 * 集中处理菜单路径匹配、当前项高亮、退出登录和移动端导航，
 * 避免各页面分别维护一套几乎相同的侧边栏导致行为不一致。
 */
export function ProfileSidebar() {
  const { user, clearSession } = useCurrentSession();
  const accountType = user?.accountType;
  const menu = accountType ? accountMenus[accountType] : [];
  const pathname = usePathname();
  const router = useRouter();
  const [logoutOpen, setLogoutOpen] = useState(false);

  const isActive = (href: string) =>
    href === (accountType ? accountHome(accountType) : "/profile")
      ? pathname === href
      : pathname === href || pathname.startsWith(`${href}/`);

  const logout = () => {
    setLogoutOpen(true);
  };

  const confirmLogout = () => {
    clearSession();
    setLogoutOpen(false);
    Toast.success("已退出登录");
    window.location.href = accountType === "EMPLOYEE" ? (learnerEntry() || "/learner") : "/login";
  };

  const selectedKey = menu.find((item) => isActive(item.href))?.href;
  return (
    <>
      <div className="hidden md:block md:w-56 md:shrink-0">
        <Nav
          style={{ width: "100%" }}
          selectedKeys={selectedKey ? [selectedKey] : []}
          items={menu.map((item) => {
            const Icon = menuIcons[item.href as keyof typeof menuIcons];
            return {
              itemKey: item.href,
              text: item.label,
              icon: Icon ? <Icon size="large" aria-hidden="true" /> : undefined,
            };
          })}
          onSelect={({ itemKey }) => router.push(String(itemKey))}
          footer={
            <Button block icon={<IconExit aria-hidden="true" />} onClick={logout}>
              退出登录
            </Button>
          }
        />
      </div>
      <div className="flex w-full items-center gap-3 md:hidden">
        <Select
          aria-label="工作台菜单"
          className="min-w-0 flex-1"
          value={selectedKey}
          placeholder="选择功能"
          optionList={menu.map((item) => ({ value: item.href, label: item.label }))}
          onChange={(value) => router.push(String(value))}
        />
        <Button icon={<IconExit aria-hidden="true" />} onClick={logout}>退出登录</Button>
      </div>
      <Modal
        bodyStyle={{ maxHeight: "calc(100dvh - 240px)", overflowY: "auto" }}
        width="min(480px, calc(100vw - 24px))"
        title="确认退出登录"
        visible={logoutOpen}
        onCancel={() => setLogoutOpen(false)}
        footer={
          <Space wrap>
            <Button
              className="modal-cancel-button"
              onClick={() => setLogoutOpen(false)}
            >
              取消
            </Button>
            <Button theme="solid" type="primary" onClick={confirmLogout}>
              确认退出
            </Button>
          </Space>
        }
      >
        确定要退出当前账号吗？
      </Modal>
    </>
  );
}
