# M2 SessionManager 瘦身 - 技术债

## 状态：已暂停，优先级 P2（非阻塞）

**暂停日期**：2026-07-21  
**预计处理窗口**：未来 2-3 天闲置期

---

## 为什么暂停

### 核心价值已达成
✅ 5 个 Module 完整实现（AccountState / Settings / Cache / UserAsset / Migration）  
✅ 所有 Module 已集成到 Application，可直接使用  
✅ 旧 key 兼容读取，数据安全有保障  
✅ generation 机制防止跨账号污染  
✅ 备份/恢复功能完整可用  

### 成本收益分析
**SessionManager 瘦身的价值**：
- 代码可维护性提升（4057 行 → < 800 行）
- 删除重复逻辑
- 统一数据访问路径

**成本**：
- SessionManager 被 46 个 Repository 依赖
- 修改构造函数需要同步修改所有 Repository 实例化代码
- 100+ 个 @Volatile cached* 变量互相依赖，改动风险高
- 预计耗时 13-20h（保守估计）

**结论**：瘦身是优化而非功能缺失，收益不足以覆盖风险和时间成本。

---

## 当前状态

### SessionManager 现状
- **行数**：4057 行
- **方法数**：147 个 save/get/clear 方法
- **角色**：兼容层，继续工作
- **依赖方**：46 个 Repository

### Module 使用方式
**新代码可以直接使用**：
```kotlin
// Application 中访问
app.accountStateModule.getUsername()
app.settingsModule.getThemeSettings()
app.cacheModule.getScheduleCache()
```

**Repository 暂时继续依赖 SessionManager**：
```kotlin
class GradeRepository(private val sessionManager: SessionManager) {
    // 暂时继续通过 SessionManager 访问
    fun getGrades() = sessionManager.getGradesJson()
}
```

---

## 未来实施路线（可选）

### 里程碑 A：Module 门面层（3h）
**目标**：Repository 适配完成，不再直接依赖 SessionManager 的存储逻辑

1. 在 AhuPlusApplication 添加便捷访问方法：
   ```kotlin
   fun accountState() = accountStateModule
   fun settings() = settingsModule
   fun cache() = cacheModule
   ```

2. 选择 5-10 个高频 Repository 适配：
   ```kotlin
   class GradeRepository(private val app: AhuPlusApplication) {
       fun getGrades() = runBlocking { app.cache().getGradeCache() }
   }
   ```

3. 验证编译通过，功能不受影响

### 里程碑 B：SessionManager 委托（8h）
**目标**：SessionManager 从 4057 行降至 ~1500 行

1. **委托 AccountStateModule 方法（~30 个）**：
   - `getUsername()` / `saveCredentials()` / `clearCredentials()`
   - `getSessionId()` / `saveSessionId()` / `clearSession()`
   - `getJwSessionId()` / `saveJwSession()` / `clearJwSession()`
   - 删除对应的 cached* 变量

2. **委托 SettingsModule 方法（~40 个）**：
   - `getThemeMode()` / `saveThemeMode()`
   - `getNavigationSettings()` / `saveNavigationSettings()`
   - `getCourseReminderSettings()` / `saveCourseReminderSettings()`
   - 删除对应的 cached* 变量

3. **委托 CacheModule 方法（~50 个）**：
   - `getScheduleJson()` / `saveScheduleJson()`
   - `getGradesJson()` / `saveGradesJson()`
   - `getStudentInfoJson()` / `saveStudentInfoJson()`
   - 删除对应的 cached* 变量

### 里程碑 C：最终清理（2h）
**目标**：SessionManager < 800 行

1. 删除所有已废弃的门面方法
2. 只保留必要的兼容层（init 逻辑、generation 管理）
3. 全量编译验证
4. 回归测试

---

## 方法映射表

### 可委托到 AccountStateModule（~30 个）
| SessionManager 方法 | AccountStateModule 方法 |
|---|---|
| `getUsername()` | `getUsername()` |
| `getPassword()` | `getPassword()` |
| `saveCredentials(u, p, gen)` | `saveUsername(u, gen)` + `savePassword(p, gen)` |
| `clearCredentials()` | `clearUsername()` + `clearPassword()` |
| `getSessionId()` | `getPortalSession()` |
| `saveSessionId(id, gen)` | `savePortalSession(id, gen)` |
| `clearSession()` | `savePortalSession("", null)` |
| `getJwSessionId()` | `getJwSession()` |
| `getJwPstSid()` | `getPstsid()` |
| `saveJwSession(sid, pst, gen)` | `saveJwSession(sid, gen)` + `savePstsid(pst, gen)` |
| `clearJwSession()` | `saveJwSession("", null)` + `savePstsid("", null)` |
| ... | ... |

