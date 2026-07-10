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
import android.content.Context
import android.widget.Toast
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ahu_plus.data.model.CProgAttempt
import com.ahu_plus.data.model.CProgExamRow
import com.ahu_plus.data.model.CProgQuestionItem
import com.ahu_plus.ui.components.AhuCard
import com.ahu_plus.ui.components.AhuTopAppBar

/**
 * 历史作答详情:题干、本人答案、得分、标准答案和解析。
 * 改错题答案以 ^~^ 分隔片段,按行渲染;单选题渲染选项。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CProgPaperScreen(
    viewModel: CProgViewModel,
    exam: CProgExamRow,
    attempt: CProgAttempt,
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
                state.error != null -> Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(state.error.orEmpty(), color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { viewModel.openPaper(exam, attempt) }) { Text("重试") }
                }
                state.paper != null -> {
                    val paper = state.paper!!
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        item {
                            Text(
                                "${paper.subjectCaption ?: ""}  ·  ${paper.paperQuestionCount} 题  ·  " +
                                    "得分 ${formatCProgScore(attempt.grade)}/${formatCProgScore(paper.paperGrade)}",
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
    val context = LocalContext.current
    var copyMenuExpanded by remember { mutableStateOf(false) }
    AhuCard {
        Column(Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "第 $index 题",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    item.studentGrade?.let {
                        Text(
                            "得分 ${formatCProgScore(it)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (it > 0.0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        )
                    }
                    Box {
                        IconButton(onClick = { copyMenuExpanded = true }) {
                            Icon(
                                Icons.Filled.ContentCopy,
                                contentDescription = "复制本题",
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        DropdownMenu(
                            expanded = copyMenuExpanded,
                            onDismissRequest = { copyMenuExpanded = false },
                        ) {
                            CopyMenuItem("复制题干和选项") {
                                copyMenuExpanded = false
                                copyText(context, "题干和选项", buildQuestionPromptCopy(index, item))
                            }
                            item.studentAnswer?.takeIf { it.isNotBlank() }?.let { answer ->
                                CopyMenuItem("复制我的答案") {
                                    copyMenuExpanded = false
                                    copyText(context, "我的答案", displayAnswer(answer, isCorrection))
                                }
                            }
                            item.answer?.takeIf { it.isNotBlank() }?.let { answer ->
                                CopyMenuItem("复制参考答案") {
                                    copyMenuExpanded = false
                                    copyText(context, "参考答案", displayAnswer(answer, isCorrection))
                                }
                            }
                            CopyMenuItem("复制整题") {
                                copyMenuExpanded = false
                                copyText(context, "整题", buildWholeQuestionCopy(index, item, isCorrection))
                            }
                        }
                    }
                }
            }
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
            item.studentAnswer?.takeIf { it.isNotBlank() }?.let { studentAnswer ->
                Spacer(Modifier.height(10.dp))
                Text("我的答案", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                CodeBlock(code = displayAnswer(studentAnswer, isCorrection), label = "我的答案")
            }

            // 参考答案
            val ans = item.answer
            if (!ans.isNullOrBlank()) {
                Spacer(Modifier.height(10.dp))
                Text("参考答案", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                CodeBlock(code = displayAnswer(ans, isCorrection), label = "参考答案")
            }
            item.analysis?.takeIf { it.isNotBlank() }?.let { analysis ->
                Spacer(Modifier.height(10.dp))
                Text("解析", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(analysis, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun CopyMenuItem(label: String, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label) },
        onClick = onClick,
        leadingIcon = {
            Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
        },
    )
}

@Composable
private fun CodeBlock(code: String, label: String) {
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
            modifier = Modifier.padding(start = 12.dp, top = 12.dp, end = 44.dp, bottom = 12.dp).fillMaxWidth(),
        )
        IconButton(
            onClick = { copyText(context, label, code) },
            modifier = Modifier.align(Alignment.TopEnd),
        ) {
            Icon(Icons.Filled.ContentCopy, contentDescription = "复制$label", modifier = Modifier.size(18.dp))
        }
    }
}

private fun copyText(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    clipboard?.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(context, "已复制$label", Toast.LENGTH_SHORT).show()
}

internal fun buildQuestionPromptCopy(index: Int, item: CProgQuestionItem): String = buildString {
    append("第 ").append(index).append(" 题")
    if (item.text.isNotBlank()) append('\n').append(item.text.trim())
    if (item.options.isNotEmpty()) {
        append("\n\n")
        item.options.forEachIndexed { optionIndex, option ->
            if (optionIndex > 0) append('\n')
            append(option.code.orEmpty()).append(". ").append(option.content.orEmpty())
        }
    }
}

internal fun buildWholeQuestionCopy(
    index: Int,
    item: CProgQuestionItem,
    isCorrection: Boolean,
): String = buildString {
    append(buildQuestionPromptCopy(index, item))
    item.studentAnswer?.takeIf { it.isNotBlank() }?.let {
        append("\n\n我的答案：\n").append(displayAnswer(it, isCorrection))
    }
    item.answer?.takeIf { it.isNotBlank() }?.let {
        append("\n\n参考答案：\n").append(displayAnswer(it, isCorrection))
    }
}

private fun displayAnswer(answer: String, isCorrection: Boolean): String =
    if (isCorrection) answer.replace("^~^", "\n") else answer
