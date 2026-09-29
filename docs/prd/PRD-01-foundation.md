# PRD-01 — Foundation & Architecture

## Goal
Create the Android-first application foundation for Aurora AI. The result must build and launch offline, use a local database as the source of truth, expose replaceable AI/tool interfaces, and establish strict module boundaries for later agent and automation work.

## Recommended stack
- Kotlin.
- Jetpack Compose for UI.
- Single Android application initially; architecture must permit a future PC companion without coupling domain logic to Android UI.
- Coroutines + Flow for asynchronous work.
- Room for structured local persistence.
- DataStore for small preferences/configuration.
- Android Keystore-backed secret protection.
- WorkManager for persistent deferrable background work; foreground execution only when Android policy permits and the user-visible notification requirement is satisfied.

Use current stable versions compatible with the project's Android toolchain. Do not hard-code library versions in this PRD; pin versions in Gradle and update them deliberately.

## Module boundaries
Suggested modules:
- :app — Android composition root and UI.
- :core:domain — pure Kotlin domain models/use cases; no Android dependency where practical.
- :core:data — repositories and Room/DataStore implementations.
- :core:ai — provider abstraction, model registry, routing.
- :core:agent — agent graph and execution state machine.
- :core:tool — tool contracts and permission metadata.
- :core:security — authorization, approval, secret handling, audit policy.
- :core:automation — schedules, triggers, durable jobs.
- :core:sync — device pairing and synchronization protocol.
- :feature:* — user-facing features.

The model must never receive direct access to Android APIs, databases, filesystem paths, GitHub credentials, or network clients. It can request typed tool calls. A policy layer decides whether the tool call may execute.

## Core execution flow
User request -> conversation repository -> planner/model -> structured action request -> policy engine -> approval gate if required -> tool/agent execution -> result validation -> persistence -> UI update.

No LLM output is trusted as executable code or permission. Tool arguments must be parsed into typed schemas and validated before execution.

## Offline requirement
The app must launch and expose chat/history/memory/settings when completely offline. If no local model is installed, show a clear unavailable state rather than attempting an invisible network fallback. API calls are optional accelerators, never the sole path to opening or reading local data.

## Error handling
Every asynchronous operation must have explicit states: queued, running, succeeded, failed, cancelled, timed_out, denied, awaiting_approval. Errors must be persisted when relevant so a background task can resume or explain failure after process death.

## Performance baseline
- Cold start target: <2.5 s on a representative mid-range Android device, excluding unavoidable OS first-install work.
- Main-thread rule: no network, model inference, database transaction over non-trivial data, file parsing, embedding, or crypto operation on the UI thread.
- UI should remain responsive at 60 fps under ordinary interaction; long AI/tool streams must not trigger unbounded Compose recomposition.
- Use bounded buffers for streaming tokens/events.
- Paginate conversations and tool logs; never load an entire history into memory.
- Cancel stale requests when the user leaves a task or starts a replacement request.

## Security foundation
- Default deny for tools.
- Least privilege per tool and agent.
- Credentials never placed in prompts, logs, analytics, or ordinary database columns.
- Sensitive values use Keystore-backed encryption/key wrapping.
- All external content is untrusted input.
- Audit security-relevant actions without storing secrets.
- Redact authorization headers, tokens, cookies, API keys, and sensitive tool arguments from logs.

## Acceptance criteria
1. Fresh install launches without network.
2. App survives process death without corrupting the local database.
3. Domain layer has no direct UI dependency.
4. A fake AI provider and fake tool can execute through the same runtime interfaces used by future real providers/tools.
5. No tool can execute merely because the model emitted a tool-call-shaped string.
6. Performance test confirms no blocking work on the main thread.
7. CI builds and runs unit tests offline where Gradle dependencies are cached.
