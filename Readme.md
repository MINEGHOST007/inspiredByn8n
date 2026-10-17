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
