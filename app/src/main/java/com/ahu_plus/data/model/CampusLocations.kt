package com.ahu_plus.data.model

/**
 * 校园预设签到地点(2026-06-25 填入磬苑实测楼坐标)。
 *
 * ⚠️ **坐标系 = GCJ-02(高德/火星坐标)**。超星签到内嵌高德 SDK 上报 GCJ-02,
 * 故此处坐标须与高德地图读数一致;App 内 GPS 定位(WGS-84)已由
 * [com.ahu_plus.util.CoordTransform] 转换为 GCJ-02 后再用,两者对齐。
 *
 * 数据来源:BIGEMAP / OpenStreetMap 公开地理信息(原始 WGS-84),已用
 * CoordTransform.wgs84ToGcj02 离线转换为 GCJ-02 后填入。
 *
 * ⚠️ 精度仅供参考:公开地图楼栋点位与超星签到判定中心可能仍有数十米偏差。
 * 最可靠仍是到现场用「使用当前位置」或存为「自定义位置」。
 * 龙河校区公开数据缺教学楼精确坐标,暂仅留校区概略中心兜底。
 *
 * 维护:直接增删条目即可,UI 按 [campus] 二级分组(校区 → 教学楼)自动读取。
 */
data class CampusLocation(
    val campus: String,   // 校区名(磬苑校区 / 龙河校区),用于二级分组
    val name: String,     // 楼名 / 地点名
    val latitude: Double,
    val longitude: Double,
)

/** 预设校区顺序(UI 一级选项顺序) */
val CAMPUS_NAMES: List<String> = listOf("磬苑校区", "龙河校区")

val CAMPUS_LOCATIONS: List<CampusLocation> = listOf(
    // 磬苑校区(GCJ-02,由 WGS-84 转换):
    CampusLocation("磬苑校区", "博学北楼", 31.768529, 117.184908),
    CampusLocation("磬苑校区", "博学南楼", 31.766548, 117.185287),
    CampusLocation("磬苑校区", "笃行南楼", 31.765402, 117.183560),
    CampusLocation("磬苑校区", "笃行北楼", 31.767702, 117.183301),
    CampusLocation("磬苑校区", "行知楼", 31.767935, 117.186755),
    CampusLocation("磬苑校区", "理工A楼", 31.768935, 117.186795),
    CampusLocation("磬苑校区", "理工B楼", 31.769284, 117.187583),
    // 龙河校区:公开数据无教学楼精确坐标,暂留概略中心(GCJ-02 由 31.84719,117.25013 转换)
    CampusLocation("龙河校区", "校区中心(概略)", 31.845177, 117.255613),
)

/** 按校区分组,供 UI 二级选择 */
fun campusLocationsByCampus(): Map<String, List<CampusLocation>> =
    CAMPUS_LOCATIONS.groupBy { it.campus }
