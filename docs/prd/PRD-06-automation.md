# PRD-06 — Automation, Scheduling & Background Execution

## Goal
Allow users to create scheduled and conditional AI workflows that can execute while the app is not foregrounded, subject to Android background execution constraints.

## Automation model
Automation = Trigger + Conditions + Action Graph + Policy.

Triggers:
- one-time time
- recurring schedule
- device/app event where platform permits
- manual
- condition check

Actions can invoke an agent or a safe tool. High-risk actions pause for user approval rather than silently executing.

## Durable execution
Use WorkManager for persistent, deferrable background work. Work must be idempotent and resumable. Persist execution checkpoints so process death does not duplicate side effects.

For work requiring user-visible immediate execution, use an appropriate foreground-service design only when justified by Android policy and with a visible notification. Do not assume unrestricted background execution is available.

## Battery/performance
- Respect charging/network/battery constraints.
- Avoid frequent polling; prefer platform-supported triggers.
- Coalesce repeated triggers.
- Set execution timeouts.
- Limit concurrent AI jobs.
- Pause or downgrade local inference when battery/thermal policy requires it.
- Never keep a CPU-heavy model alive indefinitely in the background.

## Notifications
Notifications should identify:
- automation name
- status
- whether approval is required
- failure/retry state
- action to open the task

No sensitive message content should be exposed on the lock screen by default.

## Conditional automation
Conditions are evaluated by deterministic code where possible. LLM evaluation may be offered only as an explicit, bounded condition type and must not grant privileges. Example: an LLM may classify an email as “bug report”, but the subsequent GitHub mutation still passes through normal authorization.

## Acceptance criteria
- Scheduled tasks execute after process death when Android permits.
- Duplicate execution is prevented or safely deduplicated.
- Background work has measurable battery/network limits.
- Approval-required tasks never execute silently.
- Users can pause, edit, run-now, disable, and inspect automation history.
