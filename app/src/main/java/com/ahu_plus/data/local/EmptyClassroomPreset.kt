package com.ahu_plus.data.local

data class EmptyClassroomPreset(
    val id: String,
    val title: String,
    val campusId: String,
    val buildingId: String,
    val floor: Int? = null,
    val dayOffset: Int = 0,
    val continuousFree: Boolean = false,
)
