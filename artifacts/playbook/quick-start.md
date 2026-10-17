# Create and maintain a workflow: quick start

This guide is for people who need to describe, build, test, or maintain a small
workflow in W6W. You can understand the process before you read any YAML. The
current project is operated from the repository and PowerShell; it does not yet
have a visual workflow builder or a guided authoring screen.

## The short version

1. Write down the process in the [workflow intake worksheet](workflow-intake-template.md).
2. Check that it is a small Tier 1 process and identify an owner and the people
   who do each task.
3. Copy the existing refund example into a new workflow folder and replace its
   stages, tasks, roles, fields, and actions with the reviewed process.
4. Add a scenario for each important path, including a rejection or exception
   when one exists.
5. Run the local checks, ask the process owner to review the readable summary,
   then submit the workflow and its tests together for review.

## First, understand the workflow words

| Word | Meaning | Example from Refund Processing |
|---|---|---|
| Workflow | The full process from start to a final result | Refund Processing |
| Stage | A named part of the process | Request Intake |
| Step | One action in a stage | Receive Request |
| Role | A group of people allowed to do a task | `service_agent` |
| Form | The fields a person fills in | `forms/request-intake.json` |
| Action | A named operation the host application runs | `refund.validateRequest` |
| Outcome | The choice made at a task | `approve` or `reject` |
| Scenario | A repeatable example that walks through a path | Standard Refund Approval |

A simple workflow usually reads like a checklist: collect information, check it,
ask the right person to decide, do the approved work, then finish. Model
decisions as named outcomes such as `approve` and `reject`; keep each outcome's
destination clear.

## Before editing: agree on the process

The process owner and author should be able to answer these questions in plain
language:

- What event starts the process?
- Who performs each human task? Which role should receive it?
- What information is needed, and which fields are sensitive?
- What decisions can a person make? Where does each choice go?
- Which actions happen automatically, and which registered application action
  performs them?
- What does success look like? What does rejection or failure look like?
- Who owns the workflow and approves future changes?

Write answers in the [intake worksheet](workflow-intake-template.md). If an
answer is unknown, mark it as an open question; do not guess a role, permission,
or business rule in the workflow definition.

## Create or change a workflow

### 1. Start from the example

Use the refund package as a reference:

`code/workflows/domains/customer-service/refund-processing/`

It contains the definition (`workflow.cwm.yaml`), data schema (`schemas/`),
human task forms (`forms/`), and runnable examples (`tests/scenarios/`). Copy
the folder to a new domain and workflow name before editing. Keep the definition,
forms, schema, and scenarios together so reviewers can find everything for the
process in one place.

### 2. Write the stages and tasks

Edit `workflow.cwm.yaml`. Give every workflow, stage, and step a stable,
lowercase ID with hyphens, such as `manager-approval`. Use readable names for
people who review the process. Update `metadata.key`, `name`, `version`,
`owner`, `dataClassification`, and `description` for the new process.

For the current executable subset, use these step types:

- `userTask`: a person fills in or reviews a form. Set `assignee.role` (or a
  supported explicit user assignment).
- `approval`: a person approves or rejects. Set an assignee and named outcomes.
- `serviceTask`: call a registered host action by its exact `action` name.
- `end`: finish the process. Give the final stage a `resolution` such as
  `Approved` or `Denied`.

For example, a human approval step follows this shape:

```yaml
- id: manager-approval
  name: "Manager Approval"
  type: approval
  assignee:
    role: manager
  outcomes:
    approve: next
    reject: stage:request-denied
```

Only point an outcome at a stage that exists. Keep a normal success stage and
an alternate ending explicit. The example's `eligibility-check` and
`refund-denied` stages show this pattern.

### 3. Keep fields and forms together

Put the workflow's data contract in `schemas/data.schema.json`. Put the fields
shown to a person in a JSON form under `forms/`, then reference that form from
the step. Use clear field names and descriptions. Ask the data owner to confirm
classification and avoid collecting information the process does not need.

### 4. Add scenarios for real paths

Add YAML files under `tests/scenarios/`. A scenario describes initial data,
starts the workflow, completes tasks with sample form data and outcomes, and
records the intended final resolution. Use the two refund scenarios as examples:

- `standard-approval.yaml` covers the approval path.
- `policy-denied.yaml` covers an alternate denial path.

Use invented sample data. Include at least one scenario for every important
outcome, especially rejection. The current runner uses registered mock actions,
so scenarios demonstrate local execution, not live integration with payment,
email, or other production systems. It currently selects the first active task
and only checks that the workflow completes when a `finalResolution` is present;
it does not yet verify `stepId`, `expectedNextStage`, or that the actual final
resolution matches the scenario's stated value. Treat those fields as review
guidance until the scenario assertions are strengthened.

### 5. Run the local demo and checks

From the repository root, with Java 17+ installed, run:

```powershell
.\run-local.ps1
```

This builds and verifies the project, then runs the two refund scenarios using
the local H2 SQL file database. Successful scenario results are printed in the
terminal and written to `artifacts/test_results/results.xml`; local database
files are written under `data/`.

To run only the Maven verification suite:

```powershell
.\code\platform\mvnw.cmd -f .\code\platform\pom.xml verify
```

**Important:** `run-local.ps1` currently points to the refund example. It does
not automatically discover or run a newly copied workflow. Add reusable
scenario execution for arbitrary workflow folders before treating this command
as validation of a new workflow. The current test suite does exercise parser,
linter, compiler, runtime, migration, and the sample workflow; see
`artifacts/design/srs/traceability.csv` for implementation status.

## Maintain a workflow safely

- Keep the stable workflow key. When behavior changes, increment `metadata.version`
  using Semantic Versioning and include the scenario updates in the same change.
- Ask the owner to review roles, fields, decisions, outcomes, and both terminal
  resolutions. Have platform reviewers check action names and supported types.
- Explain the change in the package README in business terms, and keep the
  migration/source reference current when applicable.
- Do not edit generated BPMN as the source of truth; CWM YAML is authoritative.
- Keep a small change focused. Add a scenario that would fail if the intended
  behavior were accidentally removed.
- Treat unsupported or low-confidence migration findings as questions for a
  human reviewer, not as permission to invent process behavior.

## What to do when something fails

| Symptom | First check |
|---|---|
| YAML will not load | Check indentation, quote strings containing punctuation, and compare with the sample. |
| Workflow will not compile | Confirm every step uses a supported type and every `serviceTask` action is registered. |
| Human task cannot be assigned | Confirm the role/user is present and the step has `assignee`. |
| Outcome points nowhere | Check the spelling of `stage:<stage-id>` and ensure that stage exists. |
| A scenario expects a task but none is active | Check the previous step's outcome and path through the stages. |
| Local demo cannot start | Confirm Java 17+ is on `PATH`; the first Maven run also needs network access to download dependencies. |

For a question about whether a process belongs in this engine, use the
[Tier 1 eligibility rubric](../design/rubric/tier-1-eligibility.md). For exact
field and step support, use the [CWM v1 contract](../design/architecture/cwm-v1.md).

## Current limits to keep in mind

This is a developer-facing local proof of concept, not yet a no-code authoring
product. The current local workflow path is CWM YAML + forms + scenarios and an
embedded H2 database. The README's SRS tracks requirements that are not yet
implemented, including the full UI, REST API, general-purpose workflow CLI,
identity/security controls, production database profile, and several advanced
workflow step types. Do not promise these capabilities based on this guide.
