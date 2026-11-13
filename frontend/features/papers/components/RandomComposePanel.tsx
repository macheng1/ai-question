"use client";
import { Typography, Space } from "@douyinfe/semi-ui";
import { Layout } from "@douyinfe/semi-ui";
import { IconPlus } from "@douyinfe/semi-icons";

import { useCallback, useEffect, useMemo, useState } from "react";
import { Table, Button, Modal, Toast, InputNumber } from "@douyinfe/semi-ui";
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
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    const [config, bankPage] = await Promise.all([getRandomConfig(paper.id), getManagedBanks()]);
    setItems(config);
    setBanks(bankPage.records.map((bank) => ({ id: bank.id, name: bank.name, questionCount: bank.questionCount })));
  }, [paper.id]);
  useEffect(() => {
    load().catch((error) => Toast.error(error instanceof Error ? error.message : "随机配置加载失败")).finally(() => setLoading(false));
  }, [load]);

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
      await saveRandomConfig(paper.id, items.map(({ bankId, singleCount, singleScore, multipleCount, multipleScore, trueFalseCount, trueFalseScore }) => ({ bankId, singleCount, singleScore, multipleCount, multipleScore, trueFalseCount, trueFalseScore })));
      if (complete) { await updatePaperStatus(paper.id, "PUBLISHED"); Toast.success("随机组卷已完成"); onCompleted(); }
      else Toast.success("随机组卷配置已保存");
    } catch (error) { Toast.error(error instanceof Error ? error.message : "保存失败"); }
  };

  if (loading) return <div className="grid min-h-[500px] place-items-center text-sm text-slate-400">加载随机配置中...</div>;
  return <Layout.Content className="min-w-0 p-4 sm:p-6">
    <Layout.Content className="w-full rounded-lg border border-slate-200 bg-white p-5 shadow-sm sm:p-6">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div><Typography.Title heading={2} className="!text-lg !font-semibold !text-slate-800">随机抽题配置</Typography.Title><p className="mt-2 text-sm text-slate-500">按题库设置抽题数量和每题分值，下方显示各题型可用题量。</p></div>
        <Button theme="light" type="primary" onClick={() => { setModalVisible(true); }} disabled={locked}><IconPlus aria-hidden="true" /> 添加题库</Button>
      </div>
      {!items.length ? <div className="grid min-h-[180px] place-items-center border-b border-slate-100 text-sm text-slate-400">请点击「添加题库」开始配置</div> : <div className="mt-5 overflow-hidden rounded-lg border border-slate-200">
        <div className="w-full text-sm">
          <Table className="w-full [&_.semi-table-table]:!w-full [&_.semi-table-table]:!table-fixed [&_.semi-table-row-head]:whitespace-nowrap" rowKey="bankId" dataSource={items} pagination={false} scroll={{ x: 1120 }} columns={[
            { title: "题库", render: (_: unknown, item: RandomConfig) => <div className="min-w-0"><div className="break-words font-medium leading-6 text-slate-800">{item.bankName}</div><div className="mt-2 flex flex-wrap gap-x-3 gap-y-1 text-xs text-slate-500"><span>单选 {item.availableSingleCount} 题</span><span>多选 {item.availableMultipleCount} 题</span><span>判断 {item.availableTrueFalseCount} 题</span></div></div> },
            ...(["single", "multiple", "trueFalse"] as const).map((kind, index) => ({ title: ["单选", "多选", "判断"][index], width: 210, render: (_: unknown, item: RandomConfig) => {
              const countKey = `${kind}Count` as keyof RandomConfig;
              const scoreKey = `${kind}Score` as keyof RandomConfig;
              return <div className="flex flex-col gap-2 py-1">
                <label className="flex items-center gap-2"><span className="w-12 shrink-0 whitespace-nowrap text-xs text-slate-500">抽取</span><InputNumber aria-label={`${item.bankName}${["单选", "多选", "判断"][index]}抽题数量`} style={{ width: "100%", minWidth: 0 }} value={String(item[countKey])} onChange={v => update(item.bankId, countKey, String(v ?? ""))} disabled={locked} suffix="题" /></label>
                <label className="flex items-center gap-2"><span className="w-12 shrink-0 whitespace-nowrap text-xs text-slate-500">每题</span><InputNumber aria-label={`${item.bankName}${["单选", "多选", "判断"][index]}每题分值`} style={{ width: "100%", minWidth: 0 }} value={String(item[scoreKey])} onChange={v => update(item.bankId, scoreKey, String(v ?? ""))} disabled={locked} suffix="分" /></label>
              </div>;
            } })),
            { title: "小计", width: 100, render: (_: unknown, item: RandomConfig) => <span className="whitespace-nowrap font-medium">{item.singleCount * Number(item.singleScore || 0) + item.multipleCount * Number(item.multipleScore || 0) + item.trueFalseCount * Number(item.trueFalseScore || 0)} 分</span> },
            { title: "操作", width: 90, render: (_: unknown, item: RandomConfig) => <Button theme="borderless" type="danger" disabled={locked} onClick={() => remove(item.bankId)}>移除</Button> }
          ]} />
        </div>
      </div>}
      <div className="mt-5 flex flex-wrap items-center justify-between gap-4 border-t border-slate-100 pt-5"><div className="text-sm text-slate-600">合计：<b className={total === Number(paper.totalScore) ? "text-green-600" : "text-orange-500"}>{total}</b> 分 <span className="text-slate-400">/ 试卷设定 {paper.totalScore} 分</span></div><Space wrap ><Button theme="light" type="primary" disabled={locked} onClick={() => save()}>保存配置</Button><Button theme="solid" type="primary" disabled={locked} onClick={() => save(true)}>完成组卷</Button></Space></div>
    </Layout.Content>
    <Modal bodyStyle={{ maxHeight: "calc(100dvh - 240px)", overflowY: "auto" }} title="选择题库" visible={modalVisible} onCancel={() => setModalVisible(false)} footer={null} width="min(460px, calc(100vw - 24px))">
      <div className="h-[420px] overflow-y-auto pr-1">
        <div className="flex flex-col gap-3">
          {banks.filter((bank) => !items.some((item) => item.bankId === bank.id)).map((bank) => <button type="button" key={bank.id} className="block h-auto w-full whitespace-normal break-words rounded-lg border border-slate-200 bg-white p-4 text-left transition hover:border-blue-400 hover:bg-blue-50 focus-visible:outline focus-visible:outline-2 focus-visible:outline-blue-500" onClick={() => selectBank(bank)}><div className="font-medium text-slate-800">{bank.name}</div><div className="mt-1 text-xs text-slate-400">共 {bank.questionCount} 题</div></button>)}
          {!banks.filter((bank) => !items.some((item) => item.bankId === bank.id)).length && <div className="py-8 text-center text-sm text-slate-400">暂无可添加题库</div>}
        </div>
      </div>
    </Modal>
  </Layout.Content>;
}
