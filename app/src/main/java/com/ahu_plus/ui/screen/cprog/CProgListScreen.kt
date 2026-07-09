package com.ahu_plus.ui.screen.cprog

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.ArrowDropDown
import com.ahu_plus.data.model.CProgExamRow
import com.ahu_plus.ui.components.AhuCard
import com.ahu_plus.ui.components.AhuTopAppBar

/**
 * 大学计算机平台列表页:科目下拉 + 分类计数 + jqGrid 分页列表。
 * 只有练习行可点进整卷看答案(其余分类当前抓包为 0 条,列表通常为空)。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CProgListScreen(
    viewModel: CProgViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.list.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    // 触底加载更多
    val shouldLoadMore by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= state.exams.size - 3 && state.hasMore
        }
    }
    LaunchedEffect(shouldLoadMore) { if (shouldLoadMore) viewModel.loadMore() }

    Scaffold(
        topBar = {
            AhuTopAppBar(
                title = { Text("大学计算机平台", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refreshList() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                    }
                    IconButton(onClick = { viewModel.logout() }) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "退出登录")
                    }
                },
            )
        },
    ) { inner ->
        Column(modifier = Modifier.fillMaxSize().padding(inner)) {
            // 分类计数条(只读展示)
            if (state.sections.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.sections.forEach { sec ->
                        AssistChip(
                            onClick = {},
                            enabled = false,
                            label = { Text("${sec.title} ${sec.counts}") },
                        )
                    }
                }
            }

            // 科目下拉
            if (state.subjects.isNotEmpty()) {
                SubjectDropdown(
                    subjects = state.subjects.map { it.subjectId to it.caption },
                    selectedId = state.selectedSubjectId,
                    onSelect = viewModel::selectSubject,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            when {
                state.loading && state.exams.isEmpty() -> Box(
                    Modifier.fillMaxSize(), contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }

                state.error != null && state.exams.isEmpty() -> Box(
                    Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center,
                ) { Text(state.error ?: "", color = MaterialTheme.colorScheme.error) }

                state.exams.isEmpty() -> Box(
                    Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center,
                ) { Text("暂无可查看的练习", color = MaterialTheme.colorScheme.onSurfaceVariant) }

                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(state.exams, key = { it.examId }) { exam ->
                        ExamCard(exam = exam, onClick = { viewModel.openPaper(exam) })
                    }
                    if (state.loadingMore) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SubjectDropdown(
    subjects: List<Pair<String, String>>,  // subjectId to caption
    selectedId: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val label = subjects.firstOrNull { it.first == selectedId }?.second ?: "全部科目"
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = label,
            onValueChange = {},
            readOnly = true,
            label = { Text("科目") },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("全部科目") }, onClick = { onSelect(""); expanded = false })
            subjects.forEach { (id, caption) ->
                DropdownMenuItem(text = { Text(caption) }, onClick = { onSelect(id); expanded = false })
            }
        }
    }
}

@Composable
private fun ExamCard(exam: CProgExamRow, onClick: () -> Unit) {
    AhuCard(modifier = Modifier.clickable(onClick = onClick)) {
        Column(Modifier.padding(14.dp)) {
            Text(exam.examCaption, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                exam.subjectCaption?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
                exam.examCreateTime?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
