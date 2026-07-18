# 排考预测数据采集工具

本目录曾用于生成 `ExamDataRepository` 消费的公开排考预测 JSON。排考预测入口已停用，Android 当前不会进入对应页面或触发这条远程数据链；这里仅保留历史工具和格式说明，后续逻辑将改为设备端直连 jwapp 后再重新设计。

## 当前代码边界

`scan_exams.py` 是面向 2025-2026-2 学期期末阶段的日期绑定脚本，当前常量为：

```text
DEFAULT_START_DATE = 2026-07-06
DEFAULT_END_DATE   = 2026-07-15
SEMESTER_LABEL     = 2025-2026-2
OUTPUT_CSV         = output_7.6-7.15.csv
OUTPUT_MD          = output_7.6-7.15.md
```

进入新学期时必须先更新这些常量和输出文件名，不能直接把默认结果当成当前学期数据。

## 历史数据流（当前停用）

```text
jwapp idToken
  -> scan_exams.py 按日期、校区、楼栋扫描 Exam 占用
  -> cache_daily.jsonl 断点续扫
  -> cache_full_scan.json 原始缓存
  -> exam_predictions.json 标准化公开产物
  -> Gitee
  -> Android ExamDataRepository
  -> 按 course_code 与用户课表精确匹配
```

停用前的 Android 链路不读取本目录缓存和报表，只拉取下列远程产物；当前客户端不会发起该请求：

```text
https://gitee.com/yao-enqi/ahu-plus-update/raw/master/exam_predictions/exam_predictions.json
```

## 运行

要求 Python 3 和 `requests`。令牌只允许放在本目录的 `.jwt_token`，或按具体脚本支持的环境变量传入；不得写入 README、命令历史示例或源码。

```powershell
python tools/exam_prediction/scan_exams.py 2026-07-06 2026-07-15
python tools/exam_prediction/scan_exams.py --reuse
```

正常扫描会逐栋拉取数据并每天写入 `cache_daily.jsonl`。`--reuse` 仅在 `cache_full_scan.json` 存在时重建标准化 JSON 和报表，不访问 API。

## 文件分类

| 文件 | 类型 | 是否是当前 App 行为依据 |
|---|---|---|
| `scan_exams.py` | 当前采集实现 | 是，工具行为依据 |
| `exam_predictions.json` | 历史标准化数据 | 否，当前客户端不消费 |
| `cache_daily.jsonl` | 断点缓存 | 否 |
| `cache_full_scan.json` | 原始扫描缓存 | 否 |
| `output_7.6-7.15.csv/.md` | 特定日期的人读报表 | 否 |
| `scan_exams_probe.py` | 演示/探针 | 否 |
| `jwapp_api_exploration.py` | API 探索脚本 | 否 |
| `.jwt_token` | 本地敏感凭据 | 绝不发布 |

历史输出和缓存是生成快照。它们不应被其他文档引用为当前考试安排，也不应提交真实教师、学生或账号信息。

## 标准化 Schema

遗留客户端模型位于 `data/model/exam/ExamPredictionsDto.kt`。顶层字段包括：

- `version`、`generated_at`、`semester`、`date_range`。
- `campuses`、`source`、`count`、`summary_by_date`。
- `exams`。

单条 `exams` 记录包括日期、起止时间、课程号、教学班号、完整课程标识、学院、教室、校区、楼栋、教师描述和活动 ID。遗留逻辑使用 `course_code` 匹配个人课表，并按日期和时间排序。

## 历史发布检查

1. 确认扫描学期、日期范围和校区正确。
2. 检查 `generated_at`、`semester`、`date_range` 和 `count`。
3. 抽样核对多课程合并占用是否正确展开。
4. 扫描输出中不得包含 Token、Cookie、学生标识或无关个人数据。
5. 仅在功能重新设计并恢复入口后，才评估是否仍需发布 `exam_predictions.json`。
6. 不得把旧数据链重新接回当前 App；恢复功能前应先完成设备端 jwapp 方案和隐私边界评审。

排考预测只是教室占用数据推导结果，不能替代教务系统的正式考试安排。
