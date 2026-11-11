# 项目资料索引

本目录集中维护 e-learning 平台的项目资料。历史文档可能使用旧名称“智企云考”。
运行配置以当前代码及环境配置为准；历史计划中的勾选状态不代表当前版本已验收。

## 项目与本地运行

- [项目说明](../README.md)
- [后端运行、数据库迁移、OSS、邮件和部署说明](../backend/README.md)
- [开发规范](development-standards.md)
- [密钥与敏感配置规范](security-secrets.md)

## 业务与权限

- [管理员与员工账号分离](account-separation.md)
- [部门与员工功能发布说明](department-release.md)
- [培训范围归属方案](superpowers/plans/2026-11-06-training-owned-scope.md)
- [员工培训与考试状态方案](superpowers/plans/2026-11-06-learner-task-status.md)
- [手动培训提醒方案](superpowers/plans/2026-11-06-manual-training-reminder.md)
- [数据驾驶舱方案](superpowers/plans/2026-11-09-dashboard.md)

## 数据库检查

- [2026-11-11 数据库结构与初始化检查](reviews/2026-11-11-database-initialization-audit.md)

## 页面与代码检查

- [页面布局检查](ui/page-layout-audit.md)
- [Semi UI 替换记录](ui/semi-migration.md)
- [冗余与未使用代码检查](reviews/2026-11-04-unused-code-audit.md)

## 测试记录

- [2026-11-06 本地回归](qa/2026-11-06-local-regression.md)
- [2026-11-06 手动提醒验证](qa/2026-11-06-manual-reminder.md)
- [2026-11-09 培训、考试与统计串联验证](qa/2026-11-09-training-exam-statistics.md)
- [2026-11-09 用户流程验收与证据](qa/2026-11-09-user-journey.md)

测试报告只证明其记录的环境、版本和场景，不自动代表后续版本或线上环境通过验收。

## 维护约定

- 新功能记录业务规则、权限边界、数据变化及验收结果，并更新本索引。
- 实施计划放在 `superpowers/plans/`，测试证据放在 `qa/`，界面检查放在 `ui/`，代码审查放在 `reviews/`。
- 已讨论但尚未实现的需求标为“待开发”，不能仅凭方案确认标为完成。例如部门人员变动触发必修培训自动分配，目前尚未实现。
- 数据库迁移脚本保留在 `backend/src/main/resources/db/migration/`，文档引用脚本，不复制维护第二份。
- 不在文档提交真实密码、令牌、OSS 密钥或数据库连接凭据。
- 已过期的方案保留日期并注明被哪份方案替代，避免历史决策与现行规则混淆。
