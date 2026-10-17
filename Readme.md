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

# Software Requirements Specification: Tier-1 Workflow Utility (W6W)

Version 0.1 (Draft for review) · 17 October 2026 · Working title: W6W

## 1. Introduction

### 1.1 Purpose

This SRS defines the requirements for W6W, an in-application, Git-versioned workflow orchestration utility for low-complexity (Tier 1) business processes, plus a migration utility that converts simplified Pega Blueprint workflows into running W6W workflows. Pega remains the platform for complex case management. W6W covers the long tail of simple workflows.

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
| O3 | Cost containment | No per-case or per-user licence cost for W6W runtime; only infrastructure and staff cost |
| O4 | Embedded utility | Runs inside the host application process, or as a sidecar owned by the same team, with no separate platform team required |
| O5 | Migration acceleration | Migration utility produces a runnable skeleton for at least 70% of Tier 1 candidate blueprints, with an explicit report for the rest |
| O6 | Protect Pega scope | Published eligibility rubric; ineligible processes are rejected with reasons, not partially converted |

## 3. Overall description

### 3.1 Product perspective

W6W is a utility inside the application ecosystem, not a standalone BPM suite. It has four deliverables:

1. **W6W Runtime**: embeddable library (Spring Boot starter assumed) wrapping an open-source engine.
2. **W6W Toolkit**: CLI, schema, validator, scaffolder and Git/CI templates.
3. **W6W UI Kit**: reusable worklist, task form, case timeline and admin components.
4. **W6W Migrate**: Blueprint/Pega-flow adapter, canonical model generator and engine generators.

```
  Git repo (CWM YAML, forms, DMN)
        |  CI: validate, compile, test, package
        v
  Host application  <----------------------------------+
   +-- W6W Runtime (Engine Port -> Flowable) --> RDBMS |
   +-- W6W UI Kit (worklist, forms, admin)             |
   +-- Integrations (REST, events, email, identity)     |
                                                        |
  Pega .blueprint / flow XML --> W6W Migrate -- PR ----+
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
| **Wrap (recommended)** | Depend on released, unmodified engine artifacts; build W6W as a thin layer above an Engine Port | Fastest, lowest maintenance. Engine upgrades are dependency bumps |
| Scratch (own engine) | Write a new engine, using OSS only as a design reference | Rejected for Tier 1. Durable timers, transactions, history and concurrency are where homemade engines fail; weeks become quarters |

### 4.2 Decision rule

Wrap by default. Fork only if a defect or missing capability blocks a Must requirement, the engine maintainers decline the fix, and no extension point exists. Any engine patch shall be offered upstream first and tracked as technical debt with an exit date.

### 4.3 Reference engine and alternates

Reference engine: **Flowable (open-source edition)** for JVM stacks. It runs embedded or as a service, executes BPMN 2.0, CMMN 1.1 and DMN, and is Apache 2.0 licensed. The open-source distribution has no graphical modeller or UI apps, which W6W supplies. Alternates behind the same Engine Port: Elsa 3 for .NET (MIT), Conductor OSS for polyglot non-embedded use (Apache 2.0). The engine decision shall be reconfirmed in the proof of concept.

### 4.4 What comes from open source and what W6W adds

| Capability | From OSS engine | Added by W6W |
| --- | --- | --- |
| Process execution, timers, gateways, user tasks, history | Yes | Configuration and hardening only |
| Decision tables | Yes (DMN) | Authoring conventions and validation |
| Definition format | BPMN XML (machine-oriented) | CWM YAML (human-reviewable) and CWM-to-BPMN/DMN compiler |
| Git lifecycle | No | Schema, linter, CI templates, deploy-on-merge, diff-friendly rendering |
| Worklist, task forms, admin screens | No | UI Kit with schema-driven forms |
| Routing, SLA policy, escalation, delegation | Primitives only | Policy layer and standard patterns |
| Embedding API and events | Java API/REST | Stable W6W facade, versioned contract |
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
| MG-12 | Cutover guidance: new cases start in W6W while existing Pega cases run to completion; in-flight migration out of scope by default | M | MVP |
| MG-13 | Optional LLM-assisted drafting of form labels and descriptions from Blueprint text, always as reviewable suggestions, never as unreviewed deployment | C | P3 |
| MG-14 | Handling of unknown or new element types by reporting, not crashing | M | MVP |

## 6. Canonical Workflow Model (illustrative sample)

```yaml
apiVersion: w6w/v1
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
| w6w-model | CWM types, JSON Schema, validation |
| w6w-compiler | CWM to BPMN/DMN generators, linter, renderer |
| w6w-engine-port | Interface plus Flowable implementation (alternate implementations later) |
| w6w-runtime-starter | Spring Boot starter: auto-configuration, action registry, policy layer (routing, SLA, escalation) |
| w6w-api | Facade, REST controllers, OpenAPI, events |
| w6w-ui-kit | Worklist, forms, timeline, admin components |
| w6w-cli | Scaffold, validate, compile, render, test, deploy |
| w6w-migrate | Adapters, intermediate model, mapper, generators, reports |
| w6w-governance | Eligibility rubric and checker |
| w6w-test | Test harness, simulated clock and users |

### 9.2 Key design decisions

1. CWM is the system of record; engine files are build outputs.
2. Engine Port prevents engine types leaking into host code.
3. Policy (routing, SLA, escalation) lives in W6W, not in engine-specific extensions, so patterns stay uniform.
4. Migration adapters are plug-ins with version tags and fixture suites.
5. One database schema owned by the engine; W6W tables prefixed and migrated with a standard tool (Flyway or Liquibase).

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
2. Which identity provider and role model will W6W use?
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
- It is likely most valuable for new Tier 1 work. Design in Blueprint, generate the skeleton, finish it in W6W. Check Pega's terms of use before building that into a standard process.
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

# End-to-End Research, Implementation and Verification Plan: W6W

Version 0.1 (Draft) · 17 October 2026 · Companion to the SRS: Tier-1 Workflow Utility (W6W)

## 1. How to use this plan

This plan turns the SRS into a sequence of phases. Each phase ends in a go/no-go gate with named evidence, so the project can stop or change course cheaply. Requirement IDs (WF, EX, HT, MG, NF and so on) refer to the SRS. Durations assume a core team of about seven people and are estimates to be re-baselined at Gate G1.

## 2. Plan on a page

| Phase | Weeks | Goal | Exit gate |
| --- | --- | --- | --- |
| R. Mobilize and research | 0 to 2 | Facts, access, decisions, business case | G0: proceed to proof of concept |
| 0. Proof of concept | 2 to 4 | Prove days-not-weeks, engine fit, Blueprint adapter feasibility | G1: commit to MVP, re-baseline |
| 1. MVP build | 5 to 14 (5 sprints) | All MVP requirements in the SRS | G2: ready for pilot |
| 2. Pilot | 15 to 18 | 2 to 3 real Tier 1 workflows in production | G3: ready to scale |
| 3. Harden, scale, roll out | 19 to 26 | P2 requirements, onboarding, runbooks, decommission plan | G4: steady state |
| 4. Operate and extend | 27 onward | Support, P3 items, quarterly portfolio review | Quarterly review |

## 3. Team, governance and cadence

| Role | Responsibility | Allocation |
| --- | --- | --- |
| Product owner | Scope, priorities, rubric ownership, stakeholder communication | 50% |
| Tech lead / architect | Architecture, engine decision, code standards | 100% |
| Backend engineers (2 or 3) | Runtime, compiler, API, adapters | 100% |
| Frontend engineer | UI Kit | 100% |
| QA / test engineer | Test strategy, automation, performance | 100% |
| DevOps / SRE | CI/CD, environments, observability | 50% |
| Pega architect (advisor) | Tier eligibility, Blueprint knowledge, mapping review | 20% |
| Security, legal, compliance (advisors) | Reviews at gates | As needed |
| Business analyst | Inventory, workflow specs, UAT coordination | 50% |

Cadence: daily stand-up, weekly 30-minute steering check (scope, risks, decisions), a demo at the end of every sprint, a written decision log (decision, options, evidence, owner, date), and a gate review pack at each gate.

## 4. Phase R: Mobilize and research (weeks 0 to 2)

### 4.1 First five days

1. Name the product owner and tech lead; open a decision log and a risk register.
2. Confirm the host application stack, framework and Java version, database, hosting model, and identity provider (SRS open questions 1, 2, 6).
3. Request access: Pega Blueprint (community login), Pega Academy sample files, the Pega contract and renewal terms, a list of current Pega applications.
4. Create the project repository with CI skeleton, license scan and SBOM job, and a docs folder.
5. Schedule the legal, security and Pega CoE review meetings and send them the SRS.

### 4.2 Research tracks

Each track has a timebox, an output, and a way to prove it is finished.

| ID | Track | Key questions | Sources and method | Output | Done when |
| --- | --- | --- | --- | --- | --- |
| R1 | Business case | What do Tier 1 workflows cost today (license, people, calendar time)? When can spend actually drop (renewal, minimums)? | Pega contract, finance data, project history, interviews with Pega CoE | One-page business case with savings range and timing | Finance and product owner sign off |
| R2 | Tier 1 portfolio | How many workflows are simple? What volumes, users, integrations, audit needs? | Inventory of Pega apps (template in 4.3), process owner interviews | Scored inventory and a candidate list of 10 to 20 workflows | Every candidate scored and reviewed by the Pega architect |
| R3 | Blueprint and flow formats | What is in a .blueprint file? Which elements are stable? How does it relate to the PDF? Is Pega flow XML usable? | Sample .blueprint plus PDF files (Pega Academy), your own exports, Pega community threads, Camunda Pega converter source | Format notes, element catalog, fixture set of at least 5 files | Parser spike (S5) reads all fixtures |
| R4 | Engine evaluation | Does the reference engine meet the SRS criteria better than alternates? | Documentation, release notes, hands-on spikes S1 to S3, community health (releases, issues, maintainers) | Weighted scorecard (4.4) and written recommendation | Scorecard reviewed by tech lead and security |
| R5 | Host integration | How will W6W embed (library or sidecar)? Which services must it call? Which events must it publish? | Architecture review with host team, API catalog | Integration sketch and interface list | Host team agrees on embedding model |
| R6 | Security, audit, compliance | Which audit, retention, segregation-of-duties and data-residency rules apply to Tier 1 workflows? | Compliance policies, audit team, existing Pega audit configuration | Control matrix mapped to SE and AU requirements | Compliance reviewer signs off |
| R7 | Legal and licensing | May we parse and process exported Blueprint files? Are all dependency licenses acceptable? Are sample files usable as fixtures? | Pega terms of use, Academy terms, legal review, license scan | Written legal opinion and approved dependency list | Opinion filed in decision log |
| R8 | UX and forms | What do task users need on a worklist and form? Which existing UI framework applies? | Observation of current Pega usage, host UI standards | UI Kit scope and packaging decision | Design sign-off on wireframes |
| R9 | Buy versus build | Does a commercial low-code workflow tool meet needs cheaper or faster? | Vendor demos, TCO model | Comparison memo | Steering group accepts or rejects |