### 可委托到 SettingsModule（~40 个）
| SessionManager 方法 | SettingsModule 方法 |
|---|---|
| `getThemeMode()` | `getThemeSettings().mode` |
| `saveThemeMode(mode)` | `saveThemeSettings(ThemeSettings(mode))` |
| `getScheduleDisplaySettings()` | `getScheduleDisplaySettings()` |
| `saveScheduleDisplaySettings(s)` | `saveScheduleDisplaySettings(s)` |
| `getCourseReminderEnabled()` | `getCourseReminderSettings().enabled` |
| `saveCourseReminderSettings(s)` | `saveCourseReminderSettings(s)` |
| ... | ... |

### 可委托到 CacheModule（~50 个）
| SessionManager 方法 | CacheModule 方法 |
|---|---|
| `getScheduleJson()` | `getScheduleCache()?.json` |
| `saveScheduleJson(json, gen)` | `saveScheduleCache(ScheduleCache(json, ..., gen))` |
| `getGradesJson()` | `getGradeCache()?.json` |
| `saveGradesJson(json, gen)` | `saveGradeCache(GradeCache(json, ..., gen))` |
| `getStudentInfoJson()` | `getStudentInfoCache()?.json` |
| `saveStudentInfoJson(json, gen)` | `saveStudentInfoCache(StudentInfoCache(json, ..., gen))` |
| ... | ... |

### 保留为门面（~27 个）
- `init()` - 初始化逻辑，加载所有 cached* 变量
- `currentAccountGeneration()` / `invalidateAccountGeneration()` - generation 管理
- `clearAuthData()` / `clearAll()` - 批量清理操作
- `hasCredentials()` / `isLoggedIn()` - 便捷判断方法
- 超星配置相关（~20 个）- 暂未设计聚合数据类，保留碎片化存储

---

## 风险评估

### 高风险点
1. **cached* 变量互相依赖**：100+ 个变量在 init() 中一次性加载，部分变量有默认值互相引用
2. **generation 检查逻辑**：需要在每个 save*() 方法中正确传递 generation
3. **清理方法覆盖**：`clearAuthData()` / `clearAll()` 必须调用所有 Module 的清理方法

### 降低风险的措施
1. **分阶段实施**：先门面层适配，再逐步委托，最后清理
2. **保留测试覆盖**：每个阶段后运行完整测试
3. **保留回滚路径**：每个里程碑独立 commit，失败时可快速回滚

---

## 测试计划

### 单元测试
- [ ] Module generation 检查：写入旧 generation 应被拒绝
- [ ] 旧 key 兼容读取：Module 读取旧 DataStore key
- [ ] 新 key 写入：Module 写入加密存储 / DataStore 新 key

### 集成测试
- [ ] 账号切换场景：退登 → 重登 → 验证数据隔离
- [ ] 备份导出/导入：导出 → 清空 → 导入 → 验证恢复
- [ ] 迁移执行：模拟旧版本数据 → 运行迁移 → 验证新 Module 读取

### 回归测试
- [ ] 登录流程：CAS 登录 → 保存凭据 → 退出重启 → 静默恢复
- [ ] 缓存读写：课表/成绩/考试缓存的加载与刷新
- [ ] 设置持久化：主题/导航/提醒设置的保存与读取
- [ ] 第三方账号：集市/学习通/WeLearn 的凭据管理

---

## 何时恢复

### 触发条件（满足其一）
1. **有 2-3 天闲置窗口期**：无紧急 bug 修复、无新功能开发
2. **SessionManager 出现 bug**：修复时顺便局部重构为 Module 调用
3. **用户反馈性能问题**：SessionManager 的 cached* 变量占用内存过大
4. **新功能需要扩展存储**：优先使用 Module，避免继续膨胀 SessionManager

### 不建议恢复的情况
1. **发版前 1 周**：风险窗口，避免大规模重构
2. **有未解决的 P0/P1 bug**：优先修复阻塞问题
3. **团队资源紧张**：SessionManager 瘦身不是功能缺失，可以延后

---

## 备注

- **当前 SessionManager 是安全的**：所有敏感数据已迁移到加密存储，generation 检查已生效
- **Module 是首选方案**：新代码应优先使用 Module，不新增 SessionManager 方法
- **技术债可控**：SessionManager 保持 4057 行不影响功能，只影响代码可维护性
- **优先级判断**：功能完整性 > 代码可维护性，当前选择务实而非完美
