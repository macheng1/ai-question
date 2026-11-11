# 文件存储配置

后端通过环境变量选择存储，默认使用 EDS，无需更改前端或数据库结构。

## 测试环境

显式设置 `STORAGE_PROVIDER=aliyun`，继续使用原有 `ALIYUN_OSS_*` 配置。

## EDS 环境

```dotenv
STORAGE_PROVIDER=eds
EDS_ENDPOINT=https://存储地址:端口
EDS_BUCKET=elearning
EDS_ACCESS_KEY_ID=部署时填写
EDS_SECRET_KEY=部署时填写
EDS_REGION=us-east-1
STORAGE_PUBLIC_BASE_URL=https://应用API域名/api
```

已同步 `.env.example` 和 Docker Compose。更新变量后需重启或重建后端容器；流水线直接 docker run 时需要显式传入变量。

STORAGE_PUBLIC_BASE_URL 是浏览器可访问的 API 根地址，包含 /api，不以斜杠结尾；用于普通附件链接。留空使用上传请求地址。反向代理部署建议显式配置，避免生成内部地址。HTTPS 页面使用 HTTPS API。

EDS 须能从应用容器连通。保留 TLS 证书及主机名验证；私有 CA 应导入 JVM truststore，通过 JAVA_TOOL_OPTIONS 配置，不关闭证书校验。

## 历史文件及权限

- 新视频、PDF、图片和普通附件使用当前存储。
- 培训对象记录 oss: 或 eds:，读取和删除根据记录的来源路由。
- 历史无前缀 PDF/图片仍读阿里云，历史本地视频仍读原目录。
- 切换不自动搬迁文件；仍需访问阿里云历史文件时保留阿里云配置。
- 培训材料经原有后端鉴权接口读取，手机无需直连 EDS 内网。
- 普通附件通过 /api/common/files/{provider}/{uuid} 公开随机链接读取，沿用原有公开附件语义，不能读取培训对象。不应上传敏感资料至普通附件入口。
- 上传对象均设私有；SVG、HTML 等普通附件强制下载，不内联执行。

## SDK 和构建

backend/lib/eds/repository 为项目内 Maven 仓库，Dockerfile 已复制。自定义 Maven 镜像应排除 eds-vendor。首次构建仍需下载标准依赖，不是完全离线构建。

OSGi 集合包仅归档，不打入应用。原始二进制校验见 SHA256SUMS。凭证和含凭证示例不入库。

## 验证记录（2026-11-11）

后端测试及打包通过，覆盖 SDK 上传/读取/删除本地 HTTP 测试服务、历史存储路由、配置错误、资源释放和普通附件目录隔离。

真实 EDS HTTPS 探测在握手阶段被断开，尚未验证远端凭证、桶权限及真实上传。需在部署网络继续联调；未关闭 TLS 校验，未修改远端桶或业务数据。
