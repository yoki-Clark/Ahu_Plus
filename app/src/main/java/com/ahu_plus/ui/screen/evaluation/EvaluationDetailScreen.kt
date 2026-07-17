package com.ahu_plus.ui.screen.evaluation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ahu_plus.data.model.evaluation.EvaluationAnswer
import com.ahu_plus.data.model.evaluation.EvaluationCommentOption
import com.ahu_plus.data.model.evaluation.EvaluationOption
import com.ahu_plus.data.model.evaluation.EvaluationQuestion
import com.ahu_plus.data.model.evaluation.EvaluationQuestionnaire
import com.ahu_plus.data.model.evaluation.TeacherEvaluationTask
import com.ahu_plus.ui.components.AhuTopAppBar
import com.ahu_plus.ui.components.CenteredError
import com.ahu_plus.ui.components.CenteredLoader
import com.ahu_plus.ui.theme.AhuGradient
import com.ahu_plus.ui.theme.AhuRed
import com.ahu_plus.ui.theme.AhuShapes
import com.ahu_plus.ui.theme.AhuSpacing
import kotlinx.coroutines.launch

/**
 * 评教详情页 — 显示一个老师的问卷,支持作答 + 提交。
 *
 * 顶部:课程名 + 老师名 + 问卷名(渐变 header)
 * 主区:每道题独立 Card
 *   - 单选:RadioButton + 文字
 *   - 文本:OutlinedTextField 多行
 * 底部固定按钮:[提交(实名)] [匿名提交]
 *
 * ponytail: 复用 AhuTopAppBar;题型靠 `EvaluationQuestion.type` 分支,
 * 不为单选/文本各写一个 Composable。
 */
@Composable
fun EvaluationDetailScreen(
    task: TeacherEvaluationTask,
    viewModel: EvaluationViewModel,
    onNeedsLogin: () -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.detailState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    var showFillDialog by rememberSaveable { mutableStateOf(false) }
    var commentOptions by remember { mutableStateOf(viewModel.currentCommentOptions()) }
    var selectedOptionId by remember(commentOptions) {
        mutableStateOf(commentOptions.firstOrNull()?.id ?: "")
    }

    fun refreshCommentOptions() {
        commentOptions = viewModel.currentCommentOptions()
        if (commentOptions.none { it.id == selectedOptionId }) {
            selectedOptionId = commentOptions.firstOrNull()?.id ?: ""
        }
    }

    LaunchedEffect(state.submitErrorVersion) {
        val message = state.submitError
        if (state.submitErrorVersion > 0 && !message.isNullOrBlank()) {
            snackbarHostState.showSnackbar(
                message = message,
                duration = SnackbarDuration.Long,
            )
        }
    }

    // 进入页面触发加载(若未加载)
    LaunchedEffect(task.stdSumTaskId) {
        if (state.task?.stdSumTaskId != task.stdSumTaskId || state.questionnaire == null) {
            viewModel.openTask(task)
        }
    }

    if (showFillDialog) {
        FillDialog(
            options = commentOptions,
            selectedId = selectedOptionId,
            onSelect = { selectedOptionId = it },
            onAdd = { text ->
                coroutineScope.launch {
                    val added = viewModel.addCustomCommentOption(text)
                    refreshCommentOptions()
                    selectedOptionId = added.id
                }
            },
            onDelete = { id ->
                coroutineScope.launch {
                    viewModel.removeCustomCommentOption(id)
                    refreshCommentOptions()
                }
            },
            onConfirm = {
                val option = commentOptions.firstOrNull { it.id == selectedOptionId }
                if (option != null) viewModel.applyFillPreset(option.text)
                showFillDialog = false
            },
            onDismiss = { showFillDialog = false },
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            AhuTopAppBar(
                title = { Text("评教") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            refreshCommentOptions()
                            showFillDialog = true
                        },
                        enabled = state.questionnaire != null,
                    ) {
                        Text("一键填写")
                    }
                },
            )
        },
        bottomBar = {
            BottomActionBar(
                submitting = state.submitting,
                onSubmit = { viewModel.submit(anonymous = false, onSuccess = onBack) },
                onAnonymousSubmit = { viewModel.submit(anonymous = true, onSuccess = onBack) },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                state.isLoading -> CenteredLoader()
                state.error != null && state.questionnaire == null -> CenteredError(
                    message = state.error!!,
                    onRetry = if (state.needsLogin) onNeedsLogin else ({ viewModel.openTask(task) }),
                    actionLabel = if (state.needsLogin) "去登录" else "重试",
                )
                state.questionnaire != null -> {
                    val q = state.questionnaire!!
                    LazyColumn(
                        contentPadding = PaddingValues(
                            horizontal = AhuSpacing.ScreenHorizontal,
                            vertical = AhuSpacing.md,
                        ),
                        verticalArrangement = Arrangement.spacedBy(AhuSpacing.CardGap),
                    ) {
                        item {
                            HeaderCard(task = task, questionnaire = q)
                            if (state.submitError != null) {
                                ErrorBanner(
                                    message = state.submitError!!,
                                    onDismiss = viewModel::clearSubmitError,
                                )
                            }
                        }
                        itemsIndexed(q.questions, key = { _, item -> item.questionId }) { idx, question ->
                            QuestionCard(
                                index = idx + 1,
                                question = question,
                                answer = state.answers[question.questionId],
                                highlightMissing = question.questionId in state.missingQuestionIds,
                                onOptionSelected = { opt ->
                                    viewModel.setOptionAnswer(
                                        question.questionId,
                                        opt.optionId,
                                        opt.optionScore,
                                    )
                                },
                                onTextChanged = { text ->
                                    viewModel.setTextAnswer(question.questionId, text)
                                },
                            )
                        }
                        item { Spacer(modifier = Modifier.height(AhuSpacing.xl)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaderCard(task: TeacherEvaluationTask, questionnaire: EvaluationQuestionnaire) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = AhuShapes.Card,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(AhuGradient.Blue.brush),
        ) {
            Column(
                modifier = Modifier.padding(AhuSpacing.md)
            ) {
                Text(
                    text = task.courseName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "任课教师：${task.teacherName}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.92f),
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = questionnaire.questionnaireName,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.78f),
                )
            }
        }
    }
}

@Composable
private fun ErrorBanner(message: String, onDismiss: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = AhuSpacing.sm),
        shape = AhuShapes.Card,
        color = AhuRed.copy(alpha = 0.12f),
    ) {
        Row(
            modifier = Modifier.padding(AhuSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = message,
                color = AhuRed,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "×",
                color = AhuRed,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier
                    .clickable(onClick = onDismiss)
                    .padding(start = 8.dp),
            )
        }
    }
}

