# Blueprint PDF migration smoke-test: Refund Processing

- **Source:** Blueprint `BP-1766322`, PDF layout adapter
- **Extraction:** 3 case types; Refund Processing has 4 primary stages, 2 alternate stages, 17 task labels, 13 extracted data fields and 2 business-rule rows.
- **Eligibility:** `REVIEW` for the generated draft. The PDF does not provide an approved data classification or per-step role assignments; the Tier 1 checker also marks any one-level child workflow for runtime review.
- **Deployment status:** draft only. This report records the output of the migration smoke test; the hand-authored example CWM in this package is not the generated CWM output.

## Review findings

| Construct | Disposition | Confidence | Required follow-up |
|---|---|---|---|
| Case and stage names/order | Converted | Medium | Verify lifecycle and terminal resolution semantics. |
| Data field names/types | Approximated | Medium | Confirm clipped PDF text, field constraints and requiredness. |
| User tasks | Approximated | Medium | Assign authorized W6W roles/users and complete form bindings. |
| Approval tasks | Approximated | Medium | Author routing, outcomes and SLA explicitly. |
| Service tasks | Approximated | Medium | Implement and register each action handler. |
| Decision and parallel icons | Unsupported | Medium | Author explicit decision table/branches and join behavior; compiler currently fails closed for these types. |
| Business rules | Unsupported | Low | Translate rule text into reviewed CWM/DMN and add decision tests. |
| Child case | Approximated | Medium | Confirm the child key and callback; current runtime does not execute child-workflow callbacks. |

## Smoke-test notes

- The local test verifies PDF extraction, intermediate-model construction, migration reporting, output planning and dry-run no-write behavior.
- The test also checks that a `.blueprint` binary is refused with an explicit unsupported-format message. This file format is not decoded.
- Generated output is reviewable scaffolding; this run does not claim that the PDF-derived draft compiles or is ready to deploy.
- Follow-up: set workflow owner/data classification, map personas to roles, resolve unsupported rule/decision constructs, run the linter/compiler, then execute scenario tests with simulated users.
