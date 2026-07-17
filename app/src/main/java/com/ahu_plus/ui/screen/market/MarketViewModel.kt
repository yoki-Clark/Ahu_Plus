package com.ahu_plus.ui.screen.market

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahu_plus.data.model.MarketComment
import com.ahu_plus.data.model.AiCommentModel
import com.ahu_plus.data.model.AiCommentStyle
import com.ahu_plus.data.model.AiCommentTemplate
import com.ahu_plus.data.model.MarketCommentReplies
import com.ahu_plus.data.model.MarketIdentity
import com.ahu_plus.data.model.MarketNode
import com.ahu_plus.data.model.MarketNotice
import com.ahu_plus.data.model.MarketTopic
import com.ahu_plus.data.remote.market.MarketApi
import com.ahu_plus.data.repository.MarketRepository
import com.ahu_plus.data.repository.AiCommentRepository
import com.ahu_plus.data.repository.MarketTopicBatch
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

class MarketViewModel(
    private val repository: MarketRepository,
    private val aiCommentRepository: AiCommentRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MarketUiState())
    val uiState: StateFlow<MarketUiState> = _uiState.asStateFlow()
    private var lastTopicsLoadedAt: Long = 0L
    private var searchJob: Job? = null
    private var searchRequestId = 0L
    private var topicJob: Job? = null
    private var topicRequestId = 0L

    private fun beginSearchRequest(): Long {
        searchJob?.cancel()
        return ++searchRequestId
    }

    private fun isCurrentSearchRequest(requestId: Long, query: String): Boolean {
        val state = _uiState.value
        return requestId == searchRequestId &&
            state.isSearching &&
            state.searchQuery.trim() == query
    }

    private fun beginTopicRequest(): Long {
        topicJob?.cancel()
        return ++topicRequestId
    }

    private fun isCurrentTopicRequest(requestId: Long, topicId: Long): Boolean {
        return requestId == topicRequestId && _uiState.value.selectedTopic?.id == topicId
    }

    init {
        refreshSettingsState()
    }

    fun activate() {
        val stale = System.currentTimeMillis() - lastTopicsLoadedAt >= 2L * 60 * 1000
        if (_uiState.value.identities.isNotEmpty() && (_uiState.value.topics.isEmpty() || stale)) {
            refreshTopics()
        }
    }

    fun refreshSettingsState() {
        val identities = repository.getAllIdentities()
        val validIdentityIds = identities.map { it.id }.toSet()
        val selectedIds = repository.getSelectedIdentityIds()
            .intersect(validIdentityIds)
            .ifEmpty { validIdentityIds }
        val blockPinned = repository.getBlockPinned()
        val blockKeywords = repository.getBlockKeywords()
        val filterNodeIds = repository.getFilterNodeIds()
        val marketEnabled = repository.getMarketEnabled()
        val thirdPartyServicesEnabled = repository.getMarketEnabled()  // 同源(parent)
        val marketChildEnabled = repository.getMarketChildEnabled()
        val chaoxingChildEnabled = repository.getChaoxingChildEnabled()
        val welearnChildEnabled = repository.getWelearnChildEnabled()
        val listLayoutMode = repository.getListLayoutMode()
        val scrollToTop = repository.getScrollToTop()
        val aiEnabled = aiCommentRepository.isEnabled()
        val aiModel = aiCommentRepository.getModel()
        val aiStyle = aiCommentRepository.getDefaultStyle()
        val aiOverallPrompt = aiCommentRepository.getOverallPrompt()
        val aiStylePrompts = aiCommentRepository.getStylePrompts()
        val aiTemplates = aiCommentRepository.getTemplates()
        val aiSelectedTemplateId = aiCommentRepository.getSelectedTemplateId()
        val identityCount = identities.size
        _uiState.update {
            it.copy(
                identities = identities,
                selectedIdentityIds = selectedIds,
                blockPinned = blockPinned,
                blockKeywords = blockKeywords,
                filterNodeIds = filterNodeIds,
                marketEnabled = marketEnabled,
                thirdPartyServicesEnabled = thirdPartyServicesEnabled,
                marketChildEnabled = marketChildEnabled,
                chaoxingChildEnabled = chaoxingChildEnabled,
                welearnChildEnabled = welearnChildEnabled,
                listLayoutMode = listLayoutMode,
                scrollToTopEnabled = scrollToTop,
                aiCommentEnabled = aiEnabled,
                aiCommentModel = aiModel,
                aiCommentStyle = aiStyle,
                aiOverallPrompt = aiOverallPrompt,
                aiStylePrompts = aiStylePrompts,
                aiTemplates = aiTemplates,
                aiSelectedTemplateId = aiSelectedTemplateId,
                aiApiKeyConfigured = aiCommentRepository.hasApiKey(),
                hasSavedIdentity = identityCount > 0,
                school = identities.firstOrNull { it.id in selectedIds }?.school
                    ?: identities.firstOrNull()?.school,
                identityInput = ""
            )
        }
    }

    fun setListLayoutMode(mode: String) {
        viewModelScope.launch {
            repository.setListLayoutMode(mode)
            _uiState.update { it.copy(listLayoutMode = mode) }
        }
    }

    fun setScrollToTop(enabled: Boolean) {
        viewModelScope.launch {
            repository.setScrollToTop(enabled)
            _uiState.update { it.copy(scrollToTopEnabled = enabled) }
        }
    }

    fun setAiCommentEnabled(enabled: Boolean) {
        viewModelScope.launch {
            aiCommentRepository.setEnabled(enabled)
            _uiState.update { it.copy(aiCommentEnabled = enabled) }
        }
    }

    fun setAiCommentModel(model: AiCommentModel) {
        viewModelScope.launch {
            aiCommentRepository.setModel(model)
            _uiState.update { it.copy(aiCommentModel = model) }
        }
    }

    fun setAiCommentStyle(style: AiCommentStyle) {
        viewModelScope.launch {
            aiCommentRepository.setDefaultStyle(style)
            _uiState.update { it.copy(aiCommentStyle = style) }
        }
    }

    fun saveAiApiKey(value: String) {
        if (value.isBlank()) return
        aiCommentRepository.saveApiKey(value)
        _uiState.update { it.copy(aiApiKeyConfigured = true) }
    }

    fun clearAiApiKey() {
        aiCommentRepository.clearApiKey()
        _uiState.update { it.copy(aiApiKeyConfigured = false) }
    }

    fun setAiOverallPrompt(prompt: String) {
        viewModelScope.launch {
            aiCommentRepository.setOverallPrompt(prompt)
            _uiState.update { it.copy(aiOverallPrompt = aiCommentRepository.getOverallPrompt()) }
        }
    }

    fun setAiStylePrompt(style: AiCommentStyle, prompt: String) {
        viewModelScope.launch {
            aiCommentRepository.setStylePrompt(style, prompt)
            _uiState.update { it.copy(aiStylePrompts = aiCommentRepository.getStylePrompts()) }
        }
    }

    fun resetAiPrompts() {
        viewModelScope.launch {
            aiCommentRepository.resetPrompts()
            _uiState.update {
                it.copy(
                    aiOverallPrompt = aiCommentRepository.getOverallPrompt(),
                    aiStylePrompts = aiCommentRepository.getStylePrompts(),
                    aiTemplates = aiCommentRepository.getTemplates(),
                    aiSelectedTemplateId = aiCommentRepository.getSelectedTemplateId()
                )
            }
        }
    }

    fun selectAiTemplate(id: String) {
        viewModelScope.launch {
            aiCommentRepository.setSelectedTemplateId(id)
            _uiState.update { it.copy(aiSelectedTemplateId = id) }
        }
    }

    fun saveAiTemplate(id: String?, name: String, prompt: String) {
        if (name.isBlank() || prompt.isBlank()) return
        viewModelScope.launch {
            val existing = id?.let { templateId ->
                aiCommentRepository.getTemplates().firstOrNull { it.id == templateId }
            }
            val template = AiCommentTemplate(
                id = existing?.id ?: UUID.randomUUID().toString(),
                name = name.trim(),
                prompt = prompt.trim(),
                temperature = existing?.temperature ?: 0.75,
                builtIn = existing?.builtIn ?: false
            )
            aiCommentRepository.saveTemplate(template)
            if (existing == null) aiCommentRepository.setSelectedTemplateId(template.id)
            _uiState.update {
                it.copy(
                    aiTemplates = aiCommentRepository.getTemplates(),
                    aiSelectedTemplateId = if (existing == null) template.id else it.aiSelectedTemplateId
                )
            }
        }
    }

    fun deleteAiTemplate(id: String) {
        viewModelScope.launch {
            aiCommentRepository.deleteTemplate(id)
            _uiState.update {
                it.copy(
                    aiTemplates = aiCommentRepository.getTemplates(),
                    aiSelectedTemplateId = aiCommentRepository.getSelectedTemplateId()
                )
            }
        }
    }

    fun refreshIdentityState() {
        refreshSettingsState()
    }

    fun onIdentityInputChanged(value: String) {
        _uiState.update { it.copy(identityInput = value, saveMessage = null, identityError = null) }
    }

    fun saveIdentity() {
        val value = _uiState.value.identityInput.trim()
        if (value.isBlank()) {
            _uiState.update { it.copy(identityError = "请粘贴集市 API 身份字段") }
            return
        }

        viewModelScope.launch {
            val normalized = MarketApi.normalizeIdentity(value)
            val school = MarketApi.schoolFromIdentity(normalized)
            val identity = MarketIdentity(
                id = UUID.randomUUID().toString(),
                token = normalized,
                school = school
            )
            val updated = _uiState.value.identities + identity
            repository.saveMarketIdentities(updated)
            val updatedSelected = _uiState.value.selectedIdentityIds + identity.id
            repository.setSelectedIdentityIds(updatedSelected)
            _uiState.update {
                it.copy(
                    identities = updated,
                    selectedIdentityIds = updatedSelected,
                    hasSavedIdentity = true,
                    school = school,
                    identityInput = "",
                    saveMessage = "已添加 ${school ?: "新校区"}",
                    identityError = null
                )
            }
            refreshTopics()
        }
    }

    fun clearIdentity() {
        viewModelScope.launch {
            repository.saveMarketIdentities(emptyList())
            repository.setSelectedIdentityIds(emptySet())
            repository.clearIdentity()
            _uiState.update {
                MarketUiState(saveMessage = "已清除")
            }
        }
    }

    fun refreshTopics() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    topics = emptyList(),
                    currentPage = 0,
                    hasMoreTopics = true,
                    isLoading = true,
                    error = null,
                    saveMessage = null
                )
            }
            loadTopicsPage(page = 1, append = false)
        }
    }

    fun loadNextPage() {
        val state = _uiState.value
        if (state.isLoadingMore || state.isLoading || !state.hasMoreTopics || !state.hasSavedIdentity) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true, error = null) }
            loadTopicsPage(page = state.currentPage + 1, append = true)
        }
    }

    // ── 设置页面 ────────────────────────────────────────

    fun openSettings() {
        refreshSettingsState()
        _uiState.update { it.copy(showSettings = true) }
    }

    fun closeSettings() {
        _uiState.update { it.copy(showSettings = false) }
    }

    // ── 搜索 ────────────────────────────────────────────

    fun openSearch() {
        beginSearchRequest()
        _uiState.update {
            it.copy(
                isSearching = true,
                searchQuery = "",
                searchResults = emptyList(),
                searchPage = 0,
                hasMoreSearch = true,
                searchLoading = false,
                searchLoadingMore = false,
                searchError = null
            )
        }
    }

    fun closeSearch() {
        beginSearchRequest()
        _uiState.update {
            it.copy(
                isSearching = false,
                searchQuery = "",
                searchResults = emptyList(),
                searchPage = 0,
                hasMoreSearch = true,
                searchLoading = false,
                searchLoadingMore = false,
                searchError = null
            )
        }
    }

    fun onSearchQueryChanged(value: String) {
        if (value != _uiState.value.searchQuery) beginSearchRequest()
        _uiState.update {
            it.copy(
                searchQuery = value,
                searchLoading = false,
                searchLoadingMore = false,
                searchError = null,
            )
        }
    }

    fun submitSearch() {
        val query = _uiState.value.searchQuery.trim()
        if (query.isBlank()) return
        val requestId = beginSearchRequest()
        searchJob = viewModelScope.launch {
            loadSearchPage(query = query, page = 1, append = false, requestId = requestId)
        }
    }

    fun loadMoreSearchResults() {
        val state = _uiState.value
        if (!state.isSearching) return
        if (state.searchLoading || state.searchLoadingMore || !state.hasMoreSearch) return
        val query = state.searchQuery.trim()
        if (query.isBlank()) return
        val requestId = beginSearchRequest()
        searchJob = viewModelScope.launch {
            loadSearchPage(
                query = query,
                page = state.searchPage + 1,
                append = true,
                requestId = requestId,
            )
        }
    }

    private suspend fun loadSearchPage(
        query: String,
        page: Int,
        append: Boolean,
        requestId: Long,
    ) {
        if (!isCurrentSearchRequest(requestId, query)) return
        _uiState.update { state ->
            if (!isCurrentSearchRequest(requestId, query)) return@update state
            if (append) state.copy(searchLoadingMore = true, searchError = null)
            else state.copy(searchLoading = true, searchError = null)
        }
        val identities = selectedIdentities()
        val result = if (identities.size == 1) {
            val identity = identities.first()
            repository.searchTopics(query, page = page, identity = identity.token).map { topics ->
                MarketTopicBatch(
                    topics = topics,
                    topicSchoolMap = identity.school?.let { school ->
                        topics.associate { it.id to school }
                    } ?: emptyMap(),
                    topicIdentityMap = topics.associate { it.id to identity.token }
                )
            }
        } else {
            repository.searchTopicsMulti(query, identities, page = page)
        }
        result.fold(
            onSuccess = { batch ->
                if (!isCurrentSearchRequest(requestId, query)) return@fold
                _uiState.update { s ->
                    if (!isCurrentSearchRequest(requestId, query)) return@update s
                    val merged = if (append) {
                        (s.searchResults + batch.topics).distinctBy { it.id }
                    } else {
                        batch.topics
                    }
                    s.copy(
                        searchLoading = false,
                        searchLoadingMore = false,
                        searchResults = merged,
                        searchPage = page,
                        // 空页视为耗尽:避免对同一末页反复请求
                        hasMoreSearch = batch.topics.isNotEmpty(),
                        searchError = null,
                        topicSchoolMap = s.topicSchoolMap + batch.topicSchoolMap,
                        topicIdentityMap = s.topicIdentityMap + batch.topicIdentityMap
                    )
                }
            },
            onFailure = { e ->
                _uiState.update { state ->
                    if (!isCurrentSearchRequest(requestId, query)) return@update state
                    state.copy(
                        searchLoading = false,
                        searchLoadingMore = false,
                        searchError = e.message ?: "搜索失败"
                    )
                }
            }
        )
    }

    // ── 多校园管理 ──────────────────────────────────────

    fun removeIdentity(id: String) {
        viewModelScope.launch {
            val updated = _uiState.value.identities.filter { it.id != id }
            repository.saveMarketIdentities(updated)
            val updatedSelected = (_uiState.value.selectedIdentityIds - id)
                .takeIf { it.isNotEmpty() || updated.isEmpty() }
                ?: setOf(updated.first().id)
            repository.setSelectedIdentityIds(updatedSelected)
            _uiState.update {
                it.copy(
                    identities = updated,
                    selectedIdentityIds = updatedSelected,
                    hasSavedIdentity = updated.isNotEmpty(),
                    school = updated.firstOrNull { i -> i.id in updatedSelected }?.school
                        ?: updated.firstOrNull()?.school
                )
            }
            refreshTopics()
        }
    }

    fun toggleIdentitySelection(id: String, selected: Boolean) {
        viewModelScope.launch {
            val updatedSelected = if (selected) {
                _uiState.value.selectedIdentityIds + id
            } else {
                _uiState.value.selectedIdentityIds - id
            }.takeIf { it.isNotEmpty() } ?: run {
                _uiState.update { it.copy(identityError = "至少需要选择一个校区") }
                return@launch
            }
            repository.setSelectedIdentityIds(updatedSelected)
            _uiState.update { it.copy(selectedIdentityIds = updatedSelected) }
            refreshTopics()
        }
    }

    /** 一键选中所有已保存的校区身份 */
    fun selectAllIdentities() {
        viewModelScope.launch {
            val allIds = _uiState.value.identities.map { it.id }.toSet()
            if (allIds.isEmpty() || allIds == _uiState.value.selectedIdentityIds) return@launch
            repository.setSelectedIdentityIds(allIds)
            _uiState.update {
                it.copy(
                    selectedIdentityIds = allIds,
                    school = _uiState.value.identities.firstOrNull()?.school
                )
            }
            refreshTopics()
        }
    }

    // ── 屏蔽置顶 ────────────────────────────────────────

    fun setBlockPinned(enabled: Boolean) {
        viewModelScope.launch {
            repository.setBlockPinned(enabled)
            _uiState.update { it.copy(blockPinned = enabled) }
        }
    }

    // ── 第三方服务:parent 总开关 (5s 弹窗) ──────────────
    // 开启后子开关可见;关闭后底部「集市」「学习通」Tab 同时隐藏
    fun setMarketEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setMarketEnabled(enabled)
            _uiState.update {
                it.copy(
                    marketEnabled = enabled,
                    thirdPartyServicesEnabled = enabled,
                )
            }
        }
    }

    // ── 第三方服务:集市 / 学习通子开关 ─────────────────
    // parent 必须开启,Tab 才可见;parent 关闭时此 flag 仅作为下次开启时的初始状态
    fun setMarketChildEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setMarketChildEnabled(enabled)
            _uiState.update { it.copy(marketChildEnabled = enabled) }
        }
    }

    fun setChaoxingChildEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setChaoxingChildEnabled(enabled)
            _uiState.update { it.copy(chaoxingChildEnabled = enabled) }
        }
    }

    fun setWelearnChildEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setWelearnChildEnabled(enabled)
            _uiState.update { it.copy(welearnChildEnabled = enabled) }
        }
    }

    // ── 屏蔽词 ──────────────────────────────────────────

    fun onKeywordInputChanged(value: String) {
        _uiState.update { it.copy(keywordInput = value) }
    }

    fun addBlockKeyword() {
        val keyword = _uiState.value.keywordInput.trim()
        if (keyword.isBlank()) return
        viewModelScope.launch {
            val updated = _uiState.value.blockKeywords + keyword
            repository.saveBlockKeywords(updated)
            _uiState.update { it.copy(blockKeywords = updated, keywordInput = "") }
        }
    }

    fun removeBlockKeyword(keyword: String) {
        viewModelScope.launch {
            val updated = _uiState.value.blockKeywords - keyword
            repository.saveBlockKeywords(updated)
            _uiState.update { it.copy(blockKeywords = updated) }
        }
    }

    // ── 板块筛选 ────────────────────────────────────────

    fun toggleFilterNode(nodeId: Long) {
        val current = _uiState.value.filterNodeIds
        val updated = if (nodeId in current) current - nodeId else current + nodeId
        viewModelScope.launch {
            repository.saveFilterNodeIds(updated)
            _uiState.update { it.copy(filterNodeIds = updated) }
        }
    }

    fun clearFilterNodes() {
        viewModelScope.launch {
            repository.saveFilterNodeIds(emptyList())
            _uiState.update { it.copy(filterNodeIds = emptyList()) }
        }
    }

    // ── 核心加载逻辑（多校园 + 客户端过滤） ──────────────────

    private fun selectedIdentities(state: MarketUiState = _uiState.value): List<MarketIdentity> {
        return state.identities
            .filter { it.id in state.selectedIdentityIds }
            .ifEmpty { state.identities.take(1) }
    }

    private fun activeIdentityToken(state: MarketUiState = _uiState.value): String? {
        return selectedIdentities(state).firstOrNull()?.token
            ?: repository.getSavedIdentity()?.takeIf { it.isNotBlank() }
    }

    private fun identityTokenForTopic(
        topicId: Long,
        state: MarketUiState = _uiState.value
    ): String? {
        return state.topicIdentityMap[topicId]
            ?: state.selectedTopicIdentity
            ?: activeIdentityToken(state)
    }

    /**
     * 多校模式：取用户选定的 composeSchoolId 对应 identity 的 token。
     * 单校模式 / composeSchoolId 未设置：回退到第一个选中 identity 的 token。
     */
    private fun resolveComposeToken(state: MarketUiState): String? {
        val schoolId = state.composeSchoolId
        if (schoolId != null) {
            state.identities.firstOrNull { it.id == schoolId }?.let { return it.token }
        }
        return activeIdentityToken(state)
    }

    private suspend fun loadTopicsPage(page: Int, append: Boolean) {
        val state = _uiState.value
        val selectedIdentities = selectedIdentities(state)

        // 兼容：若多身份列表为空，回退旧单身份
        val tokensToFetch = selectedIdentities.map { it.token }.ifEmpty {
            listOfNotNull(repository.getSavedIdentity()?.takeIf { it.isNotBlank() })
        }

        if (tokensToFetch.isEmpty()) {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    isLoadingMore = false,
                    error = "请先添加并选择校园身份"
                )
            }
            return
        }

        try {
            val batch = if (selectedIdentities.size == 1) {
                val identity = selectedIdentities.first()
                val t = repository.getTopics(page, identity.token).getOrThrow()
                val singleSchool = selectedIdentities.firstOrNull()?.school
                MarketTopicBatch(
                    topics = t,
                    topicSchoolMap = if (singleSchool != null) t.associate { it.id to singleSchool } else emptyMap(),
                    topicIdentityMap = t.associate { it.id to identity.token }
                )
            } else if (selectedIdentities.isEmpty()) {
                val token = tokensToFetch.first()
                val t = repository.getTopics(page, token).getOrThrow()
                MarketTopicBatch(
                    topics = t,
                    topicIdentityMap = t.associate { it.id to token }
                )
            } else {
                repository.getTopicsMulti(selectedIdentities, page).getOrThrow()
            }
            val topics = batch.topics
            lastTopicsLoadedAt = System.currentTimeMillis()

            // 客户端过滤
            val nodeIdByName = MarketApi.DEFAULT_NODES.associate { it.name to it.id }
            val filtered = topics.filter { topic ->
                // 屏蔽置顶
                if (state.blockPinned && topic.isTop == 1) return@filter false
                // 板块筛选
                if (state.filterNodeIds.isNotEmpty()) {
                    val topicNodeId = nodeIdByName[topic.node] ?: 0L
                    if (topicNodeId !in state.filterNodeIds) return@filter false
                }
                // 屏蔽词
                if (state.blockKeywords.isNotEmpty()) {
                    state.blockKeywords.none { kw ->
                        topic.title.contains(kw, ignoreCase = true) ||
                            topic.content.contains(kw, ignoreCase = true)
                    }
                } else true
            }
            val merged = if (append) {
                (state.topics + filtered).distinctBy { it.id }
            } else {
                filtered
            }
            _uiState.update { s ->
                s.copy(
                    topics = merged,
                    topicSchoolMap = if (append) {
                        s.topicSchoolMap + batch.topicSchoolMap
                    } else {
                        batch.topicSchoolMap
                    },
                    topicIdentityMap = if (append) {
                        s.topicIdentityMap + batch.topicIdentityMap
                    } else {
                        batch.topicIdentityMap
                    },
                    currentPage = page,
                    hasMoreTopics = topics.isNotEmpty(),
                    isLoading = false,
                    isLoadingMore = false,
                    error = null
                )
            }
        } catch (e: Exception) {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    isLoadingMore = false,
                    error = e.message ?: "集市加载失败"
                )
            }
        }
    }

    fun openTopic(topic: MarketTopic) {
        val identity = identityTokenForTopic(topic.id)
        val requestId = beginTopicRequest()
        _uiState.update {
            it.copy(
                selectedTopic = topic,
                selectedTopicIdentity = identity,
                topicDetail = topic,
                detailLoading = true,
                detailError = null,
                comments = emptyList(),
                commentsPage = 0,
                hasMoreComments = true,
                commentsLoading = true,
                replyLoadingCommentIds = emptySet(),
                replyErrors = emptyMap(),
                commentsError = null,
                commentDraft = "",
                replyingTo = null,
                isPostingComment = false,
                isGeneratingAiComment = false,
                postCommentError = null,
                postCommentSuccessMessage = null,
            )
        }
        topicJob = viewModelScope.launch {
            loadTopicDetail(topic.id, identity, requestId)
            loadCommentsPage(
                topicId = topic.id,
                page = 1,
                append = false,
                identity = identity,
                requestId = requestId,
            )
        }
    }

    fun openHotTopics() {
        _uiState.update { it.copy(showHotTopics = true, hotError = null) }
        if (_uiState.value.hotTopics.isEmpty()) refreshHotTopics()
    }

    fun closeHotTopics() {
        _uiState.update { it.copy(showHotTopics = false) }
    }

    fun openNotices() {
        if (!_uiState.value.hasSavedIdentity) {
            _uiState.update { it.copy(identityError = "请先填写集市 API 身份字段") }
            return
        }
        _uiState.update {
            it.copy(
                showNotices = true,
                noticesError = null
            )
        }
        if (_uiState.value.notices.isEmpty()) refreshNotices()
    }

    fun closeNotices() {
        _uiState.update { it.copy(showNotices = false) }
    }

    fun refreshNotices() {
        if (!_uiState.value.hasSavedIdentity) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    notices = emptyList(),
                    noticesPage = 0,
                    hasMoreNotices = true,
                    noticesLoading = true,
                    noticesError = null
                )
            }
            loadNoticesPage(page = 1, append = false, identity = activeIdentityToken())
        }
    }

    fun loadMoreNotices() {
        val state = _uiState.value
        if (state.noticesLoadingMore || state.noticesLoading || !state.hasMoreNotices) return
        viewModelScope.launch {
            _uiState.update { it.copy(noticesLoadingMore = true, noticesError = null) }
            loadNoticesPage(
                page = state.noticesPage + 1,
                append = true,
                identity = activeIdentityToken(state)
            )
        }
    }

    private suspend fun loadNoticesPage(page: Int, append: Boolean, identity: String?) {
        repository.getNotices(page, identity).fold(
            onSuccess = { noticePage ->
                _uiState.update { state ->
                    val merged = if (append) {
                        (state.notices + noticePage.rows).distinctBy { it.id }
                    } else {
                        noticePage.rows
                    }
                    state.copy(
                        notices = merged,
                        noticesPage = noticePage.page,
                        noticesCount = noticePage.count,
                        hasMoreNotices = noticePage.rows.isNotEmpty(),
                        noticesLoading = false,
                        noticesLoadingMore = false,
                        noticesError = null
                    )
                }
            },
            onFailure = { e ->
                _uiState.update {
                    it.copy(
                        noticesLoading = false,
                        noticesLoadingMore = false,
                        noticesError = e.message ?: "消息加载失败"
                    )
                }
            }
        )
    }

    fun refreshHotTopics() {
        if (!_uiState.value.hasSavedIdentity) return
        viewModelScope.launch {
            _uiState.update { it.copy(hotLoading = true, hotError = null) }
            val identities = selectedIdentities()
            val result = if (identities.size == 1) {
                val identity = identities.first()
                repository.getTopTopics(identity.token).map { topics ->
                    MarketTopicBatch(
                        topics = topics,
                        topicSchoolMap = identity.school?.let { school ->
                            topics.associate { it.id to school }
                        } ?: emptyMap(),
                        topicIdentityMap = topics.associate { it.id to identity.token }
                    )
                }
            } else {
                repository.getTopTopicsMulti(identities)
            }
            result.fold(
                onSuccess = { batch ->
                    _uiState.update {
                        it.copy(
                            hotTopics = batch.topics,
                            hotLoading = false,
                            hotError = null,
                            topicSchoolMap = it.topicSchoolMap + batch.topicSchoolMap,
                            topicIdentityMap = it.topicIdentityMap + batch.topicIdentityMap
                        )
                    }
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(
                            hotLoading = false,
                            hotError = e.message ?: "热榜加载失败"
                        )
                    }
                }
            )
        }
    }

    fun closeTopic() {
        beginTopicRequest()
        _uiState.update {
            it.copy(
                selectedTopic = null,
                selectedTopicIdentity = null,
                topicDetail = null,
                detailLoading = false,
                detailError = null,
                comments = emptyList(),
                commentsPage = 0,
                hasMoreComments = true,
                    commentsLoading = false,
                    commentsLoadingMore = false,
                    replyLoadingCommentIds = emptySet(),
                    replyErrors = emptyMap(),
                    commentsError = null,
                    commentDraft = "",
                    replyingTo = null,
                    isPostingComment = false,
                    isGeneratingAiComment = false,
                    postCommentError = null,
                    postCommentSuccessMessage = null
                )
            }
    }

    // ── 发帖编辑器 ────────────────────────────────────────

    fun openCompose() {
        if (!_uiState.value.hasSavedIdentity) {
            _uiState.update { it.copy(identityError = "请先填写集市 API 身份字段") }
            return
        }
        val nodes = MarketApi.DEFAULT_NODES
        val current = _uiState.value
        val preferredNode = if (current.composeNodeId > 0L) current.composeNodeId
            else MarketApi.DEFAULT_NODE_ID
        // 多校模式下,默认选「最近浏览过的帖子所属 identity」,否则选第一个选中 identity。
        val defaultSchoolId = current.selectedTopicIdentity
            ?.let { token -> current.identities.firstOrNull { it.token == token }?.id }
            ?: current.selectedIdentityIds.firstOrNull()
            ?: current.identities.firstOrNull()?.id
        _uiState.update {
            it.copy(
                showCompose = true,
                composeNodes = nodes,
                composeNodeId = preferredNode,
                composeTitle = "",
                composeContent = "",
                composeIsAnon = false,
                composeNodeMenuOpen = false,
                composeSchoolId = defaultSchoolId,
                postError = null,
                postSuccessMessage = null,
                postedTopicId = null
            )
        }
    }

    fun setComposeSchoolId(identityId: String) {
        _uiState.update { it.copy(composeSchoolId = identityId) }
    }

    fun closeCompose() {
        _uiState.update {
            it.copy(
                showCompose = false,
                composeNodeMenuOpen = false,
                isPosting = false,
                postError = null
            )
        }
    }

    fun onComposeTitleChanged(value: String) {
        _uiState.update { it.copy(composeTitle = value, postError = null) }
    }

    fun onComposeContentChanged(value: String) {
        _uiState.update { it.copy(composeContent = value, postError = null) }
    }

    fun onComposeAnonChanged(value: Boolean) {
        _uiState.update { it.copy(composeIsAnon = value) }
    }

    fun onComposeNodeSelected(nodeId: Long) {
        _uiState.update {
            it.copy(composeNodeId = nodeId, composeNodeMenuOpen = false)
        }
    }

    fun onComposeNodeMenuToggle(open: Boolean) {
        _uiState.update { it.copy(composeNodeMenuOpen = open) }
    }

    fun dismissPostSuccessMessage() {
        _uiState.update { it.copy(postSuccessMessage = null) }
    }

    fun submitPost() {
        val state = _uiState.value
        if (state.isPosting) return
        val content = state.composeContent.trim()
        if (content.isBlank()) {
            _uiState.update { it.copy(postError = "请填写正文内容") }
            return
        }
        if (state.composeNodeId <= 0L) {
            _uiState.update { it.copy(postError = "请选择板块") }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isPosting = true,
                    postError = null,
                    postSuccessMessage = null
                )
            }
            repository.createTopic(
                title = state.composeTitle.trim(),
                content = content,
                nodeId = state.composeNodeId,
                isAnon = state.composeIsAnon,
                identity = resolveComposeToken(state)
            ).fold(
                onSuccess = { newId ->
                    _uiState.update {
                        it.copy(
                            isPosting = false,
                            showCompose = false,
                            composeNodeMenuOpen = false,
                            postSuccessMessage = "发布成功（#$newId）",
                            postedTopicId = newId
                        )
                    }
                    refreshTopics()
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(
                            isPosting = false,
                            postError = e.message ?: "发布失败"
                        )
                    }
                }
            )
        }
    }

    // ── 评论 / 回复 ─────────────────────────────────────

    fun onCommentDraftChanged(value: String) {
        _uiState.update { it.copy(commentDraft = value, postCommentError = null) }
    }

    /** 点击顶级评论的"回复"：作为新的顶层评论发送。 */
    fun startReplyingToComment(comment: MarketComment) {
        val nickname = comment.userInfo?.nickname?.takeIf { it.isNotBlank() } ?: "匿名同学"
        _uiState.update {
            it.copy(
                replyingTo = ReplyTarget(
                    commentId = comment.id,
                    replyId = 0L,
                    targetUserId = comment.userInfo?.uuid ?: 0L,
                    displayName = nickname,
                    preview = comment.content.take(30)
                ),
                postCommentError = null
            )
        }
    }

    /** 点击楼中楼 reply 的"回复"：作为嵌套 reply 发送（带 reply_id）。 */
    fun startReplyingToReply(parent: MarketComment, reply: MarketComment) {
        val nickname = reply.userInfo?.nickname?.takeIf { it.isNotBlank() }
            ?: reply.pickUserInfo?.nickname?.takeIf { it.isNotBlank() }
            ?: "同学"
        _uiState.update {
            it.copy(
                replyingTo = ReplyTarget(
                    commentId = parent.id,
                    replyId = reply.id,
                    targetUserId = reply.userInfo?.uuid ?: 0L,
                    displayName = nickname,
                    preview = reply.content.take(30)
                ),
                postCommentError = null
            )
        }
    }

    fun cancelReply() {
        _uiState.update {
            it.copy(replyingTo = null, postCommentError = null)
        }
    }

    fun dismissPostCommentSuccessMessage() {
        _uiState.update { it.copy(postCommentSuccessMessage = null) }
    }

    fun generateAiComment(template: AiCommentTemplate) {
        val state = _uiState.value
        if (state.isGeneratingAiComment) return
        if (!state.aiCommentEnabled) {
            _uiState.update { it.copy(postCommentError = "请先在“我的 → 设置”中启用 AI 评论") }
            return
        }
        val topic = state.topicDetail ?: state.selectedTopic ?: return
        val target = state.replyingTo
        val requestId = topicRequestId

        viewModelScope.launch {
            if (!isCurrentTopicRequest(requestId, topic.id)) return@launch
            _uiState.update { current ->
                if (!isCurrentTopicRequest(requestId, topic.id)) return@update current
                current.copy(isGeneratingAiComment = true, postCommentError = null)
            }
            val fullComments = selectAiContextComments(
                fullResult = repository.loadAllCommentsWithReplies(
                    topicId = topic.id,
                    identity = identityTokenForTopic(topic.id, state)
                ),
                loadedComments = state.comments
            )
            if (!isCurrentTopicRequest(requestId, topic.id)) return@launch
            val targetComment = target?.let { replyTarget ->
                fullComments.firstOrNull { it.id == replyTarget.commentId }
            }
            val targetReplyId = target?.replyId ?: 0L
            val targetReply = if (targetReplyId > 0L) {
                targetComment?.visibleReplies?.firstOrNull { it.id == targetReplyId }
            } else null
            aiCommentRepository.generateComment(
                topic = topic,
                comments = fullComments,
                targetComment = targetComment,
                targetReply = targetReply,
                template = template,
                model = state.aiCommentModel
            ).fold(
                onSuccess = { draft ->
                    _uiState.update { current ->
                        if (!isCurrentTopicRequest(requestId, topic.id)) return@update current
                        current.copy(isGeneratingAiComment = false, commentDraft = draft, postCommentError = null)
                    }
                },
                onFailure = { error ->
                    _uiState.update { current ->
                        if (!isCurrentTopicRequest(requestId, topic.id)) return@update current
                        current.copy(
                            isGeneratingAiComment = false,
                            postCommentError = error.message ?: "AI 评论生成失败"
                        )
                    }
                }
            )
        }
    }

    fun submitComment() {
        val state = _uiState.value
        if (state.isPostingComment) return
        val topicId = state.selectedTopic?.id ?: run {
            _uiState.update { it.copy(postCommentError = "请先打开一个帖子") }
            return
        }
        val content = state.commentDraft.trim()
        if (content.isBlank()) {
            _uiState.update { it.copy(postCommentError = "请输入评论内容") }
            return
        }
        val target = state.replyingTo
        val requestId = topicRequestId

        viewModelScope.launch {
            if (!isCurrentTopicRequest(requestId, topicId)) return@launch
            _uiState.update { current ->
                if (!isCurrentTopicRequest(requestId, topicId)) return@update current
                current.copy(
                    isPostingComment = true,
                    postCommentError = null,
                    postCommentSuccessMessage = null
                )
            }
            repository.createComment(
                topicId = topicId,
                content = content,
                commentId = target?.commentId ?: 0L,
                replyId = target?.replyId ?: 0L,
                targetUserId = target?.targetUserId ?: 0L,
                identity = identityTokenForTopic(topicId, state)
            ).fold(
                onSuccess = { newComment ->
                    _uiState.update { current ->
                        if (!isCurrentTopicRequest(requestId, topicId)) return@update current
                        val updatedComments = if (target == null || target.replyId == 0L) {
                            // 顶级评论：插到列表头部
                            (listOf(newComment) + current.comments).distinctBy { it.id }
                        } else {
                            // 楼中楼：插到父评论的 replies 头部
                            current.comments.map { parent ->
                                if (parent.id == target.commentId) {
                                    parent.copy(
                                        replies = (listOf(newComment) + parent.replies)
                                            .distinctBy { it.id }
                                    )
                                } else {
                                    parent
                                }
                            }
                        }
                        current.copy(
                            isPostingComment = false,
                            commentDraft = "",
                            replyingTo = null,
                            comments = updatedComments,
                            hasMoreComments = current.hasMoreComments || current.comments.isEmpty(),
                            commentsError = null,
                            postCommentSuccessMessage = "评论已发布"
                        )
                    }
                },
                onFailure = { e ->
                    _uiState.update { current ->
                        if (!isCurrentTopicRequest(requestId, topicId)) return@update current
                        current.copy(
                            isPostingComment = false,
                            postCommentError = e.message ?: "评论失败"
                        )
                    }
                }
            )
        }
    }

    fun retryDetail() {
        val topicId = _uiState.value.selectedTopic?.id ?: return
        val identity = identityTokenForTopic(topicId)
        val shouldLoadComments = _uiState.value.comments.isEmpty()
        val requestId = beginTopicRequest()
        _uiState.update {
            it.copy(
                detailLoading = true,
                detailError = null,
                commentsLoading = shouldLoadComments,
                commentsError = null,
                replyLoadingCommentIds = emptySet(),
            )
        }
        topicJob = viewModelScope.launch {
            loadTopicDetail(topicId, identity, requestId)
            if (shouldLoadComments) {
                loadCommentsPage(
                    topicId = topicId,
                    page = 1,
                    append = false,
                    identity = identity,
                    requestId = requestId,
                )
            }
        }
    }

    /**
     * 拉取一个 topic 的全部评论（含楼中楼回复），用于导出帖子图片。
     * 调用方负责把结果传给导出工具。
     */
    suspend fun loadFullCommentsForExport(
        topicId: Long,
        identity: String?
    ): Result<List<MarketComment>> {
        return repository.loadAllCommentsWithReplies(topicId = topicId, identity = identity)
    }

    private suspend fun loadTopicDetail(topicId: Long, identity: String?, requestId: Long) {
        repository.getTopic(topicId, identity).fold(
            onSuccess = { detail ->
                _uiState.update { state ->
                    if (!isCurrentTopicRequest(requestId, topicId)) return@update state
                    state.copy(topicDetail = detail, detailLoading = false, detailError = null)
                }
            },
            onFailure = { e ->
                _uiState.update { state ->
                    if (!isCurrentTopicRequest(requestId, topicId)) return@update state
                    state.copy(
                        detailLoading = false,
                        detailError = if (state.topicDetail == null) {
                            e.message ?: "帖子详情加载失败"
                        } else {
                            null
                        }
                    )
                }
            }
        )
    }

    fun loadMoreComments() {
        val state = _uiState.value
        val topicId = state.selectedTopic?.id ?: return
        if (state.commentsLoading || state.commentsLoadingMore || !state.hasMoreComments) return

        val requestId = topicRequestId
        topicJob = viewModelScope.launch {
            if (!isCurrentTopicRequest(requestId, topicId)) return@launch
            _uiState.update { current ->
                if (isCurrentTopicRequest(requestId, topicId)) {
                    current.copy(commentsLoadingMore = true, commentsError = null)
                } else {
                    current
                }
            }
            loadCommentsPage(
                topicId = topicId,
                page = state.commentsPage + 1,
                append = true,
                identity = identityTokenForTopic(topicId, state),
                requestId = requestId,
            )
        }
    }

    fun loadMoreReplies(comment: MarketComment) {
        val state = _uiState.value
        val topicId = state.selectedTopic?.id ?: return
        if (state.replyLoadingCommentIds.contains(comment.id)) return

        val pageSize = comment.replys?.pageSize?.takeIf { it > 0 } ?: 6
        val nextPage = (comment.visibleReplies.size / pageSize) + 1
        val requestId = topicRequestId

        viewModelScope.launch {
            if (!isCurrentTopicRequest(requestId, topicId)) return@launch
            _uiState.update { current ->
                if (!isCurrentTopicRequest(requestId, topicId)) return@update current
                current.copy(
                    replyLoadingCommentIds = current.replyLoadingCommentIds + comment.id,
                    replyErrors = current.replyErrors - comment.id
                )
            }
            repository.getCommentReplies(
                topicId = topicId,
                commentId = comment.id,
                page = nextPage,
                pageSize = pageSize,
                identity = identityTokenForTopic(topicId, state)
            ).fold(
                onSuccess = { replyPage ->
                    _uiState.update { current ->
                        if (!isCurrentTopicRequest(requestId, topicId)) return@update current
                        current.copy(
                            comments = current.comments.map { item ->
                                if (item.id == comment.id) item.mergeReplies(replyPage) else item
                            },
                            replyLoadingCommentIds = current.replyLoadingCommentIds - comment.id,
                            replyErrors = current.replyErrors - comment.id
                        )
                    }
                },
                onFailure = { e ->
                    _uiState.update { current ->
                        if (!isCurrentTopicRequest(requestId, topicId)) return@update current
                        current.copy(
                            replyLoadingCommentIds = current.replyLoadingCommentIds - comment.id,
                            replyErrors = current.replyErrors + (comment.id to (e.message ?: "回复加载失败"))
                        )
                    }
                }
            )
        }
    }

    private suspend fun loadCommentsPage(
        topicId: Long,
        page: Int,
        append: Boolean,
        identity: String?,
        requestId: Long,
    ) {
        repository.getComments(topicId = topicId, page = page, identity = identity).fold(
            onSuccess = { comments ->
                _uiState.update { state ->
                    if (!isCurrentTopicRequest(requestId, topicId)) return@update state
                    val merged = if (append) {
                        (state.comments + comments).distinctBy { it.id }
                    } else {
                        comments
                    }
                    state.copy(
                        comments = merged,
                        commentsPage = page,
                        hasMoreComments = comments.isNotEmpty(),
                        commentsLoading = false,
                        commentsLoadingMore = false,
                        commentsError = null
                    )
                }
            },
            onFailure = { e ->
                _uiState.update { state ->
                    if (!isCurrentTopicRequest(requestId, topicId)) return@update state
                    state.copy(
                        commentsLoading = false,
                        commentsLoadingMore = false,
                        commentsError = e.message ?: "评论加载失败"
                    )
                }
            }
        )
    }
}

