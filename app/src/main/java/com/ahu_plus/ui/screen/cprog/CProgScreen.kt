package com.ahu_plus.ui.screen.cprog

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * 大学计算机平台入口容器,按 [CProgViewModel.Page] 分发三层页面。
 * 系统返回键:整卷 → 列表;列表/登录 → 交给外层(AppHub)。
 */
@Composable
fun CProgScreen(
    viewModel: CProgViewModel,
    onBack: () -> Unit,
) {
    val page by viewModel.page.collectAsStateWithLifecycle()

    BackHandler {
        when (page) {
            is CProgViewModel.Page.Paper -> viewModel.backToList()
            else -> onBack()
        }
    }

    when (val p = page) {
        is CProgViewModel.Page.Login -> CProgLoginScreen(viewModel = viewModel, onBack = onBack)
        is CProgViewModel.Page.List -> CProgListScreen(viewModel = viewModel, onBack = onBack)
        is CProgViewModel.Page.Paper -> CProgPaperScreen(
            viewModel = viewModel,
            exam = p.exam,
            onBack = { viewModel.backToList() },
        )
    }
}
