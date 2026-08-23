-- 公共题库初始化汇总脚本
-- 仅包含：公共题库分类、公共题库、公共题目及题目选项。
-- 不包含：用户、考试、试卷、答题记录、成绩、自测及业务配置数据。
-- 前置条件：目标数据库已执行基础表结构迁移（至少 V1-V11、V19-V20）。
-- 说明：脚本可重复执行，已存在的数据会被跳过。
SET NAMES utf8mb4;
START TRANSACTION;

-- ===== backend/src/main/resources/db/migration/V12__seed_public_question_bank_catalog.sql =====
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

-- ===== backend/src/main/resources/db/migration/V13__seed_public_question_bank_questions.sql =====
-- 为公开题库补充首批可浏览试题。
-- 题干均根据题库对应的公开法规/行业资料改写为练习题，来源地址保存在 source 字段，便于追溯。
-- 使用题库名称关联，避免依赖不同环境中的自增 ID；重复执行不会重复插入。
INSERT INTO question_item (bank_id, question_type, stem, answer_text, analysis, tags, source, difficulty, score, status, created_at, updated_at)
SELECT b.id, q.question_type, q.stem, q.answer_text, q.analysis, q.tags, q.source, q.difficulty, 1, 'ENABLED', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)
FROM question_bank b
JOIN (
  SELECT '中华人民共和国安全生产法' bank_name,'TRUE_FALSE' question_type,'生产经营单位的主要负责人对本单位的安全生产工作全面负责。' stem,'TRUE' answer_text,'《安全生产法》明确主要负责人承担本单位安全生产工作全面责任。' analysis,'安全生产法,主要负责人' tags,'https://www.npc.gov.cn/c1773/c1848/c21114/c23437/201905/t20190522_8318.html' source,'EASY' difficulty
  UNION ALL SELECT '中华人民共和国消防法','TRUE_FALSE','任何单位和个人都有维护消防安全、保护消防设施、预防火灾、报告火警的义务。','TRUE','《消防法》规定单位和个人都负有维护消防安全、保护消防设施等义务。','消防法,消防责任','https://www.samr.gov.cn/zw/zfxxgk/fdzdgknr/bgt/art/2023/art_e9e34f0731c249a891ea9c925e17f237.html','EASY'
  UNION ALL SELECT '中华人民共和国劳动法','TRUE_FALSE','劳动者享有平等就业和选择职业的权利。','TRUE','《劳动法》将平等就业和选择职业列为劳动者的基本权利。','劳动法,劳动者权利','https://www.gov.cn/banshi/2005-05/25/content_905.htm','EASY'
  UNION ALL SELECT '中华人民共和国劳动合同法','TRUE_FALSE','建立劳动关系，应当订立书面劳动合同。','TRUE','《劳动合同法》规定建立劳动关系，应当订立书面劳动合同。','劳动合同法,劳动合同','https://www.gov.cn/flfg/2007-06/29/content_669394.htm','EASY'
  UNION ALL SELECT '中华人民共和国会计法','TRUE_FALSE','单位负责人对本单位的会计工作和会计资料的真实性、完整性负责。','TRUE','《会计法》规定单位负责人对本单位会计工作和会计资料的真实性、完整性负责。','会计法,会计责任','https://fgk.chinatax.gov.cn/zcfgk/c100009/c5211790/content.html','MEDIUM'
  UNION ALL SELECT '中华人民共和国个人所得税法','TRUE_FALSE','居民个人从中国境内和境外取得的所得，依法缴纳个人所得税。','TRUE','居民个人的境内、境外所得均属于个人所得税法的征税范围。','个人所得税,税法','https://www.chinatax.gov.cn/n810219/n810744/n3752930/n3752974/c3970366/content.html','MEDIUM'
  UNION ALL SELECT '中华人民共和国电子商务法','TRUE_FALSE','电子商务经营者应当依法办理市场主体登记，但法律另有规定的除外。','TRUE','《电子商务法》确立了电子商务经营者依法登记的基本要求，并保留法定例外。','电子商务法,市场主体登记','https://www.miit.gov.cn/zwgk/zcwj/flfg/art/2022/art_fa48eb1becd2419ddbe0d14b0484d2507.html','MEDIUM'
  UNION ALL SELECT '中华人民共和国网络安全法','TRUE_FALSE','网络运营者应当采取数据分类、重要数据备份和加密等措施，保障网络数据安全。','TRUE','网络运营者应当履行网络安全保护义务，采取技术和管理措施保障数据安全。','网络安全法,数据安全','https://www.miit.gov.cn/jgsj/zfs/fl/art/2020/art_85f74fb2531449ddbe0d14b0484d2507.html','MEDIUM'
  UNION ALL SELECT '中华人民共和国数据安全法','TRUE_FALSE','开展数据处理活动应当建立健全全流程数据安全管理制度。','TRUE','《数据安全法》要求建立健全全流程数据安全管理制度。','数据安全法,数据管理','https://www.gov.cn/xinwen/2021-06/11/content_5616911.htm','MEDIUM'
  UNION ALL SELECT '中华人民共和国个人信息保护法','TRUE_FALSE','处理个人信息应当遵循合法、正当、必要和诚信原则。','TRUE','《个人信息保护法》确立个人信息处理的合法、正当、必要和诚信原则。','个人信息保护法,隐私保护','https://www.gov.cn/xinwen/2021-08/20/content_5632486.htm','EASY'
  UNION ALL SELECT '中华人民共和国特种设备安全法','TRUE_FALSE','特种设备使用单位应当建立岗位责任、隐患治理等安全管理制度。','TRUE','特种设备使用单位应履行安全主体责任，建立相应安全管理制度。','特种设备,安全管理','https://www.samr.gov.cn/zw/zfxxgk/fdzdgknr/fgs/art/2023/art_ad5e293574484b48b45047ee0ede6099.html','MEDIUM'
  UNION ALL SELECT '中华人民共和国食品安全法','TRUE_FALSE','食品生产经营者应当依照法律、法规和食品安全标准从事生产经营活动。','TRUE','食品生产经营者必须遵守食品安全法律法规和标准，对食品安全负责。','食品安全法,食品经营','https://www.npc.gov.cn/npc/c2/c183/c198/201905/t20190522_74454.html','EASY'
  UNION ALL SELECT '中华人民共和国药品管理法','TRUE_FALSE','药品上市许可持有人依法对药品的非临床研究、临床试验、生产经营和使用全过程负责。','TRUE','药品上市许可持有人制度要求持有人对药品全生命周期承担责任。','药品管理法,药品安全','https://www.nhc.gov.cn/fzs/c100048/201909/3a86b1c3ae204640acf57080a6486240.shtml','MEDIUM'
  UNION ALL SELECT '中华人民共和国建筑法','TRUE_FALSE','建筑工程开工前，建设单位应当按照国家有关规定向工程所在地县级以上人民政府建设行政主管部门申请领取施工许可证。','TRUE','《建筑法》规定建筑工程开工前应依法办理施工许可证，法定限额以下工程除外。','建筑法,施工许可','https://www.gov.cn/banshi/2005-06/21/content_8265.htm','MEDIUM'
  UNION ALL SELECT '中华人民共和国道路交通安全法','TRUE_FALSE','机动车、非机动车实行右侧通行。','TRUE','《道路交通安全法》规定机动车、非机动车实行右侧通行。','道路交通安全法,交通规则','https://www.gov.cn/banshi/2005-08/23/content_25622.htm','EASY'
  UNION ALL SELECT '中华人民共和国公务员法','TRUE_FALSE','公务员应当履行忠于国家、服务人民、恪尽职守、清正廉洁的义务。','TRUE','《公务员法》将忠于国家、服务人民、恪尽职守、清正廉洁列为公务员义务。','公务员法,公务员义务','https://www.gov.cn/xinwen/2018-12/29/content_5353323.htm','EASY'
  UNION ALL SELECT '行政职业能力测验·常识判断','TRUE_FALSE','宪法是国家的根本法，具有最高的法律效力。','TRUE','宪法在中国特色社会主义法律体系中具有最高法律效力。','常识判断,宪法','公开法律基础资料','EASY'
  UNION ALL SELECT '法律职业资格·民法基础','TRUE_FALSE','民事主体从事民事活动，应当遵循诚信原则，秉持诚实，恪守承诺。','TRUE','《民法典》将诚信原则作为民事活动的基本原则。','民法典,诚信原则','https://www.gov.cn/xinwen/2020-06/01/content_5516649.htm','EASY'
  UNION ALL SELECT '法律职业资格·刑法基础','TRUE_FALSE','法律明文规定为犯罪行为的，依照法律定罪处刑；法律没有明文规定为犯罪行为的，不得定罪处刑。','TRUE','刑法的罪刑法定原则要求犯罪和刑罚必须有法律明文规定。','刑法,罪刑法定','https://www.gov.cn/flfg/2011-02/25/content_1826571.htm','MEDIUM'
  UNION ALL SELECT '安全生产管理人员培训','TRUE_FALSE','安全生产管理人员应当根据本单位生产经营特点，对安全生产状况进行经常性检查。','TRUE','安全生产管理人员需要结合生产经营特点开展经常性安全检查。','安全培训,隐患排查','https://www.npc.gov.cn/c1773/c1848/c21114/c23437/201905/t20190522_8318.html','EASY'
  UNION ALL SELECT '企业消防安全培训','TRUE_FALSE','机关、团体、企业、事业等单位应当落实消防安全责任制。','TRUE','单位是消防安全责任主体，应当落实消防安全责任制。','消防培训,责任制','https://www.samr.gov.cn/zw/zfxxgk/fdzdgknr/bgt/art/2023/art_e9e34f0731c249a891ea9c925e17f237.html','EASY'
  UNION ALL SELECT '食品生产经营安全培训','TRUE_FALSE','食品生产经营企业应当建立食品安全追溯体系，保证食品可追溯。','TRUE','食品安全追溯有助于落实食品生产经营者责任并实现风险管理。','食品培训,追溯体系','https://www.npc.gov.cn/npc/c2/c183/c198/201905/t20190522_74454.html','MEDIUM'
  UNION ALL SELECT '特种设备作业人员安全培训','TRUE_FALSE','特种设备作业人员应当按照国家有关规定取得相应资格，方可从事相关工作。','TRUE','特种设备作业人员需要依法取得相应资格后方可上岗。','特种设备培训,作业资格','https://www.samr.gov.cn/zw/zfxxgk/fdzdgknr/fgs/art/2023/art_ad5e293574484b48b45047ee0ede6099.html','EASY'
  UNION ALL SELECT '建筑施工安全培训','TRUE_FALSE','建筑施工企业应当建立健全劳动安全生产教育培训制度。','TRUE','建筑施工企业应依法开展劳动安全生产教育培训。','建筑施工,安全培训','https://www.gov.cn/banshi/2005-06/21/content_8265.htm','EASY'
  UNION ALL SELECT '企业劳动用工合规培训','TRUE_FALSE','用人单位不得违反劳动法规定延长劳动者的工作时间。','TRUE','用人单位应遵守工时制度，不得违法延长劳动者工作时间。','劳动用工,合规培训','https://www.gov.cn/banshi/2005-05/25/content_905.htm','EASY'
  UNION ALL SELECT '企业网络安全基础培训','TRUE_FALSE','网络运营者应当制定网络安全事件应急预案，并及时处置系统漏洞、计算机病毒等安全风险。','TRUE','网络安全法要求网络运营者建立应急机制并及时处置安全风险。','网络安全培训,应急响应','https://www.miit.gov.cn/jgsj/zfs/fl/art/2020/art_85f74fb2531449ddbe0d14b0484d2507.html','MEDIUM'
  UNION ALL SELECT '企业质量管理基础培训','TRUE_FALSE','生产者应当对其生产的产品质量负责。','TRUE','产品质量责任首先由生产者承担，经营者也应履行相应义务。','质量管理,产品质量','https://www.gov.cn/flfg/2009-08/27/content_1424126.htm','EASY'
  UNION ALL SELECT '煤矿安全生产基础知识','TRUE_FALSE','煤矿企业必须建立健全安全生产责任制，加强安全生产标准化建设。','TRUE','煤矿安全生产需要落实责任制并持续推进安全生产标准化。','煤矿安全,责任制','https://www.gov.cn/zhengce/content/2022-08/09/content_5704811.htm','MEDIUM'
  UNION ALL SELECT '职业素养与企业制度培训','TRUE_FALSE','员工应当遵守企业依法制定并公示的规章制度。','TRUE','遵守依法制定并公示的规章制度是企业员工基本职业要求。','职业素养,企业制度','企业培训通用公开资料','EASY'
) q ON q.bank_name = b.name
WHERE b.published = TRUE
  AND NOT EXISTS (SELECT 1 FROM question_item old WHERE old.bank_id = b.id AND old.stem = q.stem);

