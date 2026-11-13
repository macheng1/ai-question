-- Replace only auto-derived training exam names; retain independently named exams.
UPDATE assessment_task a
JOIN training t ON t.assessment_id=a.id
JOIN test_paper p ON p.id=a.paper_id
SET a.name=p.name
WHERE a.training_managed=TRUE AND a.name=t.name;
