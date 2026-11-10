# 前后端冗余代码检查（2026-11-04）

范围：前端 app/features/components/lib 下 81 个 TypeScript 文件、后端 main 下 70 个 Java 文件及 Mapper XML。检查未使用声明、模块引用、服务及私有方法调用、重复代码与旧接口关联。静态扫描不能证明外部接口没有调用方。

## 已清理

- 题目管理重复且未调用的导入函数、未使用的题型展示变量。
- 未使用的题库删除状态、组卷路由实例、表格参数和 Java ArrayList 导入。
- 题目更新改为使用已有静态导入；随机组卷按提交字段显式构造参数。
- 无调用方的 apiRequest 兼容封装、RequestConfig 类型别名、lib/api/index.ts 重导出入口。
- 无调用方的 getPublicExam、submitPublicExam、getExamRecords 前端封装；保留实际使用的开始考试、进度、交卷链路。
- 考试次数状态有业务意义，已接入结果展示，未直接删除。

## 后端核查结论

- 服务方法、私有方法调用扫描未发现可明确删除的孤立方法。Spring 路由、配置、序列化 DTO、MyBatis Mapper 不能按普通直接引用判断废弃。
- Maven PMD CPD（100 token 阈值）未检出重复块；不代表没有更短的重复逻辑。
- TrainingService.learning 仍由培训查询和上传返回使用，并读取 training_video_progress；保留历史进度表及迁移。
- 旧 /trainings/{id}/videos/{video}/progress 未发现当前前端调用；作为外部可达旧接口暂保留，停用前应核对部署访问日志及历史客户端。

## 验证及边界

- TypeScript noUnusedLocals/noUnusedParameters 检查通过。
- ESLint 零错误，仍有 13 条警告（Hook 依赖、图片元素、配置匿名导出），未通过禁用规则掩盖。
- 后端 Maven 测试通过，48 项测试、零失败、零错误。
- 本轮未进行全页面浏览器回归，未提交或推送，未宣称全项目绝对不存在冗余。
