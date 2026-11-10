# Semi UI 统一检查（2026-11-03）

范围：前端 app、components、features 下的 TSX，以及全局和 H5 样式。

- 表单：使用 Semi Input、InputNumber、DatePicker、Select、Cascader、Radio、Checkbox、Switch、Upload 等原组件。
- 布局：使用 Layout、Card、Space 与页面内 Tailwind flex/grid；布局留白放在内容容器，网格按实际列数设置。
- 导航：顶部 Nav、账号 Dropdown；管理侧栏 Nav，窄屏横向导航。
- 标题：客户端标题使用 Typography；服务端考试页保留语义标题，避免破坏服务器数据加载边界。
- 图标：直接使用 Semi Icons，删除 AppIcon 和 ExamIcons 中转封装。
- 样式：移除通用控件和 H5 对 Semi 内部选择器的外观覆盖；保留打印、窄屏弹窗和触屏区域的必要适配。
- 保留：业务模块、鉴权、视频播放器和图表实现。页面业务容器不是新的通用 UI 组件库。

验证：完整 Next.js 生产构建；代码扫描；本地浏览器测试账号登录、管理导航、部门列表；窄屏截图检查。

限制：尚未逐项重跑所有编辑保存、视频播放、答题交卷及评估提交流程。构建仍存在历史 lint 警告，不能将构建通过视为全部业务回归通过。

逐页布局检查及限制见 [page-layout-audit.md](./page-layout-audit.md)。
