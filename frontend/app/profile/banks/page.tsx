"use client";
import { Typography, Space } from "@douyinfe/semi-ui";
import { Card } from "@douyinfe/semi-ui";
import { IconPlus } from "@douyinfe/semi-icons";

import { useEffect, useMemo, useState } from "react";
import Link from "next/link";
import "@douyinfe/semi-ui/react19-adapter";
import { Button, Input, Modal, Select, Table, Toast } from "@douyinfe/semi-ui";
import { ProfileShell } from "@/components/layout/ProfileShell";
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
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [open, setOpen] = useState(false);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [name, setName] = useState("");
  const [source, setSource] = useState("");
  const [loading, setLoading] = useState(false);
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
  const currentPage = Math.min(page, Math.max(1, Math.ceil(visible.length / pageSize)));
  const pageItems = visible.slice((currentPage - 1) * pageSize, currentPage * pageSize);
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
    <ProfileShell>
          <div className="flex flex-wrap items-center justify-between gap-4">
            <Typography.Title heading={1} className="!m-0 !text-[28px] !font-semibold !text-[#183f35]">题库管理</Typography.Title>
            <Button theme="solid" onClick={() => setOpen(true)}>
              <IconPlus aria-hidden="true" /> 新建题库
            </Button>
          </div>
          <Card bodyStyle={{ padding: 0 }} className="!mt-5"><div className="flex flex-col gap-3 p-5 sm:flex-row sm:flex-wrap">
            <Input
              value={keyword}
              onChange={value => { setKeyword(value); setPage(1); }}
              onEnterPress={load}
              placeholder="搜索题库名称..."
              className="min-w-0 flex-1"
            />
            <Select
              value={category || undefined}
              onChange={(value) => { setCategory(String(value || "")); setPage(1); }}
              showClear
              placeholder="全部分类"
              optionList={categories.map((item) => ({
                label: item.name,
                value: item.name,
              }))}
              className="sm:w-52"
            />
            <Button theme="solid" onClick={load}>
              搜索
            </Button>
            <Link
              href="/profile/banks/categories"
              className="flex h-8 items-center justify-center rounded-lg bg-slate-100 px-3 text-xs font-semibold text-slate-600 no-underline transition hover:bg-slate-200"
            >
              题库分类管理
            </Link>
          </div></Card>
          <Card bodyStyle={{ padding: 0 }} className="!mt-5 overflow-hidden">
            <Table<QuestionBank>
              rowKey="id"
              dataSource={pageItems}
              style={{ width: "100%" }}
              className="[&_table]:!w-full [&_table]:!min-w-[1000px] [&_table]:!table-fixed [&_.semi-table-thead>tr>th]:whitespace-nowrap"
              scroll={{ x: 1000 }}
              empty="暂无题库"
              pagination={{
                total: visible.length,
                currentPage,
                pageSize,
                showTotal: true,
                showSizeChanger: true,
                pageSizeOpts: [10, 20, 50],
                onPageChange: setPage,
                onPageSizeChange: size => { setPageSize(size); setPage(1); },
                style: { padding: "16px 20px", margin: 0, boxSizing: "border-box" },
              }}
              columns={[
                { title: "题库名称", dataIndex: "name", render: value => <span className="[overflow-wrap:anywhere]">{value}</span> },
                { title: "分类", dataIndex: "category", width: 160, render: value => value || "未分类" },
                { title: "描述", dataIndex: "source", render: value => <span className="text-slate-500 [overflow-wrap:anywhere]">{value || "—"}</span> },
                { title: "题目数量", dataIndex: "questionCount", width: 110, render: value => value ?? 0 },
                { title: "操作", width: 280, render: (_, bank) => (
                  <div className="flex items-center gap-2 whitespace-nowrap">
                    <Link href={`/profile/banks/${bank.id}/questions`} className="px-3 font-semibold text-blue-600 no-underline">题目管理</Link>
                    <Button theme="borderless" onClick={() => edit(bank)}>编辑</Button>
                    <Button theme="borderless" type="danger" onClick={() => remove(bank.id)}>删除</Button>
                  </div>
                ) },
              ]}
            />
          </Card>

      <Modal bodyStyle={{ maxHeight: "calc(100dvh - 240px)", overflowY: "auto" }} width="min(480px, calc(100vw - 24px))"
        title="新建题库"
        visible={open}
        onCancel={() => setOpen(false)}
        footer={
          <Space wrap >
            <Button className="modal-cancel-button" onClick={() => setOpen(false)}>取消</Button>
            <Button
              theme="solid"
              type="warning"
              loading={loading}
              onClick={save}
            >
              保存
            </Button>
          </Space>
        }
      >
        <div className="flex flex-col gap-4 py-2">
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
    </ProfileShell>
  );
}
