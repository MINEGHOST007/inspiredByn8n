# Canonical Workflow Model v1: implementation contract

**Status:** implementation baseline, not a claim that every SRS construct executes today. The JSON Schema at `code/schema/cwm/v1/workflow.schema.json` and Java model are the executable contract. The compiler/linter support matrix below is authoritative until the rest of the SRS modules land.

## Source of truth and versioning

- One UTF-8 YAML document per workflow. `apiVersion` is `w6w/v1`; `kind` is `Workflow`.
- `metadata.key` is a stable kebab-case ID; `metadata.version` is SemVer; owner, tier and source are required for deployable definitions. Add `dataClassification` before the security gate.
- `stages` preserve source order. Each step ID is unique in its process. A stage can have `alternate: true` and an explicit terminal `resolution`.
- Additive fields are allowed in v1 only when the schema and parser explicitly support them. Breaking changes require `w6w/v2`; do not rely on ignored YAML keys.
- CWM is authoritative. BPMN/DMN are deterministic, disposable build outputs.

## Step contracts

| Type | Required data | v1 handling |
|---|---|---|
| `userTask` | `assignee` (role or user), optional `form`, outcomes | BPMN user task; linter requires assignee. |
| `approval` | assignee and explicit outcomes | BPMN user task and outcome gateway. SLA/routing are represented only when authored. |
| `serviceTask` | registered `action` name | BPMN service task; action is resolved by the host registry. |
| `decision` | explicit decision/table reference and outcomes | Model field exists; compiler does not yet generate DMN/gateway semantics. Fails compilation pending full support. |
| `parallel` | explicit branch and join definition | Model field exists; compiler does not yet generate split/join semantics. Fails compilation pending full support. |
| `subStage` | nested stage reference | Reserved; not yet executable. |
| `waitEvent` | event name/correlation key | Reserved; not yet executable. |
| `timer` | ISO-8601 duration and timer outcome | Reserved; not yet executable. |
| `end` | explicit resolution | Compiles to BPMN end event. |
| `childWorkflow` | child workflow key and completion mapping | Draft-only; runtime callback is not implemented. |

Unknown types are schema/linter errors and compiler errors. Never default an unknown construct to a human task.

## Outcomes and expressions

- Transitions use named outcome maps with target `next`, `stage:<stage-id>` or `end:<resolution>`.
- `decision`/`parallel` require explicit outgoing targets before compilation. No free-form Java, reflection, method invocation or arbitrary bean access is allowed in CWM.
- Expression evaluation is not yet implemented as an executable CWM feature. Until a reviewed restricted expression language is added, use explicit role/user assignments and action handlers; treat any expression-bearing definition as unsupported.

## Data, forms, actions and errors

- Data schemas are local JSON Schema files. Remote `$ref` is forbidden. Field types derive from JSON Schema; UI hints are separate form files.
- The local PDF migration writer creates draft form/data-schema files but extracted field names can be truncated by source PDF rendering and require review.
- Service actions are registry names only. Secrets stay in the host secret store, never in CWM.
- `onError` retry/backoff/route/fail semantics are not yet implemented. Do not author a retry policy and assume it runs.
- SLA durations use ISO-8601 (`PT4H`, `P2D`). `PT8BH` is reserved for a future business calendar implementation and is not accepted as a standard duration today.

## Traceability metadata

Migration reports retain source element ID/name, disposition, confidence and finding text. Workflow metadata stores the source Blueprint identifier. Per-step source IDs in CWM are a P2 gap; do not claim full element-level provenance until implemented.

## Conformance and release gate

Every deployable workflow must parse against the schema, pass `CwmLinter`, compile without unsupported constructs, have scenario coverage, pass the eligibility checker and have an approved owner. `mvn verify` runs the current module test suite. The source PDF parser has a fixture test; `.blueprint` binary and process-flow XML adapters do not yet have conformance tests.
