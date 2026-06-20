package com.yourname.ahu_plus.data.model

/**
 * ycard 费用项级联元数据 (POST /charge/feeitem/getThirdData, type=select)。
 *
 * 三级级联:楼栋 (level=0) → 楼层 (level=1) → 房间 (level=2)
 *
 * 每个 [FeeItemOption] 的 value 字段直接是 "code&name" 格式,可原样喂给
 * [com.yourname.ahu_plus.data.repository.YcardRepository.getElectricityBalance]。
 */
data class FeeItemOption(
    /** 显示名,如 "榴园二号楼空调" / "五层" / "2514" */
    val name: String,
    /** "code&name" 格式,如 "57&榴园二号楼空调" / "228&五层" / "2514&2514" */
    val value: String
)

/** map.total 中描述级联层级的条目,目前 408/428 都返回 building→floor→room 三层。 */
data class FeeItemTotalStep(
    val code: String,
    val level: Int,
    val name: String
)

/** select 接口统一响应包 (feeitemid ∈ {408, 428},type=select,level ∈ {0,1,2}) */
data class FeeItemSelectResponse(
    val msg: String = "",
    val code: Int = 0,
    val map: FeeItemSelectMap? = null
)

data class FeeItemSelectMap(
    val total: List<FeeItemTotalStep>? = null,
    val data: List<FeeItemOption>? = null
)

/**
 * 自动填充提示 (来自学生一张表的住宿信息)。
 *
 * 解析示例: "楼栋=榴园, 房间=2514" →
 *   buildingName="榴园", buildingDigit=2, floorDigit=5, roomNumber="2514"
 */
data class DormHint(
    /** 中文园区名,如 "榴园" / "兰园" / "枫园" */
    val buildingName: String,
    /** 宿舍房间号全称,如 "2514" / "301" */
    val roomNumber: String,
    /** 楼栋序号 (房间号首位,如 2514 的首位=2 → 二号楼) */
    val buildingDigit: Int,
    /** 楼层序号 (房间号次位,如 2514 的次位=5 → 五层; 301 的次位=0) */
    val floorDigit: Int
)
