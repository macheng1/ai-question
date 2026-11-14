"use client";
import { flushActiveProgress } from "@/features/trainings/progress-navigation";
import { useRouter } from "next/navigation";
import { Layout, Toast } from "@douyinfe/semi-ui";
import type { ReactNode } from "react";
import Link from "next/link";
import { IconChevronLeft } from "@douyinfe/semi-icons";

export function H5Shell({ title, backHref = "/learner", children }: { title: string; backHref?: string; children: ReactNode }) {
  const router=useRouter();
  const leave=async(action:()=>void)=>{try{await flushActiveProgress();action();}catch(e){Toast.error(e instanceof Error?e.message:"进度保存失败，请重试");}};
  return <Layout.Content className="exam-h5">
    <Layout.Header className="exam-desktop-header print-hidden">
      <div className="exam-desktop-header-inner">
        <Link href={backHref} onClick={e=>{e.preventDefault();void leave(()=>router.push(backHref));}} className="exam-desktop-logo"><img src="/casela-logo.png" width={166} height={44} alt="CASELA Technologies" className="object-contain" /><span>e-learning<small>企业培训与考试平台</small></span></Link>
        <span className="exam-desktop-section">{title}</span>
        <button type="button" className="exam-desktop-back" onClick={() => void leave(()=>window.history.back())}><IconChevronLeft /> 返回</button>
      </div>
    </Layout.Header>
    <div className="h5-frame">
    <Layout.Header className="h5-nav print-hidden"><button type="button" aria-label="返回" onClick={() => void leave(()=>window.history.back())}><IconChevronLeft /></button><span>{title}</span><span className="h5-brand">e-learning</span></Layout.Header>
    {children}
  </div></Layout.Content>;
}
