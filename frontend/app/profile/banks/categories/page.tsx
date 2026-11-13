"use client";
import { Typography, Space } from "@douyinfe/semi-ui";
import { Card } from "@douyinfe/semi-ui";

import { IconArrowLeft, IconPlus } from "@douyinfe/semi-icons";

import { useEffect, useState } from "react";
import "@douyinfe/semi-ui/react19-adapter";
import { Table, Button, Input as SemiInput, Modal, TextArea as SemiTextArea, Toast, InputNumber } from "@douyinfe/semi-ui";
import { ProfileShell } from "@/components/layout/ProfileShell";
import {
  createBankCategory,
  deleteBankCategory,
  getBankCategories,
  updateBankCategory,
  type Category,
} from "@/features/banks/management-api";

const Input = Object.assign(SemiInput, { TextArea: SemiTextArea });

type FormState = {
  name: string;
  description: string;
  sortNo: string;
};
const emptyForm: FormState = {
  name: "",
  description: "",
  sortNo: "0",
};

export default function CategoriesPage() {
  const [categories, setCategories] = useState<Category[]>([]),
    [form, setForm] = useState<FormState>(emptyForm),
    [editingId, setEditingId] = useState<number | null>(null),
    [open, setOpen] = useState(false),
    [loading, setLoading] = useState(false);
  const load = () =>
    getBankCategories()
      .then(setCategories)
      .catch((e) =>
        Toast.error(e instanceof Error ? e.message : "分类加载失败"),
      );
  useEffect(() => {
    load();
  }, []);
  const openCreate = () => {
    setEditingId(null);
    setForm({ ...emptyForm });
    setOpen(true);
  };
  const openEdit = (item: Category) => {
    setEditingId(item.id);
    setForm({
      name: item.name,
      description: item.description || "",
      sortNo: String(item.sortNo ?? 0),
    });
    setOpen(true);
  };
  const close = () => {
    setOpen(false);
    setEditingId(null);
    setForm(emptyForm);
  };
  const save = async () => {
    if (!form.name.trim()) {
      Toast.error("请输入分类名称");
      return;
    }
    setLoading(true);
    try {
      const payload = {
        name: form.name.trim(),
        parentId: null,
        description: form.description.trim(),
        sortNo: Number(form.sortNo) || 0,
      };
      if (editingId) await updateBankCategory(editingId, payload);
      else await createBankCategory(payload);
      Toast.success(editingId ? "分类修改成功" : "分类创建成功");
      close();
      load();
    } catch (e) {
      Toast.error(e instanceof Error ? e.message : "保存失败");
    } finally {
      setLoading(false);
    }
  };
  const remove = (id: number) => {
    Modal.confirm({
      title: "删除分类",
      content: "确定删除这个分类吗？",
      onOk: async () => {
        try {
          await deleteBankCategory(id);
          Toast.success("分类已删除");
          load();
        } catch (e) {
          Toast.error(e instanceof Error ? e.message : "删除失败");
        }
      },
    });
  };
  return (
    <ProfileShell>
          <Typography.Text link={{ href: "/profile/banks" }} icon={<IconArrowLeft size="small" />} className="!inline-flex !min-h-8 !items-center !text-sm">返回题库管理</Typography.Text>
          <div className="mt-5 flex flex-wrap items-center justify-between gap-4">
            <Typography.Title heading={1} className="!m-0 !text-[28px] !font-semibold !text-[#183f35]">
              题库分类管理
            </Typography.Title>
            <Button theme="solid" onClick={() => openCreate()}>
              <IconPlus aria-hidden="true" /> 新建分类
            </Button>
          </div>
          <Card bodyStyle={{ padding: 0 }} className="!mt-5 overflow-hidden">
            <Table className="[&_table]:!w-full [&_table]:!min-w-[900px] [&_table]:!table-fixed [&_.semi-table-thead>tr>th]:whitespace-nowrap" style={{ width: "100%" }} rowKey="id" pagination={false} scroll={{x: 900}} dataSource={categories} columns={[{ title: "分类名称", render: (_value: unknown, item: Category) => <span className="font-medium [overflow-wrap:anywhere]">{item.name}</span> },
{ title: "描述", render: (_value: unknown, item: Category) => (<span className="text-slate-500 [overflow-wrap:anywhere]">{item.description || "—"}</span>) },
{ title: "排序", width: 100, render: (_value: unknown, item: Category) => (<span>{item.sortNo ?? 0}</span>) },
{ title: "操作", width: 200, render: (_value: unknown, item: Category) => (<span className="flex items-center gap-2 whitespace-nowrap">
        <Button theme="borderless" onClick={() => openEdit(item)}>
          编辑
        </Button>
        <Button theme="borderless" onClick={() => remove(item.id)} type="danger">
          删除
        </Button>
      </span>) }]} />
          </Card>

      <Modal bodyStyle={{ maxHeight: "calc(100dvh - 240px)", overflowY: "auto" }} width="min(480px, calc(100vw - 24px))"
        title={
          editingId ? "编辑分类" : "新建分类"
        }
        visible={open}
        onCancel={close}
        footer={
          <Space wrap >
            <Button className="modal-cancel-button" onClick={close}>取消</Button>
            <Button
              theme="solid"
              loading={loading}
              onClick={save}
            >
              保存
            </Button>
          </Space>
        }
      >
        <div className="flex flex-col gap-4 py-2">
          <Input
            value={form.name}
            onChange={(value) => setForm({ ...form, name: value })}
            placeholder="分类名称"
          />
          <Input.TextArea
            value={form.description}
            onChange={(value) => setForm({ ...form, description: value })}
            placeholder="描述（可选）"
          />
          <InputNumber

            value={form.sortNo}
            onChange={(value) => setForm({ ...form, sortNo: String(value ?? "") })}
            placeholder="排序"
          />
        </div>
      </Modal>
    </ProfileShell>
  );
}
