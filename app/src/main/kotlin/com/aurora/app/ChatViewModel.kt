package com.aurora.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aurora.core.ai.ChatMessage
import com.aurora.core.ai.GenerationRequest
import com.aurora.core.ai.RoutedProvider
import com.aurora.core.ai.UsageLedger
import com.aurora.core.domain.model.AuroraId
import com.aurora.core.domain.model.Message
import com.aurora.core.domain.model.MessageRole
import com.aurora.core.domain.model.MessageStatus
import com.aurora.core.domain.model.RunOutcome
import com.aurora.core.domain.port.ConversationRepository
import com.aurora.core.domain.port.MemoryRepository
import com.aurora.core.domain.service.MemoryRetrieval
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Offline chat. Uses the same AgentRuntime that future providers, agents and
 * tools will use — no UI shortcut path to the model. Memories enter the
 * prompt only through the explicit MemoryRetrieval layer (PRD-02), and the
 * status line shows provider/model/usage/fallback from the routing layer
 * (PRD-03 display requirements).
 */
class ChatViewModel(
    private val conversations: ConversationRepository,
    private val memories: MemoryRepository,
    private val runtime: com.aurora.core.agent.AgentRuntime,
    private val provider: com.aurora.core.ai.AIProvider,
    private val assistant: com.aurora.core.domain.model.AgentDefinition,
    private val ledger: UsageLedger,
    private val routing: RoutedProvider,
) : ViewModel() {

    data class UiState(
        val messages: List<Message> = emptyList(),
        val busy: Boolean = false,
        val memoryCount: Int = 0,
        val statusLine: String = "",
    )

    private val _state = MutableStateFlow(UiState(statusLine = initialStatusLine()))
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var conversationId: String? = null
    private var conversationJob: Job? = null

    init {
        // Bounded, reactive memory count (PRD-02).
        viewModelScope.launch {
            memories.observeMemories(MEMORY_NAMESPACE, limit = MEMORY_LIMIT).collect { latest ->
                _state.value = _state.value.copy(memoryCount = latest.size)
            }
        }
        // Provider/model/fallback reason after each request (PRD-03).
        viewModelScope.launch {
            routing.lastRouting.collect { refreshStatusLine() }
        }
    }

    fun send(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || _state.value.busy) return
        _state.value = _state.value.copy(busy = true)
        viewModelScope.launch {
            try {
                val conversation = conversationId
                    ?.let { conversations.getConversation(it) }
                    ?: conversations.createConversation(title = trimmed.take(40)).also {
                        conversationId = it.id
                        observeConversation(it.id)
                    }

                val userMessage = newMessage(
                    conversation.id, MessageRole.USER, trimmed,
                    status = MessageStatus.SENT,
                )
                conversations.appendMessage(userMessage)

                val memoryContext = MemoryRetrieval.buildContext(
                    memories.retrieveMemories(MEMORY_NAMESPACE, limit = MEMORY_LIMIT),
                    maxChars = MEMORY_CONTEXT_MAX_CHARS,
                )
                val systemPrompt = if (memoryContext.isEmpty()) {
                    assistant.systemPrompt
                } else {
                    assistant.systemPrompt + "\n\nKnown memories about the user:\n" + memoryContext
                }

                val request = GenerationRequest(
                    requestId = AuroraId.generate().value,
                    messages = listOf(
                        ChatMessage(MessageRole.SYSTEM, systemPrompt),
                        ChatMessage(MessageRole.USER, trimmed),
                    ),
                    model = provider.listModels().first(),
                )

                val outcome = runtime.run(assistant, request)
                val reply = when (outcome) {
                    is RunOutcome.Success -> outcome.text
                    is RunOutcome.Failure -> "요청을 완료하지 못했습니다 (${outcome.state}): ${outcome.reason}"
                }

                conversations.appendMessage(
                    newMessage(
                        conversation.id,
                        MessageRole.ASSISTANT,
                        reply,
                        status = if (outcome is RunOutcome.Success) MessageStatus.SENT else MessageStatus.FAILED,
                        providerId = assistant.providerId,
                        modelId = assistant.modelId,
                    )
                )
                refreshStatusLine()
            } finally {
                _state.value = _state.value.copy(busy = false)
            }
        }
    }

    /**
     * PRD-03 display: provider, model, local/API status, request count,
     * token usage, and estimated cost — always marked as an estimate.
     */
    private fun refreshStatusLine() {
        val routed = routing.lastRouting.value
        val totals = ledger.totalsSince(since = 0)
        _state.value = _state.value.copy(
            statusLine = buildString {
                if (routed == null) {
                    append("Provider: ${provider.providerId} · 대기 중")
                } else {
                    append("Provider: ${routed.providerId} · ${routed.modelId}")
                    if (routed.fallbackUsed) {
                        append(" · 로컬 폴백 (${routed.fallbackReason})")
                    }
                }
                append(" · 요청 ${totals.requests}회")
                if (totals.requests > 0) {
                    append(" · 토큰 ${totals.inputTokens}/${totals.outputTokens}")
                    if (totals.estimatedCostMicros > 0) {
                        append(" · 추정 비용 ${totals.estimatedCostMicros}µUSD")
                    }
                    if (!totals.allCostsAreEstimates) {
                        append(" (일부 실측)")
                    }
                }
            },
        )
    }

    private fun initialStatusLine(): String = "Provider: ${provider.providerId} · 대기 중"

    private fun newMessage(
        conversationId: String,
        role: MessageRole,
        content: String,
        status: MessageStatus,
        providerId: String? = null,
        modelId: String? = null,
    ) = Message(
        id = AuroraId.generate().value,
        conversationId = conversationId,
        role = role,
        content = content,
        timestamp = System.currentTimeMillis(),
        status = status,
        providerId = providerId,
        modelId = modelId,
    )

    private fun observeConversation(conversationId: String) {
        conversationJob?.cancel()
        conversationJob = viewModelScope.launch {
            conversations.observeMessages(conversationId, limit = 200).collect { messages ->
                _state.value = _state.value.copy(messages = messages)
            }
        }
    }

    companion object {
        private const val MEMORY_NAMESPACE = "profile"
        private const val MEMORY_LIMIT = 20
        private const val MEMORY_CONTEXT_MAX_CHARS = 2_000
    }
}