UPDATE question_bank b
SET question_count = (SELECT COUNT(*) FROM question_item q WHERE q.bank_id = b.id AND q.status = 'ENABLED'),
    updated_at = CURRENT_TIMESTAMP(3)
WHERE b.published = TRUE;

-- ===== backend/src/main/resources/db/migration/V14__expand_public_question_bank_questions.sql =====
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

-- ===== backend/src/main/resources/db/migration/V15__add_true_false_options_for_public_questions.sql =====
-- 判断题也维护标准选项，公开题库页面和考试页面统一展示 A/B 选项。
INSERT INTO question_option (question_id, option_key, option_text, is_correct, sort_no)
SELECT q.id, 'A', '正确', q.answer_text = 'TRUE', 0
FROM question_item q
JOIN question_bank b ON b.id = q.bank_id
WHERE b.published = TRUE
  AND q.question_type IN ('TRUE_FALSE', 'JUDGE')
  AND NOT EXISTS (
    SELECT 1 FROM question_option o
    WHERE o.question_id = q.id AND o.option_key = 'A'
  );

INSERT INTO question_option (question_id, option_key, option_text, is_correct, sort_no)
SELECT q.id, 'B', '错误', q.answer_text = 'FALSE', 1
FROM question_item q
JOIN question_bank b ON b.id = q.bank_id
WHERE b.published = TRUE
  AND q.question_type IN ('TRUE_FALSE', 'JUDGE')
  AND NOT EXISTS (
    SELECT 1 FROM question_option o
    WHERE o.question_id = q.id AND o.option_key = 'B'
  );

-- ===== backend/src/main/resources/db/migration/V16__add_single_multiple_questions_for_public_banks.sql =====
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

COMMIT;