@Composable
private fun QuestionCard(
    index: Int,
    question: EvaluationQuestion,
    answer: EvaluationAnswer?,
    highlightMissing: Boolean,
    onOptionSelected: (EvaluationOption) -> Unit,
    onTextChanged: (String) -> Unit,
) {
    val border = if (highlightMissing) AhuRed else MaterialTheme.colorScheme.outlineVariant

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = AhuShapes.Card,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(AhuSpacing.md)) {
            // 题号 + 题干 + 必填红星
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    text = "$index.",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(AhuSpacing.sm))
                Text(
                    text = question.content,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                if (question.required) {
                    Text(
                        text = "*",
                        style = MaterialTheme.typography.titleSmall,
                        color = AhuRed,
                        modifier = Modifier.padding(start = 2.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(AhuSpacing.sm))

            when (question.type) {
                1 -> OptionsList(
                    options = question.options,
                    selectedId = (answer as? EvaluationAnswer.Option)?.optionId,
                    onSelected = onOptionSelected,
                )
                4 -> TextAnswerField(
                    text = (answer as? EvaluationAnswer.Text)?.text.orEmpty(),
                    highlightMissing = highlightMissing,
                    onChanged = onTextChanged,
                )
                else -> Text(
                    "不支持的题型 (type=${question.type})",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun OptionsList(
    options: List<EvaluationOption>,
    selectedId: String?,
    onSelected: (EvaluationOption) -> Unit,
) {
    Column {
        options.forEach { opt ->
            val selected = opt.optionId == selectedId
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onSelected(opt) }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(
                    selected = selected,
                    onClick = { onSelected(opt) },
                    colors = RadioButtonDefaults.colors(
                        selectedColor = MaterialTheme.colorScheme.primary,
                    ),
                )
                Spacer(modifier = Modifier.width(AhuSpacing.sm))
                Text(
                    text = opt.content,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun TextAnswerField(
    text: String,
    highlightMissing: Boolean,
    onChanged: (String) -> Unit,
) {
    val border = if (highlightMissing) AhuRed else MaterialTheme.colorScheme.outline
    OutlinedTextField(
        value = text,
        onValueChange = onChanged,
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp),
        placeholder = { Text("请输入评语") },
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = if (highlightMissing) AhuRed else MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = border,
        ),
        shape = AhuShapes.Card,
        maxLines = 5,
    )
}

@Composable
private fun BottomActionBar(
    submitting: Boolean,
    onSubmit: () -> Unit,
    onAnonymousSubmit: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(AhuSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(AhuSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(
                onClick = onSubmit,
                enabled = !submitting,
                modifier = Modifier.weight(1f),
                shape = AhuShapes.Card,
            ) {
                if (submitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text("提交（实名）")
                }
            }
            Button(
                onClick = onAnonymousSubmit,
                enabled = !submitting,
                modifier = Modifier.weight(1f),
                shape = AhuShapes.Card,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                ),
            ) {
                if (submitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text("匿名提交")
                }
            }
        }
    }
}

/**
 * 一键填写弹窗。两块内容:
 *  - 上:选择题策略(只读说明:六优一良,良随机)
 *  - 下:评语选项(单选),默认「无」+ 用户新增,每行可删(内置除外)
 * 点「确定」按选中评语填空,不触发提交。
 */
@Composable
private fun FillDialog(
    options: List<EvaluationCommentOption>,
    selectedId: String,
    onSelect: (String) -> Unit,
    onAdd: (String) -> Unit,
    onDelete: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    var newComment by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("一键填写") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(AhuSpacing.sm),
            ) {
                // 选择题策略(只读)
                Column {
                    Text(
                        text = "选择题",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "六优一良(良随机选择)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // 评语选项(单选列表)
                Column {
                    Text(
                        text = "评语",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    options.forEach { option ->
                        CommentOptionRow(
                            option = option,
                            selected = option.id == selectedId,
                            onSelect = { onSelect(option.id) },
                            onDelete = { onDelete(option.id) },
                        )
                    }
                    // 添加新评语(行内输入)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = AhuSpacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = newComment,
                            onValueChange = { newComment = it.take(500) },
                            placeholder = { Text("新评语…") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = AhuShapes.Card,
                        )
                        TextButton(
                            onClick = {
                                val text = newComment.trim()
                                if (text.isNotBlank()) {
                                    onAdd(text)
                                    newComment = ""
                                }
                            },
                            enabled = newComment.isNotBlank(),
                        ) { Text("+添加") }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = selectedId.isNotBlank(),
            ) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun CommentOptionRow(
    option: EvaluationCommentOption,
    selected: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onSelect)
            .padding(vertical = 4.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Text(
            text = option.text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f).padding(start = 4.dp),
            maxLines = 2,
        )
        if (!option.builtIn) {
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "删除${option.text}",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
