I'll research Pega, its Blueprint tooling, and the current open-source workflow landscape.# Pega, the problem it creates for simple workflows, and the best open-source engine to replace it for those

## 1. What Pega is

Pega describes itself as a low-code platform for AI-powered decisioning and workflow automation, with case management at its center. It is model-driven: you configure applications through a case life cycle and rules, not by writing code.

**Core concepts** (these matter because your migration utility has to map them):

- **Case life cycle.** The building blocks are stages, processes, and steps. Stages are the first level of organizing work, and processes contain a series of steps.
- **Step types.** Collect information steps need user input (often called assignments or tasks), while automation steps are performed by the system.
- **Resolution and alternate stages.** Every case type should have at least one resolution stage, where the case finishes. Alternate stages handle exceptions so a case can still be resolved.
- **SLAs.** You can attach service-level agreements, each with a goal and a deadline, to stages, processes, and steps.
- **Routing.** Work is assigned to users, work groups, or by business logic.
- **Personas and access.** Roles and access groups are generated from personas.

**Newer capabilities:**

- **Pega Blueprint.** A SaaS application that combines generative AI with Pega best practices to design workflows. It generates case types, life cycles, data objects, and personas, which you can edit and then export. You import the exported file into Pega Platform using "Build from a Blueprint".
- **Infinity '25 (September 2025).** Blueprint can now analyze legacy assets such as documents, process diagrams, and source code. Imported Blueprints auto-generate workflows, UX views, data models, and sample data. Pega also positions end-to-end workflows made of both agentic and deterministic steps, plus an Agentic Process Fabric to govern agents.
- **Auditability.** One independent review notes that when a regulator asks how a decision was made, Pega can show the trail. This is where it earns its price.

## 2. The problem statement

Pega is excellent at complex, regulated case work, but it is a poor economic fit for simple processes.

- **Cost and opacity.** Pega publishes no dollar figures. Licensing is sales-led and scales with deployment size and usage. Third-party estimates are only indicative, but one review puts licensing at roughly $100K–$500K+ per year and typical implementations at 6–18 months. Pricing comes in per-case rates, with minimums and add-ons that shape the real cost. A reviewer sums up the simple-process case this way: implementation is short, but licensing stays costly and may not justify the use case.
- **Talent and delivery friction.** Certified talent is hard to hire, implementations run long, and the Constellation UI constrains customization.
- **Lock-in.** Pega's proprietary technology can make it hard to switch platforms later.

**Problem statement for your project:**

> Tier 1 "long tail" processes (simple approvals, intake, requests, linear or lightly branching flows) are being built on a platform designed for complex, regulated case management. This costs more in licensing, specialist labor, and calendar time than their business value justifies, and it consumes scarce Pega capacity that should go to differentiated Tier 2/3 cases. We need an in-application, Git-versioned workflow capability that delivers simple workflows in days, plus a migration utility that converts simplified Pega blueprints into running workflows on that capability.

This also tells you the right **scope boundary**. If a process needs deep decisioning, complex case hierarchies, or heavy regulatory audit, it stays in Pega. Define explicit Tier 1 eligibility criteria and publish them, or the utility will creep toward being a second Pega.

## 3. Evaluation criteria

Your requirements imply these filters:

1. **Embeddable** in your application ecosystem, ideally in-process and in the same transaction.
2. **Definitions are text files** that diff and review cleanly in Git.
3. **Human tasks, timers/SLAs, and routing** are first-class, because Pega workflows are mostly assignments and approvals.
4. **A truly open-source license**, so no future licensing surprise.
5. **A target model close to Pega's** (stages, steps, tasks, gateways), which makes the migration generator much easier to write.

## 4. The open-source options

**Flowable (my primary recommendation if your stack is JVM)**
- It is Apache 2.0 licensed and can run embedded in a Java application or as a standalone service, with Spring integration.
- It executes BPMN 2.0 processes, CMMN 1.1 cases, and DMN decision tables. That maps neatly onto Pega: stages/steps to BPMN, case behavior to CMMN, decision tables to DMN.
- Definitions are XML files that live in Git.
- Caveat: version 7 removed the graphical UI apps from the open-source distribution. You build your own task UI, which is natural when embedded in an application. One marketplace listing shows a Flowable 8.0.0 release in August 2026, so verify the current version and its Spring Boot/Java baseline on the release page.

**Operaton (the Camunda 7 fork)**
- Camunda 7 Community Edition has had no release since 14 October 2025. Operaton began as a fork in October 2024 and is maintained by a community without a single company.
- It is a reasonable choice if you already have Camunda 7 skills, but Flowable has a cleaner long-term story for a new build.

**Camunda 8 (avoid for this use case)**
- It is source-available under the Sustainable Use License, not OSI open source. Since 8.6, a license is required to run it in production.
- It also can't be embedded: Zeebe cannot be embedded because of its design. That fails your "utility inside the app" requirement and recreates the licensing problem you're escaping.

**Conductor OSS (best if you want JSON and a polyglot environment)**
- It is fully open source under Apache 2.0. Workflows are defined in JSON and managed with versioning.
- It supports human-in-the-loop: the HUMAN task type pauses a workflow until an approval, rejection, or data input arrives via API.
- Trade-off: it is a separate server you operate, not an embedded library. You would also build your own worklists, SLA escalation, and routing.

**Temporal (best durability, worst fit for your migration generator)**
- Self-hosted Temporal is MIT-licensed. Workflows are written as ordinary code. It is excellent for reliability.
- Humans, SLAs, and worklists are things you build yourself. The migration tool would also have to emit code, not declarative definitions, which is harder to generate, review, and non-developer-edit.

**CNCF Serverless Workflow spec (a good basis for your own DSL)**
- It is a vendor-neutral, community-driven DSL for workflows, hosted by the CNCF as a Sandbox project, with SDKs for .NET, Go, Java, PHP, Python, Rust, and TypeScript.
- Runtime maturity varies by language. For example, the Go SDK provides only a partial reference runner. It has no first-class notion of human worklists, so you would still build that layer.

**Not suitable:**
- Airflow is DAG-based batch orchestration built for data pipelines.
- n8n is fair-code and not OSI-approved.
- Neither matches human-centric case flows.

### Quick fit summary

