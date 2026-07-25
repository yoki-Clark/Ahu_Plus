package com.ahu_plus.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verify [ProgramCompletionRepository.parseAllCourseList] extracts typeId and deduplicates
 * preferring subcategory (smaller) allCourseList occurrences.
 *
 * Bug: same course appears in both moduleList (top-level aggregated) and allModuleList
 * (subcategory-specific) allCourseList. Old dedup kept first occurrence (top-level),
 * losing subcategory routing. Fix: prefer smaller list (subcategory) and extract typeId.
 */
class ProgramCompletionParseTest {

    // Dollar sign constant for building JS field names like $name in raw strings
    private val d = '$'

    /**
     * Build a mock completion preview HTML snippet.
     *
     * Real page: each module object has typeId matching PlanModuleNode.type.id.
     * Here we simulate the key layout:
     * - moduleList: top-level modules with aggregated allCourseList
     * - allModuleList: same modules but with subcategory children having their own allCourseList
     */
    private fun buildHtml(): String {
        val passed = "{'${d}name':'PASSED'}"
        return """
        <script>
        var moduleList = [
          {
            'id':28784, 'typeId':10, 'name':'Ideology',
            'allCourseList':[{'code':'SX001','nameZh':'Marxism','credits':3.0,'finalResultType':$passed}]
          },
          {
            'id':28786, 'typeId':42, 'name':'GeneralElective',
            'allCourseList':[
              {'code':'TX04144','nameZh':'Philosophy','credits':2.0,'finalResultType':$passed},
              {'code':'TX05M01','nameZh':'Music','credits':2.0,'finalResultType':$passed}
            ]
          }
        ];
        var allModuleList = [
          {
            'id':28786, 'typeId':42, 'name':'GeneralElective',
            'allCourseList':[
              {'code':'TX04144','nameZh':'Philosophy','credits':2.0,'finalResultType':$passed},
              {'code':'TX05M01','nameZh':'Music','credits':2.0,'finalResultType':$passed}
            ],
            'children':[
              {
                'id':28821, 'typeId':362, 'name':'Humanities',
                'allCourseList':[
                  {'code':'TX04144','nameZh':'Philosophy','credits':2.0,'finalResultType':$passed}
                ]
              },
              {
                'id':28820, 'typeId':82, 'name':'Arts',
                'allCourseList':[
                  {'code':'TX05M01','nameZh':'Music','credits':2.0,'finalResultType':$passed}
                ]
              }
            ]
          }
        ];
        </script>
        """.trimIndent()
    }

    @Test
    fun parse_extractsTypeIdFromModuleContext() {
        val html = buildHtml()
        val courses = ProgramCompletionRepository.parseAllCourseList(html)

        // Should parse 3 unique courses (SX001, TX04144, TX05M01)
        assertEquals(3, courses.size)

        val byCode = courses.associateBy { it.code }
        assertEquals(3, byCode.size)
    }

    @Test
    fun parse_subcategoryOccurrencePreferredOverTopLevelAggregation() {
        val html = buildHtml()
        val courses = ProgramCompletionRepository.parseAllCourseList(html)
        val byCode = courses.associateBy { it.code }

        // TX04144 appears in both top-level GeneralElective (size=2) and subcategory Humanities (size=1)
        // Should keep subcategory version (typeId=362), not top-level (typeId=42)
        assertEquals("TX04144 should route to Humanities subcategory (typeId=362)",
            362, byCode["TX04144"]?.typeId)

        // TX05M01 appears in both top-level GeneralElective (size=2) and subcategory Arts (size=1)
        // Should keep subcategory version (typeId=82), not top-level (typeId=42)
        assertEquals("TX05M01 should route to Arts subcategory (typeId=82)",
            82, byCode["TX05M01"]?.typeId)
    }

    @Test
    fun parse_courseOnlyInTopLevel_keepsTopLevelTypeId() {
        val html = buildHtml()
        val courses = ProgramCompletionRepository.parseAllCourseList(html)
        val byCode = courses.associateBy { it.code }

        // SX001 only appears in top-level Ideology (size=1), typeId should be 10
        assertEquals("SX001 should keep top-level module typeId=10",
            10, byCode["SX001"]?.typeId)
    }

