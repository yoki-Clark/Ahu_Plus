## 变更摘要

<!-- 说明改了什么、为什么改，以及用户可观察到的行为。 -->

## 变更类型

- [ ] feat - 新功能
- [ ] fix - Bug 修复
- [ ] refactor - 不改变外部行为的重构
- [ ] perf - 性能优化
- [ ] style - UI 或样式调整
- [ ] docs - 文档
- [ ] chore - 构建、依赖或维护

## 影响范围

<!-- 列出涉及的认证系统、缓存、页面、后台任务、Widget 或第三方服务。 -->

## 关联 Issue

<!-- 例如 Closes #123；没有则写“无”。 -->

## 验证

- 设备或模拟器：
- Android 版本：
- 验证场景：
- 未验证项及原因：

## 截图或录屏

<!-- UI 变更必填；注意遮盖学号、Token、账单、成绩等个人数据。 -->

## 检查清单

- [ ] `./gradlew :app:testDebugUnitTest` 通过
- [ ] `./gradlew assembleDebug` 通过
- [ ] 新增网络调用在 IO 调度器执行，并正确分类会话过期、非预期 HTML 和 HTTP 错误
- [ ] 敏感凭据使用 `EncryptedCredentialStore`，没有新增 DataStore 明文凭据
- [ ] 没有提交账号、密码、JWT、Cookie、学生数据、`.env`、`local.properties` 或签名文件
- [ ] 账户切换、退出登录、清缓存和进程重建行为已核对
- [ ] 涉及提醒、Widget 或前台服务时，已验证取消、重启和应用升级
- [ ] 用户可见行为、入口或配置变化已同步 README / CODEMAP / 对应 docs
- [ ] UI 变更已检查浅色、深色、窄屏和返回键行为
