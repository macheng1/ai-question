import test from 'node:test';
import assert from 'node:assert/strict';
import { learningAction, learningHref } from '../features/learner/status.ts';

test('ended training always opens read-only materials even if learning is incomplete', () => {
  for (const learningStatus of ['NOT_STARTED', 'IN_PROGRESS', 'COMPLETED']) {
    const row = { id: 69, trainingEnded: true, learningStatus, examStatus: 'READY' };
    assert.equal(learningAction(row), '回看培训');
    assert.equal(learningHref(row), '/training/69?review=1');
  }
});
test('active learning and exam entry keep their existing behavior', () => {
  assert.equal(learningHref({ id: 69, learningStatus: 'IN_PROGRESS' }), '/training/69?resume=1');
  assert.equal(learningHref({ id: 69, learningStatus: 'COMPLETED', examStatus: 'READY' }), '/training/69?exam=1');
});
