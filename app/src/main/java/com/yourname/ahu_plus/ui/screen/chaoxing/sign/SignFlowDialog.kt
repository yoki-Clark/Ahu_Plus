package com.yourname.ahu_plus.ui.screen.chaoxing.sign

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.yourname.ahu_plus.ui.common.rememberSaveableScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yourname.ahu_plus.data.model.CAMPUS_LOCATIONS
import com.yourname.ahu_plus.data.model.CampusLocation
import com.yourname.ahu_plus.data.model.CxSignType
import com.yourname.ahu_plus.ui.screen.chaoxing.ChaoxingViewModel
import com.yourname.ahu_plus.ui.screen.chaoxing.SignParams
import com.yourname.ahu_plus.util.LocationProvider
import kotlinx.coroutines.launch

/**
 * 即时签到分发对话框(2026-06-24)。
 *
 * 观察 [ChaoxingViewModel.signFlowState],当有待处理任务时按其 signType 弹出对应输入对话框:
 * 普通直接签;位置弹地点选择;手势弹九宫格;签到码弹输入;二维码弹粘贴框;拍照弹选图。
 * 一个任务完成后 VM 自动前进到下一个,直至队列清空。
 */
@Composable
fun SignFlowDialog(viewModel: ChaoxingViewModel) {
    val flow by viewModel.signFlowState.collectAsStateWithLifecycle()
    val task = flow.current ?: return
    val typeLabel = task.signType.label
    val activityName = task.activity.name.ifBlank { "签到活动" }

    when (task.signType) {
        CxSignType.NORMAL, CxSignType.PRE_SIGN -> NormalSignDialog(
            title = activityName,
            typeLabel = typeLabel,
            submitting = flow.submitting,
            error = flow.error,
            onSign = { viewModel.submitCurrentSign(SignParams.Normal) },
            onSkip = { viewModel.skipCurrentSignTask() },
            onDismiss = { viewModel.dismissSignFlow() },
        )
        CxSignType.LOCATION -> LocationSignDialog(
            title = activityName,
            viewModel = viewModel,
            submitting = flow.submitting,
            error = flow.error,
            onSign = { lat, lon, addr -> viewModel.submitCurrentSign(SignParams.Location(lat, lon, addr)) },
            onSkip = { viewModel.skipCurrentSignTask() },
            onDismiss = { viewModel.dismissSignFlow() },
        )
        CxSignType.GESTURE -> GestureSignDialog(
            title = activityName,
            submitting = flow.submitting,
            error = flow.error,
            onSign = { code -> viewModel.submitCurrentSign(SignParams.Gesture(code)) },
            onSkip = { viewModel.skipCurrentSignTask() },
            onDismiss = { viewModel.dismissSignFlow() },
        )
        CxSignType.SIGNCODE -> SignCodeDialog(
            title = activityName,
            submitting = flow.submitting,
            error = flow.error,
            onSign = { code -> viewModel.submitCurrentSign(SignParams.SignCode(code)) },
            onSkip = { viewModel.skipCurrentSignTask() },
            onDismiss = { viewModel.dismissSignFlow() },
        )
        CxSignType.QRCODE -> QrCodeSignDialog(
            title = activityName,
            submitting = flow.submitting,
            error = flow.error,
            onSign = { enc -> viewModel.submitCurrentSign(SignParams.QrCode(enc)) },
            onSkip = { viewModel.skipCurrentSignTask() },
            onDismiss = { viewModel.dismissSignFlow() },
        )
        CxSignType.PHOTO -> PhotoSignDialog(
            title = activityName,
            submitting = flow.submitting,
            error = flow.error,
            onSign = { bytes, name, mime -> viewModel.submitPhotoSign(bytes, name, mime) },
            onSkip = { viewModel.skipCurrentSignTask() },
            onDismiss = { viewModel.dismissSignFlow() },
        )
    }
}

// ── 普通签到 ──────────────────────────────────────────────────────
@Composable
private fun NormalSignDialog(
    title: String,
    typeLabel: String,
    submitting: Boolean,
    error: String?,
    onSign: () -> Unit,
    onSkip: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("类型:$typeLabel,点击下方按钮直接签到。", style = MaterialTheme.typography.bodyMedium)
                ErrorText(error)
            }
        },
        confirmButton = { SubmitButton("签到", submitting, onClick = onSign) },
        dismissButton = { TextButton(onClick = onSkip, enabled = !submitting) { Text("跳过") } },
    )
}

