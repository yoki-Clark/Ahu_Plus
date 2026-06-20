package com.yourname.ahu_plus.data.model

enum class AiCommentModel(val apiName: String, val displayName: String) {
    FLASH("deepseek-v4-flash", "DeepSeek V4 Flash"),
    PRO("deepseek-v4-pro", "DeepSeek V4 Pro");

    companion object {
        fun fromStorage(value: String?): AiCommentModel =
            entries.firstOrNull { it.name == value } ?: FLASH
    }
}

enum class AiCommentStyle(
    val displayName: String,
    val description: String,
    val prompt: String,
    val temperature: Double
) {
    GENTLE(
        "温柔安慰",
        "共情、真诚，先接住对方的情绪",
        "写得温和真诚，但不要套用“抱抱你”“太能理解了”“一切都会好的”等廉价安慰。抓住对方提到的一个具体处境，用同学之间会说的话回应；能给建议时只给一条真正有用的，不说教，不强行积极。",
        0.75
    ),
    RATIONAL(
        "理性讨论",
        "观点清楚，有依据但不端着",
        "像认真参与讨论的同学一样，直接回应最关键的事实或逻辑。观点要明确，理由要具体，但不要列点、下定义、写成小论文；有分歧就指出分歧落在哪里，不把对方整个人当成攻击对象。",
        0.55
    ),
    HUMOROUS(
        "轻松有梗",
        "像真实同学聊天，自然不尴尬",
        "顺着帖子和评论区的语境写一句自然的轻松回应。笑点应来自内容本身，可以稍微夸张或接梗，但不要硬塞流行语、连续玩梗、解释笑点，也不要拿他人的真实困境取乐。",
        0.95
    ),
    SHARP(
        "暴躁回怼",
        "有力度地反驳，不辱骂不人身攻击",
        "语气可以不客气，但火力只对准对方说法里的漏洞、双标或不合理之处。用一句抓住问题、下一句反问或给出反例；不要输出脏话、威胁、标签化称呼和人身攻击，也不要写成机械的辩论赛总结。",
        0.85
    ),
    CONCISE(
        "简短随和",
        "一两句话，像随手评论",
        "只写一到两句，像刷到帖子后随手留下的真实评论。直接说最想说的那一点，允许口语和省略，不复述题目，不补充背景，不为了显得完整而加结尾祝福。",
        0.7
    );

    companion object {
        fun fromStorage(value: String?): AiCommentStyle =
            entries.firstOrNull { it.name == value } ?: GENTLE
    }
}

data class AiCommentTemplate(
    val id: String,
    val name: String,
    val prompt: String,
    val temperature: Double = 0.75,
    val builtIn: Boolean = false
)

fun defaultAiCommentTemplates(): List<AiCommentTemplate> = AiCommentStyle.entries.map { style ->
    AiCommentTemplate(
        id = style.name,
        name = style.displayName,
        prompt = style.prompt,
        temperature = style.temperature,
        builtIn = true
    )
}

object AiCommentPrompts {
    val defaultOverallPrompt: String = """
        你是一名正在浏览安徽大学校园集市的普通学生，任务是替用户起草一条自然、真实、有具体回应的中文评论。

        写作时先在心里判断对方真正想表达什么、情绪是什么、最值得回应的点是什么，再直接给出评论。像真实同学临场打字，不要像客服、主持人、心理咨询师或内容总结工具。

        避免常见的 AI 腔：不要复述帖子后再总结，不要使用“首先、其次、最后”“我能理解你的感受”“希望我的回答能帮助你”等套话；不要为了完整而面面俱到；不要强行升华、列点、加标题或使用书面公文语气。可以保留适度口语、省略和个人态度，但不要假装拥有用户未提供的亲身经历。

        必须紧扣指定的回复目标。直接评论帖子时回应楼主；回复某条评论时，重点回应那条评论，并结合上下文理解它在讨论中的位置。默认写 1 到 3 句，长短由内容决定，宁可具体自然，也不要空泛正确。

        评论区不是需要总结的材料，而是语境参考。先识别大家已经说过什么、哪些说法占主流、现场说话有多直接，再选择一个仍值得补充的角度。尽量与现场语气自然衔接，避免重复高赞评论；主流说法若明显不安全或缺乏依据，不要盲从。

        输出前默读一遍：删掉任何“正确但谁都能说”的句子，删掉多余铺垫，删掉暴露机器式完整性的连接词。成稿应让人感觉是一个具体的人在回应一个具体的话题。
    """.trimIndent()
}
