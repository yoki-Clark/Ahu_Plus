#!/usr/bin/env python3
r"""
jwapp.ahu.edu.cn 数据接口探索报告 (2026-06-24)
================================================

本脚本既是文档也是可执行探索工具,记录 jwapp 端口所有已知 API 端点、
字段结构、认证流程和自动化可行性。

# ══════════════════════════════════════════════════════════
# 一、认证: JWT idToken 获取
# ══════════════════════════════════════════════════════════

jwapp 使用自签发的 JWT (RS512) 做认证, 所有 /eams-micro-server/* 端点
必须带 `Authorization: <idToken>` header。JWT 特性:
  - iss: jwapp.ahu.edu.cn
  - iat/exp: 签发/过期时间 (30天有效期)
  - req: wxea4e57affeb52978 (微信小程序 AppID)
  - sub: 学号 (如 G62314006)
  - ATTR_*: 用户属性 (userNo, accountId, identityTypeCode=Student, userName, ...)

## 现有获取方式
通过「智慧安大」微信小程序打开「教室占用」功能 → WebView 加载 SPA →
小程序原生层处理 CAS SSO → URL 回带 ?idToken=<jwt> → 手动复制。

## 已排除的路径 (均不可行)
  ❌ jwapp.ahu.edu.cn/cas?redirect_uri=...          → 404 (所有 UA/方法)
  ❌ jwapp.ahu.edu.cn/eams-micro-server/cas?...     → 401 (网关拦截)
  ❌ one.ahu.edu.cn CAS ST → jwapp 后端消费          → jwapp 无 CAS validate 端点
  ❌ eams-exam.paas.newcapec.cn/jwt/cas/            → K8s default backend (已下线)
  ❌ jwapp 其他子域名/端口/路径前缀                    → 均不存在或 404
  ❌ 用户名密码直登 /eams-micro-server/api/v1/login   → 401 (网关拦截所有未认证请求)

## 结论
JWT 签发链路深度耦合微信小程序原生层,无法从外部 HTTP 客户端自动获取。
必须借助微信环境 (小程序或 WebView 拦截) 才能拿到 idToken。

# ══════════════════════════════════════════════════════════
# 二、API 端点清单
# ══════════════════════════════════════════════════════════

Base: https://jwapp.ahu.edu.cn/eams-micro-server/api/v1

┌──────────────────────────────────┬────────┬──────────────────────────────┐
│ 端点                              │ 方法   │ 说明                         │
├──────────────────────────────────┼────────┼──────────────────────────────┤
│ /room/place/campus               │ GET    │ 校区列表 (4个)                │
│ /room/place/building             │ GET    │ 教学楼列表 (按 campusAssoc)   │
│ /room/place/roomTypes            │ GET    │ 教室类型 (14种)               │
│ /room/place/floors               │ GET    │ 楼层列表 (按 buildingId)      │
│ /room/system/course-units        │ GET    │ 课程时间单元 (12节)           │
│ /room/place/rooms                │ POST   │ 🔑 教室+占用信息 (主数据源)   │
│ /room/place/room-occupancy       │ POST   │ 占用摘要                       │
│ /room/place/room-occupancy-info  │ GET    │ 占用详情 (by roomIds)          │
│ /department                      │ GET    │ 院系列表                       │
│ /actuator/health                 │ GET    │ 健康检查 (200 空body)          │
└──────────────────────────────────┴────────┴──────────────────────────────┘

# ══════════════════════════════════════════════════════════
# 三、核心数据结构
# ══════════════════════════════════════════════════════════

## 3.1 教室 (Room) 基础字段
  id, nameZh, nameEn, code, buildingId, campusNameZh,
  floor, virtual, seatsForLesson, enabled, experiment

## 3.2 占用记录 (RoomOccupationInfoVm) 完整字段
  id              : int      记录 ID
  activityId      : int      活动 ID
  roomName        : str      教室名 (中文)
  roomNameEn      : str|null 教室名 (英文)
  date            : str      日期 "2026-09-07"
  startTime       : int      开始时间 (800 = 08:00)
  endTime         : int      结束时间 (935 = 09:35)
  startTimeString : str      "08:00"
  endTimeString   : str      "09:35"
  activityType    : str      "Lesson" | "Exam" | ...
  activityName    : str      "课程：<课程名>(<full_code>, <学院>)"
  activityNameEn  : str      英文版活动描述
  week            : null     教学周 (当前始终 null)
  unitStart       : null     开始节次 (当前始终 null)
  unitEnd         : null     结束节次 (当前始终 null)
  weekDay         : null     星期几 (当前始终 null)
  teacherName     : str      教师名 (中文)
  teacherNameEn   : str      教师名 (英文/拼音)

## 3.3 activityType 已知值
  - "Lesson" : 正常授课 (包含 course_name + full_code + college + teacher)
  - "Exam"   : 期末考试 (同上格式,teacherName 格式为"主监考：X；副监考：Y")
  - 其他可能值待探索 (Meeting, Activity 等)

## 3.4 activityName 解析正则
  COURSE_PATTERN = r"课程：([^,()]+?)\((\d{9}-[A-Z0-9]+\.\d{3}),?\s*(.*?)\)"
  - group(1): 课程名
  - group(2): full_code (如 202620271-GG61116.043)
  - group(3): 开课学院

  full_code 进一步拆分:
  CODE_PATTERN = r"(\d{9})-([A-Z0-9]+)\.(\d{3})"
  - group(1): semester (如 202620271)
  - group(2): course_code (如 GG61116)
  - group(3): section (如 043)

# ══════════════════════════════════════════════════════════
# 四、数据规模实测
# ══════════════════════════════════════════════════════════

  - 教室总数: 1,521 间 (分页 200/页,约 8 页)
  - 校区: 磬苑(1) / 龙河(2) / 金寨路(22) / 纯线上(6)
  - 教学楼: 磬苑 12 栋 + 龙河 N 栋 + 金寨路 N 栋
  - 教室类型: 普通教室、实验室、制图专用、户外场地等 14 种

  ┌────────────┬──────────┬──────────────────────────────────┐
  │ 日期        │ 类型     │ 规模                              │
  ├────────────┼──────────┼──────────────────────────────────┤
  │ 2026-07-10 │ Exam     │ 2,246 条 (10天考试周), 1,200+ 门  │
  │ 2026-09-07 │ Lesson   │ 732 条/天 (周一), 717 门不同课程   │
  │ 2026-05/06 │ (历史)   │ 近乎为空 (数据已清理)              │
  └────────────┴──────────┴──────────────────────────────────┘

  注意: 单日 732 条是**全校所有教室**的课表。按 5 天/周 × 18 周 ≈
  65,000+ 条记录构成完整学期课表。

# ══════════════════════════════════════════════════════════
# 五、自动化可行性评估
# ══════════════════════════════════════════════════════════

## ✅ 数据提取: 完全可行
  只要持有有效 JWT,即可全量拉取任意日期的教室占用数据。
  scan_exams.py 已实现分页+解析+标准化的完整流程。

## ❌ JWT 自动获取: 当前不可行
  JWT 签发深度绑定微信小程序,无外部 HTTP 端点可替代。

## 🔧 可行方案
  A) 维持现状 (推荐短期):
     - 维护者每 30 天手动从微信复制 token 到 .jwt_token
     - 脚本定时扫描 → 推 Gitee → 客户端拉取
     - 已实现: 考试预测数据

  B) WebView 自动捕获 (推荐中期):
     - Android App 内嵌 WebView 加载 SPA
     - 拦截 URL 变化,自动提取 ?idToken= 参数
     - 写入 SessionManager,后台自动刷新
     - 优点: 用户无感,每次打开 App 自动续期
     - 风险: 微信 WebView 环境检测可能拒绝非微信浏览器

  C) 扩展课表数据 (新需求):
     - 用现有 token 扫描整个学期的 Lesson 数据
     - 按 course_code 构建全校课程→时间→教室映射
     - 推 Gitee,客户端按自己课表匹配展示
     - 数据量: ~65K 条/学期,JSON 约 8-15 MB

  D) 代理服务 (长期):
     - 部署一个云函数/服务器,维护长期 token
     - 客户端通过该服务中转请求
     - 风险: token 过期需人工干预

# ══════════════════════════════════════════════════════════
# 六、与现有课表系统的关系
# ══════════════════════════════════════════════════════════

  现有课表来源: jw.ahu.edu.cn (教务系统) → 按学生个人查询
  jwapp 课表来源: 教室占用系统 → 按教室/时间查询 (全校视角)

  两者互补:
  - jw 课表: 学生视角 (我有什么课)
  - jwapp 课表: 教室视角 (这间教室什么时间有人上课/考试)

  交叉价值:
  - 验证 jw 课表准确性 (同一天同一教室不应有两门不同课)
  - 空教室查询 (比现有 EmptyClassroom 更准,因为是真实占用数据)
  - 考试预测 (已实现: 教室占用 Exam → 匹配学生课表)
  - 课程冲突检测
"""

