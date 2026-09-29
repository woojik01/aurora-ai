# PRD-08 — Performance Hardening, Reliability & Production Release

## Goal
Turn the prototype into a distributable Android AI OS with measurable performance, strong failure handling, and a repeatable release process.

## Performance budgets
Track at minimum:
- cold/warm startup
- memory RSS/heap during chat
- peak memory during local inference
- DB query latency
- first-token latency
- tokens/sec for supported local models
- API request latency
- tool-call latency
- battery consumption during inference/automation
- network bytes per task
- crash-free sessions
- ANR rate

Set device-class budgets instead of assuming one universal number. Benchmark representative low/mid/high Android devices.

## Memory safety
Local model loading must be capability-aware. Before loading a model, check available memory and model metadata. Prefer quantized models where quality is acceptable. Do not load multiple large models concurrently unless a measured device profile allows it. Unload idle models and release native resources deterministically.

Avoid retaining full conversation transcripts, tool outputs, images, or documents in UI state. Stream and page data.

## Thermal/battery safety
Monitor sustained local inference behavior. Provide a policy that can reduce concurrency, stop background inference, or switch to API/local lightweight model according to user settings and device state. Never circumvent Android thermal/battery protections.

## Reliability
Every long-running operation needs cancellation, timeout, retry policy, and durable state. Side-effecting operations require idempotency keys or post-action reconciliation.

## Testing matrix
- Unit tests for domain/security/policy.
- Integration tests for DB, provider routing, tool runtime, approval, automation, sync.
- Instrumentation tests on Android.
- Offline tests with network disabled.
- Process-death tests.
- Low-memory tests.
- Battery/Doze/background tests.
- Malicious input/security regression tests.
- API provider failure tests.
- Local model unavailable/over-capacity tests.
- Sync conflict/replay tests.

## CI quality gates
A PR must not merge if tests, static analysis, security checks, or required performance regression thresholds fail. Secrets must be scanned before merge. Build reproducibility should be maintained.

## Release safety
Use staged rollout. Maintain a migration/rollback plan for database schema. Never ship a new migration without an upgrade test from the previous supported version.

## Product acceptance
Aurora AI is release-ready only when:
1. Core chat works offline with a supported local model.
2. API providers can be selected and usage is visible.
3. API failure can safely fall back to local inference.
4. Tools are permission-gated.
5. High-risk actions require approvals.
6. User-created agents respect execution limits.
7. Memory is editable/deletable.
8. Scheduled automation survives process death within Android constraints.
9. Paired PC/Android sync is authenticated and incremental.
10. Security and performance regression suites pass.
