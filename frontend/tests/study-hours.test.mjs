import test from 'node:test';
import assert from 'node:assert/strict';
import { formatDuration, formatStudyDuration, formatElapsedDuration } from '../lib/duration.ts';
import { studyAxisSeconds } from '../features/dashboard/format.ts';
test('learning duration retains short sessions and hour boundaries', () => {
  for (const [seconds, expected] of [[0,'0秒'],[0.2,'不足1秒'],[1,'1秒'],[30,'30秒'],[59,'59秒'],[60,'1分'],[90,'1分30秒'],[108,'1分48秒'],[90061,'25小时1分1秒'],[3600,'1小时'],[3661,'1小时1分1秒']]) {
    assert.equal(formatStudyDuration(seconds / 3600), expected);
  }
  assert.equal(formatStudyDuration(NaN), '—');
});
test('shared total produces a stable axis that covers all durations', () => {
  assert.equal(studyAxisSeconds(0), 60);
  assert.equal(studyAxisSeconds(108 / 3600), 120);
  for (const seconds of [1, 100, 1000, 10000, 1000000]) assert.ok(studyAxisSeconds(seconds / 3600) >= seconds);
});

test('seconds formatter matches hours formatter and preserves missing values', () => {
  for (const seconds of [0, 0.2, 30, 60, 90, 3600, 3661, 90061]) {
    assert.equal(formatDuration(seconds), formatStudyDuration(seconds / 3600));
  }
  for (const value of [undefined, null, NaN, Infinity, -1]) assert.equal(formatDuration(value), '—');
});

test('elapsed duration handles missing timestamps, offsets and long exams', () => {
  assert.equal(formatElapsedDuration('2026-11-23T00:00:00Z', '2026-11-23T08:01:30+08:00'), '1分30秒');
  assert.equal(formatElapsedDuration('2026-11-23T00:00:00Z', '2026-11-24T01:01:01Z'), '25小时1分1秒');
  assert.equal(formatElapsedDuration('2026-11-23T00:00:00Z', '2026-11-23T00:00:00.200Z'), '0秒');
  assert.equal(formatElapsedDuration('2026-11-23T00:00:01Z', '2026-11-23T00:00:00Z'), '0秒');
  assert.equal(formatElapsedDuration(undefined, '2026-11-23T00:00:00Z'), '—');
  assert.equal(formatElapsedDuration('invalid', '2026-11-23T00:00:00Z'), '—');
});
