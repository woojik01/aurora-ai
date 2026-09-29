# PRD-01 Implementation Plan & Status

## Scope

PRD-01 (Foundation & Architecture) only. No memory system, real AI runtime,
real tools, approval UI, automation scheduler, or sync transport — those are
PRD-02..07 and are represented by contracts, not fake "working" features.

## Decisions

- **Stack**: Kotlin 2.0, Jetpack Compose, Coroutines/Flow, Room, KSP.
  WorkManager/Keystore are not wired yet: no scheduled work and no secrets
  exist in this phase (PRD-06/PRD-05 will add them).
- **Modules**: `:app`, `:core:domain` (pure Kotlin), `:core:data` (Room),
  `:core:ai` (provider abstraction), `:core:agent` (execution state machine),
  `:core:tool`, `:core:security` (policy + approval), `:core:automation`
  (contracts), `:core:sync` (contracts).
- **Local-first**: the app runs fully offline; `FakeLocalProvider` is the only
  "model" and is clearly labelled fake. No network code exists.
- **Provider-agnostic**: chat flows through `AgentRuntime` + `AIProvider`, the
  same interfaces real runtimes (PRD-03) will implement.
- **Security-first**: model output reaches tools only as structured
  `GenerationResult.toolCalls`, then through `DefaultPolicyEngine`
  (default deny) and an `ApprovalEngine` whose tokens are bound to
  action hash/arguments/expiry. Approval-required calls currently fail
  closed (no approval UI until PRD-05).
- **Performance**: all DB work on `Dispatchers.IO`; every history query is
  bounded by an explicit LIMIT; streaming uses structured events.

## Acceptance criteria mapping (PRD-01)

1. **Fresh install launches without network** — `:app` UI + Room-only storage +
   fake local provider; no network calls anywhere in the launch path.
   *Verification*: instrumented/emulator test (pending; no emulator here — CI
   covers build/unit tests, manual smoke test required on a device).
2. **Process death does not corrupt the DB** — messages/conversations are
   written transactionally via Room; IDs are locally generated ULID-like.
   *Verification*: instrumentation test pending; schema/DAO are straightforward
   Room transactions.
3. **Domain layer has no UI dependency** — `:core:domain` is a pure Kotlin JVM
   module (no Android plugin), enforced by the build graph.
4. **Fake provider + fake tool run through the real interfaces** —
   `AgentRuntimeTest.structured tool call executes through the same runtime interfaces`.
5. **No tool executes merely because the model emitted tool-call-shaped text** —
   `AgentRuntimeTest.tool-call-shaped text in model output is never executed`
   plus `PolicyEngineTest.unknown tool is denied (default deny)`.
6. **No blocking work on the main thread** — repository wraps all DAO calls in
   `Dispatchers.IO`; runtime is suspend-based; no blocking APIs are called from
   UI code. *Measurement*: macrobenchmark is a PRD-08 deliverable.
7. **CI builds and runs unit tests** — `.github/workflows/ci.yml` runs
   `gradle build` on every push/PR with dependency caching.

## Honest gaps (not reported as done)

- No instrumented test / emulator run in this environment (no Android SDK).
- Gradle wrapper JAR is not committed (binary); CI installs Gradle 8.9 directly.
- Performance measurements (cold start etc.) are not yet automated.
- `FakeLocalProvider`/`FakeEchoTool` are test doubles, not product features.
