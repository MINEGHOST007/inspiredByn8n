# ADR-0001: Selection of Flowable as Reference Workflow Engine

## Status
Accepted for the JVM proof of concept, subject to confirmation against the host application stack.

## Context
We need an in-application, Git-versioned workflow capability for Tier 1 "long tail" processes (simple approvals, requests, linear and lightly branching flows). Complex case management remains in PEGA.
The workflow engine must meet five essential criteria:
1. **Embeddable** directly within the host application process sharing database infrastructure without forcing an external server operations team.
2. **Git-friendly declarative definitions** that diff cleanly in code reviews.
3. **First-class human tasks, SLAs/timers, and routing** (since business flows are primarily assignment and approval-driven).
4. **Permissive OSI open-source license** (Apache 2.0 / MIT) with zero per-case or per-user runtime licensing costs.
5. **Architectural parity with PEGA concepts** (stages, steps, user tasks, gateways, decision tables) to minimize the conceptual impedance mismatch for the migration utility.

Candidate evaluations:
- **Flowable (Open Source)**: Apache 2.0, natively executes BPMN 2.0, CMMN 1.1, and DMN. Direct mapping to Pega case lifecycle. Embeddable in JVM stacks.
- **Operaton (Camunda 7 CE Fork)**: Apache 2.0, viable alternative but less forward momentum than Flowable for greenfield builds.
- **Camunda 8**: Rejected due to non-OSI license (Sustainable Use License requiring commercial production licenses) and Zeebe cannot be embedded in-process.
- **Conductor OSS**: Apache 2.0, JSON-based, but requires running an external cluster; human task routing and SLAs must be custom built.
- **Temporal**: MIT, developer/code-first. Low fit for declarative definition generation from design blueprints.

## Decision
Adopt **Flowable** (Open Source edition, Apache 2.0) as the reference engine implementation behind a strict internal `Engine Port` interface (`w6w-engine-port`).

Key architectural boundaries:
1. **No engine types leak outside the port**: Host applications and workflow authors interact only with the W6W facade, CWM YAML models, and W6W task/instance representations.
2. **Deterministic compilation**: CWM YAML is compiled deterministically into BPMN 2.0 XML at build/test time. Engine XML is a build artifact, not the system of record.
3. **Portability**: If the host stack is non-JVM or requirements change, the `Engine Port` allows swapping the engine (e.g. to Elsa 3 or Conductor OSS) without modifying workflow definitions or host business logic.

## Consequences
- **Positive**: Flowable provides a persistent BPMN engine and timer/job facilities behind the engine adapter.
- **Negative**: Open-source distribution does not include GUI modellers or task web apps; W6W supplies the schema-driven form renderer and UI Kit.
- **Current gap**: the repository has no complete REST facade, UI Kit, PostgreSQL production profile or W6W audit export; Flowable history tables alone do not satisfy the SRS audit requirement.
