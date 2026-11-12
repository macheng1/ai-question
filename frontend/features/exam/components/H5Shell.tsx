"use client";
import { Layout } from "@douyinfe/semi-ui";
import type { ReactNode } from "react";
import Link from "next/link";
import { IconChevronLeft } from "@douyinfe/semi-icons";

export function H5Shell({ title, backHref = "/learner", children }: { title: string; backHref?: string; children: ReactNode }) {
  return <Layout.Content className="exam-h5">
    <Layout.Header className="exam-desktop-header print-hidden">
      <div className="exam-desktop-header-inner">
        <Link href={backHref} className="exam-desktop-logo"><img src="/casela-logo.png" width={166} height={44} alt="CASELA Technologies" className="object-contain" /><span>e-learning<small>企业培训与考试平台</small></span></Link>
        <span className="exam-desktop-section">{title}</span>
        <Link href={backHref} className="exam-desktop-back"><IconChevronLeft /> 返回</Link>
      </div>
    </Layout.Header>
    <div className="h5-frame">
    <Layout.Header className="h5-nav print-hidden"><Link href={backHref} aria-label="返回"><IconChevronLeft /></Link><span>{title}</span><span className="h5-brand">e-learning</span></Layout.Header>
    {children}
  </div></Layout.Content>;
}
