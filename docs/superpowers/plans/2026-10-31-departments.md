# 部门与员工 Implementation Plan

> **For agentic workers:** Use superpowers:subagent-driven-development to implement and review tasks.

**Goal:** 单页部门员工管理，接通考试名单与 H5 准入、部门成绩统计。
**Architecture:** 一级部门、员工名册按 created_by 隔离。考试选择 DEPARTMENT/USERS 后事务性保存范围和人员快照。ALL 保持兼容；旧受限考试无名单时明确拒绝并提示重新配置。名单不随部门人员后续调整变化。
**Tech Stack:** Next.js / React / Semi UI; Spring Boot / JdbcTemplate / MySQL / Flyway.
**Spec:** 本会话已确认的部门员工一体管理方案。

## Global Constraints
- 不改题库、判分、OSS；不新增短信、多级部门、导入、通知。
- 使用 Semi UI，沿用管理页面与 H5 风格。
- 本地验证，不操作远程数据库或发布。
- 每个 owner 内员工手机号唯一；员工可移动部门、停用；部门停用不得用于新考试。
- 考试有参与记录后不允许替换名单；无范围变动的普通编辑不重建快照。

## Review Focus
- 跨 owner 部门/员工 ID 必须拒绝。
- 空名单、非法范围必须拒绝；保存考试与名单事务回滚。
- 名单快照不受改名、调部门、停用影响。
- 受限考试学习、参考、续答都校验；ALL 兼容。
- 历史无名单受限考试明确提示；重考统计按人去重。

### Task 1: Backend
- [x] 补充边界测试；续答资格、旧考试修复回归已执行失败到通过验证。
- [x] V28 部门 organization_department(id,created_by,name,status)，员工 organization_employee(id,created_by,department_id,name,phone,job_title,status)，assessment_scope_target(task_id,target_id)，assessment_roster(task_id,employee_id,department_id,department_name,candidate_name,phone)。
- [x] OrganizationController / OrganizationService: GET/POST /management/departments; PUT /{id}; GET/POST /management/employees; PUT /{id}. Response camelCase: Department{id,name,status,employeeCount}, Employee{id,name,phone,jobTitle,departmentId,departmentName,status}.
- [x] Assessment save/update 增加 departmentIds:number[], employeeIds:number[]；详情返回相同字段。POST /management/assessment-roster/preview {candidateScope,departmentIds,employeeIds} -> roster array. GET /management/assessments/{id}/roster -> roster array with submitted:boolean。Roster row {employeeId,departmentId,departmentName,candidateName,phone,submitted}.
- [x] Service snapshots + eligibility guard + records department fields; enforce ownership and transactions.
- [x] Run Maven tests, review behavior.

### Task 2: Organization UI
- [x] features/organization/api.ts 按 Task1 合约实现。
- [x] app/profile/organization/page.tsx 部门左员工右，同页 Semi Form/Modal/Table，空态、错误、loading、搜索、停用/恢复、调部门。
- [x] ProfileSidebar 新增入口。
- [x] 类型检查和交互验证。

### Task 3: Assessment UI and integration
- [x] CandidateScopePicker 共用部门/人员选择及名单预览；接入弹窗和新建考试页、现有编辑界面。
- [x] 成绩页展示部门筛选及应考/已考/未考名册；统计页面复用名单概览。
- [x] 前后端构建、边界回归、本地完整流程；修复问题并记录验证结果。

## Verification
- Maven: {'tests': 46, 'failures': 0, 'errors': 0, 'skipped': 1}
- Next production build / scoped ESLint / git diff --check passed.
- Local API tests and H5 UI completed; evidence in chat workspace outputs/departments.
- Ruling: legacy no-roster restricted exams permit one-time repair covering ALL prior learner identities; cannot switch to ALL.
