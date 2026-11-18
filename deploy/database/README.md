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
