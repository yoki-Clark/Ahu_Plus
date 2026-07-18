package com.ahu_plus.ui.screen.dashboard

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.content.FileProvider
import com.ahu_plus.data.model.JwcNotice
import com.ahu_plus.data.model.JwcNoticeAttachment
import com.ahu_plus.data.model.JwcNoticeDetail
import com.ahu_plus.ui.components.AhuTopAppBar
import com.ahu_plus.ui.theme.AhuShapes
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.net.URLConnection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JwcNoticeListScreen(
    viewModel: JwcNoticeListViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val attachmentDownloads = remember { mutableStateMapOf<String, AttachmentDownloadState>() }
    val selectedNotice = uiState.selectedNotice

    BackHandler(enabled = selectedNotice != null) {
        viewModel.closeNotice()
    }

    Scaffold(
        topBar = {
            AhuTopAppBar(
                title = { Text(if (selectedNotice == null) "通知公告" else "通知详情") },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (selectedNotice == null) {
                                onBack()
                            } else {
                                viewModel.closeNotice()
                            }
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            if (selectedNotice == null) {
                                viewModel.loadFirstPage()
                            } else {
                                viewModel.retryDetail()
                            }
                        }
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (selectedNotice == null) {
                NoticeListBody(
                    uiState = uiState,
                    onOpen = viewModel::openNotice,
                    onLoadMore = viewModel::loadNextPage,
                    onRetry = viewModel::loadFirstPage
                )
            } else {
                NoticeDetailBody(
                    notice = selectedNotice,
                    detailState = uiState.details[selectedNotice.url],
                    onRetry = viewModel::retryDetail,
                    downloadStates = attachmentDownloads,
                    onDownload = { attachment ->
                        val current = attachmentDownloads[attachment.url]
                        if (current is AttachmentDownloadState.Success) {
                            openJwcAttachment(context, current)
                            return@NoticeDetailBody
                        }
                        if (current is AttachmentDownloadState.Starting ||
                            current is AttachmentDownloadState.Downloading
                        ) {
                            return@NoticeDetailBody
                        }
                        attachmentDownloads[attachment.url] = AttachmentDownloadState.Starting
                        coroutineScope.launch {
                            downloadJwcAttachmentToDownloads(
                                context = context,
                                viewModel = viewModel,
                                attachment = attachment,
                                onStateChanged = { state ->
                                    attachmentDownloads[attachment.url] = state
                                },
                            )
                        }
                    }
                )
            }

            val wafChallengeUrl = uiState.wafChallengeUrl
            if (wafChallengeUrl != null) {
                key(uiState.wafBootstrapKey) {
                    JwcWafBootstrap(
                        url = wafChallengeUrl,
                        onCookie = viewModel::onWafCookieCaptured,
                        onError = viewModel::onWafBootstrapError,
                    )
                }
            }
        }
    }
}

