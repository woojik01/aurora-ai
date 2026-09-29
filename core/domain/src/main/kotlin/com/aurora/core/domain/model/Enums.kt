package com.aurora.core.domain.model

/** Lifecycle of an asynchronous operation (PRD-01 "Error handling"). */
enum class TaskStatus { QUEUED, RUNNING, SUCCEEDED, FAILED, CANCELLED, TIMED_OUT, DENIED, AWAITING_APPROVAL }

enum class MessageStatus { PENDING, SENDING, SENT, FAILED, CANCELLED }

enum class MessageRole { SYSTEM, USER, ASSISTANT, TOOL }

/** Risk classification used by the policy engine. FINANCIAL is deliberately distinct: it always requires fresh approval. */
enum class RiskLevel { LOW, MEDIUM, HIGH, FINANCIAL }

enum class DataSensitivity { PUBLIC, PERSONAL, SENSITIVE, SECRET }

enum class SideEffectClass { NONE, MUTATES_LOCAL_STATE, SENDS_DATA, DESTRUCTIVE, FINANCIAL }

enum class ToolParamType { STRING, INTEGER, BOOLEAN }

enum class NetworkPolicy { LOCAL_ONLY, ALLOW_NETWORK }

/** Root directories a file tool may touch. Empty means no file access at all. */
enum class FileScope(val roots: List<String>) {
    NONE(emptyList()),
    APP_PRIVATE(listOf("app-private")),
}

enum class ApprovalPolicy { PER_TOOL_DEFAULT, ALWAYS_REQUIRE }

/** States persisted for every agent run so the app can recover after process death (PRD-04). */
enum class ExecutionState { QUEUED, PLANNING, AWAITING_APPROVAL, RUNNING, TOOL_CALL, VALIDATING, SUCCEEDED, FAILED, CANCELLED, TIMED_OUT, DENIED }
