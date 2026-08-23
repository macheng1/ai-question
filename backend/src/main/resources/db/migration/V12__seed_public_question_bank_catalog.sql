-- 公开题库目录：只登记公开资料对应的题库主题，不伪造题目数量。
-- 后续通过题目录入接口导入题目后，question_count 由系统自动维护。

INSERT INTO question_bank_category(name, parent_id, description, sort_no, created_at, updated_at)
SELECT x.name, NULL, x.description, x.sort_no, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)
FROM (
  SELECT '公务员考试' name, '行政职业能力测验与公共基础知识' description, 10 sort_no
  UNION ALL SELECT '会计财税', '会计、税法与企业财税实务', 20
  UNION ALL SELECT '法律职业资格', '法律法规与法律职业资格考试相关知识', 30
  UNION ALL SELECT '安全生产', '生产经营单位安全生产法律法规与管理', 40
  UNION ALL SELECT '消防安全', '消防法律法规、消防设施与应急管理', 50
  UNION ALL SELECT '建筑工程', '建筑工程法律法规与施工管理', 60
  UNION ALL SELECT '特种设备', '特种设备安全管理与作业人员知识', 70
  UNION ALL SELECT '食品安全', '食品生产经营与食品安全法律法规', 80
  UNION ALL SELECT '医药卫生', '药品管理、医疗卫生与健康管理', 90
  UNION ALL SELECT '人力资源', '劳动法律法规与人力资源管理', 100
  UNION ALL SELECT '物流运输', '道路运输、物流管理与交通安全', 110
  UNION ALL SELECT '电子商务', '电子商务经营与消费者权益保护', 120
  UNION ALL SELECT '网络信息安全', '网络安全、数据安全与个人信息保护', 130
  UNION ALL SELECT '教育培训', '教师资格、教育法律法规与职业素养', 140
  UNION ALL SELECT '质量管理', '产品质量、标准化与质量管理', 150
  UNION ALL SELECT '煤矿行业', '煤矿安全生产与矿山行业规范', 160
  UNION ALL SELECT '职业技能', '职业技能等级与岗位技能培训', 170
  UNION ALL SELECT '通用企业培训', '企业通用制度、职业素养与安全培训', 180
) x
WHERE NOT EXISTS (
  SELECT 1 FROM question_bank_category c WHERE c.name = x.name AND c.parent_id IS NULL
);

INSERT INTO question_bank(name, question_count, category, source, published, created_at, updated_at)
SELECT x.name, 0, x.category, x.source, TRUE, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)
FROM (
  SELECT '中华人民共和国安全生产法' name, '安全生产' category, '中国人大网：中华人民共和国安全生产法' source
  UNION ALL SELECT '中华人民共和国消防法', '消防安全', '国家市场监督管理总局：中华人民共和国消防法'
  UNION ALL SELECT '中华人民共和国劳动法', '人力资源', '中国政府网：中华人民共和国劳动法'
  UNION ALL SELECT '中华人民共和国劳动合同法', '人力资源', '中国政府网：中华人民共和国劳动合同法'
  UNION ALL SELECT '中华人民共和国会计法', '会计财税', '国家税务总局政策法规库：中华人民共和国会计法（2024年修正）'
  UNION ALL SELECT '中华人民共和国个人所得税法', '会计财税', '国家税务总局：中华人民共和国个人所得税法'
  UNION ALL SELECT '中华人民共和国电子商务法', '电子商务', '工业和信息化部：中华人民共和国电子商务法'
  UNION ALL SELECT '中华人民共和国网络安全法', '网络信息安全', '工业和信息化部：中华人民共和国网络安全法'
  UNION ALL SELECT '中华人民共和国数据安全法', '网络信息安全', '国家法律法规数据库：中华人民共和国数据安全法'
  UNION ALL SELECT '中华人民共和国个人信息保护法', '网络信息安全', '国家法律法规数据库：中华人民共和国个人信息保护法'
  UNION ALL SELECT '中华人民共和国特种设备安全法', '特种设备', '国家市场监督管理总局：中华人民共和国特种设备安全法'
  UNION ALL SELECT '中华人民共和国食品安全法', '食品安全', '中国人大网：中华人民共和国食品安全法'
  UNION ALL SELECT '中华人民共和国药品管理法', '医药卫生', '国家卫生健康委员会：中华人民共和国药品管理法'
  UNION ALL SELECT '中华人民共和国建筑法', '建筑工程', '住房和城乡建设领域公开法规资料：中华人民共和国建筑法'
  UNION ALL SELECT '中华人民共和国道路交通安全法', '物流运输', '国家法律法规数据库：中华人民共和国道路交通安全法'
  UNION ALL SELECT '中华人民共和国公务员法', '公务员考试', '国家法律法规数据库：中华人民共和国公务员法'
  UNION ALL SELECT '行政职业能力测验·常识判断', '公务员考试', '公务员考试公共科目考试大纲相关知识'
  UNION ALL SELECT '法律职业资格·民法基础', '法律职业资格', '国家统一法律职业资格考试相关法律法规'
  UNION ALL SELECT '法律职业资格·刑法基础', '法律职业资格', '国家统一法律职业资格考试相关法律法规'
  UNION ALL SELECT '安全生产管理人员培训', '安全生产', '应急管理领域安全生产培训相关公开法规'
  UNION ALL SELECT '企业消防安全培训', '消防安全', '消防安全法律法规与单位消防安全管理公开资料'
  UNION ALL SELECT '食品生产经营安全培训', '食品安全', '市场监管领域食品安全法律法规与标准公开资料'
  UNION ALL SELECT '特种设备作业人员安全培训', '特种设备', '市场监管领域特种设备安全技术规范公开资料'
  UNION ALL SELECT '建筑施工安全培训', '建筑工程', '住房和城乡建设领域建筑施工安全公开法规'
  UNION ALL SELECT '企业劳动用工合规培训', '人力资源', '人力资源和社会保障领域劳动用工公开法规'
  UNION ALL SELECT '企业网络安全基础培训', '网络信息安全', '工业和信息化领域网络安全公开法规'
  UNION ALL SELECT '企业质量管理基础培训', '质量管理', '市场监管领域产品质量与标准化公开法规'
  UNION ALL SELECT '煤矿安全生产基础知识', '煤矿行业', '煤矿安全规程及煤矿安全生产公开法规'
  UNION ALL SELECT '职业素养与企业制度培训', '通用企业培训', '企业培训通用公开资料'
) x
WHERE NOT EXISTS (SELECT 1 FROM question_bank b WHERE b.name = x.name);
