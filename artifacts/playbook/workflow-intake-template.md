# Workflow intake worksheet

Complete this short worksheet with the process owner before creating or
migrating a workflow. Keep answers in business language. This helps the author
map each step to the right role, form, action, and test scenario.

## 1. Process basics

- **Business name:**
- **Purpose / outcome:**
- **Process owner and team:**
- **Starting event:**
- **What counts as complete:**
- **Data classification:** public / internal / confidential / restricted
- **Source process or Blueprint (if migrating):**

## 2. Steps

List one row for each stage or action. Add rows as needed.

| Order | What happens? | Who does it? (role) | Information needed | Decision choices and next step | Manual or automatic? |
|---|---|---|---|---|---|
| 1 | | | | | |
| 2 | | | | | |
| 3 | | | | | |

## 3. Data and integrations

- **Fields a person must enter or review:**
- **Sensitive fields and why they are needed:**
- **Automatic actions needed (for example, create a case or send a message):**
- **Registered host action names / integration owners:**
- **What should happen if an action fails?:**

## 4. Outcomes and exceptions

- **Normal successful outcome:**
- **Rejection / ineligible outcome:**
- **Other exception paths:**
- **Who resolves an exception?:**
- **Response-time expectations (if any):**

## 5. Examples for testing

Describe at least one realistic but anonymized example for each important path.

| Scenario name | Starting information | Decisions made | Expected final outcome |
|---|---|---|---|
| Successful path | | | |
| Rejected or exception path | | | |

## 6. Review checklist

- [ ] The process owner agrees that the written steps match the real process.
- [ ] Each human step has an accountable role.
- [ ] The owner approved the information collected and its classification.
- [ ] Every decision choice has a clear next step or final outcome.
- [ ] Each automatic action has an integration owner and registered action name.
- [ ] Success and rejection/exception examples are available for scenario tests.
- [ ] The workflow owner and reviewers are named.
- [ ] Open questions and unsupported requirements are listed for follow-up.

## Mapping this worksheet into the repository

| Worksheet item | W6W location |
|---|---|
| Process name, owner, source, classification | `metadata` in `workflow.cwm.yaml` |
| Steps, roles, decisions and outcomes | `stages` and `steps` in `workflow.cwm.yaml` |
| Workflow data fields | `schemas/data.schema.json` |
| Human task inputs | Files under `forms/` |
| Anonymized examples | Files under `tests/scenarios/` |
| Business explanation | Workflow package `README.md` |
