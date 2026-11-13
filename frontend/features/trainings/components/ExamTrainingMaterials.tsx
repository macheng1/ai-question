"use client";
import { useEffect, useRef, useState } from "react";
import { Banner, Button, Image, Space, Spin, Tag, Tooltip, Toast } from "@douyinfe/semi-ui";
import { IconMinus, IconPlus, IconExpand } from "@douyinfe/semi-icons";
import { apiClient } from "@/lib/api/client";
import { learningBase, ExamTrainingVideos, type ExamLearning } from "./ExamTrainingVideos";
import { trainingCategories } from "../api";
import type { Material } from "./TrainingMaterials";
type Props={reviewTrainingId?:string;examId:string;data:ExamLearning;onBack:()=>void;onContinue:()=>void;starting:boolean;continueLabel?:string;onMaterialProgress?:(material:Material)=>void};
export function ExamTrainingMaterials(props:Props){
 const viewer=useRef<HTMLDivElement>(null);
 const [zoom,setZoom]=useState(100);
 const [previewVisible,setPreviewVisible]=useState(false);
 useEffect(()=>setPreviewVisible(false),[props.examId]);
 const {onMaterialProgress}=props;
 const [videoDone,setVideoDone]=useState(props.data.videos.every(v=>v.completed));
 const [materials,setMaterials]=useState<Material[]>(props.data.materials||[]);
 const [selected,setSelected]=useState(materials.find(m=>!m.completed)?.id??materials[0]?.id);
 const material=materials.find(m=>m.id===selected);
 const [page,setPage]=useState(material?.pages.find(p=>!p.completed)?.pageNumber??1);
 const [url,setUrl]=useState(""),[error,setError]=useState(""),[retry,setRetry]=useState(0),[paused,setPaused]=useState(false);
 useEffect(()=>{if(material)onMaterialProgress?.(material);},[material,onMaterialProgress]);
 const [liveSeconds,setLiveSeconds]=useState(0);
 const active=material?.pages[page-1];const done=materials.every(m=>m.pages.every(p=>p.completed));const lastActivity=useRef(0);
 const activePageRef=useRef(active);activePageRef.current=active;
 useEffect(()=>{
  if(!videoDone||!selected)return;
  let alive=true,objectUrl="",heartbeatToken:string|undefined,pending=false,last=performance.now();let timer:ReturnType<typeof setInterval>|undefined;
  let saved=activePageRef.current?.studiedSeconds||0,unconfirmed=0,complete=false;
  const required=activePageRef.current?.requiredSeconds||1;
  setLiveSeconds(saved);
  const controller=new AbortController();setUrl("");setPreviewVisible(false);setError("");setPaused(false);lastActivity.current=Date.now();
  const base=props.reviewTrainingId ? `/learner/trainings/${props.reviewTrainingId}/review/materials/${selected}/pages/${page}` : `${learningBase(props.examId)}/materials/${selected}/pages/${page}`;
  const beat=async(seconds:number)=>{if(pending)return;pending=true;
   try{const p=await apiClient.put<{heartbeatToken:string;studiedSeconds:number;completed:boolean}>(`${base}/progress`,{token:props.data.token,heartbeatToken,seconds},{signal:controller.signal});if(!alive)return;heartbeatToken=p.heartbeatToken;saved=p.studiedSeconds;unconfirmed=Math.max(0,unconfirmed-seconds);complete=p.completed;setLiveSeconds(Math.min(required,saved+unconfirmed));setMaterials(ms=>ms.map(m=>m.id===selected?{...m,pages:m.pages.map(x=>x.pageNumber===page?{...x,...p}:x)}:m));return true;}
   catch(e){if(alive){setError(e instanceof Error?e.message:"进度保存失败");setLiveSeconds(saved);if(timer)clearInterval(timer);}return false;}finally{pending=false;}};
  apiClient.get<Blob>(base,props.reviewTrainingId ? undefined : {token:props.data.token},{responseType:"blob",signal:controller.signal,timeout:120000}).then(async blob=>{
   if(!alive)return;objectUrl=URL.createObjectURL(blob);setUrl(objectUrl);if(props.reviewTrainingId)return;const ok=await beat(0);last=performance.now();
   if(alive&&ok)timer=setInterval(()=>{
    const now=performance.now(),elapsed=(now-last)/1000;last=now;
    const idle=Date.now()-lastActivity.current>180000;
    setPaused(idle||document.hidden);
    if(document.hidden||idle||complete)return;
    // Ignore long gaps caused by a suspended tab; only animate active reading.
    if(elapsed>1)return;
    unconfirmed+=elapsed;
    setLiveSeconds(Math.min(required,saved+unconfirmed));
    const interval=Math.min(5,Math.max(0.5,required-saved));
    if(!pending&&unconfirmed>=interval)void beat(Math.min(unconfirmed,15));
   },100);
  }).catch(e=>{if(alive)setError(e.message);});
  const activity=()=>{if(Date.now()-lastActivity.current>180000)last=performance.now();lastActivity.current=Date.now();setPaused(document.hidden);};const visibility=()=>{last=performance.now();setPaused(document.hidden);};
  window.addEventListener("pointerdown",activity);window.addEventListener("keydown",activity);window.addEventListener("scroll",activity,true);document.addEventListener("visibilitychange",visibility);
  return()=>{alive=false;controller.abort();if(timer)clearInterval(timer);if(objectUrl)URL.revokeObjectURL(objectUrl);window.removeEventListener("pointerdown",activity);window.removeEventListener("keydown",activity);window.removeEventListener("scroll",activity,true);document.removeEventListener("visibilitychange",visibility);};
 },[props.examId,props.data.token,selected,page,videoDone,retry,props.reviewTrainingId]);
 if(!videoDone)return <ExamTrainingVideos {...props} continueLabel={materials.length?"继续材料学习":Number(props.examId)<0?"完成培训学习":"开始考试"} onContinue={()=>{if(materials.length)setVideoDone(true);else props.onContinue();}}/>;
 if(!materials.length)return <div className="h5-content"><Button theme="solid" loading={props.starting} onClick={props.onContinue}>{props.continueLabel || (Number(props.examId)<0?"完成培训学习":"继续考试")}</Button></div>;
 return <div className="h5-content flex flex-col gap-4 pb-10"><h1 className="text-xl font-semibold">培训材料学习</h1>
 <Space wrap>{props.data.trainings.map(t=><span key={t.id}>{t.name} <Tag>{trainingCategories.find(c=>c.value===t.category)?.label||"未分类"}</Tag> <Tag>{t.requirementType==="ELECTIVE"?"选修":"必修"}</Tag></span>)}</Space>
 <Space wrap>{materials.map(m=><Button key={m.id} theme={selected===m.id?"solid":"light"} onClick={()=>{setSelected(m.id);setPage(m.pages.find(p=>!p.completed)?.pageNumber??1);}}>{m.title}</Button>)}</Space><h2 className="break-words font-semibold">{material?.title}</h2>
 {props.reviewTrainingId ? <p>第 {page} / {material?.pageCount} 页 · 回看材料</p> : <><p>第 {page} / {material?.pageCount} 页 · {active?.completed?"本页已完成":liveSeconds>=(active?.requiredSeconds||1)?"正在确认阅读进度…":`本页还需阅读 ${Math.max(0,Math.ceil((active?.requiredSeconds||0)-liveSeconds))} 秒`}</p><div role="progressbar" aria-label="本页阅读进度" aria-valuemin={0} aria-valuemax={100} aria-valuenow={Math.min(100,Math.round(liveSeconds/(active?.requiredSeconds||1)*100))} className="h-1 overflow-hidden rounded-full bg-slate-100">
  <div key={`${selected}-${page}`} className="h-full w-full origin-left rounded-full bg-green-500" style={{transform:`scaleX(${Math.min(1,Math.max(0,liveSeconds/(active?.requiredSeconds||1)))})`,transition:paused||error?"none":"transform 100ms linear",willChange:"transform"}} />
 </div></>}
 <Space><Button disabled={!url||!!error} onClick={()=>setPreviewVisible(true)}>预览</Button><Tooltip content="缩小"><Button aria-label="缩小" icon={<IconMinus/>} disabled={zoom<=50} onClick={()=>setZoom(z=>z-25)}/></Tooltip><span>{zoom}%</span><Tooltip content="放大"><Button aria-label="放大" icon={<IconPlus/>} disabled={zoom>=200} onClick={()=>setZoom(z=>z+25)}/></Tooltip><Tooltip content="全屏阅读"><Button aria-label="全屏阅读" icon={<IconExpand/>} onClick={()=>{if(viewer.current?.requestFullscreen)void viewer.current.requestFullscreen().catch(()=>Toast.info("请使用缩放阅读"));else Toast.info("请使用缩放阅读");}}/></Tooltip></Space>
 {paused&&<Banner type="info" description="计时已暂停，点击页面继续阅读。"/>}{error?<Banner type="danger" description={error}><Button onClick={()=>setRetry(n=>n+1)}>重新加载</Button></Banner>:url?<div ref={viewer} className="overflow-auto rounded-lg border border-slate-200 bg-slate-100 p-3" style={{maxHeight:"72dvh"}}><div style={{width:`${zoom}%`,margin:"0 auto"}}><Image src={url} alt={`第${page}页`} width="100%" preview={{visible:previewVisible,onVisibleChange:setPreviewVisible,previewTitle:`${material?.title || "培训材料"} · 第 ${page} 页`,disableDownload:true,closeOnEsc:true,getPopupContainer:()=>viewer.current || document.body}}/></div></div>:<div className="flex min-h-48 flex-col items-center justify-center gap-3 rounded-lg border border-slate-200 bg-slate-100"><Spin size="large"/><p className="text-sm text-slate-500">加载材料…</p></div>}
 <div className="sticky bottom-0 z-10 flex flex-col gap-3 border-t border-slate-200 bg-white pt-3 pb-[max(12px,env(safe-area-inset-bottom))]"><Space><Button size="large" disabled={page<=1} onClick={()=>setPage(p=>p-1)}>上一页</Button><Button size="large" disabled={(!props.reviewTrainingId&&!active?.completed)||page===(material?.pageCount||0)} onClick={()=>setPage(p=>p+1)}>下一页</Button></Space>{!props.reviewTrainingId && <><p className="text-sm text-slate-500">阅读时自动计时，达到要求后解锁下一页。退出后可继续；{Number(props.examId)<0?"全部材料完成后可提交完成。":"全部材料完成后进入考试。"}</p>
 <Button block theme="solid" disabled={!done||!!error} loading={props.starting} onClick={props.onContinue}>{props.continueLabel || (Number(props.examId)<0?"完成培训学习":"完成学习，继续考试")}</Button></>}<Button block theme="borderless" onClick={props.onBack}>返回</Button></div></div>;
}