// ── 位置签到 ──────────────────────────────────────────────────────
@Composable
private fun LocationSignDialog(
    title: String,
    viewModel: ChaoxingViewModel,
    submitting: Boolean,
    error: String?,
    onSign: (Double, Double, String) -> Unit,
    onSkip: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            rememberSaveableScrollState().let { scroll ->
                Column(modifier = Modifier.verticalScroll(scroll)) {
                    Text("位置签到,选择坐标来源后即签到:", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    // 选中任一来源即直接提交签到
                    LocationPicker(
                        viewModel = viewModel,
                        onPicked = { lat, lon, name -> onSign(lat, lon, name) },
                    )
                    ErrorText(error)
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onSkip, enabled = !submitting) { Text("跳过") } },
    )
}

// ── 手势签到 ──────────────────────────────────────────────────────
@Composable
private fun GestureSignDialog(
    title: String,
    submitting: Boolean,
    error: String?,
    onSign: (String) -> Unit,
    onSkip: () -> Unit,
    onDismiss: () -> Unit,
) {
    var path by remember { mutableStateOf("") }
    var resetKey by remember { mutableIntStateOf(0) }
    AlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("按老师给出的手势在九宫格上滑动:", style = MaterialTheme.typography.bodyMedium)
                key(resetKey) { GesturePad(onPathChange = { path = it }) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (path.isBlank()) "尚未绘制" else "已绘制:$path",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { path = ""; resetKey++ }) { Text("清除") }
                }
                ErrorText(error)
            }
        },
        confirmButton = {
            SubmitButton("签到", submitting, enabled = path.isNotBlank()) { onSign(path) }
        },
        dismissButton = { TextButton(onClick = onSkip, enabled = !submitting) { Text("跳过") } },
    )
}

// ── 签到码签到 ────────────────────────────────────────────────────
@Composable
private fun SignCodeDialog(
    title: String,
    submitting: Boolean,
    error: String?,
    onSign: (String) -> Unit,
    onSkip: () -> Unit,
    onDismiss: () -> Unit,
) {
    var code by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("输入老师口播的签到码:", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(code, { code = it.trim() }, label = { Text("签到码") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                ErrorText(error)
            }
        },
        confirmButton = {
            SubmitButton("签到", submitting, enabled = code.isNotBlank()) { onSign(code) }
        },
        dismissButton = { TextButton(onClick = onSkip, enabled = !submitting) { Text("跳过") } },
    )
}

// ── 二维码签到 ────────────────────────────────────────────────────
@Composable
private fun QrCodeSignDialog(
    title: String,
    submitting: Boolean,
    error: String?,
    onSign: (String) -> Unit,
    onSkip: () -> Unit,
    onDismiss: () -> Unit,
) {
    var input by remember { mutableStateOf("") }
    // 从粘贴的二维码 URL 中提取 enc 参数;若用户直接粘 enc 值也兼容
    val enc = remember(input) {
        Regex("""enc=([0-9A-Za-z]+)""").find(input)?.groupValues?.get(1) ?: input.trim()
    }
    AlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    "二维码签到:用其他扫码工具扫老师的二维码,把得到的链接整段粘贴到这里(会自动提取 enc)。",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(input, { input = it }, label = { Text("二维码链接 / enc") },
                    modifier = Modifier.fillMaxWidth())
                if (input.isNotBlank()) {
                    Text("提取到 enc:$enc", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                ErrorText(error)
            }
        },
        confirmButton = {
            SubmitButton("签到", submitting, enabled = enc.isNotBlank()) { onSign(enc) }
        },
        dismissButton = { TextButton(onClick = onSkip, enabled = !submitting) { Text("跳过") } },
    )
}

// ── 拍照签到 ──────────────────────────────────────────────────────
@Composable
private fun PhotoSignDialog(
    title: String,
    submitting: Boolean,
    error: String?,
    onSign: (ByteArray, String, String) -> Unit,
    onSkip: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var picked by remember { mutableStateOf<android.net.Uri?>(null) }
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> picked = uri }

    AlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("拍照签到:选择一张照片上传后签到。", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    enabled = !submitting,
                ) { Text(if (picked == null) "选择照片" else "已选择,可重选") }
                ErrorText(error)
            }
        },
        confirmButton = {
            SubmitButton("签到", submitting, enabled = picked != null) {
                val uri = picked ?: return@SubmitButton
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (bytes != null) {
                    val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
                    val name = "sign_${System.currentTimeMillis()}.${if (mime.contains("png")) "png" else "jpg"}"
                    onSign(bytes, name, mime)
                }
            }
        },
        dismissButton = { TextButton(onClick = onSkip, enabled = !submitting) { Text("跳过") } },
    )
}

// ── 公共小组件 ────────────────────────────────────────────────────
@Composable
private fun RadioRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().selectable(selected = selected, onClick = onClick).padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun SubmitButton(text: String, busy: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled && !busy) {
        if (busy) {
            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary)
            Spacer(Modifier.size(8.dp))
        }
        Text(text)
    }
}

@Composable
private fun ErrorText(error: String?) {
    if (!error.isNullOrBlank()) {
        Spacer(Modifier.height(8.dp))
        Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
}
