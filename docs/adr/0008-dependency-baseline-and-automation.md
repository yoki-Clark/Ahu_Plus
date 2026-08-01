# ADR-0008：依赖基线由 compileSdk 约束，升级由 Dependabot 驱动

- 状态：Accepted
- 关联：[ADR-0007](./0007-ci-unsigned-release-verification.md)、[基础欠账-4-依赖治理](../plan/基础欠账-4-依赖治理.md)

## 背景

Jetpack 核心库长期停留在 2023 年前后的版本（core-ktx 1.10.1、lifecycle 2.6.1、
activity 1.8.0、datastore 1.1.1、work 2.10.1），敏感组件 `security-crypto` 停在
`1.1.0-alpha06`。升级全靠人工发现，没有自动化通道；Aliyun 镜像同步延迟又让"升到最新"
存在构建失败风险。同时 `gradle-wrapper.properties` 缺 `distributionSha256Sum`，
wrapper 发行版下载没有完整性校验。

## 决策

1. **依赖版本上界由 compileSdk 决定，不追最新。** 当前 compileSdk 36，因此 core-ktx 取
   1.18.0、lifecycle 取 2.10.0（1.19.0 / 2.11.0 要求 compileSdk 37，暂缓）。升级 compileSdk
   是独立决策，不夹带在依赖升级里。
2. **分批升级，每批独立验证。** 按依赖关系分组（Core+Lifecycle / Activity+Compose BOM /
   DataStore+Work+security-crypto），每批需通过
   `testDebugUnitTest + assembleDebug + assembleRelease`。CI 的无签名 Release 构建
   （ADR-0007）是这条的前置保护。
3. **升级前逐版本核对 Aliyun 镜像可用性。** 国内构建以 Aliyun 镜像为主源；镜像未同步的版本
   不纳入升级，避免国内用户构建失败。plugin marker 缺失时才回退
   `plugins.gradle.org/m2/`（见 `settings.gradle.kts`）。
4. **Dependabot 只负责 minor/patch，major 一律人工。** 每周一开 PR，androidx 与 kotlin 分组，
   `open-pull-requests-limit: 5`，`version-update:semver-major` 全部 ignore。Dependabot PR
   不得在 CI 未通过时合并。
5. **wrapper 发行版必须校验 SHA-256。** `distributionSha256Sum` +
   `validateDistributionUrl=true`，升级 Gradle 时同步更新校验和。
6. **`security-crypto` 升到 1.1.0 并接受其已废弃状态。** 该版本是终版（Google 在
   `1.1.0-alpha07` 废弃整个库）。选择停更的 stable 而非官方替代（DataStore 加密，仍为
   alpha），因为凭据存储不接受 alpha 依赖。格式决定参数由
   `EncryptedCredentialStoreCompatTest` 锁定；迁移触发条件记录在 `BUG_REVIEW.md`。

## 影响

- 版本基线可解释：任何"为什么不升到最新"都能回答到 compileSdk 约束或镜像可用性，不再靠记忆。
- 升级不再依赖人工巡检；但 Dependabot 生效的前提是 `.github/dependabot.yml` 已在默认分支。
- `security-crypto` 不会再收到上游补丁，凭据存储的安全维护责任转移到本项目：需按
  `BUG_REVIEW.md` 的触发条件定期复核，而非等待 Dependabot 提示。
- Compose BOM 升级引入若干 deprecation 警告（`rememberTransformableState` 离心参数版、
  `Icons.Filled.ArrowBack` → AutoMirrored），不影响功能，随后续 UI 改动清理。
- 升级操作流程见 [docs/ops/dependency-upgrade-process.md](../ops/dependency-upgrade-process.md)。
