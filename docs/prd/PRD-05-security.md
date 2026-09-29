# PRD-05 — Security, Privacy & Permission Architecture

## Goal
Treat Aurora AI as a security-sensitive AI operating system. Security controls must be enforceable outside the model and survive malicious prompts, compromised tool output, accidental model behavior, and partial failures.

## Threat model
Protect against:
1. Malicious prompt injection from web/email/files.
2. Model hallucination causing unintended actions.
3. Credential theft from logs/database/backups.
4. APK reverse engineering and API credential extraction.
5. Unauthorized agent/tool invocation.
6. Path traversal and arbitrary file access.
7. Malicious downloads.
8. SSRF/network abuse through browser/download tools.
9. Agent recursion/resource exhaustion.
10. Database corruption or rollback/replay.
11. Cross-device unauthorized pairing.
12. Sensitive context leakage to API providers.

## Mandatory principles
- Default deny.
- Least privilege.
- Defense in depth.
- Explicit trust boundaries.
- No security decision may depend solely on LLM output.
- Fail closed for authorization failures.
- Separate authentication, authorization, approval, and auditing.

## Secret management
Use Android Keystore-backed keys for protecting provider credentials/tokens. Never put secrets in SharedPreferences, plain Room fields, logs, analytics, clipboard, or prompt context. Redact secrets from exceptions and diagnostics.

If a credential must be used by a network request, keep it inside the provider/tool boundary. The model receives only an opaque provider/tool capability.

## API credential architecture
A public APK cannot safely hide a permanent unrestricted developer API secret. Production must therefore use scoped credentials, a controlled token broker/backend, or require user-supplied keys for providers that cannot safely be proxied. If a backend is introduced for developer API protection, it must remain a minimal security/billing component and must not become the source of truth for user data; Aurora's local-first requirement remains intact.

## File sandbox
Every file tool request must resolve a canonical path and verify it is within an explicitly granted root. Reject path traversal, symlink escapes where applicable, unexpected URI authorities, and paths outside the grant. File deletion requires approval. Use Android scoped storage and platform document APIs where appropriate rather than broad filesystem assumptions.

## Network security
- HTTPS/TLS only for external services.
- Certificate validation must use platform defaults unless a justified pinning strategy is maintained safely.
- Enforce host allowlists where a tool has a constrained purpose.
- Block local/private network access from generic web fetchers unless explicitly required and authorized; this reduces SSRF risk.
- Limit redirects, response sizes, connection time, and concurrent requests.

## Browser/content isolation
Treat all page/email/document text as hostile data. Strip active content where possible. Never interpret retrieved text as a new system/developer instruction. The browser tool should return structured content with provenance and origin.

## Approval engine
Approval is a separate subsystem. An approval token must be bound to action hash, tool, arguments, user/device identity, expiration, and risk level. Any argument change invalidates approval. Financial operations always require fresh approval.

## Audit
Audit events should include actor, tool, action type, risk level, outcome, timestamp, and sanitized metadata. Do not log full sensitive payloads by default. Audit storage must be append-oriented and tamper-evident where practical.

## Data protection
Sensitive local data should be encrypted at rest using Android platform facilities and app-managed keys. Backup behavior must be deliberate: secrets and protected data must not be unintentionally copied into insecure backups. Provide export/delete controls to users.

## Security testing
Required tests:
- path traversal
- malicious URI
- symlink escape where relevant
- prompt injection corpus
- forged approval token
- modified tool arguments after approval
- expired approval
- agent recursion
- oversized download
- malformed API response
- secret redaction
- database migration corruption
- unauthorized device pairing
- replayed sync mutation
- API quota abuse

Use OWASP MASVS/MASTG as a security-testing reference and Android's platform security guidance as implementation guidance.

## Acceptance criteria
A security reviewer must be able to demonstrate that model-generated text cannot bypass permission checks, approval gates, file boundaries, agent limits, or financial-operation confirmation.