| Option | Embeddable | Git-friendly | Human tasks/SLA built in | Migration target ease |
|---|---|---|---|---|
| **Flowable** | Yes (JVM) | XML | Yes | High |
| Operaton | Yes (JVM) | XML | Yes | High |
| Conductor OSS | No (server) | JSON | Partial | Medium-high |
| Temporal | SDK-embedded workers | Code | No (build it) | Low |
| Serverless Workflow | Depends on runtime | YAML | No | Medium |
| Camunda 8 | No | XML | Yes | Not recommended |

## 5. Recommended architecture

**Don't let the migration utility emit engine-specific files directly.** Add a thin canonical layer:

```
.blueprint (Pega) → parser/adapter → Canonical Workflow Model (YAML in Git)
                                          → generator → Flowable BPMN/DMN
                                          → generator → stub task forms + routing config
```

- **Canonical YAML model** (stages, steps, assignees, SLAs, data schema) is the thing humans review and version in Git. You can later swap the engine behind it without redoing the migration logic.
- **Engine** is Flowable (or Conductor) embedded behind a small internal API: start, complete task, query worklist, cancel.
- **Workflow-as-code pipeline** means PR review, CI validation against a JSON schema, and automated deployment of definitions on merge.

### Suggested Pega-to-engine mapping

| Pega concept | Flowable equivalent |
|---|---|
| Case type | Process definition |
| Stage | Sub-process or call activity (named phase) |
| Collect information step | User task |
| Approval step | User task plus exclusive gateway |
| Automation step | Service task |
| Alternate stage (rejection) | Gateway path or boundary/error event |
| SLA goal / deadline | Timer boundary events with escalation |
| Routing / work group | Candidate groups or assignee expression |
| Persona | Role/group |
| Data object | Process variables or JSON schema |
| Decision table | DMN |

## 6. A critical risk for the migration utility

**The .blueprint format is proprietary and not publicly documented.** Pega's own community says the technical structure isn't publicly documented and appears to be proprietary, and an answer on the Pega forum calls it a proprietary format that is imported by creating rules from the metadata it contains. The same thread describes it as a design artifact, not executable code, capturing case structure, data models, personas, and intent.

Implications:

- **Version the adapter.** The format may change between Pega releases, so isolate parsing in one module with per-version tests.
- **Get contractual or documented clarity.** Ask your Pega account team whether a supported schema or export API exists. Reverse-engineering a vendor format is also a legal and support question to check with your own counsel.
- **Reduce the dependency.** Consider also accepting a hand-authored canonical YAML and the Blueprint PDF/structured design as inputs.
- **Set honest expectations.** Pega's own import generates scaffolding, with default views and intelligent defaults where the Blueprint is silent, which you then refine. Your utility should aim for the same thing: a working skeleton in minutes, with humans finishing forms, integrations, and rules. Promising full automation would be a mistake.

## 7. A realistic view of "days, not weeks"

That claim holds **only if** you cap the feature set. The parts that tend to quietly expand are:

