# W6W local proof-of-concept demo (8 minutes)

This script demonstrates the working local slice and calls out unfinished product areas without implying they are available.

1. **Problem and boundary (1 min):** introduce Tier 1 long-tail workflows and state that complex case management stays in Pega.
2. **Git workflow definition (1 min):** open `code/workflows/domains/customer-service/refund-processing/workflow.cwm.yaml` and its data schema, forms and scenarios. Explain that CWM is the source of truth.
3. **Local runtime (2 min):** run `run-local.ps1`; show the Standard Approval and Policy Denied scenario outcomes and local H2 file. The runtime is embedded in the Java process.
4. **Blueprint migration (2 min):** show the sample PDF test and generated intermediate model fixture. Explain that layout extraction is deterministic, icon classification is heuristic, and the generated case draft needs manual review.
5. **Governance (1 min):** open the migration report and rubric. Show the explicit `REVIEW`/unsupported findings and fail-closed compiler behavior.
6. **Next gates (1 min):** point to `traceability.csv`; show that REST/OpenAPI, UI Kit, CLI, production PostgreSQL, auth/audit/security and operations runbooks remain open.

Do not demo a production worklist/admin screen, OIDC login, PostgreSQL recovery, agent-driven deployment, or `.blueprint` binary conversion: these capabilities are not implemented.
