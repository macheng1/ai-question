import test from 'node:test';
import assert from 'node:assert/strict';
import { loadExamPaper } from '../features/exam/load-paper.ts';

async function loadQuestion(t, question) {
  t.mock.method(globalThis, 'fetch', async () => ({ok:true,json:async()=>({success:true,data:{questions:[question]}})}));
  return (await loadExamPaper('1')).questions[0];
}
test('judge option keys match saved and correct answers after reload', async t => {
  const q = await loadQuestion(t, {id:1,kind:'judge',title:'判断',options:[{key:'TRUE',text:'正确'},{key:'FALSE',text:'错误'}],answer:['A']});
  assert.deepEqual(q.options.map(x=>x.key), ['A','B']);
  assert.equal(q.options.find(x=>x.key===q.answer[0]).text,'正确');
});
test('unsubmitted answers remain hidden while judge options are normalized', async t => {
  const q = await loadQuestion(t, {id:1,kind:'judge',options:[{key:'TRUE',text:'正确'},{key:'FALSE',text:'错误'}]});
  assert.deepEqual(q.answer, []);
  assert.equal(q.analysis,'');
});
test('multiple choice option keys and answers remain unchanged', async t => {
  const q = await loadQuestion(t, {id:1,kind:'multi',options:[{key:'A',text:'一'},{key:'C',text:'三'}],answer:['A','C'],analysis:'解析'});
  assert.deepEqual(q.options.map(x=>x.key), ['A','C']);
  assert.deepEqual(q.answer,['A','C']);
  assert.equal(q.analysis,'解析');
});
test('unauthorized exam load fails without returning a fallback paper', async t => {
  t.mock.method(globalThis,'fetch',async()=>({ok:false,status:401,json:async()=>({success:false,message:'请先确认身份'})}));
  await assert.rejects(loadExamPaper('1'), /请先确认身份/);
});
test('server TRUE and FALSE answers highlight the matching judge option', async t => {
  for (const [answer, expected] of [['TRUE','A'],['FALSE','B']]) {
    const q = await loadQuestion(t, {id:1,kind:'judge',options:[{key:'TRUE',text:'正确'},{key:'FALSE',text:'错误'}],answer:[answer]});
    assert.deepEqual(q.answer,[expected]);
    assert.ok(q.options.some(option=>option.key===q.answer[0]));
  }
});