### 4.3 Tier 1 inventory template and scoring

For every candidate workflow capture: name, owner, Pega application, case types, number of stages and steps, step types used, routing rules, SLA rules, decision tables, integrations, data classification, audit requirements, yearly volume, active users, change frequency, and annual run cost if known.

Score each workflow from 0 to 3 on: complexity (lower is better), use of unsupported constructs, integration load, audit weight, business value of moving, and change risk. Proposed outcome bands: eligible, review, stays in Pega. The SRS rubric (GV-01) is the baseline and the Pega architect owns exceptions.

### 4.4 Engine scorecard (R4)

Weights: embeddability 20, human tasks and SLA 20, definition ergonomics and Git friendliness 15, license and open-source health 15, ease of generating definitions from a Blueprint 15, operations and scale 10, team skills 5. Score each candidate 1 to 5. The reference engine must score highest overall and no criterion may score below 3. Candidates: Flowable 8, Operaton, Conductor OSS, and the stack-appropriate alternate (for example Elsa on .NET).

### 4.5 Decisions to close in this phase

D1 host stack and Java/Spring baseline; D2 embedded library versus sidecar; D3 reference engine; D4 UI Kit packaging; D5 identity and role model; D6 Tier 1 rubric thresholds; D7 audit retention; D8 cutover pattern (new cases only is the default).

### 4.6 Gate G0 (end of week 2)

Proceed if: business case shows a credible saving or capacity release; at least 10 candidate workflows are scored; legal opinion is not negative; D1 to D3 are decided; sample Blueprint fixtures are in the repository. If the business case fails, stop or switch to the buy option from R9.

## 5. Phase 0: Proof of concept (weeks 2 to 4)

### 5.1 Spikes with pass and fail criteria

| Spike | What to build | Pass criterion | Fail response |
| --- | --- | --- | --- |
| S1 Embed engine | Reference engine inside a host application skeleton behind the Engine Port; start and complete a user task | Instance state survives application restart; engine upgrade is a dependency change only | Evaluate alternate engine |
| S2 Timers and SLA | Goal and deadline timers with escalation | Timers fire correctly across restart and deploy, within 5 seconds of schedule | Redesign SLA policy layer or change engine |
| S3 Definition round trip | CWM YAML to BPMN compiler for a 10-step workflow | Two compiler runs give byte-identical output; linter rejects bad input | Simplify CWM, reduce construct list |
| S4 Task UI | Worklist and schema-driven form in the host UI | A new form is added by editing JSON only, within 2 days of work | Re-scope UI Kit |
| S5 Blueprint adapter | Parse the sample .blueprint files into an intermediate model | At least 90% of the case types, stages, steps, data objects and personas listed in each sample PDF appear in the parsed model | Make canonical YAML the primary input; use PDF as reference only |
| S6 Identity | OIDC roles mapped to candidate groups; row-level task filtering | A user sees only entitled tasks in tests | Adjust authorization design |
| S7 Load | Scripted starts and task completions | Meets NF-01 and NF-02 proposals at 50% of target on reference hardware, with a clear scaling path | Tune or revisit sizing |
| S8 Git and CI | Pull request triggers validate, compile, test and deploy to dev | Under 15 minutes end to end; revert restores prior version | Simplify pipeline |
| S9 Precedent study (optional) | Run the public Pega-to-BPMN converter on its sample | Documented list of mapped and unmapped elements | Skip if time is short |

### 5.2 Ten-day schedule

| Days | Work |
| --- | --- |
| 1 to 2 | S1 and S8 scaffolding: repository, CI, host skeleton, engine in place |
| 3 to 4 | S3 compiler and linter; S2 timer behavior |
| 5 | S6 identity and authorization |
| 6 to 7 | S4 task UI; begin hand-building real Tier 1 workflows (3 to 5, in parallel from day 3) |
| 8 | S5 adapter against all fixtures |
| 9 | S7 load run and analysis |
| 10 | Review: collect evidence, update scorecard, draft the G1 pack |

While the spikes run, hand-build 3 to 5 real workflows (a linear approval, a branch with rejection, a timed escalation, one with a decision table, one with an integration). Record the effort for each; this is the evidence for the days-not-weeks claim.

### 5.3 Gate G1 (end of week 4)

Proceed if: S1, S2, S3 and S6 pass; at least three real workflows ran end to end with measured effort; adapter result (S5) is known, with the chosen input strategy; load results show a path to NF targets; the CWM schema v0.1 is drafted from real examples. Output: re-baselined MVP plan, updated SRS, confirmed engine, and the first release of the CWM schema. If S1 or S2 fails, repeat the spike on the alternate engine before proceeding.

## 6. Phase 1: MVP build (weeks 5 to 14)

### 6.1 Workstreams

| WS | Scope | SRS references |
| --- | --- | --- |
| WS1 Model and compiler | CWM schema, validation, compiler, linter, renderer | WF |
| WS2 Runtime | Engine Port, starter, execution, policy layer, audit | EX, HT, DF, DC, IN, AU |
| WS3 API and security | Facade, REST, OpenAPI, events, authentication and authorization | API, SE |
| WS4 UI Kit | Worklist, form, timeline, admin console | UI |
| WS5 Migration | Adapters, mapper, generators, reports | MG, Section 7 of SRS |
| WS6 Governance | Eligibility checker and rubric | GV |
| WS7 Platform and DX | CLI, templates, CI/CD, observability, docs | DX, OP, VD |

### 6.2 Sprint plan (two-week sprints)

| Sprint | Goal | Deliverables | How we verify |
| --- | --- | --- | --- |
| 1 (wk 5-6) | Foundations | Repo standards, CWM schema v1, compiler, linter, Engine Port, runtime starter, CLI init and validate | Unit tests; determinism test; WF-01, WF-03, WF-04 checks in CI |
| 2 (wk 7-8) | Execution and tasks | Start, complete, cancel; user task, approval, service task, gateway, timer; routing; SLA escalation; retry and dead-letter; audit writes | Integration tests on real PostgreSQL in containers; restart-during-timer test; EX, HT, AU checks |
| 3 (wk 9-10) | API and UI | REST and Java facade, OpenAPI, events; worklist, form, timeline, admin; authentication and role-based access | Contract tests; browser end-to-end tests; accessibility scan; SE checks |
| 4 (wk 11-12) | Migration and governance | Blueprint adapter (versioned), mapper, CWM generator, form and role stubs, report, dry run, eligibility checker, PR output | Golden-file tests per fixture; smoke test of every generated skeleton; MG and GV checks |
| 5 (wk 13-14) | Hardening and readiness | Metrics, traces, health, backup and restore procedure, templates, documentation, performance and security testing, UAT | Load test vs NF-01 to NF-03; vulnerability and license scans; UAT sign-off; Gate G2 pack |

The migration stream starts in sprint 2 with a dedicated developer using the S5 spike code, so it is ready for integration in sprint 4. Sprints 2 and 3 run the backend and UI streams in parallel.

### 6.3 Definition of ready and done

Ready: requirement ID, acceptance criteria, test approach, dependencies identified. Done: code reviewed, unit and integration tests passing, traceable test linked to the SRS ID, documentation updated, security and license scans clean, demonstrated in the sprint review, no open critical defects.

### 6.4 Gate G2 (end of week 14)

Proceed to pilot if every MVP acceptance criterion in SRS Section 12.2 is met or an exception is accepted in writing; no open critical or high security finding; runbooks reviewed by operations; legal and compliance reviews complete.

## 7. Verification strategy

### 7.1 Test levels

| Level | Scope | Tooling (examples) | Target |
| --- | --- | --- | --- |
| Unit | Compiler, linter, mapper, policy rules, adapters | JUnit, property-based tests | 80% coverage on compiler and adapters (NF-07) |
| Contract | OpenAPI and Engine Port | Schema validators, contract tests | No breaking change without version bump |
| Integration | Engine, database, timers, events | Testcontainers with PostgreSQL | All EX, HT, AU, IN requirements |
| End to end | Worklist, form, approval journeys | Browser automation | Each user class has a scripted journey |
| Golden-file | Migration output for each fixture | Stored expected CWM, BPMN, reports | Byte-identical regeneration; diffs reviewed |
| Non-functional | Performance, resilience, security | Load generator, fault injection, SAST, dependency and license scanners | NF-01 to NF-10 |
| Accessibility | UI Kit | Automated scans plus manual keyboard review | WCAG 2.1 AA (NF-11) |
| Upgrade | Engine and dependency bumps | Regression suite on the previous version | Pass without code change beyond configuration |
| UAT | Real workflows with business users | Scripted scenarios | Sign-off by process owners |

### 7.2 Migration validation

1. Maintain a fixture set: the sample .blueprint files, your own exports, and synthetic edge cases (unknown elements, empty stages, deep nesting, missing SLAs).
2. For each fixture, store the expected intermediate model, CWM, compiler output and conversion report.
3. Measure conversion coverage per fixture: elements found, converted, approximated, unsupported. Publish it on a dashboard; the MVP target is 70% of eligible Blueprints producing a runnable skeleton (O5).
4. Smoke test every generated skeleton: compile, deploy to a test engine, run a scripted path with simulated users and clock, assert resolution state and SLA events.
5. Cross-check against the Blueprint PDF: every case type, stage, step, persona and data object in the PDF appears in the report as converted or flagged.
6. Unknown input must produce a report entry, never a crash (MG-14); fuzz the adapter with mutated fixtures.
7. Run business review of 3 generated skeletons with the Pega architect and the analyst before G2.

