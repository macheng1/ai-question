"use client";
import { Layout } from "@douyinfe/semi-ui";
import { Header } from "./Header";
import { Footer } from "./Footer";

export function PageShell({children}:{children:React.ReactNode}){return <Layout style={{ minHeight: "100vh" }}><Header/><Layout.Content>{children}</Layout.Content><Footer/></Layout>}
