# W6W repository rules

1. CWM, forms, schemas, decision tables and scenarios are the reviewed source of truth. Generated BPMN/DMN and runtime databases are build/runtime output.
2. Keep engine-specific classes inside `w6w-engine-flowable`. Public API contracts must not expose Flowable types.
3. Unsupported workflow constructs fail schema/linter/compiler checks; never silently convert them to user tasks or service tasks.
4. Migration output is a draft. Preserve confidence and source findings, require human review, and do not deploy unsupported or ineligible workflows.
5. Do not infer approvers, role access, decision conditions, SLA policies or resolution states from names or visual appearance alone.
6. Local development must run without hosted AI or database services. The current demo uses the local H2 file database; production PostgreSQL readiness is a separate requirement.
7. Secrets and real production data never go in CWM, Git, prompts or test fixtures. Use synthetic or approved masked fixtures.
8. Workflow changes require domain-owner review. Platform/schema/rubric changes require platform-owner review and migration notes as appropriate.
9. Keep production Java in Maven modules. `code/src/` is reserved for thin host/demo entry points; do not duplicate module classes there.
10. An authoring assistant may draft or report. It cannot bypass CI, CODEOWNERS, eligibility, human approval or environment promotion.