data class MarketUiState(
    val identityInput: String = "",
    val hasSavedIdentity: Boolean = false,
    val school: String? = null,
    val saveMessage: String? = null,
    val identityError: String? = null,
    val topics: List<MarketTopic> = emptyList(),
    val topicSchoolMap: Map<Long, String> = emptyMap(),
    val topicIdentityMap: Map<Long, String> = emptyMap(),
    val currentPage: Int = 0,
    val hasMoreTopics: Boolean = true,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
    val showHotTopics: Boolean = false,
    val hotTopics: List<MarketTopic> = emptyList(),
    val hotLoading: Boolean = false,
    val hotError: String? = null,
    val selectedTopic: MarketTopic? = null,
    val selectedTopicIdentity: String? = null,
    val topicDetail: MarketTopic? = null,
    val detailLoading: Boolean = false,
    val detailError: String? = null,
    val comments: List<MarketComment> = emptyList(),
    val commentsPage: Int = 0,
    val hasMoreComments: Boolean = true,
    val commentsLoading: Boolean = false,
    val commentsLoadingMore: Boolean = false,
    val replyLoadingCommentIds: Set<Long> = emptySet(),
    val replyErrors: Map<Long, String> = emptyMap(),
    val commentsError: String? = null,
    // ── 发帖编辑器 ─────────────────────────────────
    val showCompose: Boolean = false,
    val composeNodes: List<MarketNode> = emptyList(),
    val composeNodeId: Long = 0L,
    val composeTitle: String = "",
    val composeContent: String = "",
    val composeIsAnon: Boolean = false,
    val composeNodeMenuOpen: Boolean = false,
    val isPosting: Boolean = false,
    val postError: String? = null,
    val postSuccessMessage: String? = null,
    val postedTopicId: Long? = null,
    // ── 评论/回复输入 ──────────────────────────────
    val commentDraft: String = "",
    val replyingTo: ReplyTarget? = null,
    val isPostingComment: Boolean = false,
    val postCommentError: String? = null,
    val postCommentSuccessMessage: String? = null,
    val aiCommentEnabled: Boolean = false,
    val aiCommentModel: AiCommentModel = AiCommentModel.FLASH,
    val aiCommentStyle: AiCommentStyle = AiCommentStyle.GENTLE,
    val aiOverallPrompt: String = "",
    val aiStylePrompts: Map<AiCommentStyle, String> = emptyMap(),
    val aiTemplates: List<AiCommentTemplate> = emptyList(),
    val aiSelectedTemplateId: String = AiCommentStyle.GENTLE.name,
    val aiApiKeyConfigured: Boolean = false,
    val isGeneratingAiComment: Boolean = false,
    // ── 消息/通知列表 ──────────────────────────────
    val showNotices: Boolean = false,
    val notices: List<MarketNotice> = emptyList(),
    val noticesCount: Int = 0,
    val noticesPage: Int = 0,
    val hasMoreNotices: Boolean = true,
    val noticesLoading: Boolean = false,
    val noticesLoadingMore: Boolean = false,
    val noticesError: String? = null,
    // ── 集市设置 ──────────────────────────────────
    val showSettings: Boolean = false,
    val identities: List<MarketIdentity> = emptyList(),
    val selectedIdentityIds: Set<String> = emptySet(),
    val blockPinned: Boolean = false,
    val blockKeywords: List<String> = emptyList(),
    val keywordInput: String = "",
    val filterNodeIds: List<Long> = emptyList(),
    val marketEnabled: Boolean = true,
    // 第三方服务:parent 总开关 + 两个子开关 (集市 / 学习通)
    val thirdPartyServicesEnabled: Boolean = false,
    val marketChildEnabled: Boolean = false,
    val chaoxingChildEnabled: Boolean = false,
    val welearnChildEnabled: Boolean = false,
    // ── 搜索 ─────────────────────────────────────
    val isSearching: Boolean = false,
    val searchQuery: String = "",
    val searchResults: List<MarketTopic> = emptyList(),
    val searchPage: Int = 0,
    val hasMoreSearch: Boolean = true,
    val searchLoading: Boolean = false,
    val searchLoadingMore: Boolean = false,
    val searchError: String? = null,
    // ── 多校发帖路由 ─────────────────────────────
    // 多校模式下,发新帖用哪个 identity.token。
    // 单校模式忽略此字段(用唯一那个 identity)。
    val composeSchoolId: String? = null,
    // ── 列表布局模式 ──────────────────────────────
    // "list" 单列 / "stagger" 小红书双列瀑布
    val listLayoutMode: String = "list",
    // ── "回到顶部"按钮 ──────────────────────────────
    val scrollToTopEnabled: Boolean = true
)

