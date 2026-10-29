"use client";
import type { ReactNode } from "react";
import Link from "next/link";
import { IconChevronLeft } from "@douyinfe/semi-icons";

export function H5Shell({ title, backHref = "/profile", children }: { title: string; backHref?: string; children: ReactNode }) {
  return <main className="exam-h5"><div className="h5-frame">
    <header className="h5-nav print-hidden"><Link href={backHref} aria-label="返回"><IconChevronLeft /></Link><span>{title}</span><span className="h5-brand">智企云考</span></header>
    {children}
  </div></main>;
}
