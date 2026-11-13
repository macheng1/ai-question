-- Exams configured within a training have independent learner attempts and reuse the original paper.
ALTER TABLE assessment_task ADD COLUMN training_managed BOOLEAN NOT NULL DEFAULT FALSE;