import json, re, sys, time
from collections import Counter, defaultdict
from datetime import datetime, timedelta
from pathlib import Path
from typing import Any

import requests
import urllib3

urllib3.disable_warnings()

SCRIPT_DIR = Path(__file__).resolve().parent
TOKEN_FILE = SCRIPT_DIR / ".jwt_token"

# ── 已知端点 ─────────────────────────────────────────
BASE = "https://jwapp.ahu.edu.cn/eams-micro-server/api/v1"

ENDPOINTS = {
    "campus":       ("GET",  "/room/place/campus"),
    "building":     ("GET",  "/room/place/building?campusAssoc={campus_id}"),
    "roomTypes":    ("GET",  "/room/place/roomTypes"),
    "floors":       ("GET",  "/room/place/floors?buildingId={building_id}"),
    "courseUnits":  ("GET",  "/room/system/course-units"),
    "rooms":        ("POST", "/room/place/rooms"),
    "occupancy":    ("POST", "/room/place/room-occupancy"),
    "occupancyInfo":("GET",  "/room/place/room-occupancy-info?roomIds={room_ids}&date={date}"),
    "department":   ("GET",  "/department"),
    "health":       ("GET",  "/actuator/health"),
}

# 解析 activityName 字段: "课程：<课程名>(<full_code>, <学院>)"
COURSE_PATTERN = re.compile(
    "课程：([^,()]+?)\\((\\d{9}-[A-Z0-9]+\\.\\d{3}),?\\s*(.*?)\\)"
)
CODE_PATTERN = re.compile("(\\d{9})-([A-Z0-9]+)\\.(\\d{3})")