### 7.3 Performance and resilience tests

- Steady load: 100 concurrent task users, mixed list, claim and complete; check p95 latency against NF-01.
- Burst: 500 workflow starts in a minute; verify no loss, no duplicate by business key (EX-05).
- Soak: 24 hours at 50% of target load; watch memory, database growth, job executor lag.
- Timer storm: 10,000 timers due within one minute; verify all fire and escalate once.
- Fault injection: kill an application node mid-task; pause the database for 30 seconds; fail an external service for 10 minutes and confirm retry and dead-letter behavior.
- Recovery: restore from backup into a clean environment and verify in-flight instances and audit history.

### 7.4 Security verification

Threat model workshop in sprint 1; static analysis and dependency scanning on every pull request; SBOM per release; authorization tests for every role and row-level rule (SE-02, SE-03); secrets scanning in Git (IN-05); penetration test before pilot; review of audit completeness (AU-01).

### 7.5 Traceability

Every SRS requirement ID is linked to at least one automated test or documented manual check. CI produces a coverage report listing untested requirement IDs. A Must requirement with no passing test blocks the gate.

## 8. Phase 2: Pilot (weeks 15 to 18)

### 8.1 Choosing pilot workflows

Pick 2 or 3 from the inventory: low business risk, real users, moderate volume, at least one produced by the migration utility, and one new workflow built from a template. Avoid anything with regulatory deadlines in the pilot window.

### 8.2 Steps

1. Build or generate, finish, and review each pilot workflow through the normal pull request flow.
2. UAT with process owners using scripted scenarios; fix defects.
3. Run in shadow mode for one week where practical: W6W processes copies of real requests without business effect, and results are compared with Pega.
4. Go live for new cases with a feature flag at 5%, then 25%, then 100%, with a daily check of errors, SLA breaches and user feedback.
5. Existing Pega cases run to completion; no in-flight migration (MG-12).
6. Hold a retrospective at the end of week 18.

### 8.3 Pilot success metrics

Spec to production in two working days or less for the template-built workflow; zero lost or duplicated cases; SLA events match expectations; task completion time not worse than Pega; user satisfaction at least equal; no critical incident; measured cost per workflow below the baseline from R1.

### 8.4 Gate G3

Proceed to scale if the pilot metrics are met, the operations team has run at least one incident drill, and the support model (owner, on-call, escalation path) is staffed.

## 9. Phase 3: Harden, scale and roll out (weeks 19 to 26)

### 9.1 Work

Deliver P2 requirements by value: routing policies, working calendars, attachments, webhooks, multi-tenancy, decision tests, dashboards and alerts, accessibility completion, and performance tuning. Onboard the next wave of eligible workflows from the inventory in batches of 5 to 10, each through the same eligibility, generation, UAT and cutover steps. Publish a developer guide, templates catalog and office hours.

### 9.2 Cutover and rollback runbook (per workflow)

1. Eligibility report passes; owner approves.
2. Skeleton generated or workflow authored; pull request reviewed; CWM diagram approved by the analyst.
3. Test and UAT complete; security and audit checklist signed.
4. Deploy to production with the feature flag off.
5. Enable for new cases in stages (5%, 25%, 100%) with monitoring.
6. Keep Pega case type active for existing cases until the last case closes; track remaining count.
7. Decommission the Pega case type after a defined quiet period and archive its history.
8. Rollback at any stage: switch the flag so new cases go to Pega; cases already in W6W finish there; export their data if needed; record the reason in the decision log.

### 9.3 Gate G4

Steady state reached if: at least 10 workflows are live; availability and latency targets are met for 8 consecutive weeks; the on-call rota has handled real incidents; documentation is complete; the quarterly portfolio review is scheduled.

## 10. CI/CD and repository

### 10.1 Pipeline stages

On every pull request: YAML lint, schema validation, eligibility check, compile, unit tests, workflow scenario tests, diagram render posted to the pull request, SBOM and license scan, vulnerability scan. On merge to main: build artifacts, deploy to dev, smoke tests, integration tests, promote to test with the same artifact, manual approval for production, deploy, post-deploy smoke test. Rollback is a revert of the commit.

### 10.2 Repository layout (proposed)

Separate repositories or folders for: the platform (modules in SRS Section 9), the workflow definitions (one folder per workflow with CWM, forms, decisions, tests), the migration fixtures and golden files, and the documentation and rubric. Protect main with required reviews and required checks.

## 11. Environments and data

Dev (developer-owned, in-memory database allowed), Test (shared, PostgreSQL, synthetic data), Pre-production (production-like for performance and failover tests), Production. Use synthetic or masked data outside production (DF-04). Environment configuration lives in code, and secrets in the host secret store.

## 12. KPIs to track from G1 onward

Lead time from approved spec to production; hand-build effort per workflow; migration conversion rate and manual edits per skeleton; defects escaped to production; SLA false-breach rate; availability and p95 latency; cost per workflow versus the Pega baseline; number of workflows onboarded; developer satisfaction survey.

## 13. Risk watchlist with early warnings

| Warning sign | Likely cause | Response |
| --- | --- | --- |
| Adapter covers under 50% of fixture elements by day 8 of the proof of concept | Format harder than assumed | Make canonical YAML the primary input; use the PDF as a human reference; reduce migration promise |
| Timers or SLA events lost on restart | Engine configuration or design flaw | Stop feature work, fix or change engine before sprint 2 ends |
| Host stack or Spring Boot version conflicts with the engine release | Baseline mismatch | Decide upgrade or alternate engine at G0, not later |
| Legal opinion negative or delayed | Vendor terms on file parsing | Use hand-authored CWM only and drop adapter from MVP |
| Scope requests for rules, dynamic cases, drag-and-drop designers | Pull toward rebuilding Pega | Route to the rubric change process; default answer is no |
| UI Kit behind by more than one sprint | Effort underestimated | Cut to worklist and form; defer timeline polish |
| Pega team opposes eligibility rubric | Ownership and incentives | Joint ownership of rubric with the Pega architect; shared reporting |
| Performance short of targets in sprint 5 | Database or job executor tuning | Index and executor tuning; scale nodes; revise targets only with product owner approval |
| Key person leaves | Concentrated expertise | Pairing, runbooks, engine hidden behind CWM and Engine Port |

## 14. Start here: next ten working days

| Day | Action | Output |
| --- | --- | --- |
| 1 | Name product owner and tech lead; create repository, decision log, risk register | Governance in place |
| 2 | Confirm host stack and baseline (open questions 1, 2, 6) | Decision D1 |
| 3 | Download the two Pega Academy sample Blueprint zips and generate one simple Blueprint of your own | Fixtures v0 |
| 4 | Request Pega contract and application list; brief legal on file-parsing and fixture use | R1 and R7 started |
| 5 | Kick off inventory (4.3) with the Pega architect | First 10 workflows listed |
| 6 to 7 | Hands-on: embed the reference engine in a host skeleton (S1) | Working spike |
| 8 | Read the sample Blueprint files against their PDFs and draft the element catalog (R3) | Catalog v0 |
| 9 | Draft the engine scorecard and CWM sample for one real workflow | Scorecard v0, CWM v0 |
| 10 | Review findings with steering group; schedule Gate G0 | G0 pack outline |

## 15. Dependencies on SRS open questions

This plan assumes answers to SRS Section 13 by the end of week 1: host stack, identity provider, available Pega inputs and legal use, Tier 1 thresholds and rubric owner, audit retention, UI framework, expected volumes, and whether in-flight migration is ever needed. Unanswered items extend Phase R and should be tracked as risks.

# End-to-End Research, Implementation and Verification Plan: W6W

Version 0.1 (Draft) · 17 October 2026 · Companion to the SRS: Tier-1 Workflow Utility (the SRS title says W6W; renamed W6W)

## 1. How to use this plan

This plan turns the SRS into a sequence of phases. Each phase ends in a go/no-go gate with named evidence, so the project can stop or change course cheaply. Requirement IDs (WF, EX, HT, MG, NF and so on) refer to the SRS. Durations assume a core team of about seven people and are estimates to be re-baselined at Gate G1.

**Name.** The working name is W6W: the first and last letters of workflow with the six letters between them counted, the same convention n8n (short for nodemation) uses, and that i18n and k8s use for their words. Say it as double-u six double-u. Two alternates in the same style are p5s (process) and o9e (orchestrate). Before adopting any name, check it against internal trademark rules and the package, repository and domain names you will publish under. The SRS still carries the old working title W6W; read W6W as W6W there, and the rename can be applied to the SRS the same way.

## 2. Plan on a page

| Phase | Weeks | Goal | Exit gate |
| --- | --- | --- | --- |
| R. Mobilize and research | 0 to 2 | Facts, access, decisions, business case | G0: proceed to proof of concept |
| 0. Proof of concept | 2 to 4 | Prove days-not-weeks, engine fit, Blueprint adapter feasibility | G1: commit to MVP, re-baseline |
| 1. MVP build | 5 to 14 (5 sprints) | All MVP requirements in the SRS | G2: ready for pilot |
| 2. Pilot | 15 to 18 | 2 to 3 real Tier 1 workflows in production | G3: ready to scale |
| 3. Harden, scale, roll out | 19 to 26 | P2 requirements, onboarding, runbooks, decommission plan | G4: steady state |
| 4. Operate and extend | 27 onward | Support, P3 items, quarterly portfolio review | Quarterly review |

## 3. Team, governance and cadence

| Role | Responsibility | Allocation |
| --- | --- | --- |
| Product owner | Scope, priorities, rubric ownership, stakeholder communication | 50% |
| Tech lead / architect | Architecture, engine decision, code standards | 100% |
| Backend engineers (2 or 3) | Runtime, compiler, API, adapters | 100% |
| Frontend engineer | UI Kit | 100% |
| QA / test engineer | Test strategy, automation, performance | 100% |
| DevOps / SRE | CI/CD, environments, observability | 50% |
| Pega architect (advisor) | Tier eligibility, Blueprint knowledge, mapping review | 20% |
| Security, legal, compliance (advisors) | Reviews at gates | As needed |
| Business analyst | Inventory, workflow specs, UAT coordination | 50% |

