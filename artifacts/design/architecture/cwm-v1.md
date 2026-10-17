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
| `userTask` | `assignee` (role or user), optional `form`, outcomes and condition | BPMN user task; linter requires assignee and compiles conditions to a run-or-skip gateway. |
| `approval` | assignee and explicit outcomes | BPMN user task and outcome gateway. SLA/routing are represented only when authored. |
| `serviceTask` | registered `action` name | Asynchronous BPMN service task; action is resolved by the host registry. Failed jobs retry with backoff and appear in the local Operations dead-letter queue after exhaustion. |
| `decision` | explicit decision/table reference and outcomes | Supported when the referenced YAML decision table compiles to a version-pinned DMN table whose single output is a declared string `outcome`. BPMN invokes the Flowable DMN executor asynchronously with the same retry/dead-letter behavior and routes that outcome through the generated gateway. |
| `parallel` | explicit branch and join definition | Model field exists; compiler does not yet generate split/join semantics. Fails compilation pending full support. |
| `subStage` | nested stage reference | Reserved; not yet executable. |
| `waitEvent` | `eventName` | BPMN signal catch event; the host resumes one matching subscription through the versioned signal API using workflow key and business key. |
| `timer` | Positive ISO-8601 duration | BPMN intermediate timer catch event; Flowable persists the timer job and continues to the next step when due. |
| `end` | explicit resolution | Compiles to BPMN end event. |
| `childWorkflow` | child workflow key and completion mapping | Draft-only; runtime callback is not implemented. |

`parallel`, `subStage` and `childWorkflow` are not executable in v1. The compiler rejects them; migration must report and reject the affected case rather than writing an invalid runnable-looking workflow package. A `waitEvent` requires `eventName` and pauses at a Flowable signal subscription. `POST /api/v1/events/{eventName}` resumes the single matching subscription scoped by `businessKey` and optional `workflowKey`; payload variables are checked against the process version's data schema before the signal is delivered. Timer steps are executable. User task and approval SLAs accept a positive ISO-8601 `deadline` and `onDeadline: "escalate:<role>"`; Flowable creates a non-interrupting deadline timer that clears the old assignee, moves the task to the target role's candidate queue, and writes an audit event. SLA goal notifications, business calendars, and other deadline actions are not implemented. Unknown types are schema/linter errors and compiler errors. Never default an unknown construct to a human task.

The linter rejects duplicate IDs, missing terminal resolutions and assignees, unsupported step types, invalid route targets, and unreachable steps. When given the workflow source path (CLI validation and application catalog loading), it also resolves condition field references against the package's declared JSON data schema.

The local runtime loads each declared `data.schema` from inside its workflow package and associates it with the deployed Flowable process-definition version. Start variables, merged task submissions, and variables after service or decision actions are validated against that schema. The current runtime validator implements object/array/scalar types, required fields, enums, numeric bounds, string length and pattern, email format, and `additionalProperties: false`; it is not a complete JSON Schema 2020-12 implementation yet.

## Outcomes and expressions

- Transitions use named outcome maps with target `next`, `stage:<stage-id>` or `end:<stage-id>` for a stage's implicit terminal event.
- Step conditions use a restricted tree of `and`, `or`, `not`, and top-level data-field comparisons (`eq`, `ne`, `lt`, `lte`, `gt`, `gte`, `in`, `exists`). The compiler emits an explicit run-or-skip gateway; field references must exist in the local data schema.
- `decision`/`parallel` require explicit outgoing targets before compilation. No free-form Java, reflection, method invocation or arbitrary bean access is allowed in CWM.

## Data, forms, actions and errors

- Data schemas are local JSON Schema files. Remote `$ref` is forbidden. Field types derive from JSON Schema; UI hints are separate form files. The local renderer supports form layout rows, custom labels/help text, and conditional visibility (`eq`, `ne`, `in`, `exists`) with hidden values excluded from submission. Attachments and host reference-data lookups remain unsupported.
- The local PDF migration writer creates draft form/data-schema files but extracted field names can be truncated by source PDF rendering and require review.
- A `serviceTask` may declare a `config` object. It is compiled into the versioned BPMN as deterministic JSON and passed to the registered handler as `ActionContext.configuration`. The object is workflow source, so it must contain only non-secret values and `{secretRef: NAME}` references. The linter rejects values directly under keys that look sensitive (password, token, credential, API key, authorization, or secret).
- The runtime starter registers four standard actions: `w6w.http.request` (HTTP(S), bounded timeout and 1 MiB response, headers/body can resolve variables or host secrets), `w6w.email.send` (delegates to a host `WorkflowEmailGateway`), `w6w.events.publish` (publishes a Spring application event), and `w6w.data.set` (maps constants or workflow variables to top-level output fields). Missing host email gateway or secrets fail with an actionable error. Add application-specific actions by registering a `WorkflowActionHandler` under a unique action name.
- `{from: data.path}` resolves a workflow variable and `{secretRef: NAME}` resolves via the host `SecretProvider`. The local default reads `w6w.secrets.NAME` or `W6W_SECRET_NAME` from the process environment. Production hosts should replace it with their secret-manager adapter. Values are never persisted in CWM; runtime action inputs should avoid logging resolved secrets.
- Every generated service/decision task uses a durable Flowable async job. By default the engine attempts the job three times with a one-minute delay, then moves it to the dead-letter queue. A service task may override `retries` (1-100) and `backoff` (positive ISO-8601 duration) in `onError`. CWM v1 currently supports only `onFailure: deadLetter`; other failure policies are rejected by the linter rather than silently ignored.
- Operations lists failed jobs with workflow, case, step, error message and timestamp. An operator can requeue a job with a fresh retry budget; the action is written to the local audit log. The engine job executor must be active for automatic retries and replays to run.
- SLA durations use ISO-8601 (`PT4H`, `P2D`). `PT8BH` is reserved for a future business calendar implementation and is not accepted as a standard duration today.
- `onDeadline` currently supports only `escalate:<role>` for a user task or approval with a positive `deadline`. The escalation timer does not interrupt the task; it changes candidate-group routing and records `SLA_DEADLINE_ESCALATED`. `goal` is validated but does not currently trigger notifications.

## Traceability metadata

Migration reports retain source element ID/name, disposition, confidence and finding text. Workflow metadata stores the source Blueprint identifier. Per-step source IDs in CWM are a P2 gap; do not claim full element-level provenance until implemented.

## Conformance and release gate

Every deployable workflow must parse against the schema, pass `CwmLinter`, compile without unsupported constructs, have scenario coverage, pass the eligibility checker and have an approved owner. `mvn verify` runs the current module test suite. The source PDF parser has a fixture test; `.blueprint` binary and process-flow XML adapters do not yet have conformance tests.
