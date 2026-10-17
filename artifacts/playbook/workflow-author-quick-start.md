# Workflow author quick start

This guide explains the local W6W workflow cycle. A workflow is a small folder
of readable YAML and JSON files; the local workspace discovers and runs those
definitions. You do not need to edit Java to change a workflow's name, stages,
human tasks, forms, roles, outcomes, or time limits.

## 1. Create a workflow package

Copy the closest example under
`code/workflows/domains/customer-service/refund-processing/` into a new folder,
for example `code/workflows/domains/people/leave-request/`. Keep the package
layout simple:

```text
leave-request/
  workflow.cwm.yaml
  schemas/data.schema.json
  forms/request.json
  tests/scenarios/
```

Give `metadata.key` a unique kebab-case value. Set the owner, semantic version,
data classification, and clear workflow name. Keep forms and schemas beside
the workflow and reference them with paths relative to the package folder.

## 2. Describe stages and steps

Edit `workflow.cwm.yaml`. A basic approval uses a `userTask` or `approval`, an
assignee role, a JSON form, and explicit outcomes. The final stage must declare
a resolution. Example:

```yaml
stages:
  - id: request
    name: Request
    steps:
      - id: collect-request
        name: Collect request
        type: userTask
        assignee:
          role: request_agent
        form: forms/request.json

  - id: decision
    name: Manager decision
    steps:
      - id: manager-approval
        name: Approve request
        type: approval
        assignee:
          role: manager
        form: forms/request.json
        sla:
          goal: PT4H
          deadline: PT24H
        outcomes:
          approve: next
          reject: stage:rejected

  - id: complete
    name: Complete
    resolution: Approved
    steps:
      - id: close-request
        name: Close request
        type: end

  - id: rejected
    name: Rejected
    alternate: true
    resolution: Rejected
    steps:
      - id: close-rejected-request
        name: Close rejected request
        type: end
```

Supported executable steps are currently user tasks, approvals, service tasks,
decision tables, inbound `waitEvent` signals, timers, and terminal steps. The
CWM model reserves other step types, but they must not be used until their
compiler/runtime behavior is implemented. The checker fails closed for
unsupported types. SLA `deadline` becomes the task due date, and
`onDeadline: "escalate:<role>"` moves overdue work into the declared role queue
and records an audit event. Goal notifications and business-calendar timing are
not implemented.

## 3. Edit the form

Add fields under the form's JSON Schema `properties`; list mandatory fields in
`required`. The renderer supports common string, number, integer, boolean,
enum, and nested object fields, and validates required values, number bounds,
enums, and email format. Use `ui.labels`, `ui.helpText`, and `ui.layout` to make
the form easier to scan. Use `ui.conditionalVisibility` to show a field when a
peer field satisfies `eq`, `ne`, `in`, or `exists`; hidden values are omitted
from submission and hidden required fields do not block completion. Attachments
and host application reference-data lookups are not available in this local
slice.

## 4. Validate and run locally

Start a package with one CLI command. The four templates are `approval`,
`intake-and-fulfil`, `multi-level-approval`, and `request-with-sla`:

```powershell
.\w6w.ps1 new --key leave-request --template request-with-sla --owner people-ops --domain people
```

The CLI writes a workflow folder, form, data schema, and starter README under
`code/workflows/domains/`. Then validate, render, compile, test, and inspect Git
changes with the same tool:

```powershell
.\w6w.ps1 validate code/workflows/domains/people/leave-request/workflow.cwm.yaml
.\w6w.ps1 render code/workflows/domains/people/leave-request/workflow.cwm.yaml
.\w6w.ps1 compile code/workflows/domains/people/leave-request/workflow.cwm.yaml
.\w6w.ps1 test code/workflows/domains/people/leave-request/workflow.cwm.yaml
.\w6w.ps1 diff code/workflows/domains/people/leave-request/workflow.cwm.yaml
```

After reviewing the change, `deploy-local leave-request` asks the local app to
reload and deploy that workflow key. This endpoint is available only in
loopback-bound `local-demo` mode.

From the repository root, run the platform verification suite, then start the
workspace:

```powershell
.\code\platform\mvnw.cmd -f .\code\platform\pom.xml verify
.\run-app.ps1
```

Open `http://127.0.0.1:8080`. The app scans `code/workflows/` at startup. If you
change a definition while it is running, restart it to lint, validate its
registered actions, compile BPMN, and deploy the changed version. Unchanged
definitions are not redeployed. Existing instances stay on the definition
version with which they started.

## 5. Use the workspace

1. **Overview:** choose a workflow and start a request.
2. **My work:** select the matching local demo identity, find tasks by search,
   claim selected tasks, and complete the schema-driven form. Due dates are
   shown when an SLA deadline is configured.
3. **Cases:** search for an instance and inspect its stage and activity history.
4. **Operations:** pause, resume, reassign, cancel, or export the local action
   log. Each administrative action is written to the H2 audit table.

The identity selector is a test convenience. It does not enforce authorization.
The local demo registers mock refund actions only; a new service task requires
an action handler registered by the host application before startup. Do not
expose local-demo mode to other machines or use it for production work.

## 6. Maintain versions safely

Change the workflow files in Git and increment `metadata.version` for a reviewed
behavior change. Review CWM and form diffs together. On startup, changed BPMN
creates a new Flowable definition version; the running cases remain on the old
version, and new cases use the newest version. Keep generated BPMN and local H2
data out of Git. Have the workflow owner review roles, routing, data
classification, outcomes, and migration uncertainty before sharing the change.

## Current boundaries

This quick start covers the local workflow author and runtime path. Local
service-step retries and dead-letter replay are implemented; the complete SRS
still includes production authentication and authorization, business-calendar
SLAs, attachments, CI deployment, and additional workflow step types; see
[`traceability.csv`](../design/srs/traceability.csv) for implementation state.

## Blueprint PDF migration

Use dry-run to inspect cases and planned paths before writing generated files:

```powershell
.\w6w.ps1 migrate-pdf "Pega Blueprint - Travel Management Customer Service.pdf" --dry-run
.\w6w.ps1 migrate-pdf "Pega Blueprint - Travel Management Customer Service.pdf" --out code/workflows/domains/migrated/travel-management
.\w6w.ps1 migrate-pdf "Pega Blueprint - Travel Management Customer Service.pdf" --rules .\mapping-overrides.yaml --dry-run
```

Each case gets its own folder with a CWM draft (when eligible), form and data
schema stubs, scenario stub, and migration report. Resolve manual actions,
validate and scenario-test the CWM, then review and commit it. The adapter
currently accepts Blueprint PDFs; `--rules` can override the versioned default
step mappings. It does not decode proprietary `.blueprint` binaries.

## Decision table files

Decision tables use versioned `w6w/decision/v1` YAML. For example,
`code/workflows/domains/customer-service/refund-processing/decisions/refund-eligibility.yaml`
shows the input, output, hit policy and ordered rules. Compile a table to
deterministic DMN XML with:

```powershell
.\w6w.ps1 compile-decision code/workflows/domains/customer-service/refund-processing/decisions/refund-eligibility.yaml
```

The command writes generated DMN under the Git-ignored `code/workflows/build/decisions/` folder. A workflow decision step must
reference its table in the `decision` field and declare outcomes. Decision
execution and BPMN routing are supported when the table has one declared
string `outcome` output. The Flowable runtime evaluates the version-pinned DMN
table asynchronously and routes the result through the declared outcome. Other
table shapes fail validation; decision-table test rows are not yet run as a
separate CI gate.
