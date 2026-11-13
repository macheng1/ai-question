import test from 'node:test';
import assert from 'node:assert/strict';
import { formatPercentage, formatStatistic } from '../features/assessments/statistics-format.ts';
test('actual percentages do not expose floating point tails', () => {
  for (const [value, expected] of [[19.999999999999996, '20%'], [0.19999999999999996, '0.2%'], [50, '50%'], [100 / 3, '33.33%'], [0, '0%'], [100, '100%']]) assert.equal(formatPercentage(value), expected);
});
test('numbers retain two decimals at most and reject invalid values', () => {
  assert.equal(formatStatistic('60.50'), '60.5');
  assert.equal(formatStatistic(1), '1');
  assert.equal(formatPercentage(NaN), '—');
  assert.equal(formatPercentage(Infinity), '—');
});
