package com.ahu_plus.ui.screen.login

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahu_plus.data.local.SessionManager
import com.ahu_plus.data.repository.CasAuthException
import com.ahu_plus.data.repository.CasAuthRepository
import com.ahu_plus.data.repository.InitCoordinator
import com.ahu_plus.data.repository.YcardRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LoginViewModel(
    private val casAuthRepository: CasAuthRepository,
    private val ycardRepository: YcardRepository,
    private val adwmhCardRepository: com.ahu_plus.data.repository.AdwmhCardRepository? = null,
    private val sessionManager: SessionManager,
    /** 首次登录初始化协调器,可选(若注入则首次登录成功后启动串行预热) */
    private val initCoordinator: InitCoordinator? = null,
    /** 初始化消息 SharedFlow (从 app 注入,MainScreen 订阅) */
    private val initMessageEmitter: kotlinx.coroutines.flow.MutableSharedFlow<String>? = null,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    /**
     * 预填已保存的账号密码,方便测试。
     */
    fun prefill(username: String?, password: String?) {
        _uiState.update {
            it.copy(
                username = username ?: "",
                password = password ?: ""
            )
        }
    }

    fun onUsernameChanged(value: String) {
        _uiState.update { it.copy(username = value, error = null) }
    }

    fun onPasswordChanged(value: String) {
        _uiState.update { it.copy(password = value, error = null) }
    }

    fun onLogin() {
        val state = _uiState.value
        if (state.username.isBlank()) {
            _uiState.update { it.copy(error = "请输入学号") }
            return
        }
        if (state.password.isBlank()) {
            _uiState.update { it.copy(error = "请输入密码") }
            return
        }

        // ★ 是否首次登录 (LoginScreen 手动登录成功时) → 启动串行预热
        val isFirstLogin = !sessionManager.firstLoginInitDone

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val user = state.username.trim()
            val pass = state.password

            // CAS 与 adwmh 并发登录，adwmh 静默后台执行
            val casDeferred = async(Dispatchers.IO) {
                casAuthRepository.login(user, pass)
            }
            val adwmhDeferred = if (adwmhCardRepository != null) {
                async(Dispatchers.IO) {
                    adwmhCardRepository.autoLogin(user, pass)
                }
            } else null

            // adwmh 完成后静默处理（不阻塞导航）
            adwmhDeferred?.invokeOnCompletion { cause ->
                if (cause != null) {
                    Log.w("LoginVM", "智慧安大登录失败: ${cause.message}")
                }
            }

            // 等待 CAS 完成决定导航
            val casResult = casDeferred.await()

            casResult.fold(
                onSuccess = {
                    // CAS 成功后,ycard 自动复用 CASTGC 走简易 SSO
                    // 失败也不影响主流程(余额仍可用)
                    val ycardResult = withContext(Dispatchers.IO) {
                        ycardRepository.login(user, pass)
                    }
                    ycardResult.fold(
                        onSuccess = { /* 都成功了 */ },
                        onFailure = { e ->
                            Log.e("LoginVM", "ycard login failed: ${e.message}")
                        }
                    )
                    _uiState.update { it.copy(isLoggedIn = true, isLoading = false) }

                    // ★ 首次登录: 启动串行预热 (InitCoordinator 内部 try/catch 单步失败不中断)
                    if (isFirstLogin && initCoordinator != null) {
                        viewModelScope.launch(Dispatchers.IO) {
                            initCoordinator.runSequentially { msg ->
                                initMessageEmitter?.tryEmit(msg)
                            }
                        }
                    }
                },
                onFailure = { e ->
                    val message = when (e) {
                        is CasAuthException -> e.message ?: "登录失败"
                        else -> "网络连接失败，请检查网络"
                    }
                    _uiState.update { it.copy(error = message, isLoading = false) }
                }
            )
        }
    }
}

data class LoginUiState(
    val username: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val isLoggedIn: Boolean = false
)