Cadence: daily stand-up, weekly 30-minute steering check (scope, risks, decisions), a demo at the end of every sprint, a written decision log (decision, options, evidence, owner, date), and a gate review pack at each gate.

## 4. Phase R: Mobilize and research (weeks 0 to 2)

### 4.1 First five days

1. Name the product owner and tech lead; open a decision log and a risk register.
2. Confirm the host application stack, framework and Java version, database, hosting model, and identity provider (SRS open questions 1, 2, 6).
3. Request access: Pega Blueprint (community login), Pega Academy sample files, the Pega contract and renewal terms, a list of current Pega applications.
4. Create the single W6W repository (platform and workflows folders) with CI skeleton, license scan and SBOM job, and a docs folder.
5. Schedule the legal, security and Pega CoE review meetings and send them the SRS.

### 4.2 Research tracks

Each track has a timebox, an output, and a way to prove it is finished.

| ID | Track | Key questions | Sources and method | Output | Done when |
| --- | --- | --- | --- | --- | --- |
| R1 | Business case | What do Tier 1 workflows cost today (license, people, calendar time)? When can spend actually drop (renewal, minimums)? | Pega contract, finance data, project history, interviews with Pega CoE | One-page business case with savings range and timing | Finance and product owner sign off |
| R2 | Tier 1 portfolio | How many workflows are simple? What volumes, users, integrations, audit needs? | Inventory of Pega apps (template in 4.3), process owner interviews | Scored inventory and a candidate list of 10 to 20 workflows | Every candidate scored and reviewed by the Pega architect |
| R3 | Blueprint and flow formats | What is in a .blueprint file? Which elements are stable? How does it relate to the PDF? Is Pega flow XML usable? | Sample .blueprint plus PDF files (Pega Academy), your own exports, Pega community threads, Camunda Pega converter source | Format notes, element catalog, fixture set of at least 5 files | Parser spike (S5) reads all fixtures |
| R4 | Engine evaluation | Does the reference engine meet the SRS criteria better than alternates? | Documentation, release notes, hands-on spikes S1 to S3, community health (releases, issues, maintainers) | Weighted scorecard (4.4) and written recommendation | Scorecard reviewed by tech lead and security |
| R5 | Host integration | How will W6W embed (library or sidecar)? Which services must it call? Which events must it publish? | Architecture review with host team, API catalog | Integration sketch and interface list | Host team agrees on embedding model |
| R6 | Security, audit, compliance | Which audit, retention, segregation-of-duties and data-residency rules apply to Tier 1 workflows? | Compliance policies, audit team, existing Pega audit configuration | Control matrix mapped to SE and AU requirements | Compliance reviewer signs off |
| R7 | Legal and licensing | May we parse and process exported Blueprint files? Are all dependency licenses acceptable? Are sample files usable as fixtures? | Pega terms of use, Academy terms, legal review, license scan | Written legal opinion and approved dependency list | Opinion filed in decision log |
| R8 | UX and forms | What do task users need on a worklist and form? Which existing UI framework applies? | Observation of current Pega usage, host UI standards | UI Kit scope and packaging decision | Design sign-off on wireframes |
| R9 | Buy versus build | Does a commercial low-code workflow tool meet needs cheaper or faster? | Vendor demos, TCO model | Comparison memo | Steering group accepts or rejects |

### 4.3 Tier 1 inventory template and scoring

For every candidate workflow capture: name, owner, Pega application, case types, number of stages and steps, step types used, routing rules, SLA rules, decision tables, integrations, data classification, audit requirements, yearly volume, active users, change frequency, and annual run cost if known.

Score each workflow from 0 to 3 on: complexity (lower is better), use of unsupported constructs, integration load, audit weight, business value of moving, and change risk. Proposed outcome bands: eligible, review, stays in Pega. The SRS rubric (GV-01) is the baseline and the Pega architect owns exceptions.

### 4.4 Engine scorecard (R4)

Weights: embeddability 20, human tasks and SLA 20, definition ergonomics and Git friendliness 15, license and open-source health 15, ease of generating definitions from a Blueprint 15, operations and scale 10, team skills 5. Score each candidate 1 to 5. The reference engine must score highest overall and no criterion may score below 3. Candidates: Flowable 8, Operaton, Conductor OSS, and the stack-appropriate alternate (for example Elsa on .NET).

### 4.5 Decisions to close in this phase

D1 host stack and Java/Spring baseline; D2 embedded library versus sidecar; D3 reference engine; D4 UI Kit packaging; D5 identity and role model; D6 Tier 1 rubric thresholds; D7 audit retention; D8 cutover pattern (new cases only is the default).

### 4.6 Gate G0 (end of week 2)

Proceed if: business case shows a credible saving or capacity release; at least 10 candidate workflows are scored; legal opinion is not negative; D1 to D3 are decided; sample Blueprint fixtures are in the repository. If the business case fails, stop or switch to the buy option from R9.

## 5. Phase 0: Proof of concept (weeks 2 to 4)

### 5.1 Spikes with pass and fail criteria

| Spike | What to build | Pass criterion | Fail response |
| --- | --- | --- | --- |
| S1 Embed engine | Reference engine inside a host application skeleton behind the Engine Port; start and complete a user task | Instance state survives application restart; engine upgrade is a dependency change only | Evaluate alternate engine |
| S2 Timers and SLA | Goal and deadline timers with escalation | Timers fire correctly across restart and deploy, within 5 seconds of schedule | Redesign SLA policy layer or change engine |
| S3 Definition round trip | CWM YAML to BPMN compiler for a 10-step workflow | Two compiler runs give byte-identical output; linter rejects bad input | Simplify CWM, reduce construct list |
| S4 Task UI | Worklist and schema-driven form in the host UI | A new form is added by editing JSON only, within 2 days of work | Re-scope UI Kit |
| S5 Blueprint adapter | Parse the sample .blueprint files into an intermediate model | At least 90% of the case types, stages, steps, data objects and personas listed in each sample PDF appear in the parsed model | Make canonical YAML the primary input; use PDF as reference only |
| S6 Identity | OIDC roles mapped to candidate groups; row-level task filtering | A user sees only entitled tasks in tests | Adjust authorization design |
| S7 Load | Scripted starts and task completions | Meets NF-01 and NF-02 proposals at 50% of target on reference hardware, with a clear scaling path | Tune or revisit sizing |
| S8 Git and CI | Pull request triggers validate, compile, test and deploy to dev | Under 15 minutes end to end; revert restores prior version | Simplify pipeline |
| S9 Precedent study (optional) | Run the public Pega-to-BPMN converter on its sample | Documented list of mapped and unmapped elements | Skip if time is short |

### 5.2 Ten-day schedule

| Days | Work |
| --- | --- |
| 1 to 2 | S1 and S8 scaffolding: repository, CI, host skeleton, engine in place |
| 3 to 4 | S3 compiler and linter; S2 timer behavior |
| 5 | S6 identity and authorization |
| 6 to 7 | S4 task UI; begin hand-building real Tier 1 workflows (3 to 5, in parallel from day 3) |
| 8 | S5 adapter against all fixtures |
| 9 | S7 load run and analysis |
| 10 | Review: collect evidence, update scorecard, draft the G1 pack |

While the spikes run, hand-build 3 to 5 real workflows (a linear approval, a branch with rejection, a timed escalation, one with a decision table, one with an integration). Record the effort for each; this is the evidence for the days-not-weeks claim.

### 5.3 Gate G1 (end of week 4)

Proceed if: S1, S2, S3 and S6 pass; at least three real workflows ran end to end with measured effort; adapter result (S5) is known, with the chosen input strategy; load results show a path to NF targets; the CWM schema v0.1 is drafted from real examples. Output: re-baselined MVP plan, updated SRS, confirmed engine, and the first release of the CWM schema. If S1 or S2 fails, repeat the spike on the alternate engine before proceeding.

## 6. Phase 1: MVP build (weeks 5 to 14)

### 6.1 Workstreams

| WS | Scope | SRS references |
| --- | --- | --- |
| WS1 Model and compiler | CWM schema, validation, compiler, linter, renderer | WF |
| WS2 Runtime | Engine Port, starter, execution, policy layer, audit | EX, HT, DF, DC, IN, AU |
| WS3 API and security | Facade, REST, OpenAPI, events, authentication and authorization | API, SE |
| WS4 UI Kit | Worklist, form, timeline, admin console | UI |
| WS5 Migration | Adapters, mapper, generators, reports | MG, Section 7 of SRS |
| WS6 Governance | Eligibility checker and rubric | GV |
| WS7 Platform and DX | CLI, templates, CI/CD, observability, docs | DX, OP, VD |

### 6.2 Sprint plan (two-week sprints)

| Sprint | Goal | Deliverables | How we verify |
| --- | --- | --- | --- |
| 1 (wk 5-6) | Foundations | Repo standards, CWM schema v1, compiler, linter, Engine Port, runtime starter, CLI init and validate | Unit tests; determinism test; WF-01, WF-03, WF-04 checks in CI |
| 2 (wk 7-8) | Execution and tasks | Start, complete, cancel; user task, approval, service task, gateway, timer; routing; SLA escalation; retry and dead-letter; audit writes | Integration tests on real PostgreSQL in containers; restart-during-timer test; EX, HT, AU checks |
| 3 (wk 9-10) | API and UI | REST and Java facade, OpenAPI, events; worklist, form, timeline, admin; authentication and role-based access | Contract tests; browser end-to-end tests; accessibility scan; SE checks |
| 4 (wk 11-12) | Migration and governance | Blueprint adapter (versioned), mapper, CWM generator, form and role stubs, report, dry run, eligibility checker, PR output | Golden-file tests per fixture; smoke test of every generated skeleton; MG and GV checks |
| 5 (wk 13-14) | Hardening and readiness | Metrics, traces, health, backup and restore procedure, templates, documentation, performance and security testing, UAT | Load test vs NF-01 to NF-03; vulnerability and license scans; UAT sign-off; Gate G2 pack |

The migration stream starts in sprint 2 with a dedicated developer using the S5 spike code, so it is ready for integration in sprint 4. Sprints 2 and 3 run the backend and UI streams in parallel.

### 6.3 Definition of ready and done

Ready: requirement ID, acceptance criteria, test approach, dependencies identified. Done: code reviewed, unit and integration tests passing, traceable test linked to the SRS ID, documentation updated, security and license scans clean, demonstrated in the sprint review, no open critical defects.

