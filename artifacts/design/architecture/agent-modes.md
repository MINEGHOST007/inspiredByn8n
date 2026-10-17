# Workflow authoring agent modes

This policy defines modes for human developers and optional AI assistants working with W6W definitions. The workflow runtime itself is deterministic and does not invoke an AI agent. These modes do not change authorization, eligibility or deployment gates.

| Mode | Purpose | Allowed output | Required human action |
|---|---|---|---|
| `draft` | Scaffold CWM, forms, data-schema stubs or scenario outlines from an approved brief | New draft files on a feature branch; no deployment | Workflow owner checks fields, roles, transitions and data classification. |
| `migrate` | Extract a Blueprint PDF and map recognized constructs into an intermediate model and CWM draft | Draft CWM, schema/form stubs, smoke-test outline and a conversion report | Pega architect resolves unknown/low-confidence items; workflow owner completes missing behavior. |
| `review` | Check an existing change for schema errors, unsupported types, eligibility findings, unsafe expressions, missing tests and migration uncertainty | Findings only; it cannot edit or merge files | Required owners address blocking findings and approve the pull request. |
| `execute` | Run an already deployed, validated workflow instance | Runtime task actions allowed by host identity and workflow policy | No model or definition changes are allowed from execution context. |

## Guardrails

- `draft`, `migrate` and `review` are advisory authoring modes. An agent cannot merge a change, bypass CODEOWNERS, change the eligibility rubric, or deploy to an environment.
- Unknown constructs remain visible as `UNSUPPORTED`; low-confidence extraction remains `APPROXIMATED` and requires review. A mode cannot silently turn either into a user task or service action.
- Approval roles and expressions must come from the owning workflow team. The migration adapter must not infer access from a persona list alone.
- Secrets, credentials, production data and unrestricted Pega exports must not be sent to a remote model. The default implementation uses no hosted model. Any future local-model integration is opt-in, isolated from runtime execution and subject to the data policy.
- Human approval and ordinary CI checks are required before a generated workflow can enter an environment. An agent may prepare a change; it is never the final approver.
- `execute` is not an agent mode in the sense of autonomous planning: it is the existing deterministic workflow runtime, subject to authentication, authorization and audit policy.

## Suggested use

1. Start with `draft` from a short, reviewed process brief or `migrate` from an allowed PDF.
2. Run `review` and address all errors, unknown constructs and medium/low confidence items.
3. Have the process owner and platform reviewers approve the pull request.
4. Use CI/CD to publish the immutable definition version; runtime execution uses that version only.

The current code implements the PDF migration path and deterministic validators. Automated agent orchestration and local embedding-model integration are not implemented; see the requirement statuses and repository map.
