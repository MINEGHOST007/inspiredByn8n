# ADR-0002: Architecture Baseline, Embedding Model, and Blueprint Ingestion Strategy

## Status
Accepted for the local proof of concept; production baseline and host-stack compatibility remain unconfirmed.

## Context
Following the selection of Flowable behind `Engine Port` (ADR-0001), core baseline architectural decisions are required regarding framework compatibility, database schema separation, ingestion strategy from Pega Blueprint, and condition expression semantics.

Key realities:
1. **Prototype baseline**: the repository currently builds for Java 17, Spring Boot 3.3.4 and Flowable 7.1.0. The actual host stack is still unknown, so this is a local implementation assumption, not a final production compatibility decision.
2. **Database isolation**: local execution uses an H2 file database. PostgreSQL schema isolation, migrations and production configuration are not yet implemented.
3. **Pega Blueprint file format**: the checked-in `.blueprint` export appears opaque/high entropy and has no public specification. The adapter detects this input and refuses to decode it; no reverse engineering or decryption is performed.
4. **Expression safety**: Free-form script execution in workflow definitions creates security, audit, and maintainability hazards.

## Decision

### 1. Framework and Engine Baseline
- For this local proof of concept, use **Spring Boot 3.3.4 / Java 17 / Flowable 7.1.0**.
- Keep Flowable types inside `w6w-engine-flowable`; run contract checks before changing engine versions. Compatibility with Spring Boot 4 / Flowable 8 is not yet verified.

### 2. Embedding Model and Schema Separation
- The modules include a thin Spring Boot starter and an embedded engine adapter. The runnable demo uses a local H2 file, not PostgreSQL.
- PostgreSQL schema isolation and database migration are still required before production; same-transaction execution remains deferred (EX-07).

### 3. Ingestion Strategy: PDF-First Deterministic Extraction
- The migration utility (`w6w-migrate`) currently accepts **Pega Blueprint PDF exports** and hand-authored CWM YAML as the reliable inputs.
- Blueprint PDFs provide consistent, structured layouts for Case Types, Stages, Steps (collect, automation, approval), Data Objects, and Personas.
- Extraction uses Java PDFBox and produces a reviewable intermediate model. Color-based step classification is heuristic and is marked for human review.
- The `.blueprint` binary format is opaque and unsupported. Pega process-flow XML is also not implemented in this proof of concept. No LLM or embedding model is invoked locally or remotely.

### 4. Declarative Condition Model
- CWM does not permit arbitrary scripts or unconstrained expressions. The model has structured conditions, but safe expression compilation and execution remain incomplete.

### 5. Action Registry & Idempotency
- Service automation steps bind to Spring beans implementing `@WorkflowAction`.
- The `ActionContext` provides an explicit idempotency key (`{instanceId}:{stepId}`).
- The starter validates on application startup that every action referenced in deployed CWM workflows has a registered handler bean, failing early if any action is unmapped.

## Consequences
- **Positive**: local operation and PDF extraction do not require hosted services; binary parsing is explicitly refused.
- **Open risks**: no complete security boundary, PostgreSQL setup, production audit, DMN compiler, or final legal/host-stack sign-off is provided by this PoC.
