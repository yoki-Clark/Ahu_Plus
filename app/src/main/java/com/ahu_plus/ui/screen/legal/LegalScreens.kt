package com.ahu_plus.ui.screen.legal

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ahu_plus.R
import com.ahu_plus.data.legal.LegalContent
import com.ahu_plus.data.legal.LegalDocumentKind

@Composable
fun LegalConsentScreen(
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    var privacyAccepted by rememberSaveable { mutableStateOf(false) }
    var disclaimerAcknowledged by rememberSaveable { mutableStateOf(false) }
    var openDocument by rememberSaveable { mutableStateOf<LegalDocumentKind?>(null) }

    openDocument?.let { kind ->
        BackHandler { openDocument = null }
        LegalDocumentScreen(kind = kind, onBack = { openDocument = null })
        return
    }

    Scaffold(
        bottomBar = {
            Surface(shadowElevation = 8.dp) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = onAccept,
                        enabled = privacyAccepted && disclaimerAcknowledged,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("同意并继续")
                    }
                    TextButton(onClick = onDecline, modifier = Modifier.fillMaxWidth()) {
                        Text("不同意并退出")
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { Spacer(Modifier.height(16.dp)) }
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_launcher_foreground),
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                    )
                    Text("欢迎使用安大加", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "在开始联网、恢复校园会话或注册后台任务前，请先了解数据处理方式和使用边界。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item { ConsentSummary("校园账号与数据", "仅为用户主动使用的校园功能连接学校系统，敏感凭据使用加密存储。") }
            item { ConsentSummary("第三方服务", "集市、学习通、WeLearn、AI 与题库均按需启用，不作为基础功能捆绑授权。") }
            item { ConsentSummary("权限按需申请", "相机、位置、日历、通知和悬浮窗只在对应场景申请，可随时撤回。") }
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = { openDocument = LegalDocumentKind.THIRD_PARTY_SERVICES }) {
                        Text("查看《第三方服务清单》")
                    }
                    TextButton(onClick = { openDocument = LegalDocumentKind.PERMISSIONS }) {
                        Text("查看《权限使用说明》")
                    }
                }
            }
            item { HorizontalDivider() }
            item {
                ConsentCheckRow(
                    checked = privacyAccepted,
                    onCheckedChange = { privacyAccepted = it },
                    prefix = "我已阅读并同意",
                    linkText = "《隐私政策》",
                    onOpenDocument = { openDocument = LegalDocumentKind.PRIVACY_POLICY },
                )
            }
            item {
                ConsentCheckRow(
                    checked = disclaimerAcknowledged,
                    onCheckedChange = { disclaimerAcknowledged = it },
                    prefix = "我已阅读并知悉",
                    linkText = "《免责声明与使用须知》",
                    onOpenDocument = { openDocument = LegalDocumentKind.DISCLAIMER },
                )
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun ConsentSummary(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ConsentCheckRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    prefix: String,
    linkText: String,
    onOpenDocument: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(prefix, style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = onOpenDocument) { Text(linkText) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LegalDocumentScreen(
    kind: LegalDocumentKind,
    onBack: () -> Unit,
) {
    val document = remember(kind) { LegalContent.document(kind) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(document.kind.title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Text(
                    "版本 ${document.version} · 生效日期 ${document.effectiveDate}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item { Text(document.introduction, style = MaterialTheme.typography.bodyLarge) }
            items(document.sections) { section ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(section.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    section.paragraphs.forEach { paragraph ->
                        Text(paragraph, style = MaterialTheme.typography.bodyMedium)
                    }
                    section.bullets.forEach { bullet ->
                        Text("• $bullet", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            item { Spacer(Modifier.height(48.dp)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LegalDataManagementScreen(
    onBack: () -> Unit,
    onWithdraw: () -> Unit,
    onClearAll: () -> Unit,
) {
    var confirmWithdraw by rememberSaveable { mutableStateOf(false) }
    var confirmClearAll by rememberSaveable { mutableStateOf(false) }

    if (confirmWithdraw) {
        AlertDialog(
            onDismissRequest = { confirmWithdraw = false },
            title = { Text("撤回隐私同意？") },
            text = { Text("撤回后将停止联网、静默登录、后台学习和提醒调度，并返回隐私确认页面。本地账号与缓存不会自动删除。") },
            confirmButton = {
                TextButton(onClick = { confirmWithdraw = false; onWithdraw() }) { Text("撤回同意") }
            },
            dismissButton = { TextButton(onClick = { confirmWithdraw = false }) { Text("取消") } },
        )
    }
    if (confirmClearAll) {
        AlertDialog(
            onDismissRequest = { confirmClearAll = false },
            title = { Text("清除全部本地数据？") },
            text = { Text("将删除应用内账号、会话、身份、API Key、缓存、设置、课程备注、考核附件、应用专属下载和同意记录，并取消提醒。已导出到系统相册或公共下载目录的文件及系统日历事件不会自动删除。此操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = { confirmClearAll = false; onClearAll() }) { Text("确认清除") }
            },
            dismissButton = { TextButton(onClick = { confirmClearAll = false }) { Text("取消") } },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("个人数据管理") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "退出校园账号、撤回隐私同意和删除本地数据是三个不同操作。学校或第三方服务器保存的数据需向对应平台申请处理。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = { confirmWithdraw = true }, modifier = Modifier.fillMaxWidth()) {
                Text("撤回隐私同意")
            }
            Text(
                "停止新的联网和后台处理，但保留本地数据，之后重新同意可继续使用。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider()
            TextButton(onClick = { confirmClearAll = true }, modifier = Modifier.fillMaxWidth()) {
                Text("清除全部本地数据", color = MaterialTheme.colorScheme.error)
            }
            Text(
                "同时删除普通设置、课程备注、考核附件和应用专属下载。系统相册、公共下载目录及系统日历中的内容不属于应用私有存储。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
