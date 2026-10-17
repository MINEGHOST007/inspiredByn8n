# W6W Demo Video Creation Guide

## Purpose

This guide produces a 10 to 12 minute proof-of-concept video for W6W, the embedded workflow utility for Tier 1 processes. The video explains the business problem, shows the Git-versioned workflow definition, runs a real approval workflow in the local application, and demonstrates where migration and governance fit.

The video must leave the audience with three clear conclusions:

1. Simple approval and intake workflows do not need to consume Pega capacity intended for complex case management.
2. W6W makes a Tier 1 workflow reviewable in Git and executable inside the host application.
3. Migration produces a reviewed skeleton, not an unsafe promise of full Pega application conversion.

## Recording setup

- Use Windows at 1920x1080 display scale.
- Start from the repository root.
- Set `JAVA_HOME` to a Java 17+ JDK.
- Keep the browser on `http://127.0.0.1:8080`.
- Close unrelated applications and notifications.
- Use a wired or quiet microphone. Record at approximately 130 to 150 words per minute.
- Use the generated Playwright video as the screen track and record narration separately if the capture environment does not provide microphone mixing.
- Do not show secrets, real customer data, Pega credentials, or production URLs.

Start the application:

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21.0.12.1"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
.\run-app.ps1
```

If port 8080 is occupied, stop the previous local W6W process or use another port and set `W6W_BASE_URL` for the recording command.

Run the automated recording from `code/tests/acceptance`:

```powershell
cd code/tests/acceptance
npm install
npx playwright install chromium
npm run demo:video
```

The video and screenshots are written under `artifacts/demo_presentation/video-output/`. They are generated evidence and should not be committed unless a release requires them.

## Narrated video assembly

After generating the Roger and Jessica ElevenLabs MP3 files, use FFmpeg to combine them with the screen recording. The repository's Playwright recorder produces the screen track; the final MP4 files are:

```text
artifacts/demo_presentation/w6w-demo-roger.mp4
artifacts/demo_presentation/w6w-demo-jessica.mp4
```

The exact assembly used for the current recordings was:

```powershell
$ffmpeg = (Get-ChildItem "$env:LOCALAPPDATA\Microsoft\WinGet\Packages" -Recurse -Filter ffmpeg.exe | Select-Object -First 1 -ExpandProperty FullName)
$video = (Get-ChildItem .\artifacts\demo_presentation\video-output -Recurse -Filter video.webm | Sort-Object LastWriteTime -Descending | Select-Object -First 1 -ExpandProperty FullName)

