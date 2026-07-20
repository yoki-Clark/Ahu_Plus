# ADR-0004：后台作业使用前台执行与 WorkManager Adapter

- 状态：Accepted

## 背景

学习通、WeLearn、Widget 和提醒分别实现运行、取消、恢复和系统限制，生命周期语义不一致。

## 决策

后台作业统一表达排队、运行、成功、失败、取消、中断和恢复。用户主动长任务使用前台执行 Adapter，可延迟任务使用 WorkManager Adapter；AlarmManager 只负责用户明确创建的精确提醒。

## 影响

M1 先收口生命周期，不同时改变学习协议策略。通知停止、Repository 停止和用户取消必须落到同一终态。

## 当前实现

- `data/job/` 使用 DataStore schema v1 保存每个平台最近 20 条、最多 30 天的安全作业记录。
- 学习通和 WeLearn Service 只接收 `jobId`，参数从作业记录读取；同一平台只允许一个活动作业。
- 进程死亡和 Android 15 dataSync 超时落为可恢复的 `INTERRUPTED`，不自动重放远端操作。
- Widget 延迟刷新由 WorkManager 的午夜和课程节点一次性任务承载；AlarmManager 仅用于用户提醒。
