package com.ahu_plus.data.repository

import com.ahu_plus.data.remote.JsonUtils
import com.ahu_plus.data.model.MarketTopic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 锁定无 token 只读端点（`topics/read_only/{id}` 与 `topics/top?school_id=`）的字段映射。
 * 样本取自 2026-08 实测响应（脱敏）。
 */
class MarketReadOnlyParserTest {

    @Test
    fun `read_only detail parses id content node counts and user`() {
        // 结构:{status,code,msg,data:{id,...,content,node,createTime,likeCount,commentCount,userInfo,schoolInfo,...}}
        val body = """
            {"status":"success","code":200,"msg":"获取帖子只读详情成功","data":{
              "id":39290003,"uuid":1994420292,"title":"none","content":"求省钱教程",
              "data":{"imgs":[]},"strategy":0,"status":"normal","node":"日常","like":0,
              "linkPeople":"","linkInfo":"","linkType":0,"isAnon":1,"isOver":0,"isTop":0,"isPush":1,
              "viewCount":0,"createTime":"2026-08-02 19:45:17","likeCount":3,"commentCount":12,
              "userInfo":{"avatar":"https://x.example/a.jpeg","nickname":"神秘同学82733528"},
              "isMarked":0,"isCollected":0,"showVerificationButton":0,"imgs":[],
              "schoolInfo":{"schoolId":10681,"schoolEn":"ggahu","pageTitle":"安大圈子"},
              "gzh":{"visitIsSub":1,"tmpGzhQrUrl":""}
            }}
        """.trimIndent()
        val topic = parseMarketTopicDetail(body)
        assertEquals(39290003L, topic.id)
        assertEquals("求省钱教程", topic.content)
        assertEquals("日常", topic.node)
        assertEquals("2026-08-02 19:45:17", topic.createTime)
        assertEquals(3, topic.likeCount)
        assertEquals(12, topic.commentCount)
        assertEquals("神秘同学82733528", topic.userInfo?.nickname)
        // title 恒为 "none",UI 侧 TopicTitle 会把 "none" 当无标题处理
        assertEquals("none", topic.title)
    }

    @Test
    fun `topics_top list parses rows with content and imgs`() {
        // 结构:{status,code,msg,data:[{...},{...}]} -- data 直接是数组
        val body = """
            {"status":"success","code":200,"msg":"获取十大热帖成功","data":[
              {"id":39350265,"title":"none","content":"我爸做饭真的好难吃","data":[],"node":"新鲜事",
               "createTime":"2026-08-04 11:18:25","likeCount":2,"commentCount":62,"imgs":[],
               "userInfo":{"uuid":134241525,"avatar":"https://x.example/b.png","nickname":"霹雳人物"},
               "schoolInfo":{"id":10681,"schoolName":"安徽大学","simpleName":"安大","simpleNameEn":"ggahu"}},
              {"id":39345281,"title":"none","content":"想问一下安大","data":[],"node":"新鲜事",
               "createTime":"2026-08-04 10:06:39","likeCount":1,"commentCount":31,"imgs":[],
               "userInfo":{"nickname":"匿名"},"schoolInfo":{"id":10681}}
            ]}
        """.trimIndent()
        val rows = JsonUtils.parseRowsSafe<MarketTopic>(body)
        assertEquals(2, rows.size)
        assertEquals(39350265L, rows[0].id)
        assertEquals("我爸做饭真的好难吃", rows[0].content)
        assertEquals(62, rows[0].commentCount)
        assertEquals("霹雳人物", rows[0].userInfo?.nickname)
        assertEquals(39345281L, rows[1].id)
    }

    @Test
    fun `topics_top empty data array yields empty list`() {
        val body = """{"status":"success","code":200,"msg":"获取十大热帖成功","data":[]}"""
        val rows = JsonUtils.parseRowsSafe<MarketTopic>(body)
        assertTrue(rows.isEmpty())
    }
}
