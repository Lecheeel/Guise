# 发布签名 APK

`Signed release` 工作流由 `v*` 标签推送触发，也可从 `main` 手动运行并指定已有版本标签。
标签去掉 `v` 后必须与 `app/version.properties` 中的 `version.name` 一致；发布前递增 `version.code`。

仓库 Secrets：

- `ANDROID_KEYSTORE_BASE64`：发布 PKCS12 keystore 的 Base64 编码。
- `ANDROID_STORE_PASSWORD`、`ANDROID_KEY_ALIAS`、`ANDROID_KEY_PASSWORD`：签名凭据。

首次发布使用 RSA 4096 位证书，有效期 10000 天。之后必须继续使用同一密钥，不能为每次发布重新生成。
`.github/release-signing-certificate.sha256` 只包含公钥证书指纹；每次发布都会核对实际 APK 签名。
私钥和密码不应提交到 Git。当前本地备份保存在维护者 Windows 账户的 `.codex/private/guise-release-signing/` 下，密码由该账户的 Windows DPAPI 加密；换机或重装系统前需要另行导出可恢复的备份。

工作流执行 R8 优化、代码混淆和资源压缩，按 x86、arm64-v8a、armeabi-v7a 分别构建 APK，只保留英文和简体中文资源。
未带 `releaseAbis` 参数的普通 CI 仍生成一个通用 unsigned release APK；正式发布强制要求完整签名配置，缺失时失败。

发布前自动验证：APK v2/v3 签名、证书指纹、独立 ABI 和 ELF 架构、16 KB 对齐、版本号、非 debug 标志、API 102 模块入口及 R8 mapping。
所有检查通过后，先创建草稿、上传三个 APK、公钥证书、SHA-256 校验文件和发布清单，再发布 Release。
已有公开 Release 不会被覆盖。带 `-alpha` / `-beta` 等后缀的版本会标记为预发布。

R8 mapping 在 Actions 中单独保留 90 天，可下载用于崩溃堆栈还原；它不作为 Release 附件发布。
这些是编译与静态检查，不包括真机或模拟器运行测试。
