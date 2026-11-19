# 数据库初始化（MySQL 8）

当前完整建表脚本与代码迁移 **V60** 对齐，包含 43 张表（含 Flyway 版本记录表）。

## 新建空库

先创建空数据库，再执行 `init-schema-and-admin.sql`：

```sh
mysql -h 数据库地址 -u 数据库用户 -p -e 'CREATE DATABASE aiquestion CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;'
mysql -h 数据库地址 -u 数据库用户 -p --default-character-set=utf8mb4 aiquestion < deploy/database/init-schema-and-admin.sql
```

该脚本创建全部表结构，并初始化一个默认管理员，不包含演示业务数据。脚本写入 V60 基线，后端启动时不会重复执行 V1–V60 的迁移或演示数据初始化；后续版本仍通过 Flyway 升级。

**仅用于空库，不要向已有业务库导入完整建表脚本，也不要使用 `--force` 忽略错误。** 已有数据库升级使用后端自带的 Flyway 迁移，不手动修改版本记录。

## 仅初始化管理员

已有完整表结构、只需要补默认管理员时，执行：

```sh
mysql -h 数据库地址 -u 数据库用户 -p --default-character-set=utf8mb4 aiquestion < deploy/database/init-admin.sql
```

- 账号：`13800000000`
- 初始密码：`12345678`，首次登录后修改。
- 同手机号账号已存在时不新增，不重置密码，不更改状态或其他信息。
- 同名但不同手机号账号存在时，唯一约束会阻止新增；请先核对账号，脚本不会覆盖已有账号。
- 项目目前没有独立的 `SUPER_ADMIN` 角色；此处初始化的是管理端默认企业管理员，沿用现有 `app_user` 账号模型。

完整建表脚本已包含相同管理员初始化逻辑，无需再单独执行管理员脚本。

## 清空数据并重新初始化管理员

已有 V60 或更高版本表结构需要重置业务数据时，执行同一个文件 `reset-data-and-admin.sql`：

```sh
mysql -h 数据库地址 -u 数据库用户 -p --default-character-set=utf8mb4 aiquestion < deploy/database/reset-data-and-admin.sql
```

执行前停止应用及后台任务。该脚本同时清理已创建的 AI 配置、对话历史和会话删除标记；删除全部业务数据和原有账号，再创建默认管理员 `13800000000 / 12345678`，保留表结构和 Flyway 版本记录，不删除 OSS 或服务器上的文件。所有删除和账号初始化在同一事务中，失败自动回滚；不重置各表自增序列。需要创建、执行、删除存储过程的权限。失败时修复原因后，先执行 `DROP PROCEDURE IF EXISTS reset_business_data_and_admin_v60;`，再重新执行脚本。

## 测试库升级 V61 时保留数据

首次启动包含此开关的后端前，在测试服后端容器环境中设置 `V61_DATA_MODE=preserve`。仓库的 Compose 支持从同目录的 .env 传入该变量。更新配置后重新创建容器；所有连接同一测试库的后端实例须使用同一新版和配置。

V61 在 preserve 模式下保留现有账号、密码和业务数据，Flyway 正常记录 V61 并继续执行后续迁移。未设置变量时默认使用 reset，首次执行 V61 会清空业务数据并重建默认管理员。无效配置会中止 V61。已成功执行的 V61 不会因修改此变量而重跑；此开关仅用于 V61，不控制手动清库脚本。
