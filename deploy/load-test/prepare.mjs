import fs from 'node:fs/promises';
import path from 'node:path';
const [api,dir,run='round1']=process.argv.slice(2);
if(!api || !dir || !/^[\w-]{1,32}$/.test(run)) throw Error('prepare.mjs API 输出目录 批次名');
const phone=process.env.ADMIN_PHONE, password=process.env.ADMIN_PASSWORD;
if(!phone || !password) throw Error('请设置 ADMIN_PHONE 和 ADMIN_PASSWORD');
let token;
async function call(route,method='GET',body){
  const form=body instanceof FormData;
  const res=await fetch(api.replace(/\/$/,'')+route,{method,headers:{...(token?{Authorization:'Bearer '+token}:{}),...(!form&&body?{'Content-Type':'application/json'}:{})},body:form?body:JSON.stringify(body),signal:AbortSignal.timeout(60000)});
  const j=await res.json();
  if(!res.ok || !j.success)throw Error(method+' '+route+' '+res.status+' '+j.message);
  return j.data;
}
token=(await call('/auth/login','POST',{phone,password})).token.accessToken;
const name='压测专用-阅读1000', marker='LOADTEST-READ-V1';
const depts=(await call('/management/departments')).filter(d=>d.name===name);
if(depts.length>1 || depts[0]?.status==='INACTIVE')throw Error('压测部门重复或停用');
const department=depts[0] || await call('/management/departments','POST',{name,status:'ACTIVE'});
const list=()=>call('/management/employees');
const known=new Map((await list()).map(p=>[p.phone,p]));
const roster=Array.from({length:1000},(_,i)=>({name:'压测学员'+String(i+1).padStart(4,'0'),phone:'1997614'+String(i+1).padStart(4,'0'),departmentId:department.id,remark:marker,status:'ACTIVE'}));
function matches(a,b){return a?.name===b.name && a.departmentId===department.id && a.remark===marker && a.status==='ACTIVE';}
for(const p of roster)if(known.has(p.phone)&&!matches(known.get(p.phone),p))throw Error('测试手机号冲突，已停止：'+p.phone);
const missing=roster.filter(p=>!known.has(p.phone));
for(let i=0;i<missing.length;i+=5){
  await Promise.all(missing.slice(i,i+5).map(p=>call('/management/employees','POST',p)));
  if((i+5)%100===0)console.log('已新增学员',i+5);
}
const actual=new Map((await list()).map(p=>[p.phone,p]));
if(!roster.every(p=>matches(actual.get(p.phone),p)))throw Error('名册回读不一致');
const title=name+'-'+run;
const courses=(await call('/management/trainings')).filter(t=>t.name===title);
if(courses.length>1)throw Error('同名批次重复');
const time=ms=>new Date(Date.now()+8*3600000+ms).toISOString().slice(0,19);
const command={name:title,description:marker,category:'UNCATEGORIZED',requirementType:'REQUIRED',startAt:time(86400000),endAt:time(30*86400000),audience:{type:'DEPARTMENT',ids:[department.id]},surveyEnabled:false,questions:[],exam:{enabled:false}};
const base='/management/trainings';
let t=courses.length?await call(base+'/'+courses[0].id):await call(base,'POST',command);
if(t.description!==marker || t.assessmentId || t.audience?.type!=='DEPARTMENT' || t.audience.ids.length!==1 || t.audience.ids[0]!==department.id)throw Error('培训归属或范围不匹配');
const route=base+'/'+t.id+'/materials';
let materials=await call(route);
if(!materials.length){
  const f=new FormData();f.append('file',new Blob([await fs.readFile(new URL('./reading.pdf',import.meta.url))],{type:'application/pdf'}),'load-reading.pdf');
  await call(route,'POST',f);materials=await call(route);
}
if(materials.length!==1 || materials[0].title!=='load-reading.pdf')throw Error('材料与专用批次不符');
const mid=materials[0].id;
for(let i=0;i<90;i++){
  materials=await call(route);
  const m=materials[0];
  if(m.previewStatus==='READY')break;
  if(m.previewStatus==='FAILED'||i===89)throw Error('PDF预览未就绪，请在管理端查看并重试');
  await new Promise(r=>setTimeout(r,1000));
}
const m=materials[0];
if(!m.pages?.length)throw Error('PDF缺少页面');
if(m.pages.some(p=>p.requiredSeconds!==3600))await call(route+'/'+mid+'/timings','PUT',m.pages.map(p=>({pageNumber:p.pageNumber,requiredSeconds:3600})));
if(t.startAt>time(0))t=await call(base+'/'+t.id,'PUT',{...command,startAt:time(-60000),version:t.version});
if(t.endAt && t.endAt<=time(0))throw Error('本批次已过期，请使用新批次名');
await fs.mkdir(dir,{recursive:true});
await fs.writeFile(path.join(dir,'learners.csv'),'name,phone\n'+roster.map(p=>p.name+','+p.phone).join('\n')+'\n');
await fs.writeFile(path.join(dir,'batch.json'),JSON.stringify({api,training:t.id,department:department.id,material:mid,users:1000,run},null,2));
console.log('已就绪',JSON.stringify({training:t.id,department:department.id,material:mid,users:1000}));
