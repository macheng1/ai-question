"use client";

import Image from "next/image";
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
  Typography,
} from "@douyinfe/semi-ui";
import { useAuthStore } from "@/features/auth/store";
import { accountHome, accountSettings } from "@/features/auth/navigation";

export function Header() {
  const router = useRouter();
  const user = useAuthStore((state) => state.user);
  const clearSession = useAuthStore((state) => state.clearSession);
  const [mounted, setMounted] = useState(false);
  const [logoutOpen, setLogoutOpen] = useState(false);
  useEffect(() => setMounted(true), []);
  const identity = mounted && user?.accountType ? user : null;
  const home = identity ? accountHome(identity.accountType) : "/login";
  return (
    <Layout.Header className="border-b border-slate-200 bg-white">
      <div className="mx-auto flex min-h-16 w-[96%] max-w-[1800px] items-center justify-between gap-3">
        <Link href={home} className="flex shrink-0 flex-col items-start gap-1 py-2 no-underline sm:flex-row sm:items-center sm:gap-3">
          <Image src="/casela-logo.png" alt="CASELA Technologies" width={151} height={40} className="h-auto w-[121px] object-contain sm:w-[151px]" />
          <Typography.Text strong>e-learning</Typography.Text>
        </Link>
        {identity ? (
          <Space>
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
                    账号资料与密码
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
                {identity.name || identity.phone}
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
          window.location.assign("/login");
        }}
      >
        确认退出当前账号？
      </Modal>
    </Layout.Header>
  );
}