### 6.4 Gate G2 (end of week 14)

Proceed to pilot if every MVP acceptance criterion in SRS Section 12.2 is met or an exception is accepted in writing; no open critical or high security finding; runbooks reviewed by operations; legal and compliance reviews complete.

## 7. Verification strategy

### 7.1 Test levels

| Level | Scope | Tooling (examples) | Target |
| --- | --- | --- | --- |
| Unit | Compiler, linter, mapper, policy rules, adapters | JUnit, property-based tests | 80% coverage on compiler and adapters (NF-07) |
| Contract | OpenAPI and Engine Port | Schema validators, contract tests | No breaking change without version bump |
| Integration | Engine, database, timers, events | Testcontainers with PostgreSQL | All EX, HT, AU, IN requirements |
| End to end | Worklist, form, approval journeys | Browser automation | Each user class has a scripted journey |
| Golden-file | Migration output for each fixture | Stored expected CWM, BPMN, reports | Byte-identical regeneration; diffs reviewed |
| Non-functional | Performance, resilience, security | Load generator, fault injection, SAST, dependency and license scanners | NF-01 to NF-10 |
| Accessibility | UI Kit | Automated scans plus manual keyboard review | WCAG 2.1 AA (NF-11) |
| Upgrade | Engine and dependency bumps | Regression suite on the previous version | Pass without code change beyond configuration |
| UAT | Real workflows with business users | Scripted scenarios | Sign-off by process owners |

### 7.2 Migration validation

1. Maintain a fixture set: the sample .blueprint files, your own exports, and synthetic edge cases (unknown elements, empty stages, deep nesting, missing SLAs).
2. For each fixture, store the expected intermediate model, CWM, compiler output and conversion report.
3. Measure conversion coverage per fixture: elements found, converted, approximated, unsupported. Publish it on a dashboard; the MVP target is 70% of eligible Blueprints producing a runnable skeleton (O5).
4. Smoke test every generated skeleton: compile, deploy to a test engine, run a scripted path with simulated users and clock, assert resolution state and SLA events.
5. Cross-check against the Blueprint PDF: every case type, stage, step, persona and data object in the PDF appears in the report as converted or flagged.
6. Unknown input must produce a report entry, never a crash (MG-14); fuzz the adapter with mutated fixtures.
7. Run business review of 3 generated skeletons with the Pega architect and the analyst before G2.

### 7.3 Performance and resilience tests

- Steady load: 100 concurrent task users, mixed list, claim and complete; check p95 latency against NF-01.
- Burst: 500 workflow starts in a minute; verify no loss, no duplicate by business key (EX-05).
- Soak: 24 hours at 50% of target load; watch memory, database growth, job executor lag.
- Timer storm: 10,000 timers due within one minute; verify all fire and escalate once.
- Fault injection: kill an application node mid-task; pause the database for 30 seconds; fail an external service for 10 minutes and confirm retry and dead-letter behavior.
- Recovery: restore from backup into a clean environment and verify in-flight instances and audit history.

### 7.4 Security verification

Threat model workshop in sprint 1; static analysis and dependency scanning on every pull request; SBOM per release; authorization tests for every role and row-level rule (SE-02, SE-03); secrets scanning in Git (IN-05); penetration test before pilot; review of audit completeness (AU-01).

### 7.5 Traceability

Every SRS requirement ID is linked to at least one automated test or documented manual check. CI produces a coverage report listing untested requirement IDs. A Must requirement with no passing test blocks the gate.

## 8. Phase 2: Pilot (weeks 15 to 18)

### 8.1 Choosing pilot workflows

Pick 2 or 3 from the inventory: low business risk, real users, moderate volume, at least one produced by the migration utility, and one new workflow built from a template. Avoid anything with regulatory deadlines in the pilot window.

### 8.2 Steps

1. Build or generate, finish, and review each pilot workflow through the normal pull request flow.
2. UAT with process owners using scripted scenarios; fix defects.
3. Run in shadow mode for one week where practical: W6W processes copies of real requests without business effect, and results are compared with Pega.
4. Go live for new cases with a feature flag at 5%, then 25%, then 100%, with a daily check of errors, SLA breaches and user feedback.
5. Existing Pega cases run to completion; no in-flight migration (MG-12).
6. Hold a retrospective at the end of week 18.

### 8.3 Pilot success metrics

Spec to production in two working days or less for the template-built workflow; zero lost or duplicated cases; SLA events match expectations; task completion time not worse than Pega; user satisfaction at least equal; no critical incident; measured cost per workflow below the baseline from R1.

### 8.4 Gate G3

Proceed to scale if the pilot metrics are met, the operations team has run at least one incident drill, and the support model (owner, on-call, escalation path) is staffed.

## 9. Phase 3: Harden, scale and roll out (weeks 19 to 26)

### 9.1 Work

Deliver P2 requirements by value: routing policies, working calendars, attachments, webhooks, multi-tenancy, decision tests, dashboards and alerts, accessibility completion, and performance tuning. Onboard the next wave of eligible workflows from the inventory in batches of 5 to 10, each through the same eligibility, generation, UAT and cutover steps. Publish a developer guide, templates catalog and office hours.

### 9.2 Cutover and rollback runbook (per workflow)

1. Eligibility report passes; owner approves.
2. Skeleton generated or workflow authored; pull request reviewed; CWM diagram approved by the analyst.
3. Test and UAT complete; security and audit checklist signed.
4. Deploy to production with the feature flag off.
5. Enable for new cases in stages (5%, 25%, 100%) with monitoring.
6. Keep Pega case type active for existing cases until the last case closes; track remaining count.
7. Decommission the Pega case type after a defined quiet period and archive its history.
8. Rollback at any stage: switch the flag so new cases go to Pega; cases already in W6W finish there; export their data if needed; record the reason in the decision log.

### 9.3 Gate G4

Steady state reached if: at least 10 workflows are live; availability and latency targets are met for 8 consecutive weeks; the on-call rota has handled real incidents; documentation is complete; the quarterly portfolio review is scheduled.

## 10. CI/CD and repository

### 10.1 Pipeline stages

Pull requests that touch workflows/: YAML lint, schema validation, eligibility check, compile, unit tests, workflow scenario tests, a diagram render posted to the pull request, SBOM and license scan. Pull requests that touch platform/ or schema/: build, unit, contract and integration tests, SBOM, license and vulnerability scans, then a cross-check that compiles and scenario-tests every workflow in workflows/ with the platform built from the same commit. On merge to main: deploy to dev, smoke tests, promote the same artifact to test for integration tests, manual approval, then production and a post-deploy smoke test. Platform releases are tagged separately. Rolling back a workflow is a revert of its commit; rolling back the platform is redeploying the previous tag.

### 10.2 Repository layout (one repository, two folders)

One repository named w6w holds two top-level folders: platform/ (the product, changed by the platform team) and workflows/ (the definitions that teams edit every day). Shared contracts and documents sit beside them.

```
w6w/
├── README.md
├── CODEOWNERS                       # per-folder reviewers
├── .github/workflows/
│   ├── platform-ci.yml              # runs when platform/** or schema/** changes
│   ├── workflows-ci.yml             # runs when workflows/** changes
│   ├── cross-check.yml              # compiles every workflow with the platform at this commit
│   └── release.yml                  # tags and publishes platform artifacts
├── docs/
│   ├── srs/  plan/  adr/  architecture/
│   ├── rubric/                      # Tier 1 eligibility rubric, versioned
│   └── runbooks/                    # backup, incident, cutover and rollback
├── schema/
│   └── cwm/v1/                      # workflow, form and rubric JSON Schemas (shared contract)
├── platform/                        # the product
│   ├── pom.xml                      # parent POM, dependency management, license plugin
│   ├── modules/
│   │   ├── w6w-model/               # CWM types, validation
│   │   ├── w6w-compiler/            # CWM to BPMN/DMN, linter, diagram and summary renderer
│   │   ├── w6w-engine-port/         # engine-neutral interface
│   │   ├── w6w-engine-flowable/     # reference engine implementation
│   │   ├── w6w-runtime-starter/     # Spring Boot starter: policy/, actions/, audit/, security/
│   │   ├── w6w-api/                 # facade, REST, OpenAPI, events
│   │   ├── w6w-governance/          # eligibility checker
│   │   ├── w6w-migrate/             # migrate-core, adapter-blueprint (versioned),
│   │   │                            # adapter-pega-flow-xml, mappings/ (declarative rules)
│   │   ├── w6w-cli/                 # init, validate, compile, render, test, diff, migrate
│   │   └── w6w-test/                # harness: simulated users, clock, integrations
│   ├── packages/ui-kit/             # worklist, task form, timeline, admin
│   ├── examples/host-app/           # minimal host app, one-command run
│   ├── fixtures/migration/blueprint/
│   │   └── <fixture>/               # input.blueprint, input.pdf, expected/ (model, CWM, report)
│   ├── perf/                        # load, soak, timer-storm scenarios
│   └── ops/                         # helm, docker-compose, dashboards, alerts, db scripts
├── workflows/                       # what teams edit
│   ├── .w6w/config.yaml             # pinned platform version, rubric version, environments
│   ├── domains/
│   │   └── <domain>/<workflow-key>/
│   │       ├── workflow.cwm.yaml    # system of record
│   │       ├── schemas/data.schema.json
│   │       ├── forms/               # JSON Schema plus UI hints
│   │       ├── decisions/           # decision tables
│   │       ├── tests/scenarios/     # scripted paths
│   │       ├── migration-report.md  # only when generated by w6w-migrate
│   │       └── README.md            # owner, tier, SLA, integrations
│   ├── shared/                      # roles.yaml, reusable forms and decisions
│   ├── templates/                   # approval, multi-level approval, intake-and-fulfil, request-with-SLA
│   ├── environments/                # dev.yaml, test.yaml, prod.yaml (no secrets)
│   └── build/                       # generated BPMN/DMN, git-ignored, never committed
└── tools/                           # SBOM, license scan, requirement-coverage report
```

Conventions for the single repository:

