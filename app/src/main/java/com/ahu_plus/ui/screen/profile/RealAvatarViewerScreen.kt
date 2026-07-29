package com.ahu_plus.ui.screen.profile

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 校园真实相片大图查看页。
 *
 * - 优先显示本地缓存文件 [realAvatarFile](无网络也可见);缺失时回退在线 [avatarUrl]。
 * - 刷新:重新跑全链路 (ycard 登录 -> getUserAvatarUrl -> 下载覆盖本地文件)。
 * - 下载:把本地缓存文件写入系统相册 Pictures/AhuPlus。
 * - 初次加载若无本地文件,自动触发一次刷新 (跑全链路)。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RealAvatarViewerScreen(
    avatarUrl: String?,
    realAvatarFile: File?,
    isLoading: Boolean,
    error: String?,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var downloading by remember { mutableStateOf(false) }
    var toastMsg by remember { mutableStateOf<String?>(null) }

    // 初次加载:无本地缓存文件时跑全链路刷新
    LaunchedEffect(Unit) {
        if (realAvatarFile == null) onRefresh()
    }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted && realAvatarFile != null) {
            downloading = true
            scope.launch {
                val msg = withContext(Dispatchers.IO) { saveAvatarToGallery(context, realAvatarFile) }
                downloading = false
                toastMsg = msg
            }
        }
    }

    fun startDownload() {
        val file = realAvatarFile ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            return
        }
        downloading = true
        scope.launch {
            val msg = withContext(Dispatchers.IO) { saveAvatarToGallery(context, file) }
            downloading = false
            toastMsg = msg
        }
    }

    val model: Any? = realAvatarFile ?: avatarUrl

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("校园真实相片") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh, enabled = !isLoading) {
                        Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                    }
                    IconButton(onClick = { startDownload() }, enabled = realAvatarFile != null && !downloading) {
                        if (downloading) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Filled.Download, contentDescription = "下载到相册")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center,
        ) {
            when {
                model == null && isLoading -> {
                    CircularProgressIndicator()
                }
                model == null && error != null -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(error, color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = onRefresh) { Text("重试") }
                    }
                }
                model != null -> {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(model)
                            .crossfade(true)
                            .build(),
                        contentDescription = "真实相片",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    val newScale = (scale * zoom).coerceIn(1f, 5f)
                                    scale = newScale
                                    if (newScale == 1f) {
                                        offsetX = 0f
                                        offsetY = 0f
                                    } else {
                                        offsetX += pan.x
                                        offsetY += pan.y
                                    }
                                }
                            }
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                translationX = offsetX,
                                translationY = offsetY,
                            ),
                    )
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(16.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                }
                else -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("暂无真实相片", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = onRefresh) { Text("获取相片") }
                    }
                }
            }
        }
    }

    toastMsg?.let { msg ->
        LaunchedEffect(msg) {
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            toastMsg = null
        }
    }
}

/**
 * 把真实相片缓存文件写入系统相册 Pictures/AhuPlus。
 * Android 10+ 走 MediaStore(免权限);Android 9- 直接写公共目录(需 WRITE_EXTERNAL_STORAGE)。
 */
private suspend fun saveAvatarToGallery(context: Context, file: File): String =
    withContext(Dispatchers.IO) {
        val bytes = file.readBytes()
        val displayName = "avatar_${System.currentTimeMillis()}.jpg"
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/AhuPlus")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: return@withContext "保存失败:无法创建相册文件"
                try {
                    resolver.openOutputStream(uri)?.use { it.write(bytes) }
                        ?: throw IllegalStateException("无法写入相册文件")
                    values.clear()
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                    "已保存到相册 Pictures/AhuPlus"
                } catch (e: Exception) {
                    resolver.delete(uri, null, null)
                    "保存失败:${e.message}"
                }
            } else {
                val dir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                    "AhuPlus"
                )
                if (!dir.exists() && !dir.mkdirs()) return@withContext "保存失败:无法创建目录"
                val dest = File(dir, displayName)
                java.io.FileOutputStream(dest).use { it.write(bytes) }
                android.media.MediaScannerConnection.scanFile(
                    context, arrayOf(dest.absolutePath), arrayOf("image/jpeg"), null
                )
                "已保存到相册 Pictures/AhuPlus"
            }
        } catch (e: Exception) {
            "保存失败:${e.message}"
        }
    }