class JwappClient:
    """jwapp API 客户端。"""

    def __init__(self, token: str | None = None):
        if token is None and TOKEN_FILE.exists():
            token = TOKEN_FILE.read_text().strip()
        if not token:
            raise ValueError("JWT token required (set .jwt_token or pass token=)")
        self.token = token
        self.headers = {
            "Authorization": token,
            "User-Agent": "Mozilla/5.0 Chrome/132.0.0.0",
        }

    def _req(self, method: str, path: str,
             json_data: dict | None = None,
             params: dict | None = None,
             timeout: int = 30) -> dict[str, Any]:
        url = f"{BASE}{path}"
        h = dict(self.headers)
        if json_data is not None:
            h["Content-Type"] = "application/json"
        r = requests.request(method, url, headers=h, json=json_data,
                             params=params, verify=False, timeout=timeout)
        if r.status_code != 200:
            raise RuntimeError(f"HTTP {r.status_code}: {r.text[:200]}")
        return r.json()

    def get_campuses(self) -> list:
        return self._req("GET", "/room/place/campus")["data"]

    def get_buildings(self, campus_id: int) -> list:
        return self._req("GET", f"/room/place/building?campusAssoc={campus_id}")["data"]

    def get_room_types(self) -> list:
        return self._req("GET", "/room/place/roomTypes")["data"]

    def get_course_units(self) -> list:
        return self._req("GET", "/room/system/course-units")["data"]

    def get_departments(self) -> list:
        return self._req("GET", "/department")["data"]

    def get_rooms(self, date: str, page: int = 1, page_size: int = 200,
                  building_ids: list | None = None,
                  campus_assoc: int | None = None,
                  room_type_ids: list | None = None,
                  floors: list | None = None) -> dict:
        """获取指定日期的教室及占用信息 (分页)。"""
        body = {
            "currentPage": page, "pageSize": page_size,
            "campusAssoc": campus_assoc,
            "buildingIds": building_ids or [],
            "roomTypeIds": room_type_ids or [],
            "floors": floors or [],
            "minSeat": "", "maxSeat": "",
            "date": date,
        }
        return self._req("POST", "/room/place/rooms", json_data=body)

    def scan_date(self, date: str,
                  activity_types: set | None = None) -> list[dict]:
        """扫描某天全部教室,返回指定 activityType 的占用记录 (已去重+解析)。"""
        if activity_types is None:
            activity_types = {"Lesson", "Exam"}

        records = []
        seen = set()
        for page in range(1, 60):
            data = self.get_rooms(date, page=page)
            rooms = data.get("data", {}).get("data") or []
            if not rooms:
                break
            for room in rooms:
                for occ in room.get("roomOccupationInfoVms") or []:
                    at = occ.get("activityType", "")
                    if at not in activity_types:
                        continue
                    m = COURSE_PATTERN.search(occ.get("activityName", ""))
                    if not m:
                        continue
                    course_name = m.group(1).strip()
                    full_code = m.group(2).strip()
                    college = m.group(3).strip().rstrip(",")
                    cm = CODE_PATTERN.match(full_code)
                    if not cm:
                        continue

                    key = (full_code, occ["date"], occ["startTimeString"],
                           room.get("code"))
                    if key in seen:
                        continue
                    seen.add(key)
                    records.append({
                        "date": occ["date"],
                        "start": occ["startTimeString"],
                        "end": occ["endTimeString"],
                        "course_name": course_name,
                        "course_code": cm.group(2),
                        "section": cm.group(3),
                        "full_code": full_code,
                        "semester": cm.group(1),
                        "college": college,
                        "room_name": room["nameZh"],
                        "room_code": room.get("code", ""),
                        "campus": room.get("campusNameZh", ""),
                        "building_id": room.get("buildingId"),
                        "teacher": (occ.get("teacherName") or "").strip(),
                        "activity_id": occ.get("activityId"),
                        "activity_type": at,
                    })

            pg = data.get("data", {}).get("_page_", {})
            if page >= pg.get("totalPages", 1):
                break
            time.sleep(0.2)
        return records


