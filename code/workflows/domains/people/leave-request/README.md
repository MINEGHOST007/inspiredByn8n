# Leave Request POC

This reference Tier 1 workflow demonstrates the W6W value proposition: a small approval process is defined in Git, rendered as schema-driven forms, executed by the embedded Flowable runtime, and resolved with an auditable outcome.

Flow: employee submits leave details -> manager approves or rejects -> terminal resolution.

Validate and test it from the repository root:

```powershell
.\w6w.ps1 validate code/workflows/domains/people/leave-request/workflow.cwm.yaml
.\w6w.ps1 test code/workflows/domains/people/leave-request/workflow.cwm.yaml
```

This is a local proof of concept. Identity selection, production authorization, notifications, and CI deployment remain host-integration work.
