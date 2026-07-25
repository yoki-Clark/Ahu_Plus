# Release 工具

发布状态的唯一来源是 `release/release-state.json`。根目录 stable/beta 更新清单和官网 stable 清单均是派生文件。

## 本地配置

Release 构建需要以下四项配置，可放在环境变量、Gradle 属性或未跟踪的 `local.properties`：

```properties
AHU_RELEASE_STORE_FILE=/absolute/path/to/ahu-plus-production.p12
AHU_RELEASE_STORE_PASSWORD=...
AHU_RELEASE_KEY_ALIAS=ahuplus-release
AHU_RELEASE_KEY_PASSWORD=...
```

Windows 首次迁移历史签名时可运行 `migrate_legacy_signing.ps1`。脚本验证公开证书指纹、生成强随机口令、保留原 keystore，并在两个仓库外位置创建专用 keystore 备份。目标文件已存在时脚本会拒绝覆盖。

## 命令

```powershell
python tools/release/release.py check
python tools/release/release.py verify-apk --channel stable --target published --apk path/to/app.apk
python tools/release/release.py dry-run --channel beta
python tools/release/release.py promote --channel beta --apk path/to/app.apk --published-at 2026-07-20T20:00:00+08:00
python tools/release/release.py promote --channel beta --apk path/to/app.apk --published-at 2026-07-20T20:00:00+08:00 --apply
```

- `check` 验证状态 schema 和三份公开清单没有漂移。
- `verify-apk` 使用 Android SDK 校验签名、包名、版本、SDK、ABI、对齐、大小与摘要。
- `dry-run` 构建 arm64 与 universal APK，只写 `build/release-dry-run/`。
- `promote` 默认只生成 `build/release-promote/` 预览；`--apply` 才更新本地状态与清单。

本工具不包含上传、Git 提交、推送或远端 Release API。人工流程至少保留到一次 dry-run 和一次真实内测发布均成功。

完整历史敏感信息审计使用 `python tools/ci/check_secrets.py --history`。该命令直接读取 Git blob，只报告路径和规则名，不写出或回显命中内容。

CI 首次进入 GitHub `main` 后，由仓库管理员登录 `gh` 并应用分支保护：

```powershell
gh api --method PUT repos/yoki-Clark/Ahu_Plus/branches/main/protection --input .github/branch-protection.json
```

该配置要求 `Android verification` 与 `Sensitive information` 两项检查成功，禁止强推和删除 `main`。JSON 配置已进入版本控制，远端应用操作需要有效的 GitHub 管理员凭据。
