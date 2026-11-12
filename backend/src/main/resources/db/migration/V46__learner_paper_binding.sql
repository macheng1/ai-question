-- Dedicated allocation mutex; do not lock app_user because exam inserts reference it via foreign keys.
CREATE TABLE training_allocation_lock (
  owner_id BIGINT NOT NULL PRIMARY KEY
);

-- Permanent allocation history. No cascading foreign keys: deletion must not release eligibility.
CREATE TABLE learner_paper_binding (
  owner_id BIGINT NOT NULL,
  employee_id BIGINT NOT NULL,
  paper_id BIGINT NOT NULL,
  training_ids JSON NOT NULL,
  legacy_conflict BOOLEAN NOT NULL DEFAULT FALSE,
  bound_at DATETIME(3) NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (owner_id, employee_id, paper_id),
  KEY ix_binding_owner_paper (owner_id, paper_id)
);
-- Retain every historical source instead of arbitrarily choosing a training on conflicts.
INSERT INTO learner_paper_binding(owner_id,employee_id,paper_id,training_ids,legacy_conflict,bound_at)
SELECT source.owner_id,source.employee_id,source.paper_id,JSON_ARRAYAGG(source.training_id),COUNT(*)>1,MIN(source.assigned_at)
FROM (
  SELECT t.created_by owner_id,a.employee_id,e.paper_id,t.id training_id,MIN(a.assigned_at) assigned_at
  FROM training_audience a JOIN training t ON t.id=a.training_id
  JOIN assessment_task e ON e.id=t.assessment_id AND e.created_by=t.created_by
  JOIN organization_employee p ON p.id=a.employee_id AND p.created_by=t.created_by
  GROUP BY t.created_by,a.employee_id,e.paper_id,t.id
) source GROUP BY source.owner_id,source.employee_id,source.paper_id;
