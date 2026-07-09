package com.ahu_plus.ui.screen.profile

import android.content.ClipData
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ahu_plus.data.model.BillRecord
import com.ahu_plus.ui.theme.AhuShapes
import java.text.DecimalFormat
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillDetailScreen(
    bills: List<BillRecord>,
    isLoading: Boolean,
    error: String?,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onOpenAnalytics: () -> Unit = {}
) {
    // 2026-06-22: 单条账单选中后弹出明细 BottomSheet
    var selectedBill by remember { mutableStateOf<BillRecord?>(null) }
    val sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("校园卡账单") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = onOpenAnalytics) {
                        Icon(Icons.Filled.Assessment, contentDescription = "分析")
                    }
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Filled.Refresh, contentDescription = "刷新")
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
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            when {
                isLoading && bills.isEmpty() -> {
                    item {
                        LoadingBlock("正在加载账单...")
                    }
                }
                error != null && bills.isEmpty() -> {
                    item {
                        ErrorBlock(error = error, onRefresh = onRefresh)
                    }
                }
                bills.isEmpty() -> {
                    item {
                        EmptyBlock("暂无账单记录")
                    }
                }
                else -> {
                    item {
                        Card(
                            shape = AhuShapes.Card,
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column {
                                bills.forEachIndexed { index, bill ->
                                    BillRow(bill = bill, onClick = { selectedBill = bill })
                                    if (index != bills.lastIndex) HorizontalDivider()
                                }
                            }
                        }
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }

    // 单条账单明细 BottomSheet
    selectedBill?.let { bill ->
        androidx.compose.material3.ModalBottomSheet(
            onDismissRequest = { selectedBill = null },
            sheetState = sheetState,
            shape = AhuShapes.BottomSheet,
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            BillDetailSheet(bill = bill)
        }
    }
}

@Composable
private fun BillDetailSheet(bill: BillRecord) {
    val typeText = remember(bill) { (bill.turnoverType + " " + bill.consumeTypeName.orEmpty()).trim() }
    val isPayment = remember(bill, typeText) {
        when {
            bill.tranAmt < 0 -> true
            typeText.contains("充值") -> false
            typeText.contains("退款") || typeText.contains("退费") -> false
            typeText.contains("转入") || typeText.contains("入账") -> false
            typeText.contains("消费") || typeText.contains("支付") || typeText.contains("扣款") -> true
            else -> false
        }
    }
    val amount = remember(bill) { abs(bill.tranAmt) / 100.0 }
    val formatter = remember { DecimalFormat("¥#0.00") }
    val cardBalance = remember(bill) { abs(bill.cardBalance) / 100.0 }
    val cardBalanceStr = remember(bill, cardBalance) { if (bill.cardBalance == 0) "—" else formatter.format(cardBalance) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 顶部：商户 + 金额
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = bill.resume.ifBlank { bill.turnoverType.ifBlank { "校园卡交易" } },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = if (isPayment) "-${formatter.format(amount)}" else "+${formatter.format(amount)}",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isPayment) MaterialTheme.colorScheme.error else Color(0xFF2A9D8F)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isPayment) "支出" else "收入",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }
        }

        HorizontalDivider()

        // 明细字段
        BillDetailField("交易时间", bill.effectDateStr.ifBlank { bill.jndatetimeStr }.ifBlank { "—" })
        BillDetailField("交易类型", bill.turnoverType.ifBlank { "—" })
        if (!bill.consumeTypeName.isNullOrBlank()) {
            BillDetailField("消费类型", bill.consumeTypeName)
        }
        if (!bill.payName.isNullOrBlank()) {
            BillDetailField("支付方式", bill.payName)
        }
        if (!bill.locationName.isNullOrBlank()) {
            BillDetailField("地理位置", bill.locationName)
        }
        if (!bill.toMerchant.isNullOrBlank()) {
            BillDetailField("收款方", bill.toMerchant)
        }
        BillDetailField("卡余额", cardBalanceStr)
        if (!bill.icon.isNullOrBlank()) {
            BillDetailField("商户图标", bill.icon)
        }
        BillDetailField("订单号", bill.orderId.ifBlank { "—" }, copyable = true)
    }
}

@Composable
private fun BillDetailField(label: String, value: String, copyable: Boolean = false) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(76.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f).let {
                if (copyable) it.clickable {
                    val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
                    clipboard?.setPrimaryClip(ClipData.newPlainText(label, value))
                    Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
                } else it
            }
        )
    }
}

@Composable
fun BillRow(bill: BillRecord, onClick: () -> Unit = {}) {
    val typeText = (bill.turnoverType + " " + bill.consumeTypeName.orEmpty()).trim()
    val isPayment = when {
        bill.tranAmt < 0 -> true
        typeText.contains("充值") -> false
        typeText.contains("退款") || typeText.contains("退费") -> false
        typeText.contains("转入") || typeText.contains("入账") -> false
        typeText.contains("消费") || typeText.contains("支付") || typeText.contains("扣款") -> true
        else -> false
    }
    val amount = abs(bill.tranAmt) / 100.0
    val formatter = DecimalFormat("¥#0.00")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(
                    if (isPayment) MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
                    else Color(0xFF2A9D8F).copy(alpha = 0.14f)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isPayment) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward,
                contentDescription = null,
                tint = if (isPayment) MaterialTheme.colorScheme.error else Color(0xFF2A9D8F)
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = bill.resume.ifBlank { bill.turnoverType.ifBlank { "校园卡交易" } },
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = bill.effectDateStr.ifBlank { bill.jndatetimeStr },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = if (isPayment) "-${formatter.format(amount)}" else "+${formatter.format(amount)}",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = if (isPayment) MaterialTheme.colorScheme.error else Color(0xFF2A9D8F)
        )
    }
}

