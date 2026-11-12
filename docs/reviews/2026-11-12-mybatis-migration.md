# MyBatis Repository/XML 迁移验收

## 范围

将原有运行时 JdbcTemplate、NamedParameterJdbcTemplate 和题目 Repository 中的注解 SQL 移入 MyBatis XML。覆盖账号/学员/部门、培训/名单/材料/复制、学习会话/计时/进度、考试/成绩、试卷辅助查询、统计、提醒、个人记录及旧公开接口。

- 新增 26 个 Repository 接口；DashboardRepository 从 JDBC 类改为 Mapper 接口。
- 新增 27 个 XML 文件，并将题目引用查询加入原有 QuestionItemMapper.xml；迁移 SQL 共 241 条。
- Controller 中的数据访问及相应业务流程提取至 Service，事务仍由 Spring 管理。
- 手动创建 Service 改为依赖注入；自增主键通过 GeneratedId 回填。
- 保持动态企业/培训/考试范围、锁顺序、事务隔离、数据库计时和文件事务清理逻辑。
- 关闭会话级缓存、保留空值 Map 字段；复杂返回结构仍暂用 Map，单行结果通过 RepositoryRows 保持原有无记录/多记录语义。
- 不改接口 URL、前端、数据库结构或历史 Flyway 迁移。

## 验证

2026-11-12 在独立 MySQL 8 临时容器执行全套迁移和测试，未修改原开发数据库。

- 原始基线：159 项，0 失败，0 错误，1 跳过。
- 最终 `mvn -q package`（启用 MySQL 集成测试）：173 项，0 失败，0 错误，1 跳过。
- 跳过项为原有真实对象存储测试 `TrainingOssLiveTest`。
- 新增 Mapper XML 解析/方法绑定与运行时数据访问分层检查。
- 11 项实际 MySQL 集成测试：学员保存/空值、身份隔离、培训保存、名单失败回滚、复制主键/材料页、文件失败事务清理、阅读心跳/顺序、并发同心跳只记一次、视频进度、固定考试/重复交卷/记录、统计筛选、随机抽题及恢复（部分验证组合在同一测试内）。
- 复核随机组卷遇到不可变集合的问题并修正；已增加单元与实际数据库回归。
- 独立代码复查比较了迁移前后的 SQL、动态条件、事务与数据类型，没有未解决的迁移回归发现。
- 前端原有工作区改动、Flyway 原始文件均与迁移前快照一致；`git diff --check` 通过。

## 边界

本轮没有浏览器全流程回归、部署或重启正在运行的应用；集成测试通过 Spring MockMvc 和真实服务/数据库验证接口。不是对每条 SQL 所有组合的穷举测试。

原有 `OrganizationService.saveDepartment` 将部门 ID 传给员工必修分配方法的问题仍保留，按计划作为独立缺陷处理，避免迁移夹带业务行为改变。
