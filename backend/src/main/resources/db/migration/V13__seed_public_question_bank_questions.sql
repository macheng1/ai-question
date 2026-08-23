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
