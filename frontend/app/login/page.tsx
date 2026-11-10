"use client";

import Image from "next/image";
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
    <main className="relative isolate flex min-h-dvh items-center justify-center overflow-hidden bg-[#3949a3] px-4 py-10">
      <Image
        src="/images/login-training.webp"
        alt=""
        fill
        priority
        sizes="100vw"
        className="pointer-events-none -z-20 object-cover object-center"
      />
      <Card
        className="w-full max-w-[460px] !rounded-2xl !border-white/80 !bg-white shadow-[0_24px_80px_-20px_rgba(15,45,75,0.3)]"
        bodyStyle={{ padding: 28 }}
      >
        <div className="mb-6 flex items-center gap-3">
          <Image src="/brand-logo.png" alt="智企云考" width={48} height={48} />
          <div>
            <Typography.Title heading={3}>智企云考</Typography.Title>
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
            className="!mt-5"
          >
            {creating ? "创建企业账号" : "登录"}
          </Button>
        </Form>
        <div className="mt-5 text-center">
          {type === "ADMIN" ? (
            <Button theme="borderless" onClick={() => setCreating((v) => !v)}>
              {creating ? "返回管理员登录" : "创建企业账号"}
            </Button>
          ) : (
            <Typography.Text type="secondary">
              员工账号由企业管理员创建，忘记密码请联系管理员。
            </Typography.Text>
          )}
        </div>
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
