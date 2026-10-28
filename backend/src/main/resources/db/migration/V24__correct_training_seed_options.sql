-- 新版本替换V16的错误通用选项，旧题仅停用，不改变历史试卷和答卷内容。
CREATE TEMPORARY TABLE training_option_correction (
 suffix VARCHAR(300), a VARCHAR(500), b VARCHAR(500), c VARCHAR(500), d VARCHAR(500)
);
INSERT INTO training_option_correction VALUES
('下列哪项最适合作为岗位培训后的首要行动？','将所学要求应用到岗位操作，并记录实施情况','只保存课件，不改变实际工作方式','仅向同事转发培训通知','完成签到后忽略培训内容'),
('发现作业现场存在明显风险时，首先应当做什么？','按应急规程控制风险、保护人员并及时报告','继续作业，等到下班后再处理','只拍照留存，不采取保护措施','隐瞒风险，避免影响工作进度'),
('下列哪项最能体现规范化管理？','建立制度流程，开展培训检查并跟踪整改','仅在发生事故后临时处理','完全依赖个人经验，取消操作流程','只追求速度，不保留检查记录'),
('保存培训和检查记录的主要目的是什么？','追溯过程、复盘问题并持续改进','代替实际培训与检查','保证以后不再需要开展检查','仅用于增加档案数量'),
('高风险岗位人员上岗前最重要的条件是什么？','完成必要培训，并具备岗位要求的资格或能力','仅凭个人意愿申请上岗','只要熟悉同事即可独立上岗','先独立作业，再补充必要培训'),
('开展岗位安全或合规管理通常需要哪些措施？','建立并执行岗位制度与操作流程','开展教育培训、监督检查和问题整改','隐瞒发现的问题，避免留下记录','取消必要检查以缩短作业时间'),
('发现异常情况时，哪些做法是正确的？','按规程停止相关危险操作或采取控制措施','及时向有关负责人报告并配合处置','未经评估继续扩大作业范围','删除异常记录以避免追责'),
('一份完整的培训记录通常应包含哪些内容？','培训主题、内容和开展时间','参加人员及考核或评估情况','与培训无关的私人聊天记录','随意填写但未实际参加的人员'),
('保护业务数据或个人信息时，应当遵循哪些要求？','按授权范围和工作目的使用数据','采取访问控制等措施防止泄露和滥用','将账号密码公开给所有人使用','随意向无关人员发送完整数据'),
('应急处置结束后，哪些工作仍然需要开展？','复盘事件原因并完善预防措施','跟踪整改效果，更新培训或应急安排','立即删除全部处置记录','不再检查整改措施是否落实');
CREATE TEMPORARY TABLE training_question_replacement AS
SELECT q.id old_id, q.bank_id, q.question_type, q.stem, q.answer_text, q.analysis,
 q.tags, q.difficulty, q.score, q.sort_no, c.a, c.b, c.c, c.d
FROM question_item q
JOIN question_bank b ON b.id=q.bank_id AND b.published=TRUE
JOIN training_option_correction c ON q.stem=CONCAT('在“',b.name,'”相关学习中，',c.suffix)
WHERE q.source='公开法规与行业通用培训资料'
AND q.status='ENABLED'
AND EXISTS (SELECT 1 FROM question_option o WHERE o.question_id=q.id AND o.option_key='A' AND o.option_text='按照培训要求执行并及时记录');

INSERT INTO question_item(bank_id,question_type,stem,answer_text,analysis,tags,source,difficulty,score,sort_no,status,created_at,updated_at)
SELECT bank_id,question_type,stem,answer_text,analysis,tags,CONCAT('岗位培训示例题（选项修订）#',old_id),difficulty,score,sort_no,'ENABLED',CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3)
FROM training_question_replacement;

INSERT INTO question_option(question_id,option_key,option_text,is_correct,sort_no)
SELECT q.id,k.option_key,
 CASE k.option_key WHEN 'A' THEN r.a WHEN 'B' THEN r.b WHEN 'C' THEN r.c ELSE r.d END,
 FIND_IN_SET(k.option_key,q.answer_text)>0,k.sort_no
FROM training_question_replacement r
JOIN question_item q ON q.bank_id=r.bank_id AND q.source=CONCAT('岗位培训示例题（选项修订）#',r.old_id)
CROSS JOIN (SELECT 'A' option_key,0 sort_no UNION ALL SELECT 'B',1 UNION ALL SELECT 'C',2 UNION ALL SELECT 'D',3) k;

UPDATE question_item q JOIN training_question_replacement r ON r.old_id=q.id SET q.status='DISABLED',q.updated_at=CURRENT_TIMESTAMP(3);
UPDATE question_bank b SET question_count=(SELECT COUNT(*) FROM question_item q WHERE q.bank_id=b.id AND q.status='ENABLED') WHERE b.published=TRUE;
DROP TEMPORARY TABLE training_question_replacement;
DROP TEMPORARY TABLE training_option_correction;
