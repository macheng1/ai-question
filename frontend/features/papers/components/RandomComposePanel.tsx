"use client";
import { IconPlus } from "@/features/exam/components/ExamIcons";

import { useEffect, useMemo, useState } from "react";
import { Table, Button, Input, Modal, Toast } from "@douyinfe/semi-ui";
import {
  addRandomBank,
  getRandomConfig,
  removeRandomBank,
  saveRandomConfig,
  type Paper,
  type RandomConfig,
  updatePaperStatus,
} from "@/features/papers/api";
import { getManagedBanks } from "@/features/banks/management-api";
import { getFavoriteBankIds, getQuestionBanks } from "@/features/banks/api";

type Props = { paper: Paper; locked: boolean; onCompleted: () => void };
type Bank = { id: number; name: string; questionCount: number };

const numberValue = (value: string | number | undefined) => {
  const parsed = Number(value);
  return Number.isFinite(parsed) && parsed >= 0 ? parsed : 0;
};

export function RandomComposePanel({ paper, locked, onCompleted }: Props) {
  const [items, setItems] = useState<RandomConfig[]>([]);
  const [banks, setBanks] = useState<Bank[]>([]);
  const [modalVisible, setModalVisible] = useState(false);
  const [tab, setTab] = useState("mine");
  const [loading, setLoading] = useState(true);

  const loadBanks = async (source: string) => {
    if (source === "mine") {
      const bankPage = await getManagedBanks();
      setBanks(bankPage.records.map((bank) => ({ id: bank.id, name: bank.name, questionCount: bank.questionCount })));
      return;
    }
    const publicBanks = (await getQuestionBanks({ pageSize: 100 })).records;
    if (source === "public") {
      setBanks(publicBanks.map((bank) => ({ id: bank.id, name: bank.name, questionCount: bank.questionCount })));
      return;
    }
    const favoriteIds = new Set(await getFavoriteBankIds());
    setBanks(publicBanks
      .filter((bank) => favoriteIds.has(bank.id))
      .map((bank) => ({ id: bank.id, name: bank.name, questionCount: bank.questionCount })));
  };
  const load = async () => {
    const config = await getRandomConfig(paper.id);
    setItems(config);
    await loadBanks(tab);
  };
  useEffect(() => {
    load().catch((error) => Toast.error(error instanceof Error ? error.message : "随机配置加载失败")).finally(() => setLoading(false));
  }, [paper.id, tab]);

  const total = useMemo(() => items.reduce((sum, item) => sum + item.singleCount * Number(item.singleScore || 0) + item.multipleCount * Number(item.multipleScore || 0) + item.trueFalseCount * Number(item.trueFalseScore || 0), 0), [items]);
  const update = (bankId: number, key: keyof RandomConfig, value: string) => {
    setItems((current) => current.map((item) => item.bankId === bankId ? { ...item, [key]: numberValue(value) } : item));
  };
  const selectBank = async (bank: Bank) => {
    if (locked) return Toast.info("试卷已有考试任务，组卷已锁定");
    try {
      await addRandomBank(paper.id, bank.id);
      await load();
      setModalVisible(false);
      Toast.success("已添加题库");
    } catch (error) { Toast.error(error instanceof Error ? error.message : "添加题库失败"); }
  };
  const remove = async (bankId: number) => {
    if (locked) return Toast.info("试卷已有考试任务，组卷已锁定");
    try { await removeRandomBank(paper.id, bankId); await load(); Toast.success("已移除题库"); }
    catch (error) { Toast.error(error instanceof Error ? error.message : "移除题库失败"); }
  };
  const save = async (complete = false) => {
    if (locked) return Toast.info("试卷已有考试任务，组卷已锁定");
    if (!items.length) return Toast.error("请至少添加一个题库");
    const invalid = items.find((item) => item.singleCount > item.availableSingleCount || item.multipleCount > item.availableMultipleCount || item.trueFalseCount > item.availableTrueFalseCount);
    if (invalid) return Toast.error(`题库“${invalid.bankName}”的抽题数量超过可用题量，请调整后再保存`);
    if (total !== Number(paper.totalScore)) return Toast.error(`当前抽题总分为 ${total} 分，必须等于试卷设置的 ${paper.totalScore} 分`);
    try {
      await saveRandomConfig(paper.id, items.map(({ id, bankName, availableSingleCount, availableMultipleCount, availableTrueFalseCount, ...item }) => item));
      if (complete) { await updatePaperStatus(paper.id, "PUBLISHED"); Toast.success("随机组卷已完成"); onCompleted(); }
      else Toast.success("随机组卷配置已保存");
    } catch (error) { Toast.error(error instanceof Error ? error.message : "保存失败"); }
  };

  if (loading) return <div className="grid min-h-[500px] place-items-center text-sm text-slate-400">加载随机配置中...</div>;
  return <main className="min-h-[650px] bg-slate-100 p-4 sm:p-8">
    <section className="mx-auto max-w-[1120px] rounded-2xl border border-slate-200 bg-white p-5 shadow-sm sm:p-6">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div><h2 className="text-lg font-semibold text-slate-800">随机抽题配置</h2><p className="mt-3 text-sm text-slate-400">为每个题库分别设置各题型的抽取数量与分值。</p></div>
        <Button theme="borderless" onClick={() => { setTab("mine"); setModalVisible(true); }} disabled={locked}><IconPlus aria-hidden="true" /> 添加题库</Button>
      </div>
      {!items.length ? <div className="grid min-h-[180px] place-items-center border-b border-slate-100 text-sm text-slate-400">请点击「添加题库」开始配置</div> : <div className="mt-8 overflow-x-auto">
        <div className="min-w-[820px] text-sm">
          <Table rowKey="bankId" dataSource={items} pagination={false} scroll={{ x: 820 }} columns={[
            { title: "题库", render: (_: unknown, item: RandomConfig) => <div><b>{item.bankName}</b><div className="text-xs text-slate-400">可用 单选 {item.availableSingleCount} · 多选 {item.availableMultipleCount} · 判断 {item.availableTrueFalseCount}</div></div> },
            ...(["single", "multiple", "trueFalse"] as const).map((kind, index) => ({ title: ["单选", "多选", "判断"][index], render: (_: unknown, item: RandomConfig) => { const countKey = `${kind}Count` as keyof RandomConfig; const scoreKey = `${kind}Score` as keyof RandomConfig; return <div className="flex gap-2"><Input type="number" value={String(item[countKey])} onChange={v => update(item.bankId, countKey, v)} disabled={locked} suffix="题" /><Input type="number" value={String(item[scoreKey])} onChange={v => update(item.bankId, scoreKey, v)} disabled={locked} suffix="分" /></div>; } })),
            { title: "小计", render: (_: unknown, item: RandomConfig) => item.singleCount * Number(item.singleScore || 0) + item.multipleCount * Number(item.multipleScore || 0) + item.trueFalseCount * Number(item.trueFalseScore || 0) },
            { title: "操作", render: (_: unknown, item: RandomConfig) => <Button theme="borderless" type="danger" disabled={locked} onClick={() => remove(item.bankId)}>移除</Button> }
          ]} />
        </div>
      </div>}
      <div className="mt-5 flex flex-wrap items-center justify-between gap-4 border-t border-slate-100 pt-5"><div className="text-sm text-slate-600">合计：<b className={total === Number(paper.totalScore) ? "text-green-600" : "text-orange-500"}>{total}</b> 分 <span className="text-slate-400">/ 试卷设定 {paper.totalScore} 分</span></div><div className="flex gap-3"><Button theme="solid" type="warning" disabled={locked} onClick={() => save()}>保存配置</Button><Button theme="solid" type="primary" disabled={locked} onClick={() => save(true)}>完成组卷</Button></div></div>
    </section>
    <Modal title="选择题库" visible={modalVisible} onCancel={() => setModalVisible(false)} footer={null} width={460}>
      <div className="mb-4 flex gap-5 border-b border-slate-100 text-sm">
        {([["mine", "我的题库"], ["favorite", "收藏的题库"], ["public", "公开题库"]] as const).map(([value, label]) => (
          <Button theme="borderless" key={value} className={`pb-3 ${tab === value ? "border-b-2 border-blue-500 font-semibold text-blue-600" : "text-slate-500"}`} onClick={() => setTab(value)}>{label}</Button>
        ))}
      </div>
      <div className="h-[420px] overflow-y-auto pr-1">
        <div className="space-y-3">
          {banks.filter((bank) => !items.some((item) => item.bankId === bank.id)).map((bank) => <Button theme="borderless" key={bank.id} className="w-full rounded-lg border border-slate-200 p-4 text-left transition hover:border-blue-400 hover:bg-blue-50" onClick={() => selectBank(bank)}><div className="font-medium text-slate-800">{bank.name}</div><div className="mt-1 text-xs text-slate-400">共 {bank.questionCount} 题</div></Button>)}
          {!banks.filter((bank) => !items.some((item) => item.bankId === bank.id)).length && <div className="py-8 text-center text-sm text-slate-400">暂无可添加题库</div>}
        </div>
      </div>
    </Modal>
  </main>;
}