- UI for tasks and forms (use a generic schema-driven form renderer, not bespoke screens)
- Audit and history (Flowable's history tables cover most Tier 1 needs)
- Notifications, delegation, and reassignment
- Process versioning with in-flight instances (decide up front whether running instances finish on the old version)
- Observability and an admin console

Writing a "will not support" list is as valuable as the feature list.

## 8. Next steps

1. Confirm your application stack. This decides Flowable (JVM) versus Conductor or another option.
2. Pick 3 to 5 real Tier 1 processes and hand-build them on the engine to validate the "days" claim.
3. Define the canonical YAML schema from those examples.
4. Collect 5 to 10 real `.blueprint` exports and build the adapter against them.
5. Write the Tier 1 vs Pega eligibility rubric and get it signed off.

**What language and framework is your application built on (Java/Spring, .NET, Node, Python)?** That is the single biggest factor, and if it isn't JVM, I'd steer you toward Conductor OSS or a Serverless Workflow-based design instead of Flowable. I can then draft the canonical YAML schema and the mapping rules in detail.


I'll dig into specific projects against your criteria (embeddable, Git-versioned, human tasks, open-source licence, easy to generate from a blueprint) and look for any existing Pega-migration precedent.# The best project for your problem: Flowable (if your app is on the JVM)

I researched seven candidate projects and a Pega-to-open-source migration precedent. If you can tell me your application stack, I can firm up the recommendation, because it is the deciding factor.

## Correction to my earlier answer

I said a marketplace listing suggested Flowable 8 shipped in August 2026. That was wrong. Flowable 8.0.0 was released on 27 February 2026. It is based on Spring Framework 7 and Spring Boot 4, with no support for Spring Boot 3. It remains Apache 2.0. If your host application is on Spring Boot 3, you need to plan an upgrade or stay on the 7.2 line.

## The shortlist, scored against your problem

These ratings are my judgment against your five needs: embeddable, Git-friendly, human tasks/SLAs, a real open-source licence, and an easy target for a generator.

| Project | Embeddable in app | Git-friendly definitions | Human tasks/SLA | Licence safety | Generator target | Verdict |
|---|---|---|---|---|---|---|
| **Flowable 8** (JVM) | Strong | XML (BPMN/CMMN/DMN) | Strong | Strong | Strong | **Best overall** |
| **Elsa 3** (.NET) | Strong | JSON | Verify | Strong | Strong | Best if you're on .NET |
| **Apache KIE (jBPM/SonataFlow)** | Medium | XML/YAML | Strong | Strong (but incubating) | Medium | Heavier alternative |
| **Conductor OSS** | Weak (server) | JSON | Partial | Strong | Medium-strong | Best polyglot/non-embedded |
| **Temporal** | Medium | Code | Build it | Strong | Weak | Wrong shape |
| **Windmill** | Weak | Enterprise only | Partial | Complicated | Weak | Rule out |
| **Camunda 8** | No | XML | Strong | Source-available | Strong | Rule out |

## Why Flowable wins

- **It matches the embedding requirement.** It is Apache 2.0 licensed and can run embedded in a Java application or as a service, with Spring integration.
- **Its model maps to Pega's.** It runs BPMN 2.0 processes, CMMN 1.1 cases, and DMN decision tables. Stages and steps, user tasks, gateways, SLA timers, and decision tables all have direct equivalents.
- **It is the closest to your "keep Pega for the hard cases" line.** CMMN lets you handle a small amount of case-like behavior in Tier 1 without recreating Pega.
- **It has real migration precedent.** A Flowable forum answer on a Pega 7 migration says skip expressions can move process instances to the same activity state as the old system. They also describe two approaches for completed instances, both involving the history tables.
- **The trade-off to accept:** the open-source distribution doesn't include the graphical UI apps or a modeller. You would build your own task UI, which is natural for an embedded utility anyway. Treat that as part of the cost estimate for the "days, not weeks" claim.

## When to pick something else

- **.NET stack → Elsa 3.** It's an open-source set of .NET libraries that you can integrate into existing apps or run as a standalone server, and workflows can be defined declaratively in JSON. The .NET Foundation lists it as MIT. I couldn't confirm first-class human-task and SLA support, so verify that before committing. An older README also noted that the designer was not yet fully embeddable. Check whether that still holds.
- **Polyglot services, no embedding → Conductor OSS.** It's Apache 2.0, and its HUMAN task type pauses a workflow until an approval or input arrives via API. Workflow definitions are JSON and versioned. Expect to build worklists, routing, and SLAs yourself.
- **Quarkus, spec-first, heavier platform → Apache KIE.** jBPM covers BPMN 2.0, case management, and human tasks, and SonataFlow implements the CNCF Serverless Workflow spec. It is still Apache incubating, and the 10.2 release removed the classic BPMN and DMN editors. That is churn you'd have to manage.
- **Code-first reliability → Temporal.** Workflows are ordinary code. That makes a declarative migration generator harder to write, review, and let non-developers edit.

**Why I'd rule out Windmill:** it advertises AGPLv3, and the Enterprise edition adds Git sync. The Community Edition also bundles proprietary, non-public code alongside the AGPL and Apache sources. One independent assessment calls the licensing complex and dependent on the specific file. That conflicts with your Git-versioning and licence-safety goals.

## The migration utility has an existing precedent

Camunda Consulting published a free Pega-to-BPMN converter. It is worth studying, but it also shows the limits.

- **What it validates:** Pega process flows don't conform to any open standard, despite looking BPMN-like. Without a converter, teams redraw processes manually.
- **What it admits:** its own readme says it is not a silver bullet, typically generates a BPMN file that retains the diagram's fidelity, and you still add code and scripts to make it executable.
- **What users report:** one user said it converts only a minimal basic part, around 5%. Another hit a crash on an element type it doesn't support, a Data-MO-Connector-Transition.
- **Cutover pattern:** most users run both installations in parallel, start new instances only in the new engine, and run Pega until existing cases finish.

**Design lessons for your utility:**

1. **Whitelist, don't convert everything.** Support a small set of Tier 1 constructs and fail loudly, with a report, on anything else. That is also your Tier 1 eligibility test.
2. **Aim for a runnable skeleton, not a finished app.** Your target is "executes end to end with stub forms and routing", and humans finish it.
3. **Cut over by new cases only.** Never migrate in-flight Pega cases for Tier 1 unless there is a compelling reason.
4. **Check the licence and engine-specific output** of the Camunda tool before reusing any of its code.

## The input problem

The thread you attached confirms the earlier finding: the `.blueprint` format isn't publicly documented and appears proprietary. The workable inputs are:

- **The `.blueprint` file itself**, which carries case types, life cycles, data models, and personas. You would reverse-engineer it, which is brittle and may raise legal and support questions. Ask your Pega account team whether a supported schema exists.
- **The PDF representation** that Blueprint also generates. It is human-readable but lossy to parse.
- **Pega process-flow XML**, the input the Camunda converter uses. It is better known, but it describes flows, not Blueprint's stage/step design.
- **A hand-authored canonical YAML** that the migration utility also produces. I'd make this the system of record regardless.

My recommendation is to make the canonical YAML primary and the Blueprint adapter an optional, versioned plug-in. Then a format change on Pega's side breaks only the adapter, not your engine.

## Proposed two-week proof of concept

1. Pick 3 to 5 real Tier 1 processes, covering a linear approval, a branch with rejection, and a timed escalation.
2. Hand-build them on Flowable 8 embedded in a test app, with a generic schema-driven task form.
3. Extract a canonical YAML schema from what you built.
4. Write the generator (YAML to BPMN, plus form and routing stubs) and rebuild the same processes from YAML.
5. Collect 5 to 10 real Blueprint exports and attempt the adapter.

**Success criteria:** a new Tier 1 process goes from YAML to a running workflow in under a day, every definition diffs cleanly in Git, and the generator rejects out-of-scope input with a clear reason.

What is the host application's language and framework (Java/Spring, .NET, Node, Python), and which Spring Boot version if it's Java? That decides between Flowable, Elsa, and Conductor, and whether you hit the Spring Boot 4 constraint.

# Software Requirements Specification: Tier-1 Workflow Utility (T1WU)

Version 0.1 (Draft for review) · 17 October 2026 · Working title: T1WU

## 1. Introduction

### 1.1 Purpose

This SRS defines the requirements for T1WU, an in-application, Git-versioned workflow orchestration utility for low-complexity (Tier 1) business processes, plus a migration utility that converts simplified Pega Blueprint workflows into running T1WU workflows. Pega remains the platform for complex case management. T1WU covers the long tail of simple workflows.

### 1.2 Scope

In scope: an embeddable workflow runtime, a canonical workflow definition format stored in Git, human-task and SLA handling, generic task UI components, an embedding API, governance (Tier 1 eligibility), and the Pega migration utility. Out of scope items are listed in Section 10.

### 1.3 Definitions

| Term | Meaning |
| --- | --- |
| Tier 1 | Simple, mostly linear or lightly branching processes: intake, request, approval, notification. Eligibility rules in Section 5.14 |
| Canonical Workflow Model (CWM) | The engine-neutral YAML format that is the system of record for every workflow |
| Engine Port | Internal interface that hides the underlying open-source engine |
| Blueprint | Pega Blueprint design artifact (.blueprint file and PDF): case types, life cycles, data objects, personas |
| Skeleton | A running workflow with stub forms and routing generated by the migration utility, to be finished by a developer |
| MoSCoW | M = Must, S = Should, C = Could. Phase: MVP, P2, P3 |

### 1.4 References

- Flowable open-source documentation (BPMN, CMMN, DMN user guides) and the Flowable 8.0.0 release notes
- Pega Academy: Blueprint design approach, Blueprint import, case life cycle and SLA topics
- Pega community thread on the .blueprint file type (format not publicly documented)
- Camunda Consulting migrate-to-camunda-tools (Pega XML to BPMN precedent)
- CNCF Serverless Workflow specification (reference for the CWM design)

## 2. Problem statement and objectives

### 2.1 Problem statement

Tier 1 long-tail processes are being built on a platform designed for complex, regulated case management. This costs more in licensing, specialist labor and calendar time than their business value justifies, and consumes scarce Pega capacity that should go to differentiated Tier 2/3 cases. The organization needs an in-application, Git-versioned workflow capability that delivers simple workflows in days rather than weeks, plus a migration utility that turns simplified Pega blueprints into running workflows on that capability.

### 2.2 Objectives and proposed success metrics

Targets are proposals and shall be validated in the proof of concept (Section 12).

| ID | Objective | Proposed metric |
| --- | --- | --- |
| O1 | Fast delivery | A whitelisted Tier 1 process goes from approved spec to running workflow in 2 working days or less |
| O2 | Git as source of truth | 100% of workflow definitions, forms, and decision tables versioned in Git and deployed only through CI/CD |
| O3 | Cost containment | No per-case or per-user licence cost for T1WU runtime; only infrastructure and staff cost |
| O4 | Embedded utility | Runs inside the host application process, or as a sidecar owned by the same team, with no separate platform team required |
| O5 | Migration acceleration | Migration utility produces a runnable skeleton for at least 70% of Tier 1 candidate blueprints, with an explicit report for the rest |
| O6 | Protect Pega scope | Published eligibility rubric; ineligible processes are rejected with reasons, not partially converted |

## 3. Overall description

### 3.1 Product perspective

T1WU is a utility inside the application ecosystem, not a standalone BPM suite. It has four deliverables:

1. **T1WU Runtime**: embeddable library (Spring Boot starter assumed) wrapping an open-source engine.
2. **T1WU Toolkit**: CLI, schema, validator, scaffolder and Git/CI templates.
3. **T1WU UI Kit**: reusable worklist, task form, case timeline and admin components.
4. **T1WU Migrate**: Blueprint/Pega-flow adapter, canonical model generator and engine generators.

```
  Git repo (CWM YAML, forms, DMN)
        |  CI: validate, compile, test, package
        v
  Host application  <----------------------------------+
   +-- T1WU Runtime (Engine Port -> Flowable) --> RDBMS |
   +-- T1WU UI Kit (worklist, forms, admin)             |
   +-- Integrations (REST, events, email, identity)     |
                                                        |
  Pega .blueprint / flow XML --> T1WU Migrate -- PR ----+
```

### 3.2 User classes

| User | Needs |
| --- | --- |
| Workflow developer | Author or generate CWM, test locally, deploy by pull request |
| Business analyst | Review workflow in readable form (generated diagram and summary) and approve |
| Task user (end user) | See my tasks, complete forms, track status |
| Process owner / admin | Monitor instances, reassign, retry, suspend, view SLA breaches |
| Platform / SRE | Deploy, scale, back up, observe |
| Pega architect | Decide tier eligibility, review migration reports |
| Auditor | Retrieve who did what and when |

### 3.3 Operating environment

JVM (Java 17 or later, confirm against chosen engine release), Spring Boot, PostgreSQL (primary), container/Kubernetes or VM deployment, host-provided identity provider (OIDC). The stack is an assumption pending confirmation (Section 13).

### 3.4 Constraints

- C1: Only OSI-approved open-source components (Apache 2.0, MIT, BSD) in the runtime path. Source-available, fair-code and AGPL-with-proprietary-parts components are excluded.
- C2: The Pega .blueprint format is proprietary and not publicly documented. The adapter shall be isolated, versioned and treated as best-effort.
- C3: The chosen engine release dictates the framework baseline (Flowable 8 requires Spring Boot 4 and does not support Spring Boot 3).
- C4: Definitions shall deploy through Git and CI/CD only; no production edits via UI.

### 3.5 Assumptions and dependencies

- A1: Host application team owns identity, UI shell and database provisioning.
- A2: Real Blueprint exports (5 to 10) will be provided for adapter development.
- A3: Pega account team can confirm whether a supported schema or export API exists.
- A4: Legal review confirms that parsing exported Blueprint files is permitted.

## 4. Build strategy: clone, wrap, or start from scratch

### 4.1 Options

| Option | Description | Assessment |
| --- | --- | --- |
| Fork and modify | Clone the OSS engine repository, change its internals | Rejected. Creates a permanent merge burden and security-patch lag; negates the speed objective |
| **Wrap (recommended)** | Depend on released, unmodified engine artifacts; build T1WU as a thin layer above an Engine Port | Fastest, lowest maintenance. Engine upgrades are dependency bumps |
| Scratch (own engine) | Write a new engine, using OSS only as a design reference | Rejected for Tier 1. Durable timers, transactions, history and concurrency are where homemade engines fail; weeks become quarters |

### 4.2 Decision rule

Wrap by default. Fork only if a defect or missing capability blocks a Must requirement, the engine maintainers decline the fix, and no extension point exists. Any engine patch shall be offered upstream first and tracked as technical debt with an exit date.

### 4.3 Reference engine and alternates

Reference engine: **Flowable (open-source edition)** for JVM stacks. It runs embedded or as a service, executes BPMN 2.0, CMMN 1.1 and DMN, and is Apache 2.0 licensed. The open-source distribution has no graphical modeller or UI apps, which T1WU supplies. Alternates behind the same Engine Port: Elsa 3 for .NET (MIT), Conductor OSS for polyglot non-embedded use (Apache 2.0). The engine decision shall be reconfirmed in the proof of concept.

### 4.4 What comes from open source and what T1WU adds

| Capability | From OSS engine | Added by T1WU |
| --- | --- | --- |
| Process execution, timers, gateways, user tasks, history | Yes | Configuration and hardening only |
| Decision tables | Yes (DMN) | Authoring conventions and validation |
| Definition format | BPMN XML (machine-oriented) | CWM YAML (human-reviewable) and CWM-to-BPMN/DMN compiler |
| Git lifecycle | No | Schema, linter, CI templates, deploy-on-merge, diff-friendly rendering |
| Worklist, task forms, admin screens | No | UI Kit with schema-driven forms |
| Routing, SLA policy, escalation, delegation | Primitives only | Policy layer and standard patterns |
| Embedding API and events | Java API/REST | Stable T1WU facade, versioned contract |
| Tier governance | No | Eligibility checker and report |
| Pega migration | No | Adapter, mapper, generators, reports |
| Observability, audit export | Basic history tables | Metrics, traces, audit views and export |

## 5. Functional requirements

### 5.1 Workflow definition and Git lifecycle (WF)

| ID | Requirement | Pri | Phase |
| --- | --- | --- | --- |
| WF-01 | Every workflow shall be defined as a CWM YAML file validated by a published JSON Schema | M | MVP |
| WF-02 | Forms (JSON Schema plus UI hints) and decision tables shall be stored as separate files referenced by the CWM | M | MVP |
| WF-03 | A compiler shall generate engine artifacts (BPMN, DMN) deterministically from CWM; identical input produces identical output | M | MVP |
| WF-04 | A linter shall reject unreachable steps, missing resolution, missing assignee, undefined data references and unsupported constructs | M | MVP |
| WF-05 | A renderer shall generate a diagram and plain-language summary from CWM for analyst review in pull requests | S | MVP |
| WF-06 | Workflows shall carry metadata: key, semantic version, owner, tier, data classification | M | MVP |
| WF-07 | A test harness shall let developers run a workflow end to end locally with simulated users, clocks and integrations | M | MVP |
| WF-08 | Generated engine artifacts shall not be committed as source of truth; CWM is authoritative | M | MVP |

### 5.2 Runtime execution (EX)

| ID | Requirement | Pri | Phase |
| --- | --- | --- | --- |
| EX-01 | Start, query, suspend, resume, cancel and complete workflow instances | M | MVP |
| EX-02 | Step types: user task, approval, service task, decision, parallel branch and join, subprocess (stage), wait for event, timer | M | MVP |
| EX-03 | Stages as named phases with entry/exit status mapping and alternate (exception or rejection) stages | M | MVP |
| EX-04 | Resolution states per workflow (for example Approved, Rejected, Cancelled) | M | MVP |
| EX-05 | Idempotent start by business key to prevent duplicate instances | M | MVP |
| EX-06 | Automatic retry with backoff and a dead-letter view for failed service steps | M | MVP |
| EX-07 | Optional same-transaction execution with host application data when running embedded | S | P2 |
| EX-08 | Child workflows started by a parent, with completion callback | S | P2 |
| EX-09 | Bulk operations (reassign, cancel) with audit trail | C | P3 |

### 5.3 Human tasks, routing and SLA (HT)

| ID | Requirement | Pri | Phase |
| --- | --- | --- | --- |
| HT-01 | Assign tasks to a user, role/group candidate pool, or expression result | M | MVP |
| HT-02 | Claim, release, complete, reassign and delegate tasks, with reason capture | M | MVP |
| HT-03 | Routing rules: round robin, least loaded, by data field, by manager lookup | S | P2 |
| HT-04 | SLA per step or stage with goal and deadline; goal breach triggers notification, deadline breach triggers escalation | M | MVP |
| HT-05 | Escalation actions: notify, reassign to role, skip, auto-resolve | M | MVP |
| HT-06 | Working-calendar aware SLA (business hours, holidays) | S | P2 |
| HT-07 | Task priority and due date visible in worklist and API | M | MVP |
| HT-08 | Out-of-office and substitute assignment | C | P3 |

### 5.4 Data and forms (DF)

| ID | Requirement | Pri | Phase |
| --- | --- | --- | --- |
| DF-01 | Each workflow declares a data schema; instance data is validated at each step boundary | M | MVP |
| DF-02 | Forms render from JSON Schema with UI hints (layout, labels, conditional visibility, validation) | M | MVP |
| DF-03 | Attachments stored in host-provided object store; only references held in workflow data | S | P2 |
| DF-04 | Field-level classification and masking for sensitive data in logs and audit exports | S | P2 |
| DF-05 | Reference data lookups (dropdowns) from host application APIs | S | P2 |

### 5.5 Decisions (DC)

| ID | Requirement | Pri | Phase |
| --- | --- | --- | --- |
| DC-01 | Decision tables authored as files (DMN or CWM table syntax compiled to DMN), versioned in Git | M | MVP |
| DC-02 | Decision outcome can drive branching and routing | M | MVP |
| DC-03 | Decision tests (input rows to expected outputs) run in CI | S | P2 |

### 5.6 Integration (IN)

| ID | Requirement | Pri | Phase |
| --- | --- | --- | --- |
| IN-01 | Service steps invoke host application beans/handlers by registered action name | M | MVP |
| IN-02 | Standard actions: HTTP/REST call, email, publish event, set data | M | MVP |
| IN-03 | Inbound events and signals can resume waiting workflows (message correlation by business key) | M | MVP |
| IN-04 | Outbound domain events for lifecycle changes (started, task created, SLA breached, completed) | M | MVP |
| IN-05 | Secrets obtained from host secret store, never stored in CWM | M | MVP |
| IN-06 | Action registry with typed inputs and outputs, validated at compile time | S | P2 |

### 5.7 Embedding API and SDK (API)

| ID | Requirement | Pri | Phase |
| --- | --- | --- | --- |
| API-01 | Stable, versioned Java facade and REST API; no engine types exposed to callers | M | MVP |
| API-02 | Operations: start, get instance, list tasks, task details, complete task, claim, reassign, cancel, signal | M | MVP |
| API-03 | OpenAPI specification published and contract-tested | M | MVP |
| API-04 | Webhook subscription for lifecycle events | S | P2 |
| API-05 | Multi-tenant partitioning by tenant key where the host application is multi-tenant | S | P2 |

### 5.8 UI Kit (UI)

| ID | Requirement | Pri | Phase |
| --- | --- | --- | --- |
| UI-01 | Worklist with filters, sort, search, claim and bulk selection | M | MVP |
| UI-02 | Schema-driven task form with validation and attachments | M | MVP |
| UI-03 | Instance timeline: stages, steps, assignees, SLA status, history | M | MVP |
| UI-04 | Admin console: instance search, retry, reassign, suspend, cancel, dead-letter queue | M | MVP |
| UI-05 | Components shall be framework-agnostic web components or documented for the host UI framework; themeable | S | P2 |
| UI-06 | WCAG 2.1 AA accessibility | S | P2 |
| UI-07 | Localization of labels and messages | C | P3 |

### 5.9 Versioning and deployment (VD)

| ID | Requirement | Pri | Phase |
| --- | --- | --- | --- |
| VD-01 | Deploy on merge to main by CI; rollback by reverting the commit | M | MVP |
| VD-02 | New versions apply to new instances; in-flight instances finish on their original version by default | M | MVP |
| VD-03 | Optional migration of in-flight instances with a declared mapping and dry run | C | P3 |
| VD-04 | Environment promotion (dev, test, prod) with the same artifact | M | MVP |
| VD-05 | Deployed version and Git commit identifier visible in admin console | S | MVP |

### 5.10 Audit and history (AU)

| ID | Requirement | Pri | Phase |
| --- | --- | --- | --- |
| AU-01 | Immutable audit record of every state change, task action, data change and administrative action: who, what, when, from which version | M | MVP |
| AU-02 | Audit export (CSV/JSON) and API with retention policy per workflow | M | MVP |
| AU-03 | Decision traceability: inputs, matched rule, output stored per decision | S | P2 |
| AU-04 | Tamper-evidence (hash chaining or append-only store) | C | P3 |

### 5.11 Security (SE)

| ID | Requirement | Pri | Phase |
| --- | --- | --- | --- |
| SE-01 | Authentication delegated to host identity provider (OIDC/SAML) | M | MVP |
| SE-02 | Role-based access: who can start, see, act on, administer each workflow, defined in CWM roles | M | MVP |
| SE-03 | Row-level access: users see only tasks and instances they are entitled to | M | MVP |
| SE-04 | Encryption in transit and at rest (database and attachments) | M | MVP |
| SE-05 | Dependency vulnerability scanning and SBOM generation in CI | M | MVP |
| SE-06 | Segregation of duties rule (for example requester cannot approve own request) declarable in CWM | S | P2 |

### 5.12 Observability and operations (OP)

| ID | Requirement | Pri | Phase |
| --- | --- | --- | --- |
| OP-01 | Metrics: instances started and completed, task age, SLA breach rate, failures, queue depth (Prometheus/OpenTelemetry) | M | MVP |
| OP-02 | Distributed tracing with instance and task identifiers on all logs | M | MVP |
| OP-03 | Health and readiness endpoints; engine job-executor status | M | MVP |
| OP-04 | Documented backup, restore and history-archival procedure | M | MVP |
| OP-05 | Dashboards and alert rules shipped as code | S | P2 |

### 5.13 Developer experience for days-not-weeks delivery (DX)

| ID | Requirement | Pri | Phase |
| --- | --- | --- | --- |
| DX-01 | CLI scaffold: create workflow from templates (approval, intake-and-fulfil, multi-level approval, request-with-SLA) | M | MVP |
| DX-02 | CLI commands: validate, compile, render, test, deploy-local, diff | M | MVP |
| DX-03 | Starter project and Spring Boot starter that run with one command and an in-memory database | M | MVP |
| DX-04 | Reusable step and form library (approval step, comment step, file upload step) | S | P2 |
| DX-05 | Documentation with worked examples; a new developer completes the first workflow in under one day | S | MVP |

### 5.14 Tier eligibility and governance (GV)

| ID | Requirement | Pri | Phase |
| --- | --- | --- | --- |
| GV-01 | Published eligibility rubric. Proposed Tier 1 limits: up to 5 stages, up to 25 steps, no case hierarchy deeper than one child level, no real-time decisioning or adaptive models, no cross-case dependency, defined data classification | M | MVP |
| GV-02 | Eligibility checker scores a CWM or Blueprint against the rubric and outputs pass, review, or reject with reasons | M | MVP |
| GV-03 | Constructs outside the whitelist shall fail validation, never be silently approximated | M | MVP |
| GV-04 | Ownership and review workflow for rubric changes; rubric versioned in Git | S | P2 |
| GV-05 | Promotion path: report listing Tier 1 workflows that outgrow limits and should move to Pega | C | P3 |

### 5.15 Migration utility (MG)

| ID | Requirement | Pri | Phase |
| --- | --- | --- | --- |
| MG-01 | Accept input from: Blueprint file adapter, Pega process-flow XML adapter, or hand-authored CWM | M | MVP |
| MG-02 | Adapters isolated per input type and per format version, each with fixture-based tests; format changes break only the adapter | M | MVP |
| MG-03 | Adapters produce an intermediate model, then a CWM draft: case types, stages, steps, step types, routing, SLAs, personas, data objects | M | MVP |
| MG-04 | Mapping rules (Section 7) are declarative, versioned and overridable | M | MVP |
| MG-05 | Run eligibility check before generation; stop with a report when rejected | M | MVP |
| MG-06 | Generate: CWM YAML, form stubs from data objects, role definitions from personas, routing configuration, SLA timers, test scenarios | M | MVP |
| MG-07 | Produce a conversion report: converted, approximated (flagged), unsupported (with reason), and manual follow-up list | M | MVP |
| MG-08 | Output delivered as a Git pull request containing generated files and the report | S | MVP |
| MG-09 | Dry-run mode that parses and reports without writing files | M | MVP |
| MG-10 | Generated skeleton shall compile and start, and complete a smoke test with simulated users | M | MVP |
| MG-11 | Traceability: every generated element carries the source element identifier as metadata | S | P2 |
| MG-12 | Cutover guidance: new cases start in T1WU while existing Pega cases run to completion; in-flight migration out of scope by default | M | MVP |
| MG-13 | Optional LLM-assisted drafting of form labels and descriptions from Blueprint text, always as reviewable suggestions, never as unreviewed deployment | C | P3 |
| MG-14 | Handling of unknown or new element types by reporting, not crashing | M | MVP |

## 6. Canonical Workflow Model (illustrative sample)

```yaml
apiVersion: t1wu/v1
kind: Workflow
metadata:
  key: leave-request
  version: 1.0.0
  tier: 1
  owner: hr-platform
  source: blueprint:LeaveRequest   # set by migration utility
data:
  schema: schemas/leave-request.json
roles: [employee, manager, hr]
stages:
  - id: submit
    steps:
      - id: enter-details
        type: userTask
        assignee: { role: employee }
        form: forms/leave-details.json
  - id: approve
    steps:
      - id: manager-approval
        type: approval
        assignee: { expr: '${data.managerId}' }
        sla: { goal: PT24H, deadline: PT48H, onDeadline: escalate:hr }
        outcomes: { approve: next, reject: stage:rejected }
  - id: notify
    steps:
      - id: send-confirmation
        type: serviceTask
        action: notify.email
    resolution: Approved
  - id: rejected
    alternate: true
    resolution: Rejected
```

## 7. Pega to CWM to engine mapping (initial)

| Pega concept | CWM element | Flowable artifact |
| --- | --- | --- |
| Case type | Workflow | Process definition |
| Create stage | First stage | Start event and first subprocess |
| Primary stage | Stage | Subprocess or call activity |
| Alternate stage | Stage with alternate: true | Exception path from gateway or boundary event |
| Resolution stage | Stage with resolution | End event with resolution status variable |
| Collect information step | userTask | User task with form key |
| Approval step | approval | User task plus exclusive gateway |
| Automation step | serviceTask | Service task bound to action registry |
| Routing to work group or user | assignee | Candidate groups or assignee expression |
| SLA goal and deadline | sla | Timer boundary events: notify at goal, escalate at deadline |
| Persona | roles | Groups and access policy |
| Data object | data.schema | Process variables validated against JSON Schema |
| Decision table | Decision file | DMN decision table |
| Child case | Child workflow (one level) | Call activity |

Anything not in this table is reported as unsupported (GV-03, MG-07).

## 8. Non-functional requirements

Targets are proposed and shall be load-tested in the proof of concept.

| ID | Category | Requirement |
| --- | --- | --- |
| NF-01 | Performance | p95 latency 300 ms or less for task list and task complete calls at 100 concurrent users on reference hardware |
| NF-02 | Throughput | Sustain 50 workflow starts per second per node and 500 active tasks per second of reads; tune from measured data |
| NF-03 | Scalability | Stateless application nodes scale horizontally behind a load balancer; shared PostgreSQL; job executor clusters safely across nodes; target 1 million historical instances without degradation using archival |
| NF-04 | Availability | 99.9% monthly for the runtime, inheriting the host application deployment; no single point of failure beyond the database |
| NF-05 | Durability | No loss of committed instance state; timers survive restart and deploy |
| NF-06 | Recoverability | RPO and RTO inherited from the database; restore procedure tested quarterly |
| NF-07 | Maintainability | Engine isolated behind Engine Port; engine upgrade is a dependency bump plus contract tests; 80% unit-test coverage on compiler and adapters |
| NF-08 | Portability | Runs on any CNCF-conformant Kubernetes and on plain VMs; PostgreSQL required, other databases best-effort |
| NF-09 | Licensing | All runtime dependencies OSI-approved permissive licences; license scan in CI |
| NF-10 | Security | OWASP ASVS Level 2 aligned; no secrets in Git; SBOM per release |
| NF-11 | Accessibility | WCAG 2.1 AA for UI Kit |
| NF-12 | Compliance | Audit retention and data residency configurable per workflow to meet host policy |
| NF-13 | Usability | First workflow by a new developer in one day; analyst can read generated summary without YAML knowledge |
| NF-14 | Compatibility | Backward-compatible CWM within a major version; deprecation policy of two minor releases |

## 9. Architecture overview

### 9.1 Modules

| Module | Responsibility |
| --- | --- |
| t1wu-model | CWM types, JSON Schema, validation |
| t1wu-compiler | CWM to BPMN/DMN generators, linter, renderer |
| t1wu-engine-port | Interface plus Flowable implementation (alternate implementations later) |
| t1wu-runtime-starter | Spring Boot starter: auto-configuration, action registry, policy layer (routing, SLA, escalation) |
| t1wu-api | Facade, REST controllers, OpenAPI, events |
| t1wu-ui-kit | Worklist, forms, timeline, admin components |
| t1wu-cli | Scaffold, validate, compile, render, test, deploy |
| t1wu-migrate | Adapters, intermediate model, mapper, generators, reports |
| t1wu-governance | Eligibility rubric and checker |
| t1wu-test | Test harness, simulated clock and users |

### 9.2 Key design decisions

1. CWM is the system of record; engine files are build outputs.
2. Engine Port prevents engine types leaking into host code.
3. Policy (routing, SLA, escalation) lives in T1WU, not in engine-specific extensions, so patterns stay uniform.
4. Migration adapters are plug-ins with version tags and fixture suites.
5. One database schema owned by the engine; T1WU tables prefixed and migrated with a standard tool (Flyway or Liquibase).

## 10. Out of scope (explicit non-goals)

- Real-time decisioning, predictive models, next-best-action
- Deep case hierarchies, dynamic case wrapping, ad hoc case structures beyond one level
- Graphical drag-and-drop designer for end users in MVP (render-only diagrams are in scope)
- Customer-facing portals and omnichannel engagement
- Robotic process automation and desktop automation
- Full Pega rule resolution, rulesets and inheritance emulation
- Automatic conversion of arbitrary Pega applications; only whitelisted Tier 1 constructs convert
- Migration of in-flight Pega cases and historical case data (optional later phase)
- Building a new workflow engine or forking engine internals

## 11. Risks and mitigations

| ID | Risk | Impact | Mitigation |
| --- | --- | --- | --- |
| R1 | .blueprint format is proprietary and may change | Adapter breaks | Versioned adapters, fixtures, canonical YAML as fallback input, ask Pega for a supported schema |
| R2 | Scope creep toward a second Pega (UI, rules, case types) | Days become quarters | Section 10 enforced by governance; every new construct needs a rubric change |
| R3 | Spring Boot or Java baseline mismatch with chosen engine release | Blocked adoption | Confirm host stack in week 1; keep Engine Port to allow alternates |
| R4 | Converter coverage lower than expected (precedent converters report limited coverage) | Migration value shrinks | Whitelist plus honest report; measure on real exports in PoC; accept skeleton as the deliverable |
| R5 | Engine history tables grow large | Slow queries | Archival job, retention policy, index review in PoC |
| R6 | In-flight version change confusion | Wrong behavior on live cases | Default finish-on-original-version rule (VD-02); admin visibility of version |
| R7 | Task UI effort underestimated | Delivery delay | UI Kit scoped to worklist, form, timeline, admin only |
| R8 | Licence or legal exposure on parsing vendor format | Compliance issue | Legal review before adapter work (A4) |
| R9 | Key-person dependency on BPMN/engine expertise | Slow support | Hide engine behind CWM and Port; training and runbooks |

## 12. Roadmap and acceptance

### 12.1 Phases

| Phase | Content | Indicative duration |
| --- | --- | --- |
| 0. Proof of concept | Hand-build 3 to 5 real Tier 1 processes on the reference engine; derive CWM schema; load test; collect Blueprint samples | 2 weeks |
| 1. MVP | All MVP requirements: CWM, compiler, runtime starter, API, UI Kit, CLI, governance checker, migration adapter for sample Blueprints with report | 8 to 10 weeks |
| 2. Hardening (P2) | Routing policies, calendars, attachments, webhooks, multi-tenancy, decision tests, accessibility, dashboards | 6 to 8 weeks |
| 3. Extensions (P3) | In-flight migration, tamper-evident audit, bulk ops, optional LLM-assisted drafting | As prioritized |

Durations are estimates and shall be re-baselined after Phase 0.

### 12.2 Acceptance criteria for MVP

1. A new approval workflow is created from a template, validated, merged by pull request, deployed by CI, and executed end to end in two working days or less.
2. Re-compiling the same CWM yields byte-identical artifacts.
3. SLA goal and deadline timers fire correctly across an application restart.
4. Migration utility, run on the agreed sample Blueprints, produces a skeleton that compiles, starts and passes smoke tests for all eligible samples, and a report listing every unsupported element for the rest.
5. Ineligible Blueprints are rejected with explicit reasons and no partial output.
6. Audit export reconstructs who did what and when for a completed instance.
7. Load test meets NF-01 to NF-03 on reference hardware, or deviations are accepted in writing.
8. All runtime dependencies pass the licence scan (NF-09).

## 13. Open questions

1. What is the host application stack: language, framework, Spring Boot version, database, hosting (Kubernetes or VMs)?
2. Which identity provider and role model will T1WU use?
3. Which Pega inputs are available and legally usable: .blueprint files, PDFs, flow XML, or application exports?
4. What are the official Tier 1 eligibility thresholds and who owns the rubric?
5. Audit retention periods and data residency requirements?
6. Does the host UI use a framework (React, Angular, server-rendered) that dictates how the UI Kit is packaged?
7. Expected volume: workflow starts per day, active tasks, concurrent users, to size NF-01 to NF-03?
8. Is migration of in-flight cases or history ever required, or is new-cases-only cutover acceptable?

## 14. Traceability: problem statement to requirements

| Problem statement element | Covered by |
| --- | --- |
| Custom workflow engine for low-complexity processes | Section 4, EX, HT, DF, DC, IN |
| In-application utility within the ecosystem | O4, API, Section 9, NF-08 |
| Git-versioned workflow orchestration | WF, VD, DX, O2, C4 |
| Tier 1 long tail; keep Pega for complex case management | GV, Section 10, O6 |
| Simple workflows in days, not weeks | O1, DX, WF-07, NF-13, MVP acceptance 1 |
| Migration utility understanding a simplified Pega blueprint | MG, Section 7, R1, R4 |
| Develop end-to-end workflow from the blueprint using the tool | MG-06, MG-10, MG-08 |
| Quick, scalable base | Section 4 (wrap strategy), NF-01 to NF-08, Section 12 |

Here are the points I'd weigh most before committing.

**1. Prove the business case before building.**
- **Inventory first.** Count the Tier 1 workflows you have today, their volumes, and what they cost to run and change on Pega.
- **Check the contract.** Pega pricing often involves committed minimums, so moving workloads off may not lower the bill until renewal. Find out what you'd actually save and when.
- **Compare against buying.** Include commercial low-code and workflow tools in the TCO comparison. Building a utility makes sense only if the portfolio is big enough to amortize it.

**2. Treat Phase 0 as a go/no-go gate, not a formality.**
Don't commit MVP dates until the two-week proof of concept shows three things:
- A real process runs end to end in about two days.
- The adapter can parse real Blueprint exports.
- The engine handles your timers, task volumes and identity setup.

If the first two fail, the project is a Git-and-Flowable accelerator without the migration piece, and the plan should say so.

**3. You are building a product, so plan to own it.**
The "days not weeks" promise depends on a standing team: roadmap, on-call, security patching, engine upgrades (Flowable's major versions have moved framework baselines), documentation and user support. If nobody owns it after launch, the long tail drifts back to Pega or to shadow tools.

**4. Be clear about what the migration utility can do.**
- A Blueprint is a design artifact, not the logic of a running Pega app. Existing Pega applications hold most of their behavior in rules, so the utility gets you a skeleton, not a replica.
- It is likely most valuable for new Tier 1 work. Design in Blueprint, generate the skeleton, finish it in T1WU. Check Pega's terms of use before building that into a standard process.
- Don't promise stakeholders that existing apps will convert automatically.

**5. Get the people side right early.**
- Involve the Pega team or CoE now. They should own the Tier 1 vs Tier 2/3 rubric with you, or the project reads as a threat and the boundary gets fought over.
- Agree who approves exceptions and what happens when a Tier 1 workflow outgrows its limits.
- Bring security, audit and compliance in before MVP. Approval workflows often carry audit and segregation-of-duties requirements that you will inherit from Pega.

**6. Keep scope disciplined.**
Section 10 of the SRS (out of scope) is as important as the requirements. Every request for rules, dynamic case structure or a drag-and-drop designer is a step toward rebuilding Pega. Make each one a rubric change that someone has to approve.

**7. What I'd do in the next 30 days:**
1. Confirm the host stack and Spring Boot version.
2. Get the Pega contract and renewal terms, and the Tier 1 inventory.
3. Download the two sample blueprints and run the adapter spike.
4. Hand-build 3 to 5 real processes on the reference engine.
5. Hold a legal review on parsing the Blueprint format.
6. Name the product owner and the long-term support team.

At the end of that, you'll have evidence for a go/no-go decision.

I can turn the SRS into an epic-level backlog or a day-by-day Phase 0 plan next. Or send me your stack details and I'll draft the workflow definition schema. Your answers to the open questions in Section 13 of the SRS would sharpen all three.
