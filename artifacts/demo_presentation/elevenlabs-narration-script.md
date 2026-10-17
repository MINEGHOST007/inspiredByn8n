# W6W ElevenLabs Narration Script

## How to use this file

Generate each chapter as a separate ElevenLabs audio clip. Paste only the text inside the matching `SPEAK` block into ElevenLabs. Do not paste the headings, timing notes, or production notes.

Use one consistent voice and model for all eleven clips. Recommended delivery: clear, calm, confident, approximately 135 words per minute. Leave a short silence at the beginning and end of every generated clip when assembling the final video.

Recommended output names:

```text
01-problem.mp3
02-boundary.mp3
03-architecture.mp3
04-workflow-definition.mp3
05-runtime.mp3
06-intake.mp3
07-worklist-approval.mp3
08-case-history.mp3
09-migration.mp3
10-governance.mp3
11-closing.mp3
```

## Pronunciation notes

- Say `W6W` as: `double-u six double-u`.
- Say `Pega` as: `pay-gah`.
- Say `CWM` as: `canonical workflow model` the first time, then `C W M` if needed.
- Say `BPMN` as: `B P M N`.
- Say `DMN` as: `D M N`.
- Say `OIDC` as: `O I D C`.
- Say `H2` as: `H two`.
- Say `Tier 1` as: `tier one`.

## Chapter 01 - Problem and promise

**Target duration:** 55 seconds.

**SPEAK**

Many organizations use a powerful case-management platform for every kind of process. That is the right choice when work needs deep decisioning, complex case hierarchy, or rigorous regulatory governance.

The problem is the long tail: simple requests, approvals, intake flows, and notifications are often forced through a platform that is expensive and specialist-heavy for the value of the work.

W6W, pronounced double-u six double-u, addresses that narrow gap. It is not a replacement for Pega, pronounced pay-gah. It is an application-level workflow utility for simple, tier-one processes, while complex and differentiated case management remains in Pega.

The promise we are testing is simple: an eligible workflow should move from an approved definition to a running process in days, not weeks.

**END SPEAK**

## Chapter 02 - Boundary and division of responsibility

**Target duration:** 60 seconds.

**SPEAK**

The boundary is deliberate.

Pega remains the right home for complex case management, adaptive decisioning, deep case hierarchies, advanced rules, and heavily regulated processes.

W6W is for the long tail: limited stages, limited steps, explicit roles, declared data, straightforward approvals, and lightly branching request flows.

The Tier-one rubric is a protection against scope creep. Unsupported constructs are reported or rejected. They are not silently approximated into something that looks runnable but behaves incorrectly.

This means W6W is not trying to become a second Pega. It is a right-sized utility that reduces delivery friction for simple work and frees Pega capacity for the work where Pega is differentiated.

The local proof of concept uses Java, Spring Boot, Flowable embedded in the application process, and a local H two database. In a host application, the Engine Port keeps the workflow engine behind a stable application-facing contract.

**END SPEAK**

## Chapter 03 - Git-first architecture

**Target duration:** 70 seconds.

**SPEAK**

The source of truth is not a database export and it is not an uncontrolled designer screen. It is a small, reviewable workflow package in Git.

The package contains the Canonical Workflow Model, or C W M, in YAML. It also contains JSON data schemas, schema-driven forms, decision tables, and scenario tests.

The compiler transforms the canonical model into B P M N and D M N build artifacts. Those generated engine files are outputs. They are not the source of truth.

This creates a familiar engineering lifecycle. A developer changes a definition, reviews the diff, validates it in CI, runs the scenario tests, and promotes the reviewed version.

A business analyst can review a generated diagram and plain-language summary. A task user gets a normal worklist and form. The host application integrates through the runtime API instead of depending directly on Flowable types.

That is the key architectural idea: keep the workflow definition portable and human-reviewable, while keeping the execution engine replaceable behind the port.

**END SPEAK**

## Chapter 04 - The reference workflow

**Target duration:** 65 seconds.

