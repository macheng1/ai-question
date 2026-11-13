"use client";
import { useCallback, useEffect, useState } from "react";
import { Banner, Button, Image, InputNumber, Modal, Space, Spin, Table, Toast, Upload } from "@douyinfe/semi-ui";
import { apiClient } from "@/lib/api/client";
export type MaterialPage = { pageNumber: number; requiredSeconds: number; studiedSeconds?: number; completed?: boolean };
export type Material = { id: number; trainingId: number; title: string; materialType: "PDF" | "IMAGE"; pageCount: number; pages: MaterialPage[]; completed?: boolean };
export function TrainingMaterials({ id, readOnly = false }: { id: number; readOnly?: boolean }) {
 const [items,setItems]=useState<Material[]>([]),[busy,setBusy]=useState(false),[error,setError]=useState("");
 const [selected,setSelected]=useState<Material>(),[page,setPage]=useState(1),[url,setUrl]=useState(""),[previewError,setPreviewError]=useState("");
 const [seconds,setSeconds]=useState(120);
 const selectedId=selected?.id;
 const base=`/management/trainings/${id}/materials`;
 const load=useCallback(async()=>{try{setItems(await apiClient.get<Material[]>(base));setError("");}catch(e){setError(e instanceof Error?e.message:"材料加载失败");}},[base]);
 useEffect(()=>{void load();},[load]);
 useEffect(()=>{setUrl("");setPreviewError("");if(!selectedId)return;let url="";const controller=new AbortController();apiClient.get<Blob>(`${base}/${selectedId}/pages/${page}`,undefined,{responseType:"blob",signal:controller.signal,timeout:120000}).then(blob=>{if(!controller.signal.aborted){url=URL.createObjectURL(blob);setUrl(url);}}).catch(e=>{if(!controller.signal.aborted)setPreviewError(e.message);});return()=>{controller.abort();if(url)URL.revokeObjectURL(url);};},[base,selectedId,page]);
 const move=async(item:Material,offset:number)=>{const ordered=[...items],index=ordered.findIndex(m=>m.id===item.id);[ordered[index],ordered[index+offset]]=[ordered[index+offset],ordered[index]];setBusy(true);try{await apiClient.put(`${base}/order`,ordered.map(m=>m.id));setItems(ordered);}catch(e){Toast.error(e instanceof Error?e.message:"排序失败");}finally{setBusy(false);}};
 const remove=(item:Material)=>Modal.confirm({title:"删除培训材料",content:`确定删除“${item.title}”？已有学习记录的材料不能删除。`,onOk:async()=>{setBusy(true);try{await apiClient.delete(`${base}/${item.id}`);await load();Toast.success("材料已删除");}catch(e){Toast.error(e instanceof Error?e.message:"删除失败");}finally{setBusy(false);}}});
 return <div className="flex flex-col gap-3">
  <strong>PDF / 图片材料</strong><p className="text-sm text-slate-500">支持 PDF、PNG、JPG，单个文件不超过20MB，PDF最多200页。默认每页120秒，可统一设置或逐页调整。保存时长立即生效。</p>
  {error&&<Banner type="danger" description={error}><Button onClick={load}>重试</Button></Banner>}
  <Table<Material> rowKey="id" dataSource={items} pagination={false} scroll={{ x: 650 }} columns={[{title:"材料",dataIndex:"title",width:200},{title:"页数",dataIndex:"pageCount",width:70},{title:"操作",width:380,render:(_,item)=><Space><Button disabled={readOnly||busy||items[0]?.id===item.id} onClick={()=>move(item,-1)}>上移</Button><Button disabled={readOnly||busy||items[items.length-1]?.id===item.id} onClick={()=>move(item,1)}>下移</Button><Button theme="borderless" onClick={()=>{setSelected({...item,pages:item.pages.map(p=>({...p}))});setPage(1);}}>{readOnly ? "预览" : "设置时长"}</Button><Button type="danger" theme="borderless" disabled={readOnly||busy} onClick={()=>remove(item)}>删除</Button></Space>}]} />
  <Upload accept=".pdf,.png,.jpg,.jpeg" disabled={readOnly||busy} fileList={[]} showUploadList={false} beforeUpload={({file})=>{if(Number(file.size)>20*1024*1024){Toast.error("文件不能超过20MB");return false;}return true;}} customRequest={async({fileInstance,onSuccess,onError})=>{setBusy(true);try{const data=new FormData();data.append("file",fileInstance);await apiClient.post(base,data,{timeout:300000});await load();onSuccess({});Toast.success("材料已上传");}catch(e){Toast.error(e instanceof Error?e.message:"上传失败");onError({});}finally{setBusy(false);}}}><Button disabled={readOnly||busy} loading={busy}>上传PDF / 图片</Button></Upload>
  <Modal title={selected?.title||"材料预览"} visible={!!selected} width="min(900px,94vw)" okText="保存阅读时长" okButtonProps={{ disabled: readOnly }} confirmLoading={busy} onCancel={()=>{if(!busy)setSelected(undefined);}} onOk={async()=>{if(!selected||busy||readOnly)return;setBusy(true);try{await apiClient.put(`${base}/${selected.id}/timings`,selected.pages.map(({pageNumber,requiredSeconds})=>({pageNumber,requiredSeconds})));await load();setSelected(undefined);Toast.success("阅读时长已保存");}catch(e){Toast.error(e instanceof Error?e.message:"保存失败");}finally{setBusy(false);}}}>
   {selected&&<div className="flex max-h-[65vh] flex-col gap-4 overflow-auto"><Space wrap><span>统一时长（秒）</span><InputNumber disabled={readOnly} min={1} max={3600} value={seconds} onChange={v=>setSeconds(Number(v)||1)} /><Button disabled={readOnly} onClick={()=>setSelected({...selected,pages:selected.pages.map(p=>({...p,requiredSeconds:seconds}))})}>应用到全部页</Button></Space><Space wrap><Button disabled={page===1} onClick={()=>setPage(p=>p-1)}>上一页</Button><span>{page} / {selected.pageCount}</span><Button disabled={page===selected.pageCount} onClick={()=>setPage(p=>p+1)}>下一页</Button><span>本页阅读秒数</span><InputNumber disabled={readOnly} min={1} max={3600} value={selected.pages[page-1].requiredSeconds} onChange={v=>setSelected({...selected,pages:selected.pages.map(p=>p.pageNumber===page?{...p,requiredSeconds:Number(v)||1}:p)})}/></Space><div className="min-h-[280px] w-full shrink-0 rounded-lg border border-solid border-slate-200 bg-slate-50 p-3 sm:min-h-[360px] sm:p-4" aria-busy={!url && !previewError}>
    {previewError ? <Banner type="danger" description={previewError}/> : url ? <Image src={url} alt={`第${page}页`} width="100%"/> : (
     <div className="flex min-h-[254px] flex-col items-center justify-center gap-3 sm:min-h-[326px]" role="status" aria-live="polite">
      <Spin size="large" />
      <span className="whitespace-nowrap text-sm text-slate-600">正在加载第 {page} 页…</span>
      <span className="text-center text-xs text-slate-400">首次预览可能需要几秒，请稍候</span>
     </div>
    )}
   </div></div>}
  </Modal>
 </div>;
}
