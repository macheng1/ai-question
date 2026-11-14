"use client";
import { useCallback, useState } from "react";
import { Banner, Button, Card, Image, Progress, SideSheet, Tag, Tooltip, Toast } from "@douyinfe/semi-ui";
import { IconList, IconHelpCircleStroked, IconTickCircle } from "@douyinfe/semi-icons";
import { ExamTrainingVideos, type ExamLearning, type ExamVideo } from "./ExamTrainingVideos";
import { ExamTrainingMaterials } from "./ExamTrainingMaterials";
import { flushActiveProgress } from "../progress-navigation";
import type { Material } from "./TrainingMaterials";

type Props = { reviewOnly?: boolean; id: string; data: ExamLearning; busy: boolean; onBack: () => void; onComplete: () => void };
export function CourseWorkspace({ id, data, busy, onBack, onComplete, reviewOnly = false }: Props) {
  const [videos, setVideos] = useState(data.videos);
  const [materials, setMaterials] = useState(data.materials || []);
  const entries = [...videos.map(v => ({ key: `v${v.id}`, title: v.title, type: "video", complete: v.completed })), ...materials.map(m => ({ key: `m${m.id}`, title: m.title, type: m.materialType === "PDF" ? "pdf" : "image", complete: m.pages.every(p => p.completed) }))];
  const [selected, setSelected] = useState(entries.find(e => !e.complete)?.key || entries[0]?.key);
  const [switching,setSwitching]=useState(false);
  const [drawer, setDrawer] = useState(false);
  const video = videos.find(v => `v${v.id}` === selected);
  const material = materials.find(m => `m${m.id}` === selected);
  const videosDone = videos.every(v => v.completed);
  const completed = entries.filter(e => e.complete).length;
  const allDone = completed === entries.length;
  const updateVideo = useCallback((v: ExamVideo) => setVideos(old => old.map(x => x.id === v.id ? v : x)), []);
  const updateMaterial = useCallback((m: Material) => setMaterials(old => old.map(x => x.id === m.id ? m : x)), []);
  const next = () => {
    const remaining = entries.find(e => !e.complete && e.key !== selected);
    if (remaining) setSelected(remaining.key);
  };
  const directory = <div className="flex flex-col gap-4"><div className="flex items-center justify-between"><strong>学习目录</strong><Tag>{reviewOnly ? `${entries.length} 份材料` : `${completed} / ${entries.length}`}</Tag></div>{!reviewOnly && <Progress percent={entries.length ? Math.round(completed / entries.length * 100) : 100} />}<div className="flex flex-col gap-2">{entries.map(entry => <Button key={entry.key} block theme={selected === entry.key ? "light" : "borderless"} type={selected === entry.key ? "primary" : "tertiary"} disabled={busy || switching || selected===entry.key || (!reviewOnly && entry.type !== "video" && !videosDone)} className="!h-auto !min-h-16 !justify-start !px-3 !py-3 !text-left" onClick={async () => { setSwitching(true);try { await flushActiveProgress();setSelected(entry.key);setDrawer(false); } catch(e) { Toast.error(e instanceof Error?e.message:"进度保存失败，请重试"); } finally { setSwitching(false); } }}><span className="flex w-full min-w-0 items-center gap-3"><Image src={`/training-icons/${entry.type}.svg`} alt="" width={24} height={24} preview={false} /><span className="min-w-0 flex-1 whitespace-normal [overflow-wrap:anywhere]">{entry.title}<span className="mt-1 block text-xs text-slate-500">{entry.type === "video" ? "视频" : entry.type === "pdf" ? "PDF" : "图片"}</span></span><span className="shrink-0 text-xs">{reviewOnly ? "" : entry.complete ? <><IconTickCircle className="text-green-600" /> 已完成</> : entry.key === selected ? "学习中" : "待学习"}</span></span></Button>)}</div>{!reviewOnly && !videosDone && <p className="text-xs leading-6 text-slate-500">完成视频后可继续阅读 PDF 和图片。</p>}</div>;
  return <div className="flex flex-col gap-5"><div className="flex flex-wrap items-center justify-between gap-3"><div className="flex min-w-0 items-start gap-2"><h1 className="min-w-0 [overflow-wrap:anywhere] text-xl font-semibold">{data.trainings.map(t => t.name).join(" / ")}</h1><Tooltip content={reviewOnly ? "回看材料不计入学习时长，也不改变学习记录。" : "进度由服务端保存。视频暂停、页面进入后台时不累计学习时间；材料须达到阅读时长后才能完成。"}><Button aria-label="学习要求说明" theme="borderless" icon={<IconHelpCircleStroked />} /></Tooltip></div><div className="lg:hidden"><Button size="large" icon={<IconList />} onClick={() => setDrawer(true)}>学习目录</Button></div></div>
    <div className="grid items-start gap-5 lg:grid-cols-[280px_minmax(0,1fr)]"><aside className="hidden lg:sticky lg:top-5 lg:block"><Card>{directory}</Card></aside><Card bodyStyle={{ padding: 0 }} className="min-w-0 overflow-hidden">
      {video ? <ExamTrainingVideos key={selected} reviewTrainingId={reviewOnly ? id : undefined} examId={String(-Number(id))} data={{ ...data, videos: [video] }} starting={busy} onProgress={updateVideo} onBack={onBack} onContinue={allDone ? onComplete : next} continueLabel={allDone ? "完成培训" : "继续下一份材料"} /> : material ? <ExamTrainingMaterials key={selected} reviewTrainingId={reviewOnly ? id : undefined} examId={String(-Number(id))} data={{ ...data, videos: [], materials: [material] }} starting={busy} onMaterialProgress={updateMaterial} onBack={onBack} onContinue={allDone ? onComplete : next} continueLabel={allDone ? "完成培训" : "继续下一份材料"} /> : <div className="p-6"><Banner description="本培训没有需要学习的材料。" />{!reviewOnly && <Button className="!mt-4" theme="solid" loading={busy} onClick={onComplete}>完成培训</Button>}</div>}
    </Card></div><SideSheet className="course-directory" title="学习目录" visible={drawer} onCancel={() => setDrawer(false)} width="min(360px, 92vw)">{directory}</SideSheet>
  </div>;
}
