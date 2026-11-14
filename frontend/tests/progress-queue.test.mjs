import test from 'node:test';
import assert from 'node:assert/strict';
import { createProgressQueue } from '../features/trainings/progress-queue.ts';

test('flush waits for pending save and then saves remaining time', async () => {
 let remaining=5, release;
 const sent=[];
 const q=createProgressQueue({snapshot:()=>remaining>0?{seconds:remaining}:null,send:async p=>{sent.push(p);if(sent.length===1)await new Promise(r=>release=r);return p.seconds;},acknowledge:(p)=>{remaining-=p.seconds;}});
 const first=q.flush(); await Promise.resolve();remaining=8;
 const leaving=q.flush();release();await Promise.all([first,leaving]);
 assert.deepEqual(sent.map(p=>p.seconds),[5,3]);assert.equal(remaining,0);
});
test('failed save is retried with exactly the same request before new time',async()=>{
 let remaining=3,fail=true;const sent=[];
 const q=createProgressQueue({snapshot:()=>remaining?{seconds:remaining}:null,send:async p=>{sent.push(p);if(fail){fail=false;throw Error('response lost');}return {};},acknowledge:p=>{remaining-=p.seconds;}});
 await assert.rejects(q.flush());remaining=5;await q.flush();
 assert.strictEqual(sent[0],sent[1]);assert.deepEqual(sent.map(p=>p.seconds),[3,3,2]);assert.equal(remaining,0);
});
