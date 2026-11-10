# 数据驾驶舱实施计划

Goal: 按已批准墨绿色设计实现管理员综合统计，全部使用真实数据。
Architecture: 管理身份守卫 + Controller/Service/Repository；SQL 企业/课程过滤、批量读取最新记录，服务层统一聚合和分页；前端 feature 分离 API、类型、图表、明细及页面组合。沿用 Semi、VChart、Tailwind 3，不新增依赖。

## 范围与口径
- 新增 /profile/dashboard，保留概览及既有明细入口。
- 培训时间按培训开始时间（未设置时创建时间）筛选课程；所有指标统计选中课程的累计数据，不将筛选时间误作学习发生时间。
- 人次=培训名单中员工与培训的唯一组合，部门按分配时快照。
- 独立培训全部材料完成；关联考试同时要求最后一次答卷已提交且通过，和已有成绩统计一致。
- 无分母显示暂无数据；未开始包含尚未产生学习会话的名单人员。
- 培训、考试分别按自己的结束时间判断逾期，同一人次合并计数。
- 日学时没有历史流水，不能由累计时长伪造；图表改为最近30日培训完成趋势。
- 学时缺失单独提示，不将旧未知时长当作完整记录。
- 五个明细页签：培训汇总、学员明细、未完成名单、考试成绩（最后一次，包含次数）、学习日志（有学习记录的人员）。支持后端分页和按培训查看明细。

## 步骤
- [x] 统计规则测试：空数据、材料为空、重考、关联考试、逾期去重、日期范围校验。
- [x] repository 提供企业隔离查询、统计及分页，service 校验参数，controller 使用 ManagementAccess。
- [x] 新增前端驾驶舱和管理员菜单，筛选/加载/异常/空状态、图表、表格、下钻。
- [x] 后端测试、前端构建与类型检查、实际接口、PC和移动视口验证。

## 验收边界
只做本地，不推送、不部署、不改远程数据。真机浏览器与并发压测另行验收。

## Verification
- Backend full tests, frontend production build, TypeScript and dashboard ESLint passed.
- Live local API: 4 trainings, 5 assignments, 1 complete, 3 overdue. All five tabs and empty filters verified. Employee returns 403; anonymous returns 401.
- Chrome 1700px desktop and 390px mobile viewport: tabs, overdue drilldown, training drilldown and reset passed; no page exceptions or document horizontal overflow.
- Server aggregates matching assignments in memory; large-roster load testing and physical mobile-device testing remain outstanding.
- Local frontend 13000 uses backend 18081. Existing IDEA backend 18080 untouched. No schema migration.
