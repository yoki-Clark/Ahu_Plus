package com.ahu_plus.ui.screen.cprog

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ahu_plus.data.model.CProgAttempt
import com.ahu_plus.data.model.CProgExamRow
import com.ahu_plus.ui.components.AhuCard
import com.ahu_plus.ui.components.AhuTopAppBar

@Composable
fun CProgHistoryScreen(
    viewModel: CProgViewModel,
    exam: CProgExamRow,
    onBack: () -> Unit,
) {
    val state by viewModel.history.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val shouldLoadMore by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= state.attempts.size - 3 && state.hasMore
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) viewModel.loadMoreHistory(exam)
    }

    Scaffold(
        topBar = {
            AhuTopAppBar(
                title = { Text(exam.examCaption, fontWeight = FontWeight.Bold, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refreshHistory(exam) }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                    }
                },
            )
        },
    ) { inner ->
        when {
            state.loading && state.attempts.isEmpty() -> Box(
                Modifier.fillMaxSize().padding(inner),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            state.error != null && state.attempts.isEmpty() -> Column(
                modifier = Modifier.fillMaxSize().padding(inner).padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(state.error.orEmpty(), color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(12.dp))
                Button(onClick = { viewModel.refreshHistory(exam) }) { Text("重试") }
            }

            state.attempts.isEmpty() -> Box(
                Modifier.fillMaxSize().padding(inner).padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("暂无已提交的作答记录", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            else -> LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(inner),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(state.attempts, key = CProgAttempt::id) { attempt ->
                    AttemptCard(
                        attempt = attempt,
                        onClick = { viewModel.openPaper(exam, attempt) },
                    )
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

@Composable
private fun AttemptCard(attempt: CProgAttempt, onClick: () -> Unit) {
    AhuCard(modifier = Modifier.clickable(onClick = onClick)) {
        Column(Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("成绩 ${formatCProgScore(attempt.grade)}", fontWeight = FontWeight.SemiBold)
                if (attempt.durationSeconds > 0) {
                    Text(
                        formatDuration(attempt.durationSeconds),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            val time = attempt.submitTime ?: attempt.createTime
            if (!time.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "提交于 $time",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun formatDuration(totalSeconds: Int): String {
    val hours = totalSeconds / 3600
    val minutes = totalSeconds % 3600 / 60
    val seconds = totalSeconds % 60
    return when {
        hours > 0 -> "${hours}小时${minutes}分"
        minutes > 0 -> "${minutes}分${seconds}秒"
        else -> "${seconds}秒"
    }
}