    @Test
    fun parse_noDuplicatesInResult() {
        val html = buildHtml()
        val courses = ProgramCompletionRepository.parseAllCourseList(html)

        // Each code should appear exactly once after dedup
        val codes = courses.mapNotNull { it.code }
        assertEquals("No duplicate codes after dedup", codes.size, codes.toSet().size)
    }

    @Test
    fun parse_courseFieldsCorrectlyParsed() {
        val html = buildHtml()
        val courses = ProgramCompletionRepository.parseAllCourseList(html)
        val byCode = courses.associateBy { it.code }

        val tx = byCode["TX04144"]!!
        assertEquals("Philosophy", tx.nameZh)
        assertEquals(2.0, tx.credits)
        assertTrue("Should be PASSED", tx.isPassed)
    }

    @Test
    fun parse_emptyHtml_returnsEmptyList() {
        val courses = ProgramCompletionRepository.parseAllCourseList("<html></html>")
        assertTrue(courses.isEmpty())
    }

    // -- extractTypeId unit tests --

    @Test
    fun extractTypeId_findsClosestTypeIdBeforeAllCourseList() {
        val html = "{'id':123,'typeId':362,'name':'Sub','allCourseList':[]}"
        val idx = html.indexOf("allCourseList':[")
        val typeId = ProgramCompletionRepository.extractTypeId(html, idx)
        assertEquals(362, typeId)
    }

    @Test
    fun extractTypeId_picksLastMatchWhenMultipleInWindow() {
        // Multiple typeId in window, should pick last (closest to allCourseList)
        val html = "{'typeId':10,'name':'Parent'},{'id':99,'typeId':362,'name':'Sub','allCourseList':[]}"
        val idx = html.indexOf("allCourseList':[")
        val typeId = ProgramCompletionRepository.extractTypeId(html, idx)
        assertEquals(362, typeId)
    }

    @Test
    fun extractTypeId_returnsNullWhenNoTypeIdNearby() {
        val html = "{'id':123,'name':'Mod','allCourseList':[]}"
        val idx = html.indexOf("allCourseList':[")
        val typeId = ProgramCompletionRepository.extractTypeId(html, idx)
        assertNull(typeId)
    }

    @Test
    fun extractTypeId_returnsNullForEmptyWindow() {
        val typeId = ProgramCompletionRepository.extractTypeId("allCourseList':", 0)
        assertNull(typeId)
    }

    // -- jsToJson / findMatchingBracket helper tests --

    @Test
    fun jsToJson_convertsSingleQuotesToDoubleQuotes() {
        val js = "{'code':'TX001','name':'Test'}"
        val json = ProgramCompletionRepository.jsToJson(js)
        assertTrue(json.contains("\"code\""))
        assertTrue(json.contains("\"TX001\""))
    }

    @Test
    fun jsToJson_unquotesNullValues() {
        val js = "{'code':'TX001','score':'null'}"
        val json = ProgramCompletionRepository.jsToJson(js)
        assertTrue("null should be unquoted", json.contains("\"score\":null"))
    }

    @Test
    fun findMatchingBracket_findsClosingBracket() {
        val s = "[1,[2,3],4]"
        val end = ProgramCompletionRepository.findMatchingBracket(s, 0)
        assertEquals(s.length - 1, end)
    }

    @Test
    fun findMatchingBracket_handlesNestedArrays() {
        val s = "[[1,2],[3,[4,5]]]"
        val end = ProgramCompletionRepository.findMatchingBracket(s, 0)
        assertEquals(s.length - 1, end)
    }

    @Test
    fun findMatchingBracket_returnsNegativeWhenUnmatched() {
        val s = "[1,2,3"
        val end = ProgramCompletionRepository.findMatchingBracket(s, 0)
        assertEquals(-1, end)
    }

    // -- parseModuleTreeMapping tests (tree-based module-course mapping) --

