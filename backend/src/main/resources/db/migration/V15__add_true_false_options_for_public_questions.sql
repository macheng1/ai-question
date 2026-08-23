-- 判断题也维护标准选项，公开题库页面和考试页面统一展示 A/B 选项。
INSERT INTO question_option (question_id, option_key, option_text, is_correct, sort_no)
SELECT q.id, 'A', '正确', q.answer_text = 'TRUE', 0
FROM question_item q
JOIN question_bank b ON b.id = q.bank_id
WHERE b.published = TRUE
  AND q.question_type IN ('TRUE_FALSE', 'JUDGE')
  AND NOT EXISTS (
    SELECT 1 FROM question_option o
    WHERE o.question_id = q.id AND o.option_key = 'A'
  );

INSERT INTO question_option (question_id, option_key, option_text, is_correct, sort_no)
SELECT q.id, 'B', '错误', q.answer_text = 'FALSE', 1
FROM question_item q
JOIN question_bank b ON b.id = q.bank_id
WHERE b.published = TRUE
  AND q.question_type IN ('TRUE_FALSE', 'JUDGE')
  AND NOT EXISTS (
    SELECT 1 FROM question_option o
    WHERE o.question_id = q.id AND o.option_key = 'B'
  );
