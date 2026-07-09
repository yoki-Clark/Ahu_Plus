package com.ahu_plus.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrainingPlanRepositoryTest {

    @Test
    fun parseProgramIdFromStandaloneProgramVariable() {
        val html = """
            <script>
              var studentId = 12345;
              var program = {'auditState':{'${'$'}type':'CommonAuditState','${'$'}name':'ACCEPTED'},'enabled':true,'grade':'2024','id':4567,'nameZh':'当前用户培养方案'};
            </script>
        """.trimIndent()

        assertEquals(4567L, TrainingPlanRepository.parseProgramIdFromCompletionHtml(html))
        assertEquals(12345L, TrainingPlanRepository.parseStudentIdFromCompletionHtml(html))
    }

    @Test
    fun parseProgramIdFromModelProgramObjectFallback() {
        val html = """
            <script>
              var model = {'id':null,'student':{'id':99166},'program':{'auditState':{'${'$'}type':'CommonAuditState','${'$'}name':'ACCEPTED'},'currentNode':'流程已结束','id':6789,'nameZh':'另一位同学培养方案'}};
            </script>
        """.trimIndent()

        assertEquals(6789L, TrainingPlanRepository.parseProgramIdFromCompletionHtml(html))
    }

    @Test
    fun parseProgramIdReturnsNullWhenProgramIsMissing() {
        val html = "<script>var studentId = 99166;</script>"

        assertNull(TrainingPlanRepository.parseProgramIdFromCompletionHtml(html))
    }
}
