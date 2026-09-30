package com.aurora.app

import android.content.Context
import androidx.room.Room
import com.aurora.core.agent.AgentRuntime
import com.aurora.core.ai.AIProvider
import com.aurora.core.ai.FakeLocalProvider
import com.aurora.core.data.db.AuroraDatabase
import com.aurora.core.data.repository.ConversationRepositoryImpl
import com.aurora.core.data.repository.MemoryRepositoryImpl
import com.aurora.core.domain.model.AgentDefinition
import com.aurora.core.domain.model.ApprovalPolicy
import com.aurora.core.domain.model.FileScope
import com.aurora.core.domain.model.NetworkPolicy
import com.aurora.core.domain.port.ConversationRepository
import com.aurora.core.domain.port.MemoryRepository
import com.aurora.core.security.DefaultPolicyEngine
import com.aurora.core.tool.FakeEchoTool
import com.aurora.core.tool.ToolRegistry

/**
 * Manual composition root. No DI framework yet — dependencies are few and the
 * graph is intentionally explicit. Everything here works fully offline.
 */
object AuroraCompositionRoot {

    fun create(context: Context): AuroraDependencies {
        val database = Room.databaseBuilder(
            context.applicationContext,
            AuroraDatabase::class.java,
            AuroraDatabase.NAME,
        )
            // PRD-02: every schema change ships a real migration; destructive
            // migrations are forbidden.
            .addMigrations(AuroraDatabase.MIGRATION_1_2)
            .build()

        val conversationRepository: ConversationRepository =
            ConversationRepositoryImpl(database.conversationDao())

        val memoryRepository: MemoryRepository =
            MemoryRepositoryImpl(database.memoryDao())

        val provider: AIProvider = FakeLocalProvider()

        val toolRegistry = ToolRegistry().apply {
            // Foundation-phase tool. Real tools arrive in PRD-04.
            register(FakeEchoTool().tool)
        }

        // Until the approval UI exists (PRD-05), approval-required tool calls
        // fail closed: the gate always answers "not approved".
        val failClosedApprovalGate = com.aurora.core.agent.ApprovalGate { _, _ -> false }

        val runtime = AgentRuntime(
            provider = provider,
            toolRegistry = toolRegistry,
            policyEngine = DefaultPolicyEngine(),
            approvalGate = failClosedApprovalGate,
        )

        val assistantAgent = AgentDefinition(
            id = "aurora-assistant",
            name = "Aurora",
            systemPrompt = "You are Aurora, an offline-first personal assistant.",
            providerId = provider.providerId,
            modelId = FakeLocalProvider.MODEL.modelId,
            allowedTools = setOf("fake.echo"),
            maxToolCalls = 5,
            maxExecutionDepth = 2,
            maxRuntimeMs = 30_000,
            networkPolicy = NetworkPolicy.LOCAL_ONLY,
            fileScope = FileScope.NONE,
            approvalPolicy = ApprovalPolicy.PER_TOOL_DEFAULT,
            enabled = true,
        )

        return AuroraDependencies(conversationRepository, memoryRepository, runtime, provider, assistantAgent)
    }
}

data class AuroraDependencies(
    val conversations: ConversationRepository,
    val memories: MemoryRepository,
    val runtime: AgentRuntime,
    val provider: AIProvider,
    val assistant: AgentDefinition,
)
