package com.ahu_plus.ui.screen.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ahu_plus.AhuPlusApplication
import com.ahu_plus.data.model.Announcement
import com.ahu_plus.util.BrowserOpener
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 通知公告历史列表 —— 展示从 Gitee 拉取的全部公告(含已过期/已忽略),
 * 供用户在「我的 → 通知公告」回看。进入时后台刷新一次。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnnouncementHistoryScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as AhuPlusApplication
    val repo = app.announcementRepository

    var announcements by remember { mutableStateOf(repo.getAllAnnouncements()) }
    var isLoading by remember { mutableStateOf(announcements.isEmpty()) }

    suspend fun reload() {
        isLoading = true
        repo.fetchRemote()
        announcements = repo.getAllAnnouncements()
        isLoading = false
    }

    LaunchedEffect(Unit) { reload() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("通知公告") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when {
                isLoading && announcements.isEmpty() -> {
                    item { LoadingBlock("正在加载公告...") }
                }
                announcements.isEmpty() -> {
                    item {
                        ProfileSection {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 28.dp, horizontal = 20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "暂无公告",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                else -> {
                    items(announcements, key = { it.id }) { item ->
                        AnnouncementCard(item)
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun AnnouncementCard(announcement: Announcement) {
    val context = LocalContext.current
    val now = remember { System.currentTimeMillis() }
    val expireMillis = remember(announcement.expireAt) {
        Announcement.parseTimeOrNull(announcement.expireAt)
    }
    val isExpired = expireMillis != null && now > expireMillis

    ProfileSection {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Campaign,
                    contentDescription = null,
                    tint = if (isExpired) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(0.dp))
                Text(
                    text = announcement.title.ifBlank { "公告" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .weight(1f)
                )
                if (isExpired) {
                    Text(
                        text = "已过期",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = announcement.content,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            val timeText = announcementTimeText(announcement)
            if (timeText.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = timeText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (announcement.hasAction()) {
                Spacer(modifier = Modifier.height(4.dp))
                TextButton(
                    onClick = { BrowserOpener.open(context, announcement.actionUrl) },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                ) {
                    Text(announcement.actionLabel)
                }
            }
        }
    }
}

private fun announcementTimeText(announcement: Announcement): String {
    val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    val start = Announcement.parseTimeOrNull(announcement.startAt)
    val expire = Announcement.parseTimeOrNull(announcement.expireAt)
    return when {
        start != null && expire != null ->
            "${fmt.format(Date(start))} ~ ${fmt.format(Date(expire))}"
        start != null -> "发布于 ${fmt.format(Date(start))}"
        expire != null -> "有效期至 ${fmt.format(Date(expire))}"
        else -> ""
    }
}
