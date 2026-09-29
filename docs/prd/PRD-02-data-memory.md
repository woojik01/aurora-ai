# PRD-02 — Local Data, Conversations & Memory

## Goal
Implement durable local storage for conversations, memories, tasks, agents, automations, tool executions, approvals, and audit records. The database is the local source of truth.

## Data model
At minimum define entities for:
- Conversation(id, title, createdAt, updatedAt, archived)
- Message(id, conversationId, role, content, timestamp, status, providerId, modelId)
- Memory(id, namespace, type, content, sourceMessageId?, createdAt, updatedAt, sensitivity, deletedAt?)
- Task(id, status, title, payload, createdAt, updatedAt, retryCount)
- Agent(id, name, systemPrompt, modelPolicy, toolPolicy, maxCalls, maxDepth, enabled)
- AgentRun(id, agentId, parentRunId?, status, callsUsed, startedAt, finishedAt)
- ToolExecution(id, taskId?, agentRunId?, toolId, status, sanitizedArguments, resultSummary, timestamps)
- Approval(id, actionId, riskLevel, requestedAt, expiresAt, decision)
- Automation(id, triggerType, schedule/condition, actionGraph, enabled)
- Device(id, publicKey, displayName, lastSeen, trustState)
- SyncRecord(entityType, entityId, revision, deviceId, updatedAt, tombstone)
- AuditEvent(id, category, action, actor, result, timestamp, redactedMetadata)

Use stable UUID/ULID-like identifiers generated locally so records can be created offline on multiple devices. Do not rely on auto-increment IDs for sync identity.

## Memory behavior
Conversation history and semantic memory are separate concepts. The assistant may use memories only through an explicit memory retrieval layer. The memory layer must return source/provenance metadata so the UI can show why a memory exists.

User controls:
- view memory
- edit memory
- delete memory
- clear conversation
- export data
- import/restore backup

Deletion must propagate as a tombstone during device sync rather than silently resurrecting deleted data.

## Database performance
- Index foreign keys and frequent query fields.
- Use paging for messages/logs.
- Batch inserts for streaming messages where safe.
- Keep transactions short.
- Avoid storing large binary files in Room; store encrypted files separately and reference them from the DB.
- Use database migrations for every schema change; destructive migrations are forbidden in production unless an explicit user-confirmed migration path exists.

## Memory retrieval performance
Do not send the entire database to a model. Retrieve a bounded context set using deterministic filters first, then optional semantic retrieval. Enforce token/character budgets before prompt construction.

## Privacy
Local data should remain local unless a feature explicitly requires external transmission. Before an API request, a data-classification layer should identify potentially sensitive fields and apply the configured policy. The UI should make it possible to tell whether a response used local data only or transmitted context to an API provider.

## Acceptance criteria
- Full conversation history survives offline restarts.
- Memory CRUD is deterministic and testable.
- Deleted memories cannot be returned by normal retrieval.
- Database queries for the active conversation remain bounded in memory.
- Corrupted/partial writes are recovered transactionally.
- Export/import round trip preserves IDs, timestamps, relationships, and deletion state.
