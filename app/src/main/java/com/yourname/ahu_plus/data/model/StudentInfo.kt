package com.yourname.ahu_plus.data.model

data class StudentInfo(
    val basicFields: List<StudentInfoField> = emptyList(),
    val housingFields: List<StudentInfoField> = emptyList()
) {
    fun firstValueOf(vararg labels: String): String? {
        val allFields = basicFields + housingFields
        return labels.firstNotNullOfOrNull { label ->
            allFields.firstOrNull { it.label == label }?.value?.takeIf(String::isNotBlank)
        }
    }
}

data class StudentInfoField(
    val label: String,
    val value: String
)
