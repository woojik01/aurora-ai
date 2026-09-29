# Aurora AI

Aurora AI는 Android 우선의 범용 AI OS형 개인 비서다. 로컬 모델과 개발자/API 모델을 하나의 실행 계층으로 통합하고, 대화·기억·도구·에이전트·자동화·동기화를 로컬 중심으로 제공한다.

## Product principles
- Local-first: 인터넷이 없어도 기본 AI 기능은 동작한다.
- Provider-agnostic: 로컬 실행기와 API 제공자를 교체할 수 있다.
- Permission-first: 모델이 아니라 권한 계층이 실제 행동을 통제한다.
- Human approval: 파일 삭제, 금전 관련 작업 등 위험 작업은 명시적 승인을 요구한다.
- Offline-first: 핵심 데이터와 상태는 로컬 DB가 정본이다.
- Performance-first: Android의 메모리, 배터리, 저장공간, 백그라운드 실행 제한을 고려한다.
- Auditable: 중요한 Tool/Agent 실행은 추적 가능해야 한다.

## PRD roadmap
1. PRD-01 Foundation & Architecture
2. PRD-02 Local Data & Memory
3. PRD-03 AI Provider Abstraction
4. PRD-04 Agent & Tool Runtime
5. PRD-05 Security & Permission System
6. PRD-06 Automation & Background Execution
7. PRD-07 PC Companion & Device Sync
8. PRD-08 Production Hardening & Release

Design language is intentionally deferred. The eventual UI should be simple and restrained, with a subtle Aurora-like flowing/wave visual identity; visual design must not alter the architectural/security requirements.

## Authoritative implementation rule
Each PRD is an implementation contract. Later PRDs may extend earlier interfaces but must not silently weaken security invariants or break offline-first behavior. Every milestone must include unit tests, integration tests, failure-path tests, and performance measurements appropriate to its scope.