    @Test
    fun parseModuleTreeMapping_extractsCourseToModuleMappingFromTree() {
        val html = buildHtml()
        val mapping = ProgramCompletionRepository.parseModuleTreeMapping(html)

        // TX04144 should map to Humanities subcategory (typeId=362, depth=1)
        val tx04144 = mapping["TX04144"]
        assertTrue("TX04144 should be in tree mapping", tx04144 != null)
        assertEquals(362, tx04144?.typeId)
        assertEquals("Humanities", tx04144?.moduleName)

        // TX05M01 should map to Arts subcategory (typeId=82, depth=1)
        val tx05m01 = mapping["TX05M01"]
        assertTrue("TX05M01 should be in tree mapping", tx05m01 != null)
        assertEquals(82, tx05m01?.typeId)
        assertEquals("Arts", tx05m01?.moduleName)
    }

    @Test
    fun parseModuleTreeMapping_prefersDeeperModuleForSameCourse() {
        val d = '$'
        val passed = "{'${d}name':'PASSED'}"
        val html = """
        <script>
        var allModuleList = [
          {
            'id':1, 'typeId':42, 'name':'GeneralElective',
            'allCourseList':[{'code':'TX001','nameZh':'Test','credits':2.0,'finalResultType':$passed}],
            'children':[
              {
                'id':2, 'typeId':100, 'name':'SubCategory',
                'allCourseList':[{'code':'TX001','nameZh':'Test','credits':2.0,'finalResultType':$passed}]
              }
            ]
          }
        ];
        </script>
        """.trimIndent()

        val mapping = ProgramCompletionRepository.parseModuleTreeMapping(html)
        // TX001 appears in both parent (depth=0) and child (depth=1); should prefer child
        assertEquals(100, mapping["TX001"]?.typeId)
        assertEquals("SubCategory", mapping["TX001"]?.moduleName)
    }

    @Test
    fun parseModuleTreeMapping_handlesUnquotedKeys() {
        val d = '$'
        val passed = "{${d}name:'PASSED'}"
        val html = """
        <script>
        var allModuleList = [
          {
            id: 1, typeId: 42, name: 'GeneralElective',
            allCourseList: [{code: 'TX001', nameZh: 'Test', credits: 2.0, finalResultType: $passed}],
            children: [
              {
                id: 2, typeId: 100, name: 'SubCat',
                allCourseList: [{code: 'TX001', nameZh: 'Test', credits: 2.0, finalResultType: $passed}]
              }
            ]
          }
        ];
        </script>
        """.trimIndent()

        val mapping = ProgramCompletionRepository.parseModuleTreeMapping(html)
        assertEquals(100, mapping["TX001"]?.typeId)
        assertEquals("SubCat", mapping["TX001"]?.moduleName)
    }

    @Test
    fun parseModuleTreeMapping_handlesDoubleQuotedKeys() {
        val html = """
        <script>
        var allModuleList = [
          {
            "id": 1, "typeId": 42, "name": "GeneralElective",
            "allCourseList": [{"code": "TX001", "nameZh": "Test", "credits": 2.0, "finalResultType": {"${'$'}name": "PASSED"}}],
            "children": [
              {
                "id": 2, "typeId": 100, "name": "SubCat",
                "allCourseList": [{"code": "TX001", "nameZh": "Test", "credits": 2.0, "finalResultType": {"${'$'}name": "PASSED"}}]
              }
            ]
          }
        ];
        </script>
        """.trimIndent()

        val mapping = ProgramCompletionRepository.parseModuleTreeMapping(html)
        assertEquals(100, mapping["TX001"]?.typeId)
        assertEquals("SubCat", mapping["TX001"]?.moduleName)
    }

    @Test
    fun parseModuleTreeMapping_returnsEmptyWhenNoModuleList() {
        val html = "<html><body>no data</body></html>"
        val mapping = ProgramCompletionRepository.parseModuleTreeMapping(html)
        assertTrue(mapping.isEmpty())
    }

