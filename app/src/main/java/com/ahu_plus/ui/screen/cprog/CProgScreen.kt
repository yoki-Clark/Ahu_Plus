package com.ahu_plus.ui.screen.cprog

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * 大学计算机平台入口容器,分发登录、成绩、作答记录和详情页面。
 */
@Composable
fun CProgScreen(
    viewModel: CProgViewModel,
    onBack: () -> Unit,
) {
    val page by viewModel.page.collectAsStateWithLifecycle()

    BackHandler {
        when (val current = page) {
            is CProgViewModel.Page.Paper -> viewModel.backToHistory(current.exam)
            is CProgViewModel.Page.History -> viewModel.backToList()
            else -> onBack()
        }
    }

    when (val p = page) {
        is CProgViewModel.Page.Login -> CProgLoginScreen(viewModel = viewModel, onBack = onBack)
        is CProgViewModel.Page.List -> CProgListScreen(viewModel = viewModel, onBack = onBack)
        is CProgViewModel.Page.History -> CProgHistoryScreen(
            viewModel = viewModel,
            exam = p.exam,
            onBack = { viewModel.backToList() },
        )
        is CProgViewModel.Page.Paper -> CProgPaperScreen(
            viewModel = viewModel,
            exam = p.exam,
            attempt = p.attempt,
            onBack = { viewModel.backToHistory(p.exam) },
        )
    }
}