**SPEAK**

This is the reference tier-one workflow used in the proof of concept: a leave request.

The employee enters leave details. A manager reviews the request and chooses approve or reject. The process then reaches an explicit approved or rejected resolution.

The workflow definition declares the key, semantic version, owner, data classification, roles, stages, assignees, forms, outcomes, and terminal states.

Nothing important is hidden in a label. The manager role is declared. The outcome routes are declared. The data contract is declared. The form is a separate reviewed file.

That makes the process small enough to review in a pull request and concrete enough to execute end to end.

This is the practical meaning of Git-versioned workflow orchestration: the behavior can be inspected, compared, tested, and changed using the same review discipline as application code.

**END SPEAK**

## Chapter 05 - From definition to running application

**Target duration:** 60 seconds.

**SPEAK**

At startup, the local workspace discovers workflow definitions under the workflows directory.

W6W validates the canonical model, checks the data schema, registers the required actions, compiles the workflow to B P M N, and deploys the definition to the embedded Flowable runtime.

The user sees a workflow catalog and task workspace. The user does not need to understand engine deployment identifiers or engine-specific APIs.

The runtime exposes application-facing REST endpoints and OpenAPI documentation. A real host application can use those contracts inside its own user interface and identity model.

This is the first tangible proof of the business case. A workflow package remains small and reviewable, but it becomes a real executable process with task state, history, routing, and resolution.

**END SPEAK**

## Chapter 06 - Employee intake

**Target duration:** 70 seconds.

**SPEAK**

I am now acting as the employee.

The form is rendered from JSON Schema and user-interface hints. It is not a bespoke screen hard-coded for this one process.

I will enter synthetic data: an employee name and email address, a first day, a last day, a leave type, and a reason.

The runtime validates the submission at the workflow boundary before creating the case. This prevents incomplete or invalid data from entering the process.

When I start the workflow, W6W creates a business-keyed instance. That business key is important because it makes retries idempotent. If the calling application repeats the same start request, W6W returns the existing case instead of creating a duplicate.

The employee request is now in the workflow and ready for the next role.

**END SPEAK**

## Chapter 07 - Worklist and manager approval

**Target duration:** 95 seconds.

**SPEAK**

The employee task appears in the role-filtered worklist.

This is the human-task part of the workflow: find the task, claim it when needed, complete the form, route the process forward, and preserve the task history.

I will complete the employee task first. The workflow then creates the manager approval task.

For this local demonstration, I am switching to a synthetic manager identity. In a production host, this selector would be replaced by the organization identity provider and its authorization rules.

The manager sees the approval task and two explicit outcomes. Approve routes to the approved resolution. Reject routes to the rejected alternate stage.

The important point is that the transition is declarative, reviewable, and testable. The workflow does not depend on a hidden manual handoff or an operator remembering the next step.

The manager approves the request, and the engine advances the case to its terminal resolution.

**END SPEAK**

## Chapter 08 - Case history and auditability

**Target duration:** 65 seconds.

**SPEAK**

The case is now complete.

The Cases view shows the workflow status, the current or final stage, and the resolution. The detail view shows the activity timeline instead of presenting the result as a black box.

We can see the intake task, the manager approval, the actors, and the final approved state.

The local application also records lifecycle and operator events in its audit feed and exposes CSV and JSON export paths.

This demonstrates the shape of the audit trail for the proof of concept. Production retention, tamper evidence, data residency, and compliance controls still require host decisions and operational hardening.

The distinction is intentional. Local H two and demo identities prove the workflow behavior; they do not claim to be production security controls.

**END SPEAK**

## Chapter 09 - Migration to a reviewed skeleton

**Target duration:** 90 seconds.

**SPEAK**

The migration utility sits before the normal Git workflow.

It extracts a simplified Blueprint representation, maps supported constructs into an intermediate model, generates a C W M draft, creates form and data stubs, and writes a conversion report.

The output is a skeleton for human review. Converted elements, approximations, unsupported constructs, confidence, and manual follow-up are visible.