& $ffmpeg -y -i $video -i "$env:USERPROFILE\Documents\roger.mp3.mp3" -filter:v "setpts=0.9437*PTS" -map 0:v:0 -map 1:a:0 -c:v libx264 -preset medium -crf 22 -pix_fmt yuv420p -c:a aac -b:a 192k -shortest .\artifacts\demo_presentation\w6w-demo-roger.mp4
& $ffmpeg -y -i $video -i "$env:USERPROFILE\Documents\jessica.mp3.mp3" -filter:v "setpts=0.8723*PTS" -map 0:v:0 -map 1:a:0 -c:v libx264 -preset medium -crf 22 -pix_fmt yuv420p -c:a aac -b:a 192k -shortest .\artifacts\demo_presentation\w6w-demo-jessica.mp4
```

The video speed factors are based on the measured audio lengths. Recalculate them if new narration files are generated. Verify the final files with `ffprobe` and confirm that each contains one H.264 video stream and one AAC audio stream.

## Storyboard and speaker notes

### Page 1 - The problem: expensive platform, simple work

**On screen:** Title card or the W6W Overview page. Show the words `Tier 1 workflow utility`, `Git-versioned`, and `Embedded in the application`.

**Duration:** 45 seconds.

**Speaker notes:**

> Many organizations use a powerful case-management platform for every kind of process. That works well when the process needs deep decisioning, complex case hierarchy, or rigorous case governance. The problem is the long tail: simple requests, approvals, intake, and notifications are forced through a platform that is expensive and specialist-heavy for the value of the work.
>
> W6W addresses that narrow gap. It is not a replacement for Pega. It is an application-level workflow utility for Tier 1 processes, while complex and differentiated case management remains in Pega.
>
> The promise we are testing is simple: a small workflow should move from an approved definition to a running process in days, not weeks.

**Transition:** Move from the problem statement to the boundary and architecture.

### Page 2 - The boundary: keep Pega for the hard cases

**On screen:** Show a simple two-column diagram: `Pega` for complex case management and `W6W` for Tier 1 intake/approval/request flows.

**Duration:** 55 seconds.

**Speaker notes:**

> The boundary is deliberate. Pega stays the right home for complex case management, rules, adaptive decisioning, deep case hierarchies, and heavily regulated processes. W6W accepts a published Tier 1 rubric: limited stages and steps, shallow branching, explicit roles, declared data, and no hidden approximation of unsupported behavior.
>
> That boundary is important because the goal is not to build a second Pega. The goal is to right-size the platform used for simple work and free Pega capacity for the work where it is differentiated.
>
> The local proof of concept uses Java, Spring Boot, Flowable embedded in the application process, and a local H2 database. The same Engine Port is intended to hide the engine from the host application API.

### Page 3 - The Git-first architecture

**On screen:** Show the repository folders and the flow `CWM YAML -> compiler -> Flowable BPMN/DMN -> runtime UI/API`.

**Duration:** 70 seconds.

**Speaker notes:**

> The source of truth is not a database export or a designer screen. It is a small, reviewable workflow package in Git.
>
> A workflow package contains the Canonical Workflow Model YAML, JSON data schemas, schema-driven forms, decision tables, and scenario tests. The compiler creates BPMN and DMN as build output. Generated engine files are not the source of truth.
>
> This gives a developer a normal engineering workflow: make a change, review the diff, validate it in CI, run the scenario, and deploy the reviewed definition. It also gives a business analyst a generated diagram and summary to review without needing to understand every engine detail.

### Page 4 - The reference workflow: Leave Request

**On screen:** Open `code/workflows/domains/people/leave-request/workflow.cwm.yaml` in the editor, then show its forms and data schema.

**Duration:** 70 seconds.

**Speaker notes:**

> This is the reference Tier 1 workflow for the demo. It has an employee intake task, a manager approval task, and two explicit resolutions: Approved and Rejected.
>
> Notice what is visible in the definition: the workflow key and semantic version, the owner, data classification, roles, stages, assignees, forms, outcomes, and terminal resolution states.
>
> There is no hidden approver inferred from a label and no silent conversion of an unsupported construct. The workflow is intentionally small enough to review in a pull request and concrete enough to execute end to end.

### Page 5 - From definition to running application

**On screen:** W6W Overview page showing the workflow catalog and `Leave Request`.

**Duration:** 55 seconds.

**Speaker notes:**

> The local workspace discovers the versioned definitions from `code/workflows`. At startup it validates the workflow, registers actions, compiles the CWM to BPMN, and deploys the definition to the embedded Flowable engine.
>
> The application gives the user a workflow catalog rather than exposing engine internals. The same runtime exposes REST and OpenAPI endpoints for a host application to integrate with its own shell and identity provider.
>
> This is the first tangible proof of the economic argument: the workflow is a small package, but it is a real executable process with task state, history, and resolution.

### Page 6 - Start the employee request

**On screen:** Click `Start a workflow`, select `Leave Request`, and complete the employee form.

**Duration:** 75 seconds.

**Speaker notes:**

> I am now acting as the employee. The form is rendered from JSON Schema and UI hints; it is not a bespoke screen hard-coded for this one workflow.
>
> I will enter synthetic data: an employee name and email, a date range, a leave type, and a reason. The runtime validates the submission at the workflow boundary before creating the case.
>
> When I start the workflow, W6W creates a business-keyed workflow instance. Business keys make starts idempotent, so a retry from the calling application does not create a duplicate case.

### Page 7 - Worklist and manager approval

**On screen:** Open `My work`, complete the employee task, switch identity to the manager, open the approval task, and choose `approve`.

**Duration:** 100 seconds.

**Speaker notes:**

> The task appears in the role-filtered worklist. This is the human-task part of the workflow: claim, complete, route to the next stage, and preserve the task history.
>
> I will complete the employee task, then switch to the synthetic manager identity. In a production host, this selector is replaced by the host identity provider; in this local demo it lets us exercise multiple role queues without a login service.
>
> The manager receives an explicit approval outcome. Approve routes to the Approved resolution; Reject routes to the Rejected alternate stage. The important point is that the transition is declarative, reviewable, and testable.

### Page 8 - Case history and auditability

**On screen:** Open `Cases`, show the completed Leave Request, open details, and show the timeline and resolution.

**Duration:** 65 seconds.

**Speaker notes:**

> The case timeline shows the workflow as a sequence of activities rather than a black-box result. We can see the intake task, the approval task, the actors, and the final resolution.
>
> The local application also records lifecycle and operator events in its audit feed and exposes CSV and JSON export paths. This is enough to demonstrate the audit shape for the proof of concept.
>
> Production audit retention, tamper evidence, data residency, and compliance controls still require host decisions and operational hardening. We show the evidence honestly instead of calling local H2 and demo identities production security.

### Page 9 - Migration: Blueprint to reviewed skeleton

**On screen:** Show the migration command, generated CWM package, form/data stubs, and migration report. Use the supplied PDF fixture or an approved synthetic Blueprint representation.

**Duration:** 80 seconds.

**Speaker notes:**

> The migration utility sits before the normal Git workflow. It extracts a simplified Blueprint representation, maps supported constructs into an intermediate model, generates a CWM draft, creates form and data stubs, and writes a conversion report.
>
> The output is a skeleton for human review. Converted elements, approximations, unsupported constructs, confidence, and manual follow-up are visible. Unknown elements are reported rather than silently turned into something else.
>
> The proprietary `.blueprint` binary format is not publicly documented, so this local implementation uses the PDF path and versioned mapping rules. A supported binary adapter can be added only after legal and vendor-format decisions. The architecture keeps that adapter isolated so the canonical model remains stable.

### Page 10 - Governance, value, and next gates

**On screen:** Show the traceability report, CI validation, and the final completed workflow screen.

**Duration:** 80 seconds.

**Speaker notes:**

> The proof of concept closes the loop: a Tier 1 definition is stored in Git, validated, compiled, started, assigned, completed, resolved, and audited through an application-facing workspace.
>
> The traceability report separates implemented, partial, and not-yet-implemented requirements. That distinction matters. Local demo mode is not presented as production authentication, and PDF migration is not presented as full Pega conversion.
>
> The next gates are PostgreSQL and restart testing, real identity and authorization, production CI promotion, performance and resilience evidence, richer routing and SLA policies, additional migration adapters, and a decision about which real Tier 1 processes should move from Pega.
>
> The success metric is not feature count. It is whether an eligible simple process can be delivered and changed in days while complex case work stays on Pega.

## Closing line

> W6W is a deliberately bounded workflow utility: Git-versioned definitions, an embedded runtime, reusable task UI, and a reviewed migration path for simple processes. It gives teams a faster path for the long tail without pretending to replace the platform built for the hard cases.

## Post-production checklist

- Add a title card with the W6W name and the phrase `Tier 1 workflow utility`.
- Add chapter labels matching the ten pages.
- Keep the screen recording and narration synchronized around the form submission and approval.
- Add captions from the speaker notes.
- Display a small `LOCAL POC` label whenever the identity selector or local H2 database is visible.
- End with the repository URL or internal project location, plus the warning that production deployment is not yet claimed.
- Archive the final video with the exact commit SHA used for the recording.
