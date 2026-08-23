-- 公开在线自测目录：每套自测绑定一个公开题库，游客可浏览和开始自测。
CREATE TABLE IF NOT EXISTS public_self_test (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  bank_id BIGINT NOT NULL,
  name VARCHAR(160) NOT NULL,
  description VARCHAR(500) NULL,
  single_count INT NOT NULL DEFAULT 30,
  multiple_count INT NOT NULL DEFAULT 10,
  true_false_count INT NOT NULL DEFAULT 10,
  random_order BOOLEAN NOT NULL DEFAULT TRUE,
  published BOOLEAN NOT NULL DEFAULT TRUE,
  sort_no INT NOT NULL DEFAULT 0,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  CONSTRAINT uk_public_self_test_bank UNIQUE (bank_id),
  CONSTRAINT fk_public_self_test_bank FOREIGN KEY (bank_id) REFERENCES question_bank(id)
);

-- 目录数据只引用已有公开题库；题目数量由题库实时统计，不在这里复制。
INSERT INTO public_self_test (bank_id, name, description, single_count, multiple_count, true_false_count, random_order, published, sort_no, created_at, updated_at)
SELECT b.id, x.name, x.description, 30, 10, 10, TRUE, TRUE, x.sort_no, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)
FROM (
  SELECT '人工智能知识点' name, '人工智能基础概念与常见应用自测' description, '人工智能知识点' bank_name, 10 sort_no
  UNION ALL SELECT '法律职业资格-职业道德', '法律职业资格考试职业道德与执业规范自测', '法律职业资格·民法基础', 20
  UNION ALL SELECT '全媒体运营师考卷', '全媒体运营与内容运营基础知识自测', '企业质量管理基础培训', 30
  UNION ALL SELECT '跨境电商认证', '跨境电商经营与消费者权益保护自测', '中华人民共和国电子商务法', 40
  UNION ALL SELECT '法律职业资格-民诉', '民事诉讼基础知识自测', '法律职业资格·刑法基础', 50
  UNION ALL SELECT '注册师-安全生产技术基础', '安全生产技术与岗位风险防控自测', '安全生产管理人员培训', 60
  UNION ALL SELECT '煤矿安全生产条例', '煤矿安全生产法规与基础知识自测', '煤矿安全生产基础知识', 70
  UNION ALL SELECT '网络信息安全基础', '网络安全、数据安全与个人信息保护自测', '企业网络安全基础培训', 80
  UNION ALL SELECT '企业劳动用工合规', '劳动用工与企业合规基础自测', '企业劳动用工合规培训', 90
  UNION ALL SELECT '消防安全基础', '单位消防安全管理基础自测', '企业消防安全培训', 100
  UNION ALL SELECT '职业素养与企业制度', '职业素养与企业制度基础自测', '职业素养与企业制度培训', 110
) x
JOIN question_bank b ON b.name = x.bank_name AND b.published = TRUE
WHERE NOT EXISTS (SELECT 1 FROM public_self_test p WHERE p.bank_id = b.id);
