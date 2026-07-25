# ADR-0001：发布签名安全失败与单一状态源

- 状态：Accepted

## 背景

历史稳定版与 beta 使用同一 Android Debug 证书，applicationId 已被安装用户和本地数据目录依赖。版本与发布清单曾在多个文件中手工维护。

## 决策

保留历史证书指纹 `D290B9CF0653C1F80B2DF26EF39B3385C854F9D653793FB34CAE36AC9FB6D463`，将同一私钥迁入专用强密码 PKCS12。Release 缺少完整配置或指纹不匹配时必须在打包前失败。版本、候选和 stable/beta 已发布元数据统一由 `release/release-state.json` 表达。

## 影响

老用户可以继续覆盖升级；证书显示名仍为 Android Debug，但其私钥按生产密钥管理。新字节内容必须增加 versionCode，公开清单只能由发布工具生成。
