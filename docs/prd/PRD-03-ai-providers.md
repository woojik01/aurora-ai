# PRD-03 — AI Provider Abstraction, Local Models & API Routing

## Goal
Support multiple local-model runtimes and multiple API providers behind one typed interface. Users can choose a model directly. The app has a developer-provided API by default, while user-provided providers may be added. API failure can fall back to an available local model.

## Provider contract
Define a provider interface covering:
- listModels()
- healthCheck()
- generate(request)
- stream(request)
- cancel(requestId)
- estimateUsage(request)
- capabilities()

A model descriptor must expose provider, model ID, context limit if known, modality, tool-calling support, streaming support, local/API classification, estimated cost metadata, and availability.

## Local runtime abstraction
Do not hard-code Ollama, llama.cpp, or another runtime into the domain layer. Create LocalRuntimeAdapter. Initial adapters can be selected after benchmarking Android feasibility. A PC runtime adapter can later expose the same model contract over an authenticated local connection.

## API routing
Routing modes:
- Explicit user selection.
- Automatic fallback: selected API fails -> available local model.
- Optional future smart routing based on capability/cost/privacy.

Never silently send sensitive local context to an API if the user's privacy policy forbids it.

## Developer API
The developer API credential is an app-managed secret, not source code. Do not embed long-lived unrestricted credentials in a public APK. The production architecture must assume an exposed client can be reverse-engineered. Therefore the developer API layer needs server-side abuse controls, scoped/short-lived credentials, quota enforcement, or another architecture that does not rely on a permanent secret embedded in the APK.

The app must display:
- provider
- model
- local/API status
- request status
- input/output usage when available
- estimated cost when available
- period usage and limits
- fallback reason

## Cost accounting
Create a local usage ledger. Never claim an exact cost when the provider does not expose exact billing data; mark estimates as estimates. Store provider/model, timestamp, token counts if available, pricing version, currency, and estimate status.

## Streaming
Use structured stream events rather than appending raw strings directly to UI state. Events should include token delta, tool-call delta, usage, completion, error, and cancellation. Apply backpressure and coalesce UI updates to prevent recomposition storms.

## Failure policy
Classify failures into authentication, quota, timeout, network, provider overload, malformed response, unsupported capability, and cancellation. Retry only idempotent operations and use bounded exponential backoff. Do not blindly retry tool calls that may have side effects.

## Acceptance criteria
- At least one fake local provider and one fake API provider pass the same provider test suite.
- Provider replacement requires no UI/domain rewrite.
- API failure falls back to local only when a compatible local model exists.
- No API secret appears in logs, crash reports, prompts, or repository files.
- Usage/cost display distinguishes measured usage from estimates.
