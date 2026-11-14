import fs from 'node:fs';
import { setTimeout as sleep } from 'node:timers/promises';
const [base, training, csv, n, seconds, ramp] = process.argv.slice(2);
const users = Number(n), duration = Number(seconds);
if (!/^https?:\/\//.test(base || '') || !/^[1-9]\d*$/.test(training || '') || !Number.isInteger(users) || users < 1 || users > 1000 || !Number.isInteger(duration) || duration < 5) throw Error('用法: node training-load.mjs API地址 培训ID 名单.csv 人数 持续秒数');
const rampSeconds = ramp ? Number(ramp) : (users - 1) * 0.02;
if (!Number.isFinite(rampSeconds) || rampSeconds < 0 || rampSeconds * 1000 > 2147483647) throw Error('入场总秒数超出有效范围');
const intervalMs = users > 1 ? rampSeconds * 1000 / (users - 1) : 0;
const rows = fs.readFileSync(csv, 'utf8').trim().replace(/^\uFEFF/, '').split(/\r?\n/);
if (rows.shift().trim() !== 'name,phone') throw Error('首行应为 name,phone');
const roster = rows.slice(0, users).map(row => { const [name,phone] = row.trim().split(',').map(x=>x.trim()); return {name,phone}; });
if(roster.length !== users || roster.some(p=>!p.name || !/^1\d{10}$/.test(p.phone)) || new Set(roster.map(p=>p.phone)).size !== users) throw Error('学员数量不足、手机号无效或手机号重复');
const stats = {}, counts = {active:0, peak:0, finished:0, failed:0, advanced:0}; let stopping = false;
process.on('SIGINT',()=>{stopping=true;});
async function call(label,path,method='GET',body,auth,binary=false) {
  const s = stats[label] ??= {ms:[],errors:0,status:{}}, start = performance.now();
  try {
    const signal=AbortSignal.timeout(15000);
    let r=await fetch(base.replace(/\/$/,'')+path,{method,headers:{...(body?{'Content-Type':'application/json'}:{}),...(auth?{Authorization:'Bearer '+auth}:{})},body:body?JSON.stringify(body):undefined,signal});
    if(binary && r.ok && r.headers.get('content-type')?.includes('application/json')) {
      const {url}=await r.json();
      // Signed object downloads must never carry the learner's API credentials.
      r=await fetch(url,{signal});
    }
    s.status[r.status]=(s.status[r.status]||0)+1;
    if(!r.ok){await r.body?.cancel(); throw Error(label+' HTTP '+r.status);}
    if(binary){if(!(await r.arrayBuffer()).byteLength)throw Error('空预览');return;}
    const j=await r.json();if(!j.success)throw Error(label+' 业务拒绝');return j.data;
  } catch(e){s.errors++;throw e;} finally{s.ms.push(Math.round(performance.now()-start));}
}
async function learner(p,i){
  await sleep(Math.round(i * intervalMs));if(stopping)return;
  const login=await call('身份','/learner/session','POST',{type:'TRAINING',id:Number(training),...p});
  const auth=login.token.accessToken;
  const e=await call('进入学习','/public/courses/'+training+'/learning','POST',p,auth);
  const m=e.materials?.[0];if(!m?.pages?.length)throw Error('培训缺少可阅读材料');
  const path='/public/courses/'+training+'/learning/materials/'+m.id+'/pages/1';
  const read=()=>call('预览',path+'?direct=true&token='+encodeURIComponent(e.token),'GET',undefined,auth,true);
  await read();let r=await call('进度',path+'/progress','PUT',{token:e.token,seconds:0},auth);
  const initial=r.studiedSeconds;counts.active++;counts.peak=Math.max(counts.peak,counts.active);
  try{const end=Date.now()+duration*1000;let cycle=0;
    while(!stopping && Date.now()<end){await sleep(5000);if(stopping)break;
      r=await call('进度',path+'/progress','PUT',{token:e.token,heartbeatToken:r.heartbeatToken,seconds:5},auth);
      if(++cycle%6===0)await read();
    }
    if(r.studiedSeconds>initial)counts.advanced++;
    if(!stopping)counts.finished++;
  }finally{counts.active--;}
}
const tick=setInterval(()=>console.log('运行中',JSON.stringify(counts)),30000);
await Promise.all(roster.map((p,i)=>learner(p,i).catch(e=>{if(++counts.failed<=5)console.error('第'+(i+1)+'名学员',e.message);})));
clearInterval(tick);
const report={users,duration,rampSeconds,stopped:stopping,...counts,requests:{}};
for(const [k,s] of Object.entries(stats)){s.ms.sort((a,b)=>a-b);report.requests[k]={count:s.ms.length,errors:s.errors,status:s.status,p95ms:s.ms[Math.ceil(s.ms.length*.95)-1],maxMs:s.ms.at(-1)};}
console.log(JSON.stringify(report,null,2));if(stopping||counts.finished!==users||counts.advanced!==users)process.exitCode=1;