Unknown elements are reported rather than silently turned into a different task type.

The proprietary dot-blueprint binary format is not publicly documented. The current local implementation therefore uses the Blueprint PDF path and versioned mapping rules. A binary adapter can be added only after legal and vendor-format decisions are complete.

This limitation is part of the design, not a hidden defect. The adapter is isolated, while the canonical model and runtime remain stable.

The migration promise is therefore honest: W6W can accelerate the creation of a runnable Tier-one skeleton. It does not claim to reproduce every rule in an existing Pega application automatically.

**END SPEAK**

## Chapter 10 - Governance and next gates

**Target duration:** 85 seconds.

**SPEAK**

The proof of concept closes the loop: a tier-one definition is stored in Git, validated, compiled, started, assigned, completed, resolved, and audited through an application-facing workspace.

The traceability report separates implemented, partial, and not-yet-implemented requirements. That distinction matters because local demo mode is not production authentication, and PDF migration is not full Pega conversion.

The next engineering gates are PostgreSQL and restart testing, real identity and authorization, production CI promotion, performance and resilience evidence, richer routing and service-level policies, additional migration adapters, and a decision about which real tier-one processes should move from Pega.

The success metric is not feature count. It is whether an eligible simple process can be delivered and changed in days while complex case work stays on Pega.

**END SPEAK**

## Chapter 11 - Closing

**Target duration:** 40 seconds.

**SPEAK**

W6W is a deliberately bounded workflow utility.

It combines Git-versioned definitions, an embedded runtime, reusable task forms, worklists, case history, and a reviewed migration path for simple processes.

It gives application teams a faster path for the long tail without pretending to replace the platform built for the hard cases.

The next step is to validate this proof of concept against several real tier-one workflows, measure delivery time against the Pega baseline, and use those results to decide whether W6W should move from local demonstration to a controlled pilot.

Thank you.

**END SPEAK**

## Assembly timing map

Use these as target durations, not guaranteed generated-audio durations:

| Clip | Target | Purpose |
|---|---:|---|
| 01 | 0:55 | Problem and promise |
| 02 | 1:00 | Pega and W6W boundary |
| 03 | 1:10 | Git-first architecture |
| 04 | 1:05 | Leave Request definition |
| 05 | 1:00 | Runtime startup and catalog |
| 06 | 1:10 | Employee intake |
| 07 | 1:35 | Worklist and approval |
| 08 | 1:05 | Timeline and audit |
| 09 | 1:30 | Migration skeleton |
| 10 | 1:25 | Governance and next gates |
| 11 | 0:40 | Closing |
| **Total** | **11:35** | Before editing pauses and transitions |

## ElevenLabs generation guidance

Generate clips separately rather than pasting all chapters into one generation. This gives the Playwright video and the voice track stable chapter boundaries.

The Playwright recorder uses the target durations above by default. After generating the clips, you can synchronize the recording to their measured durations by passing a JSON object in `W6W_AUDIO_DURATIONS`:

```powershell
$env:W6W_AUDIO_DURATIONS = '{"problem":58,"boundary":62,"architecture":71,"definition":64,"runtime":59,"intake":73,"approval":98,"history":66,"migration":93,"governance":87,"closing":41}'
cd code/tests/acceptance
npm run demo:video
```

The values are seconds. Use the actual duration of each selected ElevenLabs clip, not the target estimate.

For each clip:

1. Select the same voice, model, language, and stability/style settings.
2. Paste only the matching `SPEAK` text.
3. Generate two versions and keep the clearer pronunciation.
4. Listen specifically for W6W, Pega, C W M, B P M N, D M N, O I D C, and H two.
5. Rename the selected file using the names above.
6. Record the actual duration of each file before combining it with the video.

Do not paste `[pause]`, headings, quotation marks, or the `END SPEAK` marker into the narration. Add pauses during editing between clips. Exact timing should be controlled by the video editor or FFmpeg, not assumed from text-to-speech punctuation.