    @Test
    fun parseModuleTreeMapping_fallsBackToModuleListVarName() {
        val d = '$'
        val passed = "{'${d}name':'PASSED'}"
        // Only moduleList, no allModuleList
        val html = """
        <script>
        var moduleList = [
          {
            'id':1, 'typeId':42, 'name':'GeneralElective',
            'allCourseList':[{'code':'TX001','nameZh':'Test','credits':2.0,'finalResultType':$passed}]
          }
        ];
        </script>
        """.trimIndent()

        val mapping = ProgramCompletionRepository.parseModuleTreeMapping(html)
        assertEquals(42, mapping["TX001"]?.typeId)
        assertEquals("GeneralElective", mapping["TX001"]?.moduleName)
    }

    // -- extractModuleName tests --

    @Test
    fun extractModuleName_findsModuleNameBeforeAllCourseList() {
        val html = "{'id':123,'typeId':362,'name':'Humanities','allCourseList':[]}"
        val idx = html.indexOf("allCourseList")
        val name = ProgramCompletionRepository.extractModuleName(html, idx)
        assertEquals("Humanities", name)
    }

    @Test
    fun extractModuleName_handlesUnquotedKey() {
        val html = "{id:123, typeId:362, name: 'SubCat', allCourseList:[]}"
        val idx = html.indexOf("allCourseList")
        val name = ProgramCompletionRepository.extractModuleName(html, idx)
        assertEquals("SubCat", name)
    }

    @Test
    fun extractModuleName_returnsNullWhenNoName() {
        val html = "{'id':123,'allCourseList':[]}"
        val idx = html.indexOf("allCourseList")
        val name = ProgramCompletionRepository.extractModuleName(html, idx)
        assertNull(name)
    }

    // -- extractTypeId flexible key matching tests --

    @Test
    fun extractTypeId_handlesUnquotedKey() {
        val html = "{id:123, typeId: 362, name:'Sub', allCourseList:[]}"
        val idx = html.indexOf("allCourseList")
        val typeId = ProgramCompletionRepository.extractTypeId(html, idx)
        assertEquals(362, typeId)
    }

    @Test
    fun extractTypeId_handlesDoubleQuotedKey() {
        val html = """{"id":123, "typeId": 362, "name":"Sub", "allCourseList":[]}"""
        val idx = html.indexOf("allCourseList")
        val typeId = ProgramCompletionRepository.extractTypeId(html, idx)
        assertEquals(362, typeId)
    }

    @Test
    fun extractTypeId_handlesWhitespaceAroundColon() {
        val html = "{'id':123, 'typeId' : 42, 'allCourseList':[]}"
        val idx = html.indexOf("allCourseList")
        val typeId = ProgramCompletionRepository.extractTypeId(html, idx)
        assertEquals(42, typeId)
    }

    // -- Integration: tree mapping + text fallback in parseAllCourseList --

    @Test
    fun parseAllCourseList_setsModuleNameFromTreeMapping() {
        val html = buildHtml()
        val courses = ProgramCompletionRepository.parseAllCourseList(html)
        val byCode = courses.associateBy { it.code }

        // TX04144 should have moduleName from tree (Humanities)
        assertEquals("Humanities", byCode["TX04144"]?.moduleName)
        // TX05M01 should have moduleName from tree (Arts)
        assertEquals("Arts", byCode["TX05M01"]?.moduleName)
    }

    @Test
    fun parseAllCourseList_setsModuleNameFromTextFallback() {
        val d = '$'
        val passed = "{'${d}name':'PASSED'}"
        // No allModuleList, only allCourseList arrays with module context
        val html = """
        <script>
        var moduleList = [
          {
            'id':1, 'typeId':42, 'name':'GeneralElective',
            'allCourseList':[{'code':'TX001','nameZh':'Test','credits':2.0,'finalResultType':$passed}]
          }
        ];
        </script>
        """.trimIndent()

        val courses = ProgramCompletionRepository.parseAllCourseList(html)
        val course = courses.find { it.code == "TX001" }
        assertTrue(course != null)
        assertEquals(42, course?.typeId)
        assertEquals("GeneralElective", course?.moduleName)
    }
}
