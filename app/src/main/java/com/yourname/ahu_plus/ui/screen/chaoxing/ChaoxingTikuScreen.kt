package com.yourname.ahu_plus.ui.screen.chaoxing

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yourname.ahu_plus.data.repository.ChaoxingTikuRepository.TikuType
import com.yourname.ahu_plus.ui.components.AhuShapes
import kotlinx.coroutines.launch

/**
 * 题库中心(2026-06-20 重做)。
 *
 * 修复: 使用本地 testing/testResult 状态,不再读 signState。
 * 改进: Card 样式统一,provider 行更清晰。
 */
@Composable
fun ChaoxingTikuScreen(viewModel: ChaoxingViewModel) {
    val scope = rememberCoroutineScope()
    val tikuTestResult by viewModel.tikuTestResult.collectAsStateWithLifecycle()

    // Provider 顺序（从 SessionManager 加载）
    var providerOrder by remember { mutableStateOf(loadOrder(viewModel)) }
    var yanxiTokens by remember { mutableStateOf("") }
    var testing by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { Spacer(Modifier.height(12.dp)) }

        // ── Provider 回退链 ───────────────────────────────────
        item {
            Text(
                "Provider 回退链",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                "按顺序依次查询，首个非空答案即返回",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
        }

        items(providerOrder, key = { it.name }) { type ->
            ProviderRow(
                index = providerOrder.indexOf(type),
                total = providerOrder.size,
                type = type,
                onMoveUp = {
                    val idx = providerOrder.indexOf(type)
                    if (idx > 0) {
                        providerOrder = providerOrder.toMutableList().also {
                            it.removeAt(idx)
                            it.add(idx - 1, type)
                        }
                        persistOrder(viewModel, providerOrder)
                    }
                },
                onMoveDown = {
                    val idx = providerOrder.indexOf(type)
                    if (idx < providerOrder.size - 1) {
                        providerOrder = providerOrder.toMutableList().also {
                            it.removeAt(idx)
                            it.add(idx + 1, type)
                        }
                        persistOrder(viewModel, providerOrder)
                    }
                },
            )
        }

        // ── 言溪 Token 配置 ──────────────────────────────────
        item {
            Spacer(Modifier.height(8.dp))
            Card(shape = AhuShapes.Card, elevation = CardDefaults.cardElevation(1.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "言溪 Token",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        "多个 Token 逗号分隔，按序轮询",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = yanxiTokens,
                        onValueChange = {
                            yanxiTokens = it
                            scope.launch { viewModel.sessionManager.saveCxTokensYanxi(it) }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("token1,token2,...") },
                        shape = AhuShapes.Card,
                        singleLine = true,
                    )
                }
            }
        }

        // ── AI 连通性测试 ────────────────────────────────────
        item {
            Card(shape = AhuShapes.Card, elevation = CardDefaults.cardElevation(1.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(
                                "AI 大模型连通性测试",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                "测试配置的 AI API 是否可用",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Button(
                            onClick = {
                                testing = true
                                scope.launch {
                                    viewModel.testLlmConnection()
                                    testing = false
                                }
                            },
                            enabled = !testing,
                            shape = AhuShapes.Card,
                        ) {
                            if (testing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                            } else {
                                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.size(4.dp))
                                Text("测试")
                            }
                        }
                    }

                    if (tikuTestResult != null) {
                        Spacer(Modifier.height(8.dp))
                        val isSuccess = tikuTestResult!!.startsWith("✓")
                        Surface(
                            color = if (isSuccess) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.errorContainer
                            },
                            shape = AhuShapes.Card,
                        ) {
                            Text(
                                text = tikuTestResult!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isSuccess) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onErrorContainer
                                },
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            )
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(32.dp)) }
    }
}

// ══════════════════════════════════════════════════════════════
//  Provider 行
// ══════════════════════════════════════════════════════════════

@Composable
private fun ProviderRow(
    index: Int,
    total: Int,
    type: TikuType,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = AhuShapes.Card,
        elevation = CardDefaults.cardElevation(1.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 序号
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Text(
                    text = "${index + 1}",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            Spacer(Modifier.size(12.dp))

            // 名称 + 描述
            Column(modifier = Modifier.weight(1f)) {
                Text(type.label, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = when (type) {
                        TikuType.DISABLED -> "不使用任何题库"
                        TikuType.CACHE -> "仅查本地 DataStore 缓存"
                        TikuType.YANXI -> "言溪题库 tk.enncy.cn"
                        TikuType.GO -> "GO 题 q.icodef.com"
                        TikuType.LIKE -> "LIKE 知识库 datam.site"
                        TikuType.ADAPTER -> "自部署 tikuAdapter"
                        TikuType.AI -> "OpenAI 兼容 API"
                        TikuType.SILICONFLOW -> "硅基流动 deepseek/Qwen"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // 排序按钮
            IconButton(onClick = onMoveUp, enabled = index > 0, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Filled.ArrowUpward, contentDescription = "上移", modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = onMoveDown, enabled = index < total - 1, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Filled.ArrowDownward, contentDescription = "下移", modifier = Modifier.size(18.dp))
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════
//  辅助函数
// ══════════════════════════════════════════════════════════════

private fun loadOrder(viewModel: ChaoxingViewModel): List<TikuType> {
    val chain = viewModel.sessionManager.getCxProviderChain()
    if (chain.isBlank()) return listOf(TikuType.CACHE)
    return chain.split(",").mapNotNull {
        runCatching { TikuType.valueOf(it.trim().uppercase()) }.getOrNull()
    }.ifEmpty { listOf(TikuType.CACHE) }
}

private fun persistOrder(viewModel: ChaoxingViewModel, order: List<TikuType>) {
    viewModel.reorderProviderChain(order)
}