@Composable
private fun NoticeListBody(
    uiState: JwcNoticeListUiState,
    onOpen: (JwcNotice) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit
) {
    // 2026-07-06 P0: LazyListState.Saver 让 SaveableStateHolder 跨分支剔除时恢复滚动位置。
    val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }

    val shouldLoadMore by remember {
        derivedStateOf {
            val total = listState.layoutInfo.totalItemsCount
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            total > 0 && lastVisible >= total - 3
        }
    }
    LaunchedEffect(shouldLoadMore, uiState.hasMore, uiState.isLoadingMore, uiState.isLoading) {
        if (shouldLoadMore && uiState.hasMore && !uiState.isLoadingMore && !uiState.isLoading) {
            onLoadMore()
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        when {
            uiState.isLoading && uiState.notices.isEmpty() -> {
                item {
                    LoadingRow(text = "正在加载通知公告...")
                }
            }

            uiState.error != null && uiState.notices.isEmpty() -> {
                item {
                    ErrorBlock(message = uiState.error, onRetry = onRetry)
                }
            }

            else -> {
                items(items = uiState.notices, key = { it.url }) { notice ->
                    NoticeRow(notice = notice, onClick = { onOpen(notice) })
                }
                if (uiState.isLoadingMore || (uiState.hasMore && uiState.notices.isNotEmpty() && !uiState.isLoading)) {
                    item {
                        LoadingRow(text = "加载下一页...", compact = true)
                    }
                } else if (!uiState.hasMore && uiState.notices.isNotEmpty()) {
                    item {
                        Text(
                            text = "已经到底了",
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NoticeRow(
    notice: JwcNotice,
    onClick: () -> Unit
) {
    Card(
        shape = AhuShapes.Card,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .padding(end = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Campaign,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = notice.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (notice.date.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = notice.date,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun NoticeDetailBody(
    notice: JwcNotice,
    detailState: NoticeDetailState?,
    onRetry: () -> Unit,
    downloadStates: Map<String, AttachmentDownloadState>,
    onDownload: (JwcNoticeAttachment) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        item {
            NoticeDetailHeader(notice = notice)
        }

        when (detailState) {
            null, NoticeDetailState.Loading -> {
                item {
                    LoadingRow(text = "正在加载通知详情...")
                }
            }

            is NoticeDetailState.Error -> {
                item {
                    ErrorBlock(message = detailState.message, onRetry = onRetry)
                }
            }

            is NoticeDetailState.Success -> {
                item {
                    NoticeDetailContent(detail = detailState.detail)
                }
                if (detailState.detail.attachments.isNotEmpty()) {
                    item {
                        SectionTitle(text = "附件")
                    }
                    items(items = detailState.detail.attachments, key = { it.url }) { attachment ->
                        AttachmentRow(
                            attachment = attachment,
                            downloadState = downloadStates[attachment.url],
                            onDownload = { onDownload(attachment) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NoticeDetailHeader(notice: JwcNotice) {
    Card(
        shape = AhuShapes.Card,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = notice.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            if (notice.date.isNotBlank()) {
                Text(
                    text = notice.date,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun NoticeDetailContent(detail: JwcNoticeDetail) {
    Card(
        shape = AhuShapes.Card,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        SelectionContainer {
            Text(
                text = detail.content.ifBlank { "未读取到正文内容" },
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = MaterialTheme.typography.bodyMedium.lineHeight
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
private fun AttachmentRow(
    attachment: JwcNoticeAttachment,
    downloadState: AttachmentDownloadState?,
    onDownload: () -> Unit
) {
    val isDownloading = downloadState is AttachmentDownloadState.Starting ||
        downloadState is AttachmentDownloadState.Downloading
    Surface(
        shape = AhuShapes.Card,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isDownloading, onClick = onDownload)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.AttachFile,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
            ) {
                Text(
                    text = attachment.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                AttachmentDownloadStatus(attachment.url, downloadState)
            }
            AttachmentDownloadIndicator(downloadState)
        }
    }
}

@Composable
private fun AttachmentDownloadStatus(
    url: String,
    state: AttachmentDownloadState?,
) {
    val text = when (state) {
        AttachmentDownloadState.Starting -> "正在准备下载..."
        is AttachmentDownloadState.Downloading -> state.progress?.let { "下载中 ${it}%" } ?: "正在下载..."
        is AttachmentDownloadState.Success -> "点击打开文件"
        is AttachmentDownloadState.Error -> state.message
        null -> url
    }
    val color = when (state) {
        is AttachmentDownloadState.Success -> MaterialTheme.colorScheme.primary
        is AttachmentDownloadState.Error -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun AttachmentDownloadIndicator(state: AttachmentDownloadState?) {
    Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
        when (state) {
            AttachmentDownloadState.Starting -> CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp,
            )
            is AttachmentDownloadState.Downloading -> {
                val progress = state.progress
                if (progress == null) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                } else {
                    CircularProgressIndicator(
                        progress = { progress / 100f },
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                    )
                }
            }
            is AttachmentDownloadState.Success -> Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = "下载完成",
                tint = MaterialTheme.colorScheme.primary,
            )
            else -> Icon(
                imageVector = Icons.Filled.Download,
                contentDescription = if (state is AttachmentDownloadState.Error) "重试下载" else "下载",
            )
        }
    }
}

@Composable
private fun LoadingRow(
    text: String,
    compact: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = if (compact) 0.dp else 80.dp, bottom = if (compact) 0.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(if (compact) 18.dp else 22.dp),
            strokeWidth = 2.dp
        )
        Text(
            text = text,
            modifier = Modifier.padding(start = 10.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ErrorBlock(
    message: String,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 80.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
        FilledTonalButton(onClick = onRetry) {
            Text("重新加载")
        }
    }
}

private suspend fun downloadJwcAttachmentToDownloads(
    context: Context,
    viewModel: JwcNoticeListViewModel,
    attachment: JwcNoticeAttachment,
    onStateChanged: (AttachmentDownloadState) -> Unit,
) {
    val uri = Uri.parse(attachment.url)
    if (uri.scheme !in setOf("http", "https")) {
        onStateChanged(AttachmentDownloadState.Error("附件链接无效"))
        return
    }

    val target = try {
        withContext(Dispatchers.IO) {
            createJwcDownloadTarget(context, resolveDownloadFileName(attachment, uri))
        }
    } catch (error: Throwable) {
        onStateChanged(AttachmentDownloadState.Error(error.message ?: "无法创建下载文件"))
        return
    }

    try {
        viewModel.downloadAttachment(attachment, target.output) { downloaded, total ->
            val progress = if (total > 0L) {
                ((downloaded * 100L) / total).toInt().coerceIn(0, 100)
            } else {
                null
            }
            withContext(Dispatchers.Main.immediate) {
                onStateChanged(AttachmentDownloadState.Downloading(progress))
            }
        }.getOrThrow()
        withContext(Dispatchers.IO) { target.complete() }
        onStateChanged(AttachmentDownloadState.Success(target.fileName, target.uri, target.mimeType))
        Toast.makeText(context, "已保存到下载目录：${target.fileName}", Toast.LENGTH_SHORT).show()
    } catch (error: CancellationException) {
        withContext(Dispatchers.IO) { target.discard() }
        throw error
    } catch (error: Throwable) {
        withContext(Dispatchers.IO) { target.discard() }
        onStateChanged(AttachmentDownloadState.Error(error.message ?: "下载失败，请点击重试"))
    }
}

private fun createJwcDownloadTarget(context: Context, requestedName: String): JwcDownloadTarget {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val resolver = context.contentResolver
        val mimeType = URLConnection.guessContentTypeFromName(requestedName) ?: "application/octet-stream"
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, requestedName)
            put(MediaStore.Downloads.MIME_TYPE, mimeType)
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("无法创建下载文件")
        val output = resolver.openOutputStream(uri, "w") ?: run {
            resolver.delete(uri, null, null)
            throw IllegalStateException("无法写入下载文件")
        }
        return JwcDownloadTarget(
            fileName = requestedName,
            uri = uri,
            mimeType = mimeType,
            output = output,
            complete = {
                output.close()
                resolver.update(
                    uri,
                    ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) },
                    null,
                    null,
                )
            },
            discard = {
                runCatching { output.close() }
                resolver.delete(uri, null, null)
            },
        )
    }

    // API 24-28 cannot write public Downloads without a runtime permission. Keep the
    // download private instead; FileProvider still exposes it to the share/open action.
    val directory = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        ?: File(context.filesDir, Environment.DIRECTORY_DOWNLOADS)
    if (!directory.exists() && !directory.mkdirs()) throw IllegalStateException("无法创建下载目录")
    val file = uniqueDownloadFile(directory, requestedName)
    val mimeType = URLConnection.guessContentTypeFromName(file.name) ?: "application/octet-stream"
    val output = FileOutputStream(file)
    return JwcDownloadTarget(
        fileName = file.name,
        uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file),
        mimeType = mimeType,
        output = output,
        complete = {
            output.close()
            MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), null, null)
        },
        discard = {
            runCatching { output.close() }
            file.delete()
        },
    )
}

private fun uniqueDownloadFile(directory: File, requestedName: String): File {
    val initial = File(directory, requestedName)
    if (!initial.exists()) return initial
    val extension = requestedName.substringAfterLast('.', missingDelimiterValue = "")
    val baseName = if (extension.isBlank()) requestedName else requestedName.removeSuffix(".$extension")
    var index = 1
    while (true) {
        val suffix = if (extension.isBlank()) " ($index)" else " ($index).$extension"
        File(directory, baseName + suffix).takeIf { !it.exists() }?.let { return it }
        index++
    }
}

private data class JwcDownloadTarget(
    val fileName: String,
    val uri: Uri,
    val mimeType: String,
    val output: OutputStream,
    val complete: () -> Unit,
    val discard: () -> Unit,
)

private sealed interface AttachmentDownloadState {
    data object Starting : AttachmentDownloadState
    data class Downloading(val progress: Int?) : AttachmentDownloadState
    data class Success(val fileName: String, val uri: Uri, val mimeType: String) : AttachmentDownloadState
    data class Error(val message: String) : AttachmentDownloadState
}

private fun openJwcAttachment(context: Context, state: AttachmentDownloadState.Success) {
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(state.uri, state.mimeType)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    runCatching { context.startActivity(intent) }
        .onFailure {
            Toast.makeText(context, "没有可打开此文件的应用", Toast.LENGTH_SHORT).show()
        }
}

private fun resolveDownloadFileName(
    attachment: JwcNoticeAttachment,
    uri: Uri
): String {
    val urlName = Uri.decode(uri.lastPathSegment.orEmpty()).substringAfterLast('/')
    val rawName = attachment.name.trim().ifBlank { urlName }.ifBlank { "jwc_attachment" }
    val sanitized = rawName
        .replace(Regex("""[\\/:*?"<>|]"""), "_")
        .replace(Regex("""\s+"""), " ")
        .trim()
        .take(120)
        .ifBlank { "jwc_attachment" }
    val urlExtension = urlName.substringAfterLast('.', missingDelimiterValue = "")
    return if (sanitized.contains('.') || urlExtension.isBlank() || urlExtension.length > 8) {
        sanitized
    } else {
        "$sanitized.$urlExtension"
    }
}
