# 密钥管理

- `.env` 只用于本地运行，已加入 `.gitignore`。
- `.env.example` 只放变量名和占位符，不放真实密钥。
- 生产环境建议使用 Docker Secret、云厂商密钥管理服务或 CI/CD Secret 注入变量。
- JWT access secret 和 refresh secret 必须使用不同的随机值，且长度不少于 32 个字符。
- 阿里云 AccessKey 一旦出现在聊天记录、截图、日志或仓库中，应立即禁用并重新生成。
- OSS 上传服务只返回对象 URL，不在业务层暴露 AccessKey。
