import test from 'node:test';
import assert from 'node:assert/strict';
import { reportFilters } from '../features/dashboard/report.ts';

test('report retains learner and combined scope, excludes pagination and detail status', () => {
  const query={start:'2026-11-01',end:'2026-11-27',trainingId:3,departmentId:4,employeeId:5,category:'SAFETY',tab:'learners',status:'OVERDUE',page:7,pageSize:10};
  assert.deepEqual(reportFilters(query),{start:query.start,end:query.end,trainingId:3,departmentId:4,employeeId:5,category:'SAFETY'});
  assert.equal(query.page,7);
});
test('reset report scope has no stale learner', () => {
  assert.equal(reportFilters({tab:'trainings',status:'ALL',page:1,pageSize:10}).employeeId,undefined);
});
