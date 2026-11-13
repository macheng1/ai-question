-- A paper can be reused; a scheduled exam belongs to at most one training.
-- Preflight on existing installations:
-- SELECT assessment_id, GROUP_CONCAT(id) FROM training WHERE assessment_id IS NOT NULL
-- GROUP BY assessment_id HAVING COUNT(*) > 1;
-- If shared legacy exams exist, stop and resolve their attempt ownership first.
-- Never duplicate or discard historical results automatically.
ALTER TABLE training ADD CONSTRAINT uk_training_assessment UNIQUE (assessment_id);
