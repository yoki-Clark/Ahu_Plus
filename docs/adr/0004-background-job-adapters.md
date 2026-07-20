# ADR-0004：后台作业使用前台执行与 WorkManager Adapter

- 状态：Accepted

## 背景

学习通、WeLearn、Widget 和提醒分别实现运行、取消、恢复和系统限制，生命周期语义不一致。

## 决策

后台作业统一表达排队、运行、成功、失败、取消、中断和恢复。用户主动长任务使用前台执行 Adapter，可延迟任务使用 WorkManager Adapter；AlarmManager 只负责用户明确创建的精确提醒。

## 影响

M1 先收口生命周期，不同时改变学习协议策略。通知停止、Repository 停止和用户取消必须落到同一终态。
