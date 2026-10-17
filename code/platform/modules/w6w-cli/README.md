# W6W Workflow CLI

The `w6w` PowerShell launcher builds the local modules and runs the command
against the repository at `W6W_ROOT`.

```powershell
.\w6w.ps1 help
.\w6w.ps1 new --key leave-request --template request-with-sla --owner people-ops --domain people
.\w6w.ps1 validate code/workflows/domains/people/leave-request/workflow.cwm.yaml
.\w6w.ps1 compile-decision code/workflows/domains/customer-service/refund-processing/decisions/refund-eligibility.yaml
.\w6w.ps1 compile code/workflows/domains/people/leave-request/workflow.cwm.yaml
.\w6w.ps1 render code/workflows/domains/people/leave-request/workflow.cwm.yaml
.\w6w.ps1 test code/workflows/domains/people/leave-request/workflow.cwm.yaml
.\w6w.ps1 deploy-local leave-request
.\w6w.ps1 diff code/workflows/domains/people/leave-request/workflow.cwm.yaml
.\w6w.ps1 migrate-pdf .\blueprint.pdf --dry-run
```

`migrate-pdf` accepts PDF blueprints and writes reviewable draft packages. It
supports `--out DIRECTORY`, `--dry-run`, and `--rules RULES.yaml` for a
versioned mapping-rule override. The default rules live in the migration
module's `migration/blueprint-mappings-v1.yaml`. It does not parse proprietary
`.blueprint` binaries. Generated workflow drafts are not deployed automatically.
Generated DMN is written to `code/workflows/build/decisions/` by default so the
human-authored decision YAML remains the Git source of truth.
