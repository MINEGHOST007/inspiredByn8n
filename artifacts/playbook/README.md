# W6W playbook index

The phased implementation plan and SRS are maintained in the root [Readme.md](../../Readme.md) to preserve the provided project document. Companion documents include the [repository map](../design/architecture/repository-structure.md), [CWM v1 contract](../design/architecture/cwm-v1.md), [Tier 1 rubric](../design/rubric/tier-1-eligibility.md), and [requirement traceability matrix](../design/srs/traceability.csv).

## Learn to author and maintain workflows

Start with the [workflow quick start](quick-start.md) for the beginner-friendly
path through the current local project. Before editing a definition, complete
the [workflow intake worksheet](workflow-intake-template.md) with the process
owner. The quick start explains the example folder, supported task types,
scenario testing, local commands, maintenance, and current product limits.

## Local developer run

From the repository root on Windows with Java 17+:

```powershell
.\run-local.ps1
```

This runs the full Maven install/test lifecycle, then the two scenario paths against the local H2 file database. PDF migration extraction and file generation are covered by `w6w-migrate` tests. Generated files stay under Maven `target/`; scenario output is written to ignored `data/` and `artifacts/test_results/` paths.

## Operational runbooks still required before production

Production runbooks for deploy/rollback, stuck instances, dead letters, timer backlog, PostgreSQL backup/restore, schema migration, engine upgrade, credential rotation, retention/archive, and incident response have not been implemented. The current H2 local demo is not a production backup or recovery solution.