1. **Two ownership zones.** CODEOWNERS gives platform/ and schema/ to the platform team and each workflows/domains/\<domain>/ folder to its domain owners, so a workflow-only change never waits on the platform team.
2. **Path-filtered pipelines.** A change under workflows/ runs only workflow checks; a change under platform/ or schema/ runs platform checks plus the cross-check against every workflow. A platform change that breaks a workflow cannot merge.
3. **Version pin.** workflows/.w6w/config.yaml pins the released platform version used to deploy to test and production. Upgrading the pin is an ordinary pull request that must pass the full scenario suite.
4. **Generated files stay out of Git.** build/ is ignored. Migration output arrives as a pull request on a branch such as migrate/\<blueprint-name> and touches only workflows/domains/ (including the source element IDs in the workflow metadata).
5. **Atomic schema changes.** A schema change and the workflows it affects land in one pull request with a migration note. Inside a major version (cwm/v1) the schema changes only by adding.
6. **Releases.** Platform releases are tagged (platform-vX.Y.Z). Each deployed workflow records its commit and the platform version, shown in the admin console (VD-05).
7. **Authors stay light.** Workflow authors can use git sparse checkout to clone only workflows/, schema/ and docs/.
8. **Split trigger.** If several teams need different access to workflows/, or repository size or CI time degrades, move workflows/ to its own repository. The folder layout stays the same, so the move is mechanical (a subtree split).

## 11. Environments and data

Dev (developer-owned, in-memory database allowed), Test (shared, PostgreSQL, synthetic data), Pre-production (production-like for performance and failover tests), Production. Use synthetic or masked data outside production (DF-04). Environment configuration lives in code, and secrets in the host secret store.

## 12. KPIs to track from G1 onward

Lead time from approved spec to production; hand-build effort per workflow; migration conversion rate and manual edits per skeleton; defects escaped to production; SLA false-breach rate; availability and p95 latency; cost per workflow versus the Pega baseline; number of workflows onboarded; developer satisfaction survey.

## 13. Risk watchlist with early warnings

| Warning sign | Likely cause | Response |
| --- | --- | --- |
| Adapter covers under 50% of fixture elements by day 8 of the proof of concept | Format harder than assumed | Make canonical YAML the primary input; use the PDF as a human reference; reduce migration promise |
| Timers or SLA events lost on restart | Engine configuration or design flaw | Stop feature work, fix or change engine before sprint 2 ends |
| Host stack or Spring Boot version conflicts with the engine release | Baseline mismatch | Decide upgrade or alternate engine at G0, not later |
| Legal opinion negative or delayed | Vendor terms on file parsing | Use hand-authored CWM only and drop adapter from MVP |
| Scope requests for rules, dynamic cases, drag-and-drop designers | Pull toward rebuilding Pega | Route to the rubric change process; default answer is no |
| UI Kit behind by more than one sprint | Effort underestimated | Cut to worklist and form; defer timeline polish |
| Pega team opposes eligibility rubric | Ownership and incentives | Joint ownership of rubric with the Pega architect; shared reporting |
| Performance short of targets in sprint 5 | Database or job executor tuning | Index and executor tuning; scale nodes; revise targets only with product owner approval |
| Key person leaves | Concentrated expertise | Pairing, runbooks, engine hidden behind CWM and Engine Port |

## 14. Start here: next ten working days

| Day | Action | Output |
| --- | --- | --- |
| 1 | Name product owner and tech lead; create repository, decision log, risk register | Governance in place |
| 2 | Confirm host stack and baseline (open questions 1, 2, 6) | Decision D1 |
| 3 | Download the two Pega Academy sample Blueprint zips and generate one simple Blueprint of your own | Fixtures v0 |
| 4 | Request Pega contract and application list; brief legal on file-parsing and fixture use | R1 and R7 started |
| 5 | Kick off inventory (4.3) with the Pega architect | First 10 workflows listed |
| 6 to 7 | Hands-on: embed the reference engine in a host skeleton (S1) | Working spike |
| 8 | Read the sample Blueprint files against their PDFs and draft the element catalog (R3) | Catalog v0 |
| 9 | Draft the engine scorecard and CWM sample for one real workflow | Scorecard v0, CWM v0 |
| 10 | Review findings with steering group; schedule Gate G0 | G0 pack outline |

## 15. Dependencies on SRS open questions

This plan assumes answers to SRS Section 13 by the end of week 1: host stack, identity provider, available Pega inputs and legal use, Tier 1 thresholds and rubric owner, audit retention, UI framework, expected volumes, and whether in-flight migration is ever needed. Unanswered items extend Phase R and should be tracked as risks.

This folder map preserves the existing top-level project directories and places W6W modules, workflow assets and test locations beneath them.

## Updated folder structure

```
main/
│
├── artifacts/
│   ├── demo_presentation/
│   │   └── demo_video.mp4
│   │
│   ├── design/
│   │   ├── design.MD
│   │   ├── srs/
│   │   │   └── requirements-specification.md
│   │   ├── architecture/
│   │   │   └── system-architecture.md
│   │   └── adr/
│   │       └── 0001-workflow-engine.md
│   │
│   ├── playbook/
│   │   ├── runbook.MD
│   │   └── implementation-plan.md
│   │
│   ├── test_results/
│   │   └── results.xml
│   │
│   └── user_stories/
│       └── user_story_1.md
│
├── code/
│   │
│   ├── platform/
│   │   ├── pom.xml
│   │   │
│   │   ├── modules/
│   │   │   ├── w6w-model/
│   │   │   │   └── src/
│   │   │   │       ├── main/java/
│   │   │   │       └── test/java/
│   │   │   │
│   │   │   ├── w6w-compiler/
│   │   │   │   └── src/
│   │   │   │       ├── main/java/
│   │   │   │       └── test/java/
│   │   │   │
│   │   │   ├── w6w-engine-port/
│   │   │   │   └── src/
│   │   │   │       ├── main/java/
│   │   │   │       └── test/java/
│   │   │   │
│   │   │   ├── w6w-engine-flowable/
│   │   │   │   └── src/
│   │   │   │       ├── main/java/
│   │   │   │       └── test/java/
│   │   │   │
│   │   │   ├── w6w-runtime-starter/
│   │   │   │   └── src/
│   │   │   │       ├── main/java/
│   │   │   │       └── test/java/
│   │   │   │
│   │   │   ├── w6w-api/
│   │   │   ├── w6w-governance/
│   │   │   ├── w6w-migrate/
│   │   │   ├── w6w-cli/
│   │   │   └── w6w-test/
│   │   │
│   │   ├── packages/
│   │   │   └── ui-kit/
│   │   │       ├── src/
│   │   │       └── tests/
│   │   │
│   │   ├── examples/
│   │   │   └── host-app/
│   │   │       ├── src/main/java/
│   │   │       ├── src/test/java/
│   │   │       └── pom.xml
│   │   │
│   │   ├── fixtures/
│   │   │   └── migration/
│   │   │       └── blueprint/
│   │   │           └── sample-01/
│   │   │               ├── input.blueprint
│   │   │               ├── input.pdf
│   │   │               └── expected/
│   │   │                   ├── model.json
│   │   │                   ├── workflow.cwm.yaml
│   │   │                   └── migration-report.md
│   │   │
│   │   ├── perf/
│   │   │   ├── load/
│   │   │   └── soak/
│   │   │
│   │   └── ops/
│   │       ├── docker-compose.yml
│   │       ├── helm/
│   │       ├── dashboards/
│   │       └── database/
│   │
│   ├── workflows/
│   │   ├── .w6w/
│   │   │   └── config.yaml
│   │   │
│   │   ├── domains/
│   │   │   └── hr/
│   │   │       └── leave-request/
│   │   │           ├── workflow.cwm.yaml
│   │   │           ├── schemas/
│   │   │           │   └── data.schema.json
│   │   │           ├── forms/
│   │   │           ├── decisions/
│   │   │           ├── tests/
│   │   │           │   └── scenarios/
│   │   │           ├── migration-report.md
│   │   │           └── README.md
│   │   │
│   │   ├── shared/
│   │   ├── templates/
│   │   ├── environments/
│   │   └── build/
│   │
│   ├── schema/
│   │   └── cwm/
│   │       └── v1/
│   │           ├── workflow.schema.json
│   │           ├── form.schema.json
│   │           └── rubric.schema.json
│   │
│   ├── integration-tests/
│   │   └── src/test/java/
│   │
│   ├── src/
│   │   └── main/java/
│   │       └── com/w6w/demo/
│   │
│   ├── tests/
│   │   └── acceptance/
│   │
│   └── tools/
│       ├── license-scan/
│       ├── sbom/
│       └── coverage-report/
│
├── prompts/
│   └── promptsample.txt
│
├── .github/
│   └── workflows/
│       ├── platform-ci.yml
│       ├── workflows-ci.yml
│       ├── cross-check.yml
│       └── release.yml
│
├── .gitignore
├── CODEOWNERS
├── README.md
└── RULES.md
```

The module names and `platform/`, `workflows/`, `schema/`, and `tools/` directories follow the repository format described in the SRS and its implementation plan.&#x20;

Pasted text(20261017-070445).txt



## How to use the existing folders

| Existing location         | Purpose after the update                                                                               |
| ------------------------- | ------------------------------------------------------------------------------------------------------ |
| `artifacts/design/`       | Design documentation, SRS, architecture and architecture decision records                              |
| `artifacts/playbook/`     | Runbooks and implementation plan                                                                       |
| `artifacts/test_results/` | Generated test result reports                                                                          |
| `artifacts/user_stories/` | User stories and acceptance context                                                                    |
| `code/platform/`          | Actual W6W product and its Maven modules                                                               |
| `code/workflows/`         | Git-versioned Canonical Workflow Model definitions                                                     |
| `code/integration-tests/` | Tests across runtime, database, API and timers                                                         |
| `code/src/`               | Existing demo or host-application entry point; migrate production classes to their appropriate modules |
| `code/tests/`             | Cross-module acceptance and smoke tests                                                                |
| `code/schema/`            | Shared CWM, form and eligibility schemas                                                               |
| `prompts/`                | Prompt samples and supporting prompt files                                                             |

Important implementation detail: the individual Maven modules own their production source and unit tests. The existing `code/src/` and `code/tests/` directories are retained as part of your parent structure, but shouldn't become duplicate locations for the same production classes. The `code/platform/pom.xml` should manage the platform modules.

