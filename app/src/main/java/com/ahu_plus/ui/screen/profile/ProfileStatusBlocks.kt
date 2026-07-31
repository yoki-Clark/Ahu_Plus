package com.ahu_plus.ui.screen.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ahu_plus.ui.components.AhuStatusCard
import com.ahu_plus.ui.theme.AhuPlusTheme

@Composable
fun LoadingBlock(text: String) {
    AhuStatusCard(
        text = text,
        loading = true,
        modifier = Modifier.padding(vertical = 12.dp)
    )
}

@Composable
fun ErrorBlock(error: String, onRefresh: () -> Unit) {
    AhuStatusCard(
        text = error,
        tone = MaterialTheme.colorScheme.error,
        actionText = "重试",
        onAction = onRefresh,
        modifier = Modifier.padding(vertical = 12.dp)
    )
}

@Composable
fun LoadingInline(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun ErrorInline(error: String, onRefresh: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = error,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
        TextButton(onClick = onRefresh) {
            Text("重试")
        }
    }
}

@Composable
fun EmptyBlock(text: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 80.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            Icons.Filled.CreditCard,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(text = text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Preview(name = "Loading Block", showBackground = true)
@Composable
private fun PreviewLoadingBlock() {
    AhuPlusTheme {
        LoadingBlock("正在加载账单数据...")
    }
}

@Preview(name = "Error Block", showBackground = true)
@Composable
private fun PreviewErrorBlock() {
    AhuPlusTheme {
        ErrorBlock("网络连接失败", onRefresh = {})
    }
}

@Preview(name = "Loading Inline", showBackground = true)
@Composable
private fun PreviewLoadingInline() {
    AhuPlusTheme {
        LoadingInline("正在同步...")
    }
}

@Preview(name = "Error Inline", showBackground = true)
@Composable
private fun PreviewErrorInline() {
    AhuPlusTheme {
        ErrorInline("认证失败", onRefresh = {})
    }
}

@Preview(name = "Empty Block", showBackground = true)
@Composable
private fun PreviewEmptyBlock() {
    AhuPlusTheme {
        EmptyBlock("暂无数据")
    }
}