/**
 * 回复目标：既支持顶级评论（replyId=0），也支持楼中楼（replyId>0）。
 *
 * @param commentId    顶级评论 id（请求体里的 comment_id）
 * @param replyId      楼中楼目标 reply 的 id（0 = 回复顶级评论本身）
 * @param targetUserId 被回复人的 user uuid（0 表示匿名）
 * @param displayName  显示在输入框上方的"@昵称"
 * @param preview      被回复内容的预览（暂时 UI 未展示，预留）
 */
data class ReplyTarget(
    val commentId: Long,
    val replyId: Long = 0L,
    val targetUserId: Long = 0L,
    val displayName: String = "",
    val preview: String = ""
)

private fun MarketComment.mergeReplies(replyPage: MarketCommentReplies): MarketComment {
    val existing = visibleReplies
    val merged = (existing + replyPage.rows).distinctBy { it.id }
    val oldCount = visibleReplyCount
    val newCount = maxOf(oldCount, replyPage.count, merged.size)
    val pageSize = replyPage.pageSize.takeIf { it > 0 }
        ?: replys?.pageSize?.takeIf { it > 0 }
        ?: 6

    return copy(
        replyCount = newCount,
        replys = MarketCommentReplies(
            count = newCount,
            page = replyPage.page,
            rows = merged,
            loading = false,
            pageSize = pageSize
        )
    )
}

internal fun selectAiContextComments(
    fullResult: Result<List<MarketComment>>,
    loadedComments: List<MarketComment>
): List<MarketComment> {
    fullResult.exceptionOrNull()?.let { error ->
        if (error is kotlinx.coroutines.CancellationException) throw error
    }
    return fullResult.getOrElse { loadedComments }
}
