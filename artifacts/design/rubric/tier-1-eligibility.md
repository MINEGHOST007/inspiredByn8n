# Tier 1 eligibility rubric v1

**Status:** proposed defaults from the SRS; not a signed-off business policy. The checker is configurable but currently uses code defaults.

## Pass / review / reject

| Decision | Condition |
|---|---|
| Reject | Predictive/adaptive/AI decisioning, ad-hoc case creation, or child hierarchy deeper than one level. Keep the workflow in Pega. |
| Review | More than two assignee roles, more than four parallel branches, conditions requiring manual interpretation, any one-level child workflow until callback support is verified, more than five primary stages, more than 25 total steps. |
| Pass | None of the reject/review conditions apply and the workflow is structurally valid. |

## Other eligibility conditions

- The process has an owner, a clear end state and a data classification.
- No cross-case dependency or real-time decision strategy is required.
- All step types are explicitly supported and every human task has an approved assignee.
- The CWM linter passes. Migration unknowns and low-confidence mappings are resolved.
- A pass from the rubric does not by itself mean deployable: security, audit, scenario tests and human approvals are separate gates.

Rubric changes are versioned in Git and require product-owner and Pega-architect review. The runtime checker currently covers child depth, AI/adaptive step types, ad-hoc case types, role count, parallel branch count, manual-condition flags, stage count and step count. Cross-case dependency and data-classification checks remain gaps.
