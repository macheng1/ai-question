"use client";

import { AppIcon } from "@/components/ui/AppIcon";

import { useEffect, useState } from "react";
import Link from "next/link";
import { Button, Empty, Tag, Toast } from "@douyinfe/semi-ui";
import { IconStar } from "@douyinfe/semi-icons";
import "@douyinfe/semi-ui/react19-adapter";
import { PageShell } from "@/components/layout/PageShell";
import { ProfileSidebar } from "@/components/layout/ProfileSidebar";
import { getFavoriteBankIds, getQuestionBank, removeFavoriteBank } from "@/features/banks/api";
import type { QuestionBank } from "@/features/banks/types";

/** 个人中心的收藏题库页面，收藏状态和题库详情均来自后端。 */
export default function FavoritesPage() {
  const [banks, setBanks] = useState<QuestionBank[]>([]);
  const [loading, setLoading] = useState(true);

  const loadFavorites = async () => {
    setLoading(true);
    try {
      const ids = await getFavoriteBankIds();
      const results = await Promise.all(ids.map((id) => getQuestionBank(id).catch(() => null)));
      setBanks(results.filter((bank): bank is QuestionBank => Boolean(bank)));
    } catch (error) {
      Toast.error(error instanceof Error ? error.message : "收藏加载失败");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void loadFavorites();
  }, []);

  const removeFavorite = async (id: number) => {
    try {
      await removeFavoriteBank(id);
      setBanks((current) => current.filter((bank) => bank.id !== id));
      Toast.success("已取消收藏");
    } catch (error) {
      Toast.error(error instanceof Error ? error.message : "取消收藏失败");
    }
  };

  return (
    <PageShell>
      <main className="mx-auto flex min-h-[720px] w-[92%] max-w-[1160px] flex-col gap-6 py-10 sm:py-14 md:flex-row">
        <ProfileSidebar />
        <section className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <div>
              <Link href="/profile" className="text-sm text-slate-400 no-underline hover:text-blue-500">
                <AppIcon name="arrowLeft" /> 返回个人中心
              </Link>
              <h1 className="mt-4 text-2xl font-bold text-slate-800">我的收藏</h1>
              <p className="mt-2 text-sm text-slate-500">查看我收藏的公开题库和题目</p>
            </div>
            <Link href="/banks" className="no-underline">
              <Button theme="solid" type="primary">浏览题库大全</Button>
            </Link>
          </div>

          <div className="mt-6 rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
            {loading ? (
              <div className="py-20 text-center text-sm text-slate-400">正在加载收藏...</div>
            ) : banks.length === 0 ? (
              <Empty description="暂无收藏题库，去题库大全看看吧" />
            ) : (
              <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
                {banks.map((bank) => (
                  <article key={bank.id} className="rounded-xl border border-slate-200 p-5 transition hover:-translate-y-0.5 hover:border-blue-300 hover:shadow-md">
                    <div className="flex items-start justify-between gap-3">
                      <div className="grid h-10 w-10 place-items-center rounded-xl bg-orange-50 text-xl text-orange-500"><IconStar /></div>
                      <Tag color="blue">已收藏</Tag>
                    </div>
                    <h2 className="mt-4 line-clamp-2 min-h-12 text-base font-bold text-slate-800">{bank.name}</h2>
                    <p className="mt-2 line-clamp-2 min-h-10 text-sm text-slate-500">{bank.description || bank.source || "公开题库"}</p>
                    <div className="mt-4 flex items-center justify-between border-t border-slate-100 pt-4 text-sm">
                      <span className="text-slate-400">共 {bank.questionCount || 0} 道题</span>
                      <div className="flex items-center gap-3">
                        <Link href={`/banks/${bank.id}`} className="text-blue-500 no-underline hover:text-blue-600">查看</Link>
                        <Button htmlType="button" onClick={() => void removeFavorite(bank.id)} className="text-slate-400 hover:text-red-500">取消收藏</Button>
                      </div>
                    </div>
                  </article>
                ))}
              </div>
            )}
          </div>
        </section>
      </main>
    </PageShell>
  );
}