The `.github/` directory is an additional repository-level folder for the SRS CI/CD pipelines; it doesn't replace any of the existing screenshot folders. Test result files and generated workflow builds should normally be excluded from Git or retained only as explicitly required release artifacts.

# W6W Implementation Plan: Gap Fill (Addendum v0.1)

Companion to the SRS and the Research, Implementation and Verification Plan. Date: 17 October 2026.
Each section below names a gap in the current plan, then supplies the missing content. Section numbers (G1 to G16) are local to this addendum.

---

## G1. Cleanups needed in the existing documents

| # | Issue | Fix |
|---|---|---|
| 1 | Rename incomplete: the plan still says W6W in R5, section 8.2 step 3 and section 9.2 step 8; the SRS has leftovers in section 9.2 item 5 and open question 2 | Run a global replace W6W -> W6W, then search to confirm zero hits |
| 2 | Repository layout in the plan (`w6w/platform`, `w6w/workflows`) does not match your actual folder structure (`code/platform`, `code/workflows`, plus `artifacts/`, `prompts/`) | Adopt the mapping in G2 and update CI path filters and CODEOWNERS |
| 3 | `code/src/` and `code/tests/` exist beside the Maven modules, with no defined role | Define them as in G2 (host demo app and cross-module acceptance tests only) |
| 4 | Plan says "Java 17 or later" but Flowable 8 sits on Spring Boot 4 | Pin the baseline at G0 (see G11) |
| 5 | Weeks are relative; no calendar dates | Use the calendar in G3 |
| 6 | MVP acceptance criterion "2 working days" has no definition of what is included | Use the definition in G12 |

## G2. Repository mapping to your folder structure

| Plan path | Actual path in your repo |
|---|---|
| `platform/` | `code/platform/` |
| `workflows/` | `code/workflows/` |
| `schema/` | `code/schema/` |
| `tools/` | `code/tools/` |
| `docs/srs`, `docs/adr`, `docs/architecture` | `artifacts/design/srs`, `artifacts/design/adr`, `artifacts/design/architecture` |
| `docs/runbooks`, implementation plan | `artifacts/playbook/` |
| `docs/rubric/` | `artifacts/design/rubric/` (versioned; also copied into `code/schema/cwm/v1/rubric.schema.json` as a machine form) |
| Test run output | `artifacts/test_results/results.xml` (JUnit XML, produced by CI, not hand-edited) |
| Demo | `artifacts/demo_presentation/` |

Path filters to change in `.github/workflows/`:

- `workflows-ci.yml` triggers on `code/workflows/**`
- `platform-ci.yml` triggers on `code/platform/**` and `code/schema/**`
- `cross-check.yml` compiles every workflow in `code/workflows/` against the platform built from the same commit
- CODEOWNERS: `/code/platform/ @platform-team`, `/code/schema/ @platform-team`, `/code/workflows/domains/<domain>/ @<domain>-owners`, `/artifacts/design/rubric/ @product-owner @pega-architect`

Rules for the two extra folders:

- `code/src/main/java/com/w6w/demo/`: only the thin demo entry point that boots the host-app example. No production classes; those live in the Maven modules.
- `code/tests/acceptance/`: black-box scenario tests that call only the public W6W API (they must not import engine types).
- `code/integration-tests/`: Testcontainers tests (PostgreSQL, timers, restart).
- `artifacts/test_results/` is produced by CI. Commit only the release-tagged copy; otherwise add to `.gitignore`.
- `code/workflows/build/` stays git-ignored.

## G3. Calendar dates (start Monday 19 October 2026)

| Phase | Weeks | Dates |
|---|---|---|
| R. Mobilize and research | 1 to 2 | 19 Oct to 30 Oct 2026 |
| Gate G0 | end wk 2 | 30 Oct |
| 0. Proof of concept | 3 to 4 | 2 Nov to 13 Nov |
| Gate G1 | end wk 4 | 13 Nov |
| 1. MVP build (5 sprints) | 5 to 14 | 16 Nov 2026 to 22 Jan 2027 |
| Gate G2 | end wk 14 | 22 Jan |
| 2. Pilot | 15 to 18 | 25 Jan to 19 Feb 2027 |
| Gate G3 | end wk 18 | 19 Feb |
| 3. Harden and scale | 19 to 26 | 22 Feb to 16 Apr 2027 |
| Gate G4 | end wk 26 | 16 Apr |

Calendar risks to confirm with the team: Diwali (around 8 Nov 2026), year-end leave in late December, and Republic Day (26 Jan 2027, inside Gate G2 week). Add 1 to 2 weeks of float in Phase 1 if December leave is heavy. Gates are re-dated at G1.

## G4. Gap: the Canonical Workflow Model is only a sample, not a specification

The SRS shows one example but never defines the language. Before sprint 1, write a CWM v1 specification covering:

1. **Step types, with exact fields:** `userTask`, `approval`, `serviceTask`, `decision`, `parallel` (fork/join), `subStage`, `waitEvent`, `timer`, `end`.
2. **Outcomes and transitions:** named outcomes, default outcome, `next`, `stage:<id>`, `end:<resolution>`. No free-form goto.
3. **Expression language:** pick one (recommendation: Spring Expression Language or JUEL, because Flowable supports both) and restrict it to a safe subset (no method calls on arbitrary beans, no reflection, whitelisted functions only). Document it in the spec.
4. **Time values:** ISO-8601 durations; business-calendar form `PT8BH` reserved for P2.
5. **Error handling:** per step `onError: retry(n, backoff) | route(stage) | fail`; dead-letter rules.
6. **Data:** JSON Schema draft 2020-12 subset; no remote `$ref`.
7. **Versioning rules:** `apiVersion` bumps; additive-only inside `v1`; deprecation window of two minor releases (matches NF-14).
8. **Reserved keys** for migration traceability (`source.element-id`, `source.system`, `source.confidence`).
9. **Conformance suite:** a set of valid and invalid CWM fixtures that both the linter and the compiler must pass.

Acceptance: spec reviewed by tech lead and Pega architect; every Pega mapping row in SRS section 7 has a CWM construct.

## G5. Gap: Pega-to-CWM mapping covers too few constructs

SRS section 7 lists 14 mappings but says nothing about the many Pega constructs a real Blueprint will contain. Proposed disposition table (to be validated against real exports in R3):

| Pega construct | Disposition | Handling |
|---|---|---|
| Case type, stages, steps (collect, approval, automation) | Convert | As SRS section 7 |
| Alternate stage, resolution stage | Convert | As SRS section 7 |
| Persona, access role | Convert | CWM `roles` |
| Data object, fields, simple validation | Convert | JSON Schema |
| SLA goal/deadline/urgency | Convert (goal and deadline); urgency becomes task priority | Warn on escalation actions outside the whitelist |
| Simple decision table | Convert | DMN |
| Child case (one level) | Convert | Call activity |
| Parallel processes, split-join | Convert if two to four branches and no nested splits | Else reject |
| Conditions ("when" rules) | Approximate | Translate simple comparisons; flag complex ones |
| Notifications and correspondence | Approximate | Template stub, text reviewed by a human |
| Integrations (connectors, data pages) | Stub | Action-registry stub with the endpoint name, never working code |
| Declarative rules, data pages, activities | Unsupported | Report with reason |
| Case hierarchy deeper than one level, dynamic or ad hoc cases | Reject whole workflow | Stays in Pega |
| Decision strategies, predictive or adaptive models, next-best-action | Reject whole workflow | Stays in Pega |
| Constellation views, portals, channels | Out of scope | UI Kit generic forms replace them |
| Reports and dashboards | Out of scope | Standard W6W metrics instead |
| Unknown element type | Report and continue | MG-14 |

Add three outputs per conversion: a **coverage score** (converted / approximated / unsupported counts), a **confidence tag** per element, and a **manual follow-up checklist** ordered by effort.

## G6. Gap: walking skeleton and a worked end-to-end example

The plan has spikes but no single thin vertical slice. Add a **walking skeleton** as the first deliverable of Phase 0 (days 1 to 3):

1. A two-step leave-request CWM (`submit` -> `manager approval` -> `notify`), compiled to BPMN by a minimal compiler.
2. Started and completed through the REST facade on the Flowable engine behind the Engine Port.
3. One worklist call and one task completion via the UI Kit form.
4. Run in CI with `results.xml` published.

Everything after that grows from this slice. Use the `hr/leave-request` workflow (already in your folder structure) as the reference example, and keep its CWM, form, data schema, scenario tests and generated BPMN as the golden documentation example.

## G7. Gap: engine and baseline decisions needed before sprint 1

Decide and record as ADRs under `artifacts/design/adr/`:

| ADR | Decision | Input needed |
|---|---|---|
| 0001 | Reference engine (Flowable 8 vs alternate) | Host stack, scorecard R4 |
| 0002 | Java and Spring Boot baseline (Flowable 8 needs Spring Boot 4; check Java level and that your other dependencies support it, or stay on the 7.x line) | Host stack |
| 0003 | Embedded library vs sidecar | Host team |
| 0004 | Expression language | G4 |
| 0005 | Form renderer approach (framework-agnostic web components vs host framework) | Host UI |
| 0006 | Audit store (engine history tables vs separate append-only table) | Compliance (R6) |
| 0007 | Identity and role mapping model | IdP team |
| 0008 | Cutover pattern (new-cases-only default) | Pega CoE |

Open question to the user: the host stack is still unconfirmed in all prior material. Until it is, treat the Java and Flowable path as an assumption.

## G8. Gap: effort, capacity and budget

The plan gives team shape but no numbers. Proposed planning baseline (re-baseline at G1):

| Phase | Effort (person-weeks, indicative) | Notes |
|---|---|---|
| R | 8 to 10 | Mostly product owner, analyst, architect and advisors |
| 0 | 12 to 16 | Whole core team for two weeks |
| 1 | 55 to 70 | Seven people over ten weeks, less part-time roles |
| 2 | 20 to 24 | Pilot support dominates |
| 3 | 40 to 50 | P2 features plus onboarding batches |

Also add:

- **Infrastructure estimate:** dev, test, pre-prod, prod (application nodes, PostgreSQL, CI runners, observability).
- **Run cost model per workflow**, compared to the Pega baseline from R1.
- **Break-even calculation:** build cost / annual saving per migrated workflow = number of workflows needed. If the candidate inventory is below that, the business case at G0 fails.
- **Contingency:** 20% on Phase 1.

