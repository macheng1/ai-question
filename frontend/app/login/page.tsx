"use client";

import Image from "next/image";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { Suspense, useState } from "react";
import "@douyinfe/semi-ui/react19-adapter";
import { Button, Input, Toast } from "@douyinfe/semi-ui";
import { IconEyeClosedStroked, IconEyeOpenedStroked } from "@douyinfe/semi-icons";
import { login, register as registerAccount, sendCaptcha } from "@/features/auth/api";
import { useAuthStore } from "@/features/auth/store";

function LoginForm() {
  const [register, setRegister] = useState(false);
  const [phone, setPhone] = useState("");
  const [captcha, setCaptcha] = useState("");
  const [name, setName] = useState("");
  const [unitName, setUnitName] = useState("");
  const [password, setPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);
  const [address, setAddress] = useState("");
  const [loading, setLoading] = useState(false);
  const [countdown, setCountdown] = useState(0);
  const setSession = useAuthStore((state) => state.setSession);
  const searchParams = useSearchParams();

  const field = (value: string, onChange: (value: string) => void, placeholder: string, type?: string, visible = false, onToggle?: () => void) => <Input className="w-full" size="large" value={value} onChange={onChange} placeholder={placeholder} type={visible ? "text" : type} suffix={type === "password" ? <button type="button" aria-label={visible ? "隐藏密码" : "显示密码"} className="grid h-7 w-7 place-items-center border-0 bg-transparent p-0 text-slate-400 transition hover:text-[#239f99]" onClick={onToggle}>{visible ? <IconEyeOpenedStroked /> : <IconEyeClosedStroked />}</button> : undefined} />;
  const getCaptcha = async () => {
    if (!/^1\d{10}$/.test(phone)) { Toast.error("请先输入正确手机号"); return; }
    try {
      const result = await sendCaptcha(phone);
      Toast.success(result.testCode ? `验证码：${result.testCode}` : "验证码已发送");
      setCountdown(60);
      const timer = window.setInterval(() => setCountdown((current) => { if (current <= 1) { window.clearInterval(timer); return 0; } return current - 1; }), 1000);
    } catch (error) { Toast.error(error instanceof Error ? error.message : "验证码发送失败"); }
  };
  const submit = async () => {
    if (!/^1\d{10}$/.test(phone) || password.length < 6) { Toast.error("请输入正确手机号和不少于 6 位的密码"); return; }
    if (register && (!name.trim() || !unitName.trim())) { Toast.error("请填写姓名和所属单位"); return; }
    setLoading(true);
    try {
      const result = register ? await registerAccount({ phone, captcha, password, confirmPassword, name, unitName, address }) : await login({ phone, password });
      setSession({ phone: result.phone, name: result.name }, result.token.accessToken);
      const redirect = searchParams.get("redirect");
      location.href = redirect?.startsWith("/") ? redirect : "/profile";
    } catch (error) { Toast.error(error instanceof Error ? error.message : "请求失败，请稍后重试"); } finally { setLoading(false); }
  };

  return <main className="min-h-screen bg-[#f5fbfa] px-4 py-6 sm:px-8 sm:py-10"><Link href="/" className="mx-auto flex w-fit items-center gap-2 text-sm text-slate-500 no-underline transition hover:text-[#239f99] sm:mx-0"><span>←</span> 返回首页</Link><div className="mx-auto grid min-h-[calc(100vh-100px)] w-full max-w-[1080px] items-center gap-10 lg:grid-cols-[.9fr_1fr] lg:gap-20">
    <section className="hidden lg:block"><Image src="/brand-logo.png" alt="智企云考 Logo" width={96} height={96} className="h-20 w-20 object-contain" priority /><p className="mt-7 text-sm font-bold tracking-[.22em] text-[#239f99]">ENTERPRISE ASSESSMENT</p><h1 className="mt-4 text-5xl font-bold leading-tight tracking-tight text-[#18353d]">让企业培训<br/><span className="text-[#239f99]">更有结果</span></h1><p className="mt-6 max-w-md text-base leading-8 text-slate-500">题库、组卷、考试、判分和数据分析，一站式帮助培训管理者完成员工考核。</p><div className="mt-9 grid max-w-md gap-3 text-sm text-slate-600"><span className="flex items-center gap-3"><i className="grid h-6 w-6 place-items-center rounded-full bg-[#dff8f6] text-xs not-italic text-[#239f99]">✓</i>员工免注册，扫码或考试码即可参加</span><span className="flex items-center gap-3"><i className="grid h-6 w-6 place-items-center rounded-full bg-[#dff8f6] text-xs not-italic text-[#239f99]">✓</i>固定组卷与随机抽题灵活复用</span><span className="flex items-center gap-3"><i className="grid h-6 w-6 place-items-center rounded-full bg-[#dff8f6] text-xs not-italic text-[#239f99]">✓</i>考试结束后自动汇总成绩</span></div></section>
    <section className="login-form-panel mx-auto w-full max-w-[500px] rounded-3xl border border-slate-200/80 bg-white p-6 shadow-[0_18px_55px_rgba(25,80,80,.10)] sm:p-9"><div className="flex items-center gap-3 lg:hidden"><Image src="/brand-logo.png" alt="智企云考 Logo" width={48} height={48} className="h-12 w-12 object-contain"/><div><strong className="block text-lg text-slate-800">智企云考</strong><span className="text-xs text-slate-400">企业培训考试，一站式管理</span></div></div><div className="mt-6 flex items-end gap-4 border-b border-slate-100 pb-4 lg:mt-0"><h2 className="text-2xl font-bold text-slate-800">{register ? "创建企业账号" : "企业账号登录"}</h2><span className="pb-0.5 text-sm text-slate-400">{register ? "开始管理你的培训考试" : "欢迎回来"}</span></div><div className="mt-7 space-y-4">{register ? <><div className="grid gap-4 sm:grid-cols-2"><label className="block text-sm font-semibold text-slate-700">手机号{field(phone, setPhone, "请输入手机号")}</label><label className="block text-sm font-semibold text-slate-700">验证码<div className="flex gap-2">{field(captcha, setCaptcha, "验证码")}<Button theme="borderless" type="primary" disabled={countdown > 0} onClick={getCaptcha}>{countdown ? `${countdown}s` : "获取验证码"}</Button></div></label></div><label className="block text-sm font-semibold text-slate-700">姓名{field(name, setName, "请输入管理员姓名")}</label><label className="block text-sm font-semibold text-slate-700">所属单位<span className="ml-2 font-normal text-slate-400">用于创建企业账号</span>{field(unitName, setUnitName, "请输入企业或单位名称")}</label><label className="block text-sm font-semibold text-slate-700">联系地址<span className="ml-2 font-normal text-slate-400">选填</span>{field(address, setAddress, "请输入联系地址")}</label><div className="grid gap-4 sm:grid-cols-2"><label className="block text-sm font-semibold text-slate-700">设置密码{field(password, setPassword, "不少于 6 位", "password", showPassword, () => setShowPassword(!showPassword))}</label><label className="block text-sm font-semibold text-slate-700">确认密码{field(confirmPassword, setConfirmPassword, "再次输入密码", "password", showConfirmPassword, () => setShowConfirmPassword(!showConfirmPassword))}</label></div></> : <><label className="block text-sm font-semibold text-slate-700">手机号{field(phone, setPhone, "请输入手机号")}</label><label className="block text-sm font-semibold text-slate-700">登录密码{field(password, setPassword, "请输入登录密码", "password", showPassword, () => setShowPassword(!showPassword))}</label></>}</div><Button theme="solid" type="warning" size="large" loading={loading} block className="mt-7 !h-12 !rounded-xl" onClick={submit}>{register ? "创建企业账号" : "登录"}</Button><p className="mt-5 text-center text-xs leading-6 text-slate-400">登录即表示你同意平台服务规则，企业数据仅用于本次账号服务。</p><div className="mt-2 text-center text-sm text-slate-400">{register ? "已有企业账号？" : "还没有企业账号？"}<button className="ml-1 border-0 bg-transparent p-0 font-semibold text-[#239f99]" onClick={() => setRegister(!register)}>{register ? "返回登录" : "立即创建"}</button></div></section>
  </div></main>;
}

export default function LoginPage() {
  return (
    <Suspense fallback={<main className="min-h-screen bg-[#f5fbfa]" />}>
      <LoginForm />
    </Suspense>
  );
}
