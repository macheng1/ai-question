"use client";

import Image from "next/image";
import styles from "./login.module.css";
import { Suspense, useEffect, useState } from "react";
import { useSearchParams } from "next/navigation";
import {
  Button,
  Card,
  Form,
  Tabs,
  TabPane,
  Toast,
  Typography,
} from "@douyinfe/semi-ui";
import {
  login,
  register,
  sendCaptcha,
  type AccountType,
} from "@/features/auth/api";
import { accountHome } from "@/features/auth/navigation";
import { useAuthStore } from "@/features/auth/store";

function LoginForm() {
  const search = useSearchParams();
  const [type, setType] = useState<AccountType>(
    search.get("type") === "EMPLOYEE" ? "EMPLOYEE" : "ADMIN",
  );
  const [creating, setCreating] = useState(false);
  const [busy, setBusy] = useState(false);
  const [phone, setPhone] = useState("");
  const [countdown, setCountdown] = useState(0);
  useEffect(() => {
    if (!countdown) return;
    const timer = setTimeout(() => setCountdown((v) => v - 1), 1000);
    return () => clearTimeout(timer);
  }, [countdown]);
  const captcha = async () => {
    if (!/^1\d{10}$/.test(phone)) {
      Toast.error("请输入正确手机号");
      return;
    }
    try {
      const result = await sendCaptcha(phone);
      Toast.success(
        result.testCode ? `验证码：${result.testCode}` : result.message,
      );
      setCountdown(60);
    } catch (e) {
      Toast.error(e instanceof Error ? e.message : "发送失败");
    }
  };
  return (
    <main className="relative isolate flex min-h-screen items-center justify-center bg-slate-50 px-5 py-8 sm:bg-[#3949a3] sm:py-10">
      <Image
        src="/images/login-training.webp"
        alt=""
        fill
        priority
        sizes="100vw"
        className="pointer-events-none -z-20 hidden object-cover object-center sm:block"
      />
      <div aria-hidden="true" className="pointer-events-none absolute inset-0 -z-10 hidden bg-[#14204a]/20 sm:block" />
      <Card
        className={`w-full max-w-[460px] !rounded-2xl !border-white/80 !bg-white shadow-[0_12px_40px_-16px_rgba(15,45,75,0.25)] ${styles.card}`}
      >
        <div className="mb-6 flex flex-col items-center gap-4 text-center">
          <Image src="/casela-logo.png" alt="CASELA Technologies" width={200} height={53} className="h-auto max-w-full object-contain" priority />
          <div>
            <Typography.Title heading={3} style={{ margin: "0 0 4px", fontSize: 24 }}>e-learning</Typography.Title>
            <Typography.Text type="secondary">
              企业培训与考试平台
            </Typography.Text>
          </div>
        </div>
        <Tabs
          activeKey={type}
          onChange={(key) => {
            setType(key as AccountType);
            setCreating(false);
          }}
        >
          <TabPane tab="企业管理员" itemKey="ADMIN" />
          <TabPane tab="员工登录" itemKey="EMPLOYEE" />
        </Tabs>
        <Form
          className={styles.form}
          key={`${type}-${creating}`}
          onSubmit={async (values) => {
            setBusy(true);
            try {
              const credentials = {
                phone: String(values.phone).trim(),
                password: String(values.password),
              };
              const result = creating
                ? await register({
                    ...credentials,
                    name: String(values.name).trim(),
                    unitName: String(values.unitName).trim(),
                    captcha: String(values.captcha),
                    confirmPassword: String(values.confirmPassword),
                  })
                : await login(credentials, type);
              useAuthStore
                .getState()
                .setSession(result, result.token.accessToken);
              const redirect = search.get("redirect");
              const home = accountHome(result.accountType);
              const safe =
                redirect?.startsWith("/") &&
                !redirect.startsWith("//") &&
                !redirect.includes("\\") &&
                !(
                  result.accountType === "EMPLOYEE" &&
                  redirect.startsWith("/profile")
                ) &&
                !(
                  result.accountType === "ADMIN" &&
                  redirect.startsWith("/learner")
                );
              window.location.assign(safe && redirect ? redirect : home);
            } catch (e) {
              Toast.error(e instanceof Error ? e.message : "登录失败");
            } finally {
              setBusy(false);
            }
          }}
        >
          <Form.Input
            field="phone"
            label="手机号"
            placeholder="请输入手机号"
            autoComplete="username"
            maxLength={11}
            size="large"
            onChange={setPhone}
            rules={[
              { required: true, message: "请输入手机号" },
              { pattern: /^1\d{10}$/, message: "请输入正确手机号" },
            ]}
          />
          {creating && (
            <>
              <Form.Input
                field="name"
                label="管理员姓名"
                maxLength={80}
                rules={[{ required: true, whitespace: true }]}
              />
              <Form.Input
                field="unitName"
                label="企业名称"
                maxLength={160}
                rules={[{ required: true, whitespace: true }]}
              />
              <Form.Input
                field="captcha"
                label="验证码"
                rules={[{ required: true }]}
                suffix={
                  <Button
                    theme="borderless"
                    disabled={countdown > 0}
                    onClick={captcha}
                  >
                    {countdown ? `${countdown}秒` : "获取验证码"}
                  </Button>
                }
              />
            </>
          )}
          <Form.Input
            field="password"
            label="密码"
            placeholder="请输入密码"
            autoComplete={creating ? "new-password" : "current-password"}
            mode="password"
            size="large"
            maxLength={64}
            rules={[
              { required: true, message: "请输入密码" },
              { min: 6, message: "密码至少6位" },
            ]}
          />
          {creating && (
            <Form.Input
              field="confirmPassword"
              label="确认密码"
              mode="password"
              maxLength={64}
              rules={[{ required: true }]}
            />
          )}
          <Button
            htmlType="submit"
            theme="solid"
            block
            size="large"
            loading={busy}
            className="!mt-5 !h-11"
          >
            {creating ? "创建企业账号" : "登录"}
          </Button>
        </Form>
        {type === "EMPLOYEE" && (
          <div className="mt-5 text-center">
            <Typography.Text type="secondary">
              员工账号由企业管理员创建，忘记密码请联系管理员。
            </Typography.Text>
          </div>
        )}
      </Card>
    </main>
  );
}
export default function LoginPage() {
  return (
    <Suspense>
      <LoginForm />
    </Suspense>
  );
}
