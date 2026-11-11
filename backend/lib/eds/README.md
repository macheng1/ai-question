# EDS Java 离线 SDK

## 来源与版本

来自用户提供的 `Java-1.11.254.zip`，于 2026-11-11 收录，标称版本 `1.11.254`。与同批 `java-离线sdk.zip` 中的对应文件逐字节一致。保留原始二进制，不重新打包。

## 文件用途

| 文件 | 用途 |
| --- | --- |
| `repository/com/casela/vendor/eds-s3/1.11.254/eds-s3-1.11.254.jar` | S3 模块，不包含 AWS Core，不能单独作为全部运行依赖。 |
| `aws-java-sdk-osgi-1.11.254.jar` | 厂商 SDK 集合包，约 78 MiB，包含 Core 和 S3 等模块。 |
| `SHA256SUMS` | 原始 JAR 的 SHA-256 校验值。 |

两个 JAR 存在模块重叠，保留用于后续适配与差异核对，不应同时直接加入应用运行类路径。运行依赖组合见下文。

未收录源码包、original 打包中间产物、重复 ZIP、包含示例凭证的 Java 文件及说明文档。

## 当前接入状态

已配置 Maven 项目内仓库和 Docker 构建。仅使用 S3 模块，Core、KMS、JMESPath 使用同版本 Maven 依赖；OSGi 集合包不进入运行类路径。首次构建仍需下载标准依赖。

使用 STORAGE_PROVIDER 选择存储实现。凭证通过环境变量注入。配置说明见 docs/storage-configuration.md。

## 校验

在本目录运行：

```sh
shasum -a 256 -c SHA256SUMS
```

## 分发说明

此目录保存供应方交付的二进制，不代表已核实其与官方 Maven 构件完全一致。供应方未在本次交付中提供单独的许可证文件；公开分发或对外发布前，应确认相应授权及声明要求。
