-- 保留分类 ID 和名称，题库按名称关联，不改变已有题库归属。
UPDATE question_bank_category SET parent_id = NULL WHERE parent_id IS NOT NULL;
