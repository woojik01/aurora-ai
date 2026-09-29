# PRD-04 — Agent & Tool Runtime

## Goal
Allow Aurora AI to perform real work through typed tools and user-created agents while preventing uncontrolled execution.

## Agent definition
Each agent has:
- ID/name
- system instructions
- selected model/provider
- allowed tools
- max tool calls per run
- max execution depth
- max runtime
- concurrency limit
- network policy
- file-scope policy
- approval policy
- enabled/disabled state

Users can create and edit agents. Agent-to-agent invocation is explicit and policy checked.

## Tool categories
Initial tool contracts should cover:
- Web search
- Browser/navigation
- Email read/write
- Calendar read/write
- Notes
- Files read/write/delete/download
- GitHub pull/push/branch/PR operations
- Image analysis
- Voice input/output
- Task scheduling

Tool implementations are replaceable. The LLM sees schemas and descriptions, never raw platform privileges.

## Execution state machine
QUEUED -> PLANNING -> AWAITING_APPROVAL (if needed) -> RUNNING -> TOOL_CALL -> VALIDATING -> RUNNING -> SUCCEEDED/FAILED/CANCELLED/TIMED_OUT.

Every state transition should be persisted so the app can recover after process death.

## Tool safety
Every tool declares:
- risk level
- required permissions
- data sensitivity
- network requirement
- side-effect class
- idempotency
- approval requirement

File deletion requires explicit approval by default. Financial actions always require explicit approval immediately before execution; pre-approved policies must never bypass the financial rule.

GitHub push/pull is permitted according to the configured repository scope. Destructive repository operations require separate permissions.

Downloads must use safe temporary storage, size limits, timeouts, MIME/type validation, and destination policy. Do not automatically execute downloaded files.

## Prompt injection defense
Web pages, emails, GitHub issues, documents, and downloaded content are untrusted data. Their instructions must never automatically become system instructions. Tool output must be wrapped in typed/untrusted-content boundaries. The planner must distinguish user intent from retrieved content.

## Agent limits
Enforce max calls, max depth, max wall-clock time, max output size, max downloaded bytes, and max concurrent child agents. Limits are enforced by runtime code, not by prompting.

## Acceptance criteria
- A malicious model response cannot directly execute an arbitrary Android command.
- Agent recursion stops at configured depth.
- Tool calls stop at configured count/time limits.
- Required approvals cannot be bypassed by changing the prompt.
- Every side-effecting tool has an idempotency/replay strategy.
- Tool failures are visible and recoverable.
