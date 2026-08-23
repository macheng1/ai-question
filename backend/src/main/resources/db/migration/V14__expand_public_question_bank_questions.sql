-- 将公开题库扩充到每个题库至少 10 道可浏览试题。
-- 这些是基于公开法规/行业通用知识整理的练习题，题库详情页可直接展示。
-- 通过题库名称关联并按题干幂等，重复启动不会产生重复数据。
INSERT INTO question_item (bank_id, question_type, stem, answer_text, analysis, tags, source, difficulty, score, status, created_at, updated_at)
SELECT b.id,
       'TRUE_FALSE',
       CONCAT('在“', b.name, '”相关学习中，', t.stem),
       'TRUE',
       t.analysis,
       t.tags,
       t.source,
       t.difficulty,
       1,
       'ENABLED',
       CURRENT_TIMESTAMP(3),
       CURRENT_TIMESTAMP(3)
FROM question_bank b
JOIN (
  SELECT '从业人员应当熟悉与岗位相关的安全、合规或业务要求。' stem,
         '掌握岗位相关要求是完成培训并降低工作风险的基础。' analysis,
         '岗位基础,合规要求' tags,
         '公开法规与行业通用培训资料' source,
         'EASY' difficulty
  UNION ALL SELECT '发现风险、隐患或不合规情形时，应当及时报告并按规定处理。',
         '及时报告和处置问题有助于防止风险扩大。',
         '风险管理,隐患排查','公开法规与行业通用培训资料','EASY'
  UNION ALL SELECT '单位应当通过制度、培训和检查等方式持续改进管理工作。',
         '制度建设、教育培训和监督检查是持续改进的重要手段。',
         '制度管理,持续改进','公开法规与行业通用培训资料','MEDIUM'
  UNION ALL SELECT '未经必要培训或不具备相应能力的人员，不得擅自从事高风险岗位工作。',
         '高风险岗位应落实培训、资格和授权要求，避免无资质上岗。',
         '岗位资格,培训管理','公开法规与行业通用培训资料','MEDIUM'
  UNION ALL SELECT '工作记录、检查记录和培训记录应当按照要求真实、完整地留存。',
         '真实完整的记录能够支持追溯、复盘和责任落实。',
         '记录管理,过程留痕','公开法规与行业通用培训资料','EASY'
  UNION ALL SELECT '执行岗位操作前，应当按照要求确认设备、环境或业务条件处于正常状态。',
         '作业前确认条件并检查风险，是预防事故和差错的重要步骤。',
         '作业前检查,风险预防','公开法规与行业通用培训资料','EASY'
  UNION ALL SELECT '发生异常、事故或紧急情况时，应当优先采取措施控制风险并保护人员安全。',
         '应急处置应坚持人员安全优先，并按预案报告和组织救援。',
         '应急管理,安全优先','公开法规与行业通用培训资料','MEDIUM'
  UNION ALL SELECT '个人信息、业务数据或其他受保护资料应当按照授权范围使用和保存。',
         '按授权使用并妥善保护数据，是信息安全和合规管理的基本要求。',
         '数据保护,信息安全','公开法规与行业通用培训资料','MEDIUM'
  UNION ALL SELECT '培训结束后，应当结合考试、实操或检查结果评估培训效果。',
         '通过结果评估可以发现知识薄弱点并持续优化培训内容。',
         '培训评估,能力提升','公开法规与行业通用培训资料','EASY'
) t
WHERE b.published = TRUE
  AND NOT EXISTS (
    SELECT 1
    FROM question_item old
    WHERE old.bank_id = b.id
      AND old.stem = CONCAT('在“', b.name, '”相关学习中，', t.stem)
  );

UPDATE question_bank b
SET question_count = (
      SELECT COUNT(*)
      FROM question_item q
      WHERE q.bank_id = b.id
        AND q.status = 'ENABLED'
    ),
    updated_at = CURRENT_TIMESTAMP(3)
WHERE b.published = TRUE;
