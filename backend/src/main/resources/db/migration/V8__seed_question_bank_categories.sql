INSERT INTO question_bank_category(name, parent_id, description, sort_no, created_at, updated_at)
SELECT DISTINCT category, NULL, '由现有题库自动初始化', 0, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)
FROM question_bank
WHERE category IS NOT NULL AND category <> ''
  AND NOT EXISTS (SELECT 1 FROM question_bank_category c WHERE c.name = question_bank.category AND c.parent_id IS NULL);
