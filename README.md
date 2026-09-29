
## Repository layout (PRD-01 status)
```
:app              Compose UI + manual composition root (offline chat)
:core:domain      pure Kotlin domain models & ports (no Android dependency)
:core:data        Room persistence (source of truth), bounded queries
:core:ai          AIProvider abstraction + fake local provider
:core:agent       AgentRuntime: state machine, runtime-enforced limits
:core:tool        Tool contract, metadata, registry, fake tool
:core:security    Policy engine (default deny) + approval engine (hash-bound tokens)
:core:automation  durable job contracts (PRD-06)
:core:sync        sync mutation contracts (PRD-07)
```
Implementation notes: [docs/dev/PRD-01-plan.md](docs/dev/PRD-01-plan.md)

## Build
CI runs `gradle build` (unit tests included) on every push/PR. Locally:
```
gradle build
```
The Gradle wrapper JAR is not committed (binary); CI installs Gradle 8.9 directly.
