"use client";
import { Typography, Space } from "@douyinfe/semi-ui";
import { Card } from "@douyinfe/semi-ui";

import { IconArrowLeft, IconPlus } from "@douyinfe/semi-icons";

import { useEffect, useMemo, useState } from "react";
import "@douyinfe/semi-ui/react19-adapter";
import { Table, Button, Input as SemiInput, Modal, Select, TextArea as SemiTextArea, Toast, InputNumber } from "@douyinfe/semi-ui";
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
  parentId: number | null;
  description: string;
  sortNo: string;
};
const emptyForm: FormState = {
  name: "",
  parentId: null,
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
  const roots = useMemo(
    () => categories.filter((item) => !item.parentId),
    [categories],
  );
  const childrenOf = (id: number) =>
    categories.filter((item) => item.parentId === id);
  const openCreate = (parentId: number | null = null) => {
    setEditingId(null);
    setForm({ ...emptyForm, parentId });
    setOpen(true);
  };
  const openEdit = (item: Category) => {
    setEditingId(item.id);
    setForm({
      name: item.name,
      parentId: item.parentId ?? null,
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
        parentId: form.parentId,
        description: form.description.trim(),
        sortNo: Number(form.sortNo) || 0,
      };
      if (editingId) await updateBankCategory(editingId, payload);
      else await createBankCategory(payload);
      Toast.success(
        editingId
          ? "分类修改成功"
          : form.parentId
            ? "子分类创建成功"
            : "分类创建成功",
      );
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
      content: "删除分类会同时删除子分类，确定继续吗？",
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
          <div className="mt-5 flex items-center justify-between gap-3">
            <Typography.Title heading={1} className="!m-0 !text-[28px] !font-semibold !text-[#183f35]">
              题库管理 - 分类管理
            </Typography.Title>
            <Button theme="solid" type="warning" onClick={() => openCreate()}>
              <IconPlus aria-hidden="true" /> 新建根分类
            </Button>
          </div>
          <Card bodyStyle={{ padding: 0 }} className="!mt-6 overflow-hidden">
            <Table rowKey="id" pagination={false} scroll={{x: 720}} dataSource={roots.flatMap(root => [{...root, child: false}, ...childrenOf(root.id).map(item => ({...item, child: true}))])} columns={[{ title: "分类名称", render: (_value: unknown, item: Category & {child: boolean}) => (<span className={item.child ? "pl-6" : "font-medium"}>
        {item.child && <span className="mr-2 inline-block h-3 w-3 border-b border-l border-slate-300" aria-hidden="true" />}
        {item.name}
      </span>) },
{ title: "描述", render: (_value: unknown, item: Category & {child: boolean}) => (<span>{item.description || "-"}</span>) },
{ title: "排序", render: (_value: unknown, item: Category & {child: boolean}) => (<span>{item.sortNo ?? 0}</span>) },
{ title: "操作", render: (_value: unknown, item: Category & {child: boolean}) => (<span className="flex flex-wrap gap-3">
        <Button theme="borderless" onClick={() => openCreate(item.id)} className="text-blue-500">
          添加子分类
        </Button>
        <Button theme="borderless" onClick={() => openEdit(item)} className="text-slate-500">
          编辑
        </Button>
        <Button theme="borderless" onClick={() => remove(item.id)} className="text-red-500">
          删除
        </Button>
      </span>) }]} />
          </Card>

      <Modal bodyStyle={{ maxHeight: "calc(100dvh - 240px)", overflowY: "auto" }} width="min(480px, calc(100vw - 24px))"
        title={
          editingId ? "编辑分类" : form.parentId ? "新建子分类" : "新建根分类"
        }
        visible={open}
        onCancel={close}
        footer={
          <Space wrap >
            <Button className="modal-cancel-button" onClick={close}>取消</Button>
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
          <Input
            value={form.name}
            onChange={(value) => setForm({ ...form, name: value })}
            placeholder="分类名称"
          />
          <Select
            value={form.parentId ?? undefined}
            onChange={(value) =>
              setForm({ ...form, parentId: value ? Number(value) : null })
            }
            placeholder="- 作为根分类 -"
            optionList={roots
              .filter((item) => item.id !== editingId)
              .map((item) => ({ label: item.name, value: item.id }))}
            className="w-full"
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
