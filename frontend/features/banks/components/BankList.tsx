"use client";
import { IconBookStroked, IconArrowRight } from "@douyinfe/semi-icons";
import { Typography } from "@douyinfe/semi-ui";
import { Layout, Card } from "@douyinfe/semi-ui";


import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useMemo, useState } from "react";
import { IconBook, IconSearch, IconStar, IconStarStroked } from "@douyinfe/semi-icons";
import { Button, Input } from "@douyinfe/semi-ui";
import { addFavoriteBank, getFavoriteBankIds, getPublicBankCategories, getQuestionBanks, removeFavoriteBank } from "../api";
import { useAuthStore } from "@/features/auth/store";
import { Toast } from "@douyinfe/semi-ui";
import type { PublicBankCategory, QuestionBank } from "../types";

const iconStyles = ["bg-sky-100 text-sky-600", "bg-amber-100 text-amber-600", "bg-emerald-100 text-emerald-600", "bg-violet-100 text-violet-600"];

export function BankList() {
  const router = useRouter();
  const accessToken = useAuthStore((state) => state.accessToken);
  const [banks, setBanks] = useState<QuestionBank[]>([]);
  const [categoryRecords, setCategoryRecords] = useState<PublicBankCategory[]>([]);
  const [keyword, setKeyword] = useState("");
  const [category, setCategory] = useState("全部");
  const [favorites, setFavorites] = useState<number[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (accessToken) getFavoriteBankIds().then(setFavorites).catch(() => setFavorites([])); else setFavorites([]);
    Promise.all([getQuestionBanks({ pageSize: 100 }), getPublicBankCategories()])
      .then(([page, categories]) => { setBanks(page.records); setCategoryRecords(categories); })
      .catch(() => undefined)
      .finally(() => setLoading(false));
  }, [accessToken]);

  const categories = useMemo(() => ["全部", ...categoryRecords.filter(item => item.parentId == null).map(item => item.name)], [categoryRecords]);
  const visible = useMemo(() => banks.filter(item => {
    const text = `${item.name}${item.source}${item.description ?? ""}`;
    return (category === "全部" || item.category === category) && (!keyword.trim() || text.includes(keyword.trim()));
  }), [banks, category, keyword]);
  const toggleFavorite = async (id: number) => {
    if (!accessToken) { router.push(`/login?redirect=${encodeURIComponent("/banks")}`); return; }
    try {
      if (favorites.includes(id)) { await removeFavoriteBank(id); setFavorites(favorites.filter(item => item !== id)); Toast.success("已取消收藏"); }
      else { await addFavoriteBank(id); setFavorites([...favorites, id]); Toast.success("收藏成功"); }
    } catch (error) { Toast.error(error instanceof Error ? error.message : "收藏操作失败"); }
  };

  return <Layout.Content>
    <Card bodyStyle={{ padding: 0 }} className=""><div className="p-6">
      <div className="flex flex-wrap items-center gap-2 text-sm text-slate-500"><IconBook /><span>分类</span>
        {categories.map(item => <Button key={item} theme={category === item ? "solid" : "light"} type={category === item ? "primary" : "tertiary"} className="!rounded-full" onClick={() => setCategory(item)}>{item}</Button>)}
      </div>
      <div className="mt-6 max-w-sm"><Input prefix={<IconSearch />} value={keyword} onChange={setKeyword} placeholder="搜索题库名称..." showClear /></div>
      <div className="mt-6 border-t border-slate-100 pt-5 text-sm text-slate-500">共 <span className="font-bold text-[#35c5bd]">{visible.length}</span> 个题库</div>
    </div></Card>
    {loading && <div className="py-16 text-center text-sm text-slate-400">正在加载题库...</div>}
    {!loading && visible.length === 0 && <div className="py-20 text-center text-sm text-slate-400">暂无题库</div>}
    <div className="mt-7 grid gap-5 md:grid-cols-2 lg:grid-cols-3">{visible.map((bank, index) => <Card bodyStyle={{ padding: 0 }} key={bank.id} className="transition hover:-translate-y-0.5 hover:border-[#46c9c0] hover:shadow-lg"><div className="p-7">
      <div className="flex items-start justify-between"><span className={`grid h-12 w-12 place-items-center rounded-xl ${iconStyles[index % iconStyles.length]}`}><IconBook /></span><Button theme="borderless" className={favorites.includes(bank.id) ? "!border !border-[#35c5bd] !text-[#35c5bd]" : "!border !border-slate-200"} icon={favorites.includes(bank.id) ? <IconStar /> : <IconStarStroked />} onClick={() => toggleFavorite(bank.id)}>{favorites.includes(bank.id) ? "已收藏" : "收藏"}</Button></div>
      <Typography.Title heading={2} className="!mt-6 !text-lg !font-bold !text-slate-800">{bank.name}</Typography.Title><p className="mt-2 min-h-10 text-sm leading-6 text-slate-500">{bank.source || "公开题库"}</p>
      <div className="mt-5 flex items-center justify-between border-t border-slate-100 pt-5 text-sm text-slate-400"><span><IconBookStroked /> 共 {bank.questionCount} 道</span><Link href={`/banks/${bank.id}`} className="font-semibold text-[#35c5bd] no-underline">查看题库 <IconArrowRight /></Link></div>
    </div></Card>)}</div>
  </Layout.Content>;
}