# ── CLI demo ──────────────────────────────────────────
def main():
    import argparse
    p = argparse.ArgumentParser(description="jwapp API explorer")
    p.add_argument("action", nargs="?", default="info",
                   choices=["info", "meta", "lessons", "exams", "scan"])
    p.add_argument("--date", default="2026-09-07")
    p.add_argument("--activity", default="Lesson")
    args = p.parse_args()

    if args.action == "info":
        print(__doc__)
        return

    client = JwappClient()

    if args.action == "meta":
        campuses = client.get_campuses()
        print(f"=== 校区 ({len(campuses)}) ===")
        for c in campuses:
            print(f"  {c['id']}: {c['nameZh']}")
            blds = client.get_buildings(c['id'])
            for b in blds:
                print(f"    {b['id']}: {b['nameZh']} ({b.get('code')})")

        types = client.get_room_types()
        print(f"\n=== 教室类型 ({len(types)}) ===")
        for t in types:
            print(f"  {t['id']}: {t['nameZh']}")

        units = client.get_course_units()
        print(f"\n=== 课程时间单元 ({len(units)}) ===")
        for u in units:
            print(f"  第{u['indexNo']}节: {u['startTime']:04d}-{u['endTime']:04d} ({u['dayPart']})")

        depts = client.get_departments()
        print(f"\n=== 院系 ({len(depts)}) ===")
        for d in depts[:10]:
            print(f"  {d['id']}: {d['nameZh']}")

    elif args.action in ("lessons", "exams", "scan"):
        at = {"lessons": "Lesson", "exams": "Exam", "scan": None}[args.action]
        activity_types = {at} if at else {"Lesson", "Exam"}
        print(f"扫描 {args.date} (activityType={'/'.join(sorted(activity_types))})...")
        records = client.scan_date(args.date, activity_types=activity_types)
        print(f"共 {len(records)} 条记录")

        if records:
            courses = {(r["course_code"], r["section"]) for r in records}
            print(f"唯一课程: {len(courses)}")
            campuses = Counter(r["campus"] for r in records)
            print(f"校区分部: {dict(campuses)}")

            # Sample
            for r in records[:3]:
                print(f"  {r['start']}-{r['end']} {r['course_name']} "
                      f"({r['course_code']}.{r['section']}) "
                      f"{r['room_name']} | {r['teacher'][:20]}")

    else:
        p.print_help()


if __name__ == "__main__":
    main()
