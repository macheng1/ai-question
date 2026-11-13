"use client";
import { useEffect,useState } from "react";
import { useParams } from "next/navigation";
import { Banner,Button,Card,Input,Table,Tag,Tooltip,Typography } from "@douyinfe/semi-ui";
import { IconArrowLeft, IconHelpCircleStroked } from "@douyinfe/semi-icons";
import { ProfileShell } from "@/components/layout/ProfileShell";
import { useCompactViewport } from "@/lib/hooks/useCompactViewport";
import { apiClient } from "@/lib/api/client";
type Part={id:number;title:string;pageNumber?:number;requiredSeconds?:number;studiedSeconds?:number;completed:boolean;firstStudiedAt?:string;lastStudiedAt?:string;completedAt?:string};
type Row={id:number;name:string;phone:string;department:string;studiedSeconds:number;completed:boolean;materials:Part[]};
const time=(v?:number|null)=>v==null?"未记录":`${Math.floor(v/60)}分${Math.floor(v%60)}秒`;
const dateTime=(value?:string)=>{
 if(!value || !Number.isFinite(Date.parse(value)))return "—";
 return new Intl.DateTimeFormat("zh-CN",{timeZone:"Asia/Shanghai",year:"numeric",month:"2-digit",day:"2-digit",hour:"2-digit",minute:"2-digit",second:"2-digit",hourCycle:"h23"}).format(new Date(value)).replaceAll("/","-");
};
export default function LearningRecords(){const compact=useCompactViewport();const [page,setPage]=useState(1),[pageSize,setPageSize]=useState(10);const {id}=useParams<{id:string}>();const [rows,setRows]=useState<Row[]>([]),[loading,setLoading]=useState(true),[error,setError]=useState(""),[keyword,setKeyword]=useState(""),[searchKeyword,setSearchKeyword]=useState("");
 useEffect(()=>{apiClient.get<Row[]>(`/management/trainings/${id}/learning-records`).then(setRows).catch(e=>setError(e.message)).finally(()=>setLoading(false));},[id]);
 const filtered=rows.filter(r=>[r.name,r.phone,r.department].some(v=>(v||"").includes(searchKeyword)));
 const currentPage=Math.min(page,Math.max(1,Math.ceil(filtered.length/pageSize)));
 const pageItems=filtered.slice((currentPage-1)*pageSize,currentPage*pageSize);
 return <ProfileShell><Typography.Text link={{href:"/profile/trainings"}} icon={<IconArrowLeft size="small"/>} className="!inline-flex !min-h-8 !items-center !text-sm">返回培训列表</Typography.Text><div className="mt-5 flex items-center gap-2"><Typography.Title heading={1} className="!m-0 !text-[28px] !font-semibold !text-[#183f35]">培训统计</Typography.Title><Tooltip trigger="click" position="bottomLeft" content={<div className="max-h-[65vh] max-w-[360px] overflow-y-auto text-sm leading-6">
  <strong>培训统计规则</strong>
  <ul className="mt-2 list-disc space-y-1 pl-4">
    <li>统计本培训中已进入学习、产生学习会话的学员；尚未进入学习的人员不显示在此列表中。</li>
    <li>累计学习时长为各视频及 PDF / 图片各页已记录的有效学习时长之和，不包含考试用时，也不是从进入到退出的停留时间。</li>
    <li>视频按系统校验后的有效播放时长累计；PDF / 图片按有效阅读时长累计，各页达到设定时长后计为完成。</li>
    <li>本培训全部视频及材料页面完成后显示“已完成”，否则显示“学习中”；无材料时，以学员确认完成为准。考试成绩不影响这里的学习状态。</li>
    <li>只汇总当前培训的材料进度，不混入其他培训的学习时长；重复进入同一学习记录不会重复增加列表人数。</li>
    <li>PDF / 图片阅读时，页面隐藏或连续 3 分钟无操作会暂停计时；异常断联期间不补计时长。</li>
    <li>历史记录中缺失的时长不补算，因此“已完成”不代表一定有完整的历史时长记录。</li>
    <li>本页显示已保存的数据，不实时刷新；刷新页面可查看最新记录。展开学员行可查看材料进度、最近学习及完成时间。</li>
  </ul>
</div>}><Button aria-label="培训统计规则说明" theme="borderless" type="tertiary" size="small" icon={<IconHelpCircleStroked />} /></Tooltip></div><p className="mt-2 text-sm text-slate-500">展开学员行查看各项培训材料的学习进度。</p>
 <Card bodyStyle={{padding:0}} className="!my-5"><form className="flex flex-col gap-4 p-5 sm:flex-row sm:items-center" onSubmit={event=>{event.preventDefault();setSearchKeyword(keyword.trim());setPage(1);}}><Input className="min-w-0 flex-1" aria-label="搜索学员" placeholder="搜索姓名、手机号或部门" value={keyword} onChange={setKeyword} showClear /><Button htmlType="submit" theme="solid" className="shrink-0" loading={loading}>搜索</Button></form></Card>
 {error ? <Banner type="danger" description={error}/> : <Card bodyStyle={{padding:0}} className="overflow-x-auto"><Table<Row> loading={loading} rowKey="id" dataSource={pageItems} empty={rows.length ? "没有匹配的学员" : "暂无数据"} pagination={{total:filtered.length,currentPage,pageSize,showTotal:!compact,showSizeChanger:!compact,pageSizeOpts:[10,20,50],onPageChange:setPage,onPageSizeChange:size=>{setPageSize(size);setPage(1);},style:{padding:"16px 20px",margin:0,boxSizing:"border-box"}}} style={{width:"100%"}} scroll={compact ? {x:900} : undefined} columns={[{title:"姓名",dataIndex:"name",width:"20%"},{title:"手机号",dataIndex:"phone",width:"20%"},{title:"部门",dataIndex:"department",width:"20%"},{title:"累计学习时长",width:"24%",render:(_,r)=>time(r.studiedSeconds)},{title:"学习状态",width:"16%",render:(_,r)=><Tag color={r.completed?"green":"grey"}>{r.completed?"已完成":"学习中"}</Tag>}]} expandedRowRender={r=>r?<div className="flex flex-col gap-4 p-4"><strong>材料学习记录</strong><Table<Part> pagination={false} style={{width:"100%"}} className="[&_table]:!w-full [&_table]:!min-w-[1120px] [&_table]:!table-fixed" dataSource={r.materials.map((p,i)=>({...p,key:i}))} rowKey="key" scroll={{x:1120}} columns={[
 {title:"材料",width:300,render:(_,p)=><span className="block break-all" title={p.title}>{p.title}</span>},
 {title:"页码",width:70,className:"whitespace-nowrap",render:(_,p)=>p.pageNumber??"—"},
 {title:"阅读要求",width:140,className:"whitespace-nowrap",render:(_,p)=>p.requiredSeconds?time(p.requiredSeconds):"视频播放进度"},
 {title:"有效时长",width:130,className:"whitespace-nowrap",render:(_,p)=>time(p.studiedSeconds)},
 {title:"状态",width:100,className:"whitespace-nowrap",render:(_,p)=><Tag color={p.completed?"green":"grey"}>{p.completed?"已完成":"未完成"}</Tag>},
 {title:"最近学习",width:190,className:"whitespace-nowrap",render:(_,p)=>dateTime(p.lastStudiedAt)},
 {title:"完成时间",width:190,className:"whitespace-nowrap",render:(_,p)=>dateTime(p.completedAt)}
 ]}/></div>:null}/></Card>}</ProfileShell>;
}
