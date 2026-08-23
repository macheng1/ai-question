"use client";

import { useEffect, useMemo, useState } from "react";
import Link from "next/link";
import "@douyinfe/semi-ui/react19-adapter";
import { Button, Input, Modal, Select, Toast } from "@douyinfe/semi-ui";
import { PageShell } from "@/components/layout/PageShell";
import { ProfileSidebar } from "@/components/layout/ProfileSidebar";
import {
  createBank,
  deleteBank,
  getBankCategories,
  getManagedBanks,
  updateBank,
  type Category,
} from "@/features/banks/management-api";
import type { QuestionBank } from "@/features/banks/types";

export default function ManagedBanksPage() {
  const [banks, setBanks] = useState<QuestionBank[]>([]);
  const [categories, setCategories] = useState<Category[]>([]);
  const [keyword, setKeyword] = useState("");
  const [category, setCategory] = useState("");
  const [open, setOpen] = useState(false);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [name, setName] = useState("");
  const [source, setSource] = useState("");
  const [loading, setLoading] = useState(false);
  const [removeId, setRemoveId] = useState<number | null>(null);
  const load = () => {
    getManagedBanks(keyword)
      .then((page) => setBanks(page.records))
      .catch((e) =>
        Toast.error(e instanceof Error ? e.message : "题库加载失败"),
      );
    getBankCategories()
      .then(setCategories)
      .catch(() => undefined);
  };
  useEffect(() => {
    load();
  }, []);
  useEffect(() => {
    if (!open) setEditingId(null);
  }, [open]);
  const visible = useMemo(
    () =>
      banks.filter(
        (bank) =>
          (!category || bank.category === category) &&
          (!keyword || bank.name.includes(keyword)),
      ),
    [banks, category, keyword],
  );
  const save = async () => {
    if (!name.trim()) {
      Toast.error("请输入题库名称");
      return;
    }
    if (!category) {
      Toast.error("请选择分类");
      return;
    }
    setLoading(true);
    try {
      const data = { name: name.trim(), category, source, published: false };
      if (editingId) await updateBank(editingId, data);
      else await createBank(data);
      Toast.success(editingId ? "题库修改成功" : "题库创建成功");
      setOpen(false);
      setEditingId(null);
      setName("");
      setSource("");
      load();
    } catch (e) {
      Toast.error(e instanceof Error ? e.message : "保存失败");
    } finally {
      setLoading(false);
    }
  };
  const edit = (bank: QuestionBank) => {
    setEditingId(bank.id);
    setName(bank.name);
    setSource(bank.source || "");
    setCategory(bank.category || "");
    setOpen(true);
  };
  const remove = (id: number) => {
    Modal.confirm({
      title: "删除题库",
      content: "确定删除这个题库吗？删除后不可恢复。",
      onOk: async () => {
        try {
          await deleteBank(id);
          Toast.success("题库已删除");
          load();
        } catch (e) {
          Toast.error(e instanceof Error ? e.message : "删除失败");
        }
      },
    });
  };
  return (
    <PageShell>
      <main className="mx-auto flex min-h-[720px] w-[92%] max-w-[1160px] flex-col gap-6 py-10 sm:py-14 md:flex-row">
        <ProfileSidebar />
        <section className="min-w-0 flex-1">
          <div className="flex items-center justify-between gap-3">
            <h1 className="text-2xl font-bold text-slate-800">题库管理</h1>
            <Button theme="solid" type="warning" onClick={() => setOpen(true)}>
              + 新建题库
            </Button>
          </div>
          <div className="mt-6 flex flex-col gap-3 rounded-2xl border border-slate-200 bg-white p-4 sm:flex-row">
            <Input
              value={keyword}
              onChange={setKeyword}
              placeholder="搜索题库名称..."
              className="min-w-0 flex-1"
            />
            <Select
              value={category || undefined}
              onChange={(value) => setCategory(String(value || ""))}
              placeholder="全部分类"
              optionList={categories.map((item) => ({
                label: item.name,
                value: item.name,
              }))}
              className="sm:w-52"
            />
            <Button theme="solid" type="warning" onClick={load}>
              搜索
            </Button>
            <Link
              href="/profile/banks/categories"
              className="flex h-8 items-center justify-center rounded-lg bg-slate-100 px-3 text-xs font-semibold text-slate-600 no-underline transition hover:bg-slate-200"
            >
              题库分类管理
            </Link>
          </div>
          <div className="mt-5 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {visible.map((bank) => (
              <article
                key={bank.id}
                className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm"
              >
                <div className="flex items-start justify-between gap-2">
                  <h3 className="font-bold text-slate-800">{bank.name}</h3>
                </div>
                <p className="mt-4 text-sm text-slate-500">
                  {bank.source || "暂无描述"}
                </p>
                <p className="mt-4 text-sm text-slate-500">
                  包含 {bank.questionCount} 道题
                </p>
                <div className="mt-5 flex items-center justify-between text-sm">
                  <Link
                    href={`/profile/banks/${bank.id}/questions`}
                    className="rounded-lg bg-blue-50 px-4 py-2 font-semibold text-blue-600 no-underline"
                  >
                    题目管理
                  </Link>
                  <span className="flex gap-3">
                    <button
                      onClick={() => edit(bank)}
                      className="text-slate-600"
                    >
                      编辑
                    </button>
                    <button
                      onClick={() => remove(bank.id)}
                      className="text-red-500"
                    >
                      删除
                    </button>
                  </span>
                </div>
              </article>
            ))}
            {!visible.length && (
              <p className="col-span-full py-16 text-center text-sm text-slate-400">
                暂无题库
              </p>
            )}
          </div>
        </section>
      </main>
      <Modal
        title="新建题库"
        visible={open}
        onCancel={() => setOpen(false)}
        footer={
          <div className="flex justify-end gap-3">
            <Button className="modal-cancel-button" onClick={() => setOpen(false)}>取消</Button>
            <Button
              theme="solid"
              type="warning"
              loading={loading}
              onClick={save}
            >
              保存
            </Button>
          </div>
        }
      >
        <div className="space-y-4 py-2">
          <Input value={name} onChange={setName} placeholder="题库名称" />
          <Select
            value={category || undefined}
            onChange={(value) => setCategory(String(value || ""))}
            placeholder="请选择分类"
            optionList={categories.map((item) => ({
              label: item.name,
              value: item.name,
            }))}
            className="w-full"
          />
          <Input
            value={source}
            onChange={setSource}
            placeholder="描述（可选）"
          />
        </div>
      </Modal>
    </PageShell>
  );
}