## G9. Gap: stakeholder, change management and training

| Item | Content |
|---|---|
| RACI | Product owner (A for scope), tech lead (A for architecture), Pega architect (R for rubric), security (C), legal (C), operations (R for runbooks), process owners (R for UAT) |
| Communication plan | Fortnightly sponsor update, sprint demos, one-page monthly status with KPIs from section 12 of the plan |
| Pega CoE engagement | Joint rubric ownership; agree a framing of "right-sizing" so the CoE focuses on Tier 2/3 |
| Training | Developer workshop (half day), CWM authoring guide, analyst reading guide for generated summaries, admin console training for support |
| Adoption | Template catalog, office hours, a "first workflow in a day" onboarding track |
| Exit criteria for adoption | At least 10 workflows live and two teams building workflows without core-team help (G4) |

## G10. Gap: operations and support model

- **Support tiers:** L1 helpdesk, L2 W6W support (admin console actions), L3 platform engineers.
- **Severity definitions:** Sev1 (lost or duplicated cases, total outage), Sev2 (SLA timers not firing, degraded latency), Sev3 (single workflow defect), Sev4 (cosmetic). Response and resolution targets per severity, set with operations.
- **On-call:** begins at pilot; rotation of at least four people before G4.
- **Runbooks to write** (in `artifacts/playbook/`): deploy and rollback, stuck instance, dead-letter handling, timer backlog, database restore, engine upgrade, schema migration, credential rotation, incident template.
- **Patch policy:** critical dependency CVEs patched within 7 days, high within 30 days.
- **Upgrade cadence:** engine and Spring minor updates quarterly through the upgrade test suite.
- **Archival:** history older than the retention period moves to cold storage; test restore quarterly.

## G11. Gap: security and compliance detail

- Threat model document (STRIDE) in `artifacts/design/architecture/`, produced in sprint 1.
- CWM expression sandbox review (see G4, item 3), since user-authored expressions are the main injection risk.
- Secrets: host secret store only; CI secret scanner blocks commits.
- PII handling: data classification per field, masking in logs and audit exports (DF-04), retention per workflow.
- Segregation-of-duties tests for every approval workflow.
- Third-party license scan and SBOM gating (NF-09, SE-05).
- Use of LLM assistance (MG-13): written policy on which data may leave the organization, prompt logging, human review before merge.
- Legal confirmation before any real `.blueprint` or Pega-derived content is stored as a fixture (R7).

## G12. Gap: definition of "days, not weeks"

Define the metric precisely so the pilot can prove it.

- **Clock starts:** approved one-page spec (steps, roles, SLA, data fields).
- **Clock stops:** workflow deployed to production behind a flag, with passing scenario tests and approved pull request.
- **Included:** authoring, form definition, test scenarios, review, CI/CD run.
- **Excluded:** waiting for other teams' integration endpoints, security sign-off lead time, UAT waiting time.
- **Target:** two working days for a template-based workflow, five working days for a workflow with one integration.
- **Baseline:** measure the same steps for the equivalent Pega workflows (use history from R2). Report median and range, not the best case.

## G13. Gap: requirements traceability matrix seed

Create `artifacts/design/srs/traceability.csv` with columns: `req_id, priority, phase, module, test_id, test_level, status`. Seed all requirement IDs from the SRS (WF, EX, HT, DF, DC, IN, API, UI, VD, AU, SE, OP, DX, GV, MG, NF). A CI job (`tools/coverage-report/`) fails the build if any Must requirement targeted by the current gate has no passing test, as already required in plan section 7.5. Generate `artifacts/test_results/results.xml` and a requirement-coverage report from the same run.

## G14. Gap: user stories and acceptance scenarios

Seed `artifacts/user_stories/` with at least one story per user class in SRS section 3.2:

1. Workflow developer: "As a developer, I scaffold an approval workflow from a template and deploy it by pull request within two days."
2. Business analyst: "I read a generated diagram and summary and approve without reading YAML."
3. Task user: "I see only my tasks, complete a form and track status."
4. Process owner/admin: "I find a failed instance, retry it and see SLA breaches."
5. Pega architect: "I run the eligibility checker and receive pass, review or reject with reasons."
6. Auditor: "I export who did what and when for a completed instance."
7. Platform/SRE: "I deploy, scale and restore from backup using the runbook."

Each story has Given/When/Then acceptance scenarios that map to automated tests in `code/tests/acceptance/`.

## G15. Gap: demo and evidence package

Your structure includes `artifacts/demo_presentation/` and `prompts/`, which the plan does not cover. Add:

- **Demo script (8 to 10 minutes):** (1) a Blueprint in; (2) eligibility report; (3) generated pull request with CWM, diagram and migration report; (4) CI run; (5) running workflow with worklist, approval, SLA timer firing; (6) admin console retry; (7) audit export.
- **Demo data:** the `hr/leave-request` workflow plus one rejected (ineligible) Blueprint to show the reject path.
- **Evidence pack per gate:** requirements coverage, `results.xml`, performance results, security scan summary, KPI snapshot.
- **Prompts folder:** keep prompt files used for generation or LLM-assisted drafting, each with purpose, input, expected output and review rule.
- **RULES.md:** list working rules (no engine types in public API, CWM is the source of truth, generated files never committed, no unsupported construct silently converted).

## G16. Additional risks missing from the register

| ID | Risk | Mitigation |
|---|---|---|
| R10 | Flowable 8 / Spring Boot 4 is new; ecosystem libraries may lag | Pilot the baseline in S1; keep a fallback to the 7.x line |
| R11 | Open-source edition lacks a modeler or admin UI; effort for UI Kit grows | Hold the UI Kit scope at worklist, form, timeline, admin |
| R12 | Pega contract terms may block parsing or reuse of exports | Legal gate before adapter work; hand-authored CWM remains the fallback |
| R13 | Business case fails because Pega minimums prevent savings until renewal | Test at G0; reframe value as capacity release and delivery speed |
| R14 | Not enough eligible workflows to amortize the build | Break-even check in G8; stop or switch to buy at G0 |
| R15 | Users bypass the eligibility rubric and put complex logic into W6W | Linter and rubric as hard gates; quarterly portfolio review |
| R16 | LLM-assisted drafting leaks data or produces wrong form text | Policy in G11; suggestions only, never auto-merge |
| R17 | Single database becomes a shared bottleneck across workflows | Per-workflow retention, archival, index review, load test at S7 |

## G17. Updated gate checklists (additions)

- **G0 adds:** break-even calculation; ADRs 0001 to 0003 recorded; legal opinion on fixtures; RACI agreed.
- **G1 adds:** walking skeleton running in CI; CWM v1 specification reviewed; effort baseline measured for three hand-built workflows; calendar re-baselined.
- **G2 adds:** traceability matrix with no untested Must requirement; threat model closed with no open high findings; runbooks drafted; demo rehearsed.
- **G3 adds:** "days not weeks" measured against the G12 definition; support model staffed; one incident drill run.
- **G4 adds:** two non-core teams delivered a workflow independently; patch and upgrade cycle exercised once.

## G18. Next three actions

1. Apply G1 (rename cleanup, repository mapping, CI path filters).
2. Confirm the host stack (open question 1) and record ADRs 0001 and 0002.
3. Write the CWM v1 specification (G4) and build the walking skeleton (G6).
# Run the current implementation locally

The platform is an embedded Java workflow engine: Flowable runs in-process and
the demo stores engine state in a local H2 file database. The Blueprint PDF
path uses Apache PDFBox; there is no hosted inference or embedding service in
the current implementation.

Requirements: Windows, PowerShell, and a Java 17+ JDK. Maven 3.9.9 is included
under `code/platform/.maven/`; Maven downloads project dependencies the first
time it runs. From the repository root:

```powershell
.\run-app.ps1
```

Open `http://127.0.0.1:8080` for the local W6W workspace. It includes workflow
catalog and launch, a role-filtered worklist with schema-driven task forms,
case history/timelines, and local operator actions. REST endpoints are under
`/api/v1` (the `/api` alias remains available); OpenAPI documentation is served
at `/swagger-ui/index.html`. The app
uses a local H2 SQL file database in `data/w6w-app` and reads versioned
definitions from `code/workflows/`. It runs in `local-demo` mode with a demo
identity selector; this is for local development and is not an authentication
or production deployment configuration.

To run the local scenario suite and refund approval/denial examples:

```powershell
.\run-local.ps1
```

This builds and verifies the Maven modules, runs the refund approval and policy
denial scenarios locally, and writes:

- `data/w6w-demo.mv.db` — persistent embedded H2 SQL database
- `artifacts/test_results/results.xml` — JUnit-compatible scenario report

The generated database and test report are local runtime outputs and are
ignored by Git. To run only the automated Maven verification suite:

```powershell
.\code\platform\mvnw.cmd -f .\code\platform\pom.xml verify
```

## Implementation companion documents

The complete SRS and plan remain in this file. Start with the [workflow author quick start](artifacts/playbook/workflow-author-quick-start.md) to create and maintain a local workflow, and the [local operations runbook](artifacts/playbook/local-operations.md) for backup and restore procedures. Current repository responsibilities and ownership are documented in [repository-structure.md](artifacts/design/architecture/repository-structure.md); the CWM v1 contract is in [cwm-v1.md](artifacts/design/architecture/cwm-v1.md); authoring and assistant modes are in [agent-modes.md](artifacts/design/architecture/agent-modes.md). [traceability.csv](artifacts/design/srs/traceability.csv) tracks all 95 functional and 14 non-functional requirements against current code and test evidence. `RULES.md` lists contribution and safety rules.

The traceability matrix intentionally distinguishes partial implementation from complete acceptance. The local application provides a workflow workspace and REST/OpenAPI integration surface. The W6W CLI supports scaffold, validate, compile, render, scenario test, local reload, diff, and Blueprint PDF migration with versioned mapping rules that can be overridden. These are local authoring paths; production identity and authorization, full CWM step-type and SLA behavior, additional migration adapters and Git pull request delivery, production database/deployment profiles, and several operations and CI acceptance gates remain open requirements. Use `artifacts/design/srs/traceability.csv` for evidence by requirement; do not treat local-demo mode as production ready.

---

