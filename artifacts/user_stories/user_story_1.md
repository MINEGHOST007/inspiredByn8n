# User Stories & Acceptance Scenarios: W6W Tier-1 Workflow Platform

## Story 1: Workflow Developer Delivery
**As a** workflow developer,  
**I want to** scaffold a workflow from a Pega Blueprint skeleton or template, customize form schemas, and deploy via Git PR,  
**So that** simple business processes can be delivered to production in under 2 working days.

### Acceptance Criteria
- **Given** an approved Blueprint PDF or CWM YAML specification,
- **When** I run `w6w migrate` or use the CLI scaffold,
- **Then** a valid CWM YAML workflow, form JSON schema stubs, action registry contracts, and test scenarios are generated in minutes,
- **And** CI passes linter checks, compiles deterministic BPMN 2.0 XML, and executes scenario tests with simulated clocks.

---

## Story 2: Business Analyst Review
**As a** business analyst,  
**I want to** review workflow definitions in plain language with a rendered lifecycle diagram directly in the Pull Request,  
**So that** I can verify business requirements without needing to read underlying code or BPMN XML.

### Acceptance Criteria
- **Given** a pull request containing changes in `code/workflows/`,
- **When** the workflow CI pipeline executes,
- **Then** an updated stage/step lifecycle diagram and structured step summary are posted to the PR.

---

## Story 3: Task User Execution
**As an** operations agent or manager,  
**I want to** see tasks assigned to my role in my worklist and complete them using dynamic, schema-driven forms,  
**So that** I can process intakes and approvals quickly with full data validation.

### Acceptance Criteria
- **Given** an active task in a workflow instance assigned to my role,
- **When** I open the task in the worklist,
- **Then** the form renders according to its JSON Schema, enforces required fields, and submits the outcome to advance the workflow.

---

## Story 4: Process Owner & Admin Operations
**As a** process owner or support engineer,  
**I want to** monitor running instances, view SLA breaches, and retry failed service tasks from a dead-letter queue,  
**So that** operational exceptions can be resolved without engineering interventions.

### Acceptance Criteria
- **Given** a transient external failure during a service step (e.g. payment gateway timeout),
- **When** the step retries 3 times and moves to dead-letter status,
- **Then** the admin console displays the failure details, allows parameter inspection, and provides a one-click retry.

---

## Story 5: Pega Architect Tier Governance
**As a** Pega system architect,  
**I want** an automated eligibility checker that verifies workflows against Tier 1 boundaries (max 1 child level, <= 5 stages, <= 25 steps, no adaptive AI),  
**So that** complex case management stays strictly in PEGA and prevents scope creep in the lightweight engine.

### Acceptance Criteria
- **Given** a new workflow definition or blueprint,
- **When** the eligibility checker executes,
- **Then** it produces an explicit scorecard (`Pass`, `Review`, or `Reject`) with itemized reasons.
