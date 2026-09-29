package com.aurora.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aurora.core.ai.ChatMessage
import com.aurora.core.ai.FakeLocalProvider
import com.aurora.core.ai.GenerationRequest
import com.aurora.core.domain.model.AuroraId
import com.aurora.core.domain.model.Message
import com.aurora.core.domain.model.MessageRole
import com.aurora.core.domain.model.MessageStatus
import com.aurora.core.domain.model.RunOutcome
import com.aurora.core.domain.port.ConversationRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Offline chat. Uses the same AgentRuntime that future providers, agents and
 * tools will use — no UI shortcut path to the model.
 */
class ChatViewModel(
    private val conversations: ConversationRepository,
    private val runtime: com.aurora.core.agent.AgentRuntime,
    private val provider: com.aurora.core.ai.AIProvider,
    private val assistant: com.aurora.core.domain.model.AgentDefinition,
) : ViewModel() {

    data class UiState(
        val messages: List<Message> = emptyList(),
        val busy: Boolean = false,
        val statusLine: String = "Offline · local model: ${FakeLocalProvider.MODEL.modelId}",
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var conversationId: String? = null
    private var conversationJob: Job? = null

    fun send(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || _state.value.busy) return
        _state.value = _state.value.copy(busy = true)
        viewModelScope.launch {
            try {
                // conversationId is a String id, not a Conversation: a plain
                // elvis between String and Conversation would type the result
                // as their common supertype and break member access. Resolve
                // the id to a Conversation first, recreating when it is gone.
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

                val request = GenerationRequest(
                    requestId = AuroraId.generate().value,
                    messages = listOf(
                        ChatMessage(MessageRole.SYSTEM, assistant.systemPrompt),
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
            } finally {
                _state.value = _state.value.copy(busy = false)
            }
        }
    }

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
}
