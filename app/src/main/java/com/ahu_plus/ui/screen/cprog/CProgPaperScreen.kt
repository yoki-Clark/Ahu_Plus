package com.ahu_plus.ui.screen.cprog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ahu_plus.data.model.CProgExamRow
import com.ahu_plus.data.model.CProgQuestionItem
import com.ahu_plus.ui.components.AhuCard
import com.ahu_plus.ui.components.AhuTopAppBar

/**
 * 整卷页:题干 + 参考答案(等宽代码块 + 一键复制)。
 * 改错题答案以 ^~^ 分隔片段,按行渲染;单选题渲染选项。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CProgPaperScreen(
    viewModel: CProgViewModel,
    exam: CProgExamRow,
    onBack: () -> Unit,
) {
    val state by viewModel.paper.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            AhuTopAppBar(
                title = { Text(exam.examCaption, fontWeight = FontWeight.Bold, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { inner ->
        Box(Modifier.fillMaxSize().padding(inner)) {
            when {
                state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                state.error != null -> Box(
                    Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center,
                ) { Text(state.error ?: "", color = MaterialTheme.colorScheme.error) }
                state.paper != null -> {
                    val paper = state.paper!!
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        item {
                            Text(
                                "${paper.subjectCaption ?: ""}  ·  ${paper.paperQuestionCount} 题  ·  满分 ${paper.paperGrade.toInt()}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        paper.questionTypes.forEach { type ->
                            item {
                                Text(
                                    "${type.questionTypeCaption}（${type.questionCount}）",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(top = 8.dp),
                                )
                            }
                            itemsIndexed(type.items) { idx, q ->
                                QuestionCard(index = idx + 1, item = q, isCorrection = type.baseQuestionType == "CL_E")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuestionCard(index: Int, item: CProgQuestionItem, isCorrection: Boolean) {
    AhuCard {
        Column(Modifier.padding(14.dp)) {
            Text(
                "第 $index 题",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
            item.knowledgeCaption?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (item.text.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(item.text, style = MaterialTheme.typography.bodyMedium)
            }
            // 单选题选项
            if (item.options.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                item.options.forEach { o ->
                    Text(
                        "${o.code ?: ""}. ${o.content ?: ""}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(vertical = 1.dp),
                    )
                }
            }
            // 参考答案
            val ans = item.answer
            if (!ans.isNullOrBlank()) {
                Spacer(Modifier.height(10.dp))
                Text("参考答案", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                // 改错题:^~^ 分隔的片段按行拼接;其余原样
                val display = if (isCorrection) ans.replace("^~^", "\n") else ans
                CodeBlock(code = display)
            }
        }
    }
}

@Composable
private fun CodeBlock(code: String) {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                RoundedCornerShape(8.dp),
            ),
    ) {
        Text(
            text = code,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
        )
        IconButton(
            onClick = {
                val cm = context.getSystemService(ClipboardManager::class.java)
                cm?.setPrimaryClip(ClipData.newPlainText("参考答案", code))
                Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.align(Alignment.TopEnd),
        ) {
            Icon(Icons.Filled.ContentCopy, contentDescription = "复制", modifier = Modifier.size(18.dp))
        }
    }
}
