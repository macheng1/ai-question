-- 清理早期可能存在的失效关联，再以外键防止并发删除造成悬空入口。
UPDATE training t LEFT JOIN assessment_task a ON a.id=t.assessment_id SET t.assessment_id=NULL WHERE t.assessment_id IS NOT NULL AND a.id IS NULL;
ALTER TABLE training ADD CONSTRAINT fk_training_assessment FOREIGN KEY (assessment_id) REFERENCES assessment_task(id) ON DELETE RESTRICT;
