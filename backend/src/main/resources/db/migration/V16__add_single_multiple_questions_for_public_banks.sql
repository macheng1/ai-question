-- 为公开题库补充单选题、多选题，避免公开题库只有判断题。
-- 每个公开题库新增 5 道单选题和 5 道多选题，重复执行不会重复插入。
INSERT INTO question_item (bank_id, question_type, stem, answer_text, analysis, tags, source, difficulty, score, status, created_at, updated_at)
SELECT b.id, t.question_type,
       CONCAT('在“', b.name, '”相关学习中，', t.stem),
       t.answer_text, t.analysis, t.tags, t.source, t.difficulty, 1, 'ENABLED', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)
FROM question_bank b
JOIN (
  SELECT 'SINGLE' question_type,'下列哪项最适合作为岗位培训后的首要行动？' stem,'A' answer_text,'培训后应将知识落实到岗位实践，并按要求记录和反馈。' analysis,'岗位实践,培训转化' tags,'公开法规与行业通用培训资料' source,'EASY' difficulty
  UNION ALL SELECT 'SINGLE','发现作业现场存在明显风险时，首先应当做什么？','A','应先控制风险、保护人员安全，并按规定报告。','风险处置,安全管理','公开法规与行业通用培训资料','EASY'
  UNION ALL SELECT 'SINGLE','下列哪项最能体现规范化管理？','A','通过制度、流程、培训和检查形成闭环管理。','规范管理,闭环管理','公开法规与行业通用培训资料','MEDIUM'
  UNION ALL SELECT 'SINGLE','保存培训和检查记录的主要目的是什么？','A','便于追溯过程、复盘问题并持续改进。','记录管理,追溯','公开法规与行业通用培训资料','EASY'
  UNION ALL SELECT 'SINGLE','高风险岗位人员上岗前最重要的条件是什么？','A','完成必要培训并具备相应资格或能力。','岗位资格,上岗管理','公开法规与行业通用培训资料','MEDIUM'
  UNION ALL SELECT 'MULTIPLE','开展岗位安全或合规管理通常需要哪些措施？','A,B','制度建设、教育培训和监督检查共同构成管理基础。','制度建设,教育培训,监督检查','公开法规与行业通用培训资料','EASY'
  UNION ALL SELECT 'MULTIPLE','发现异常情况时，哪些做法是正确的？','A,B','应及时停止或控制风险，并向有关人员报告。','异常处置,风险报告','公开法规与行业通用培训资料','MEDIUM'
  UNION ALL SELECT 'MULTIPLE','一份完整的培训记录通常应包含哪些内容？','A,B','培训主题、参加人员和培训时间等信息有助于形成有效记录。','培训记录,过程留痕','公开法规与行业通用培训资料','EASY'
  UNION ALL SELECT 'MULTIPLE','保护业务数据或个人信息时，应当遵循哪些要求？','A,B','应按授权使用数据，并采取必要措施防止泄露和滥用。','数据保护,授权管理','公开法规与行业通用培训资料','MEDIUM'
  UNION ALL SELECT 'MULTIPLE','应急处置结束后，哪些工作仍然需要开展？','A,B','应复盘原因、完善措施并跟踪整改效果。','应急复盘,整改闭环','公开法规与行业通用培训资料','MEDIUM'
) t
WHERE b.published = TRUE
  AND NOT EXISTS (
    SELECT 1 FROM question_item old
    WHERE old.bank_id = b.id
      AND old.stem = CONCAT('在“', b.name, '”相关学习中，', t.stem)
  );

-- 单选、多选题统一补充四个选项；正确答案由 answer_text 决定。
INSERT INTO question_option (question_id, option_key, option_text, is_correct, sort_no)
SELECT q.id, o.option_key, o.option_text,
       FIND_IN_SET(o.option_key, REPLACE(q.answer_text, '，', ',')) > 0,
       o.sort_no
FROM question_item q
JOIN question_bank b ON b.id = q.bank_id
JOIN (
  SELECT 'A' option_key, '按照培训要求执行并及时记录' option_text, 0 sort_no
  UNION ALL SELECT 'B', '及时报告问题并配合整改', 1
  UNION ALL SELECT 'C', '隐瞒异常情况等待问题自行消失', 2
  UNION ALL SELECT 'D', '未经授权自行变更管理要求', 3
) o
WHERE b.published = TRUE
  AND q.question_type IN ('SINGLE', 'MULTIPLE', 'SINGLE_CHOICE', 'MULTIPLE_CHOICE')
  AND NOT EXISTS (
    SELECT 1 FROM question_option old
    WHERE old.question_id = q.id
  );

UPDATE question_bank b
SET question_count = (
      SELECT COUNT(*) FROM question_item q
      WHERE q.bank_id = b.id AND q.status = 'ENABLED'
    ),
    updated_at = CURRENT_TIMESTAMP(3)
WHERE b.published = TRUE;
