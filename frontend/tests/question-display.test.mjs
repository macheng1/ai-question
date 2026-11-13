import { test } from 'node:test';
import assert from 'node:assert/strict';
import { formatQuestionOption, getQuestionDisplayOptions } from '../lib/question-display.ts';

test('legacy boolean labels use A/B without changing stored keys or correctness', () => {
  const options = [{ optionKey: 'FALSE', optionText: '错误', correct: true }, { optionKey: 'TRUE', optionText: '正确', correct: false }];
  assert.equal(getQuestionDisplayOptions({ questionType: 'TRUE_FALSE', options }), options);
  assert.deepEqual(options.map(formatQuestionOption), ['B. 错误', 'A. 正确']);
  assert.equal(options[0].optionKey, 'FALSE');
  assert.equal(options[0].correct, true);
});
test('missing boolean options derive highlighting from the saved answer', () => {
  for (const answer of ['FALSE', 'B', '错误']) {
    const options = getQuestionDisplayOptions({ questionType: 'JUDGE', answerText: answer });
    assert.deepEqual(options.map(o => o.correct), [false, true]);
  }
  assert.deepEqual(getQuestionDisplayOptions({ questionType: 'TRUE_FALSE', answerText: 'TRUE' }).map(o => o.correct), [true, false]);
  assert.deepEqual(getQuestionDisplayOptions({ questionType: 'TRUE_FALSE' }).map(o => o.correct), [false, false]);
});
test('ordinary choices keep their labels', () => {
  assert.equal(formatQuestionOption({ optionKey: 'C', optionText: '选项内容' }), 'C. 选项内容');
  assert.deepEqual(getQuestionDisplayOptions({ questionType: 'SINGLE' }), []);
});
