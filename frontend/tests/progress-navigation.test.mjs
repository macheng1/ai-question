import { test } from 'node:test';
import assert from 'node:assert/strict';
import { flushActiveProgress, registerProgressSaver } from '../features/trainings/progress-navigation.ts';

test('leaving waits for all active progress saves', async () => {
  let finish;
  let left = false;
  const unregister = registerProgressSaver(() => new Promise(resolve => { finish = resolve; }));
  try {
    const leaving = flushActiveProgress().then(() => { left = true; });
    await Promise.resolve();
    assert.equal(left, false);
    finish();
    await leaving;
    assert.equal(left, true);
  } finally { unregister(); }
});

test('save failure prevents navigation and allows retry', async () => {
  let fail = true;
  let left = false;
  const unregister = registerProgressSaver(async () => { if (fail) throw new Error('save failed'); });
  try {
    await assert.rejects(flushActiveProgress().then(() => { left = true; }), /save failed/);
    assert.equal(left, false);
    fail = false;
    await flushActiveProgress().then(() => { left = true; });
    assert.equal(left, true);
  } finally { unregister(); }
});

test('review or unloaded player without a saver can leave', async () => {
  await assert.doesNotReject(flushActiveProgress());
});
