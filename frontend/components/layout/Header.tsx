"use client";

import Image from "next/image";
import { IconBookOpenStroked } from "@douyinfe/semi-icons";
import Link from "next/link";
import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import {
  Avatar,
  Button,
  Dropdown,
  Layout,
  Modal,
  Space,
  Tag,
} from "@douyinfe/semi-ui";
import { useCurrentSession } from "@/features/auth/store";
import { learnerEntry } from "@/features/auth/learner-session";
import { accountHome, accountSettings } from "@/features/auth/navigation";

export function Header() {
  const router = useRouter();
  const { user, clearSession } = useCurrentSession();
  const [mounted, setMounted] = useState(false);
  const [logoutOpen, setLogoutOpen] = useState(false);
  useEffect(() => setMounted(true), []);
  const identity = mounted && user?.accountType ? user : null;
  const home = identity ? accountHome(identity.accountType) : "/login";
  return (
    <Layout.Header className="border-b border-slate-200 bg-white">
      <div className="mx-auto flex flex-wrap min-h-16 w-[96%] max-w-[1800px] items-center justify-between gap-3">
        <Link href={home} className="flex shrink-0 items-center py-2 no-underline">
          <Image src="/casela-logo.png" alt="CASELA Technologies" width={151} height={40} className="h-auto w-[121px] object-contain sm:w-[151px]" />
        </Link>
        {identity ? (
          <Space className="ml-auto max-w-full" wrap>
            {identity.accountType === "ADMIN" && (
              <a
                href="/manuals/admin-guide.docx"
                download="企业管理员操作手册.docx"
                aria-label="下载企业管理员操作手册"
                title="下载企业管理员操作手册"
                className="inline-flex h-9 w-9 items-center justify-center rounded text-blue-600 hover:bg-blue-50 focus-visible:outline-2 focus-visible:outline-blue-600"
              >
                <IconBookOpenStroked size="large" aria-hidden="true" />
              </a>
            )}
            <Tag>
              {identity.accountType === "ADMIN" ? "企业管理员" : "员工"}
            </Tag>
            <Dropdown
              trigger="click"
              position="bottomRight"
              render={
                <Dropdown.Menu>
                  <Dropdown.Item onClick={() => router.push(home)}>
                    个人中心
                  </Dropdown.Item>
                  <Dropdown.Item
                    onClick={() =>
                      router.push(accountSettings(identity.accountType))
                    }
                  >
                    {identity.accountType === "EMPLOYEE" ? "员工资料" : "账号资料与密码"}
                  </Dropdown.Item>
                  <Dropdown.Divider />
                  <Dropdown.Item onClick={() => setLogoutOpen(true)}>
                    退出登录
                  </Dropdown.Item>
                </Dropdown.Menu>
              }
            >
              <Button
                theme="borderless"
                icon={
                  <Avatar size="extra-small">
                    {identity.name?.slice(0, 1)}
                  </Avatar>
                }
              >
                <span className="block max-w-[45vw] truncate">{identity.name || identity.phone}</span>
              </Button>
            </Dropdown>
          </Space>
        ) : (
          <Button theme="solid" onClick={() => router.push("/login")}>
            登录
          </Button>
        )}
      </div>
      <Modal
        title="退出登录"
        visible={logoutOpen}
        onCancel={() => setLogoutOpen(false)}
        onOk={() => {
          clearSession();
          window.location.assign(identity?.accountType === "EMPLOYEE" ? (learnerEntry() || "/learner") : "/login");
        }}
      >
        确认退出当前账号？
      </Modal>
    </Layout.Header>
  );
}
