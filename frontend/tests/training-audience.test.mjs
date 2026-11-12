import test from 'node:test';
import assert from 'node:assert/strict';
import { summarizeAudience } from '../features/trainings/audience-rules.ts';
const employees = [
  {id:1,departmentId:10,status:'ACTIVE'},
  {id:2,departmentId:10,status:'ACTIVE'},
  {id:3,departmentId:11,status:'ACTIVE'},
  {id:4,departmentId:10,status:'INACTIVE'},
];
test('department scope counts bound learners as excluded and ignores inactive departments',()=>{
  assert.deepEqual(summarizeAudience({type:'DEPARTMENT',ids:[10]}, employees, [10], [1]), {eligible:1,excluded:1,invalidSelection:false});
  assert.deepEqual(summarizeAudience({type:'ALL',ids:[]}, employees, [10], [1,2]), {eligible:0,excluded:2,invalidSelection:false});
});
test('individual selections cannot silently drop bound or missing learners',()=>{
  assert.deepEqual(summarizeAudience({type:'USERS',ids:[1,2]}, employees, [10], [1]), {eligible:1,excluded:1,invalidSelection:true});
  assert.equal(summarizeAudience({type:'USERS',ids:[99]}, employees, [10], []).invalidSelection,true);
});
