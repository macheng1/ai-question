import type { Metadata } from "next";
import "@douyinfe/semi-ui/react19-adapter";
import "./tailwind.css";
import "./globals.css";
import "./interaction.css";

export const metadata: Metadata = {
  title: "智企云考 | 企业考试平台",
  description: "面向企业培训与人才测评的题库、组卷、考试、判分和数据分析平台。",
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return <html lang="zh-CN" suppressHydrationWarning><body>{children}</body></html>;
}
