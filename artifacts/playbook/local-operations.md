# Local operations runbook

This procedure covers the local-demo installation only. It is not a production
backup, disaster-recovery, or records-retention design.

## Start and stop

- From the repository root, run `.\run-app.ps1` in PowerShell.
- Confirm `http://127.0.0.1:8080/api/v1/health` returns `UP` and the expected
  workflow count.
- Stop the application with `Ctrl+C` in the same PowerShell window. Wait for
  Spring Boot and Flowable to close and the H2 connection pool to stop before
  copying or restoring the database.
- Workflow definitions stay in Git under `code/workflows/`. The local engine
  deploys changed CWM as a new Flowable version on the next startup. Do not
  replace database contents to publish a workflow change.

## Back up the local database

The app defaults to the H2 database file `data/w6w-app.mv.db`. The path can be
changed with `W6W_DB_PATH`; copy the file at the configured path instead when
that override is set.

1. Stop the app as described above.
2. Confirm the server process has exited and the database file is no longer
   changing.
3. Copy `data/w6w-app.mv.db` to a dated backup location outside the Git repo.
4. Record the backup date, app commit, workflow commit, and file size alongside
   the copy. Keep backups access-controlled because workflow instances and
   audit events may contain sensitive business data.
5. Restart the app and verify health.

Do not copy the database while the application is running. Do not commit the
database or backups into Git.

## Restore

1. Stop the app and confirm the process has exited.
2. Preserve the current database file as a separate recovery copy.
3. Copy the selected backup to the configured H2 path, replacing the local
   database file.
4. Start the app and confirm `/api/v1/health` reports `UP`.
5. Check `/api/v1/workflows`, a known case in `/api/v1/instances`, and its audit
   events at `/api/v1/instances/{instanceId}/audit`.
6. If startup or data checks fail, stop the app and restore the preserved
   pre-restore file. Keep the failed backup for investigation.

## Workflow and audit records

The Flowable H2 database contains deployed definitions, active instances,
history, and the local `w6w_audit_event` action log. Do not manually update or
delete Flowable or audit tables. The application currently has no automatic
history archival, retention enforcement, scheduled backup, backup encryption,
or tamper-evidence. Set and approve a records-retention policy before using
real business data; until then, treat all local workflow and audit data as
retained indefinitely and restrict access to the machine and database file.

## Basic checks and recovery

- Health: `GET /api/v1/health`
- Readiness: `GET /actuator/health/readiness`
- Metrics: `GET /actuator/metrics` (local application metrics only; custom
  business counters and dashboards are not yet implemented)
- API reference: `http://127.0.0.1:8080/swagger-ui/index.html`
- Scenario suite: `.\run-local.ps1`; it must finish with zero failures and
  writes JUnit-compatible evidence to `artifacts/test_results/results.xml`.

## Failed service steps

Generated service and decision steps run as durable asynchronous jobs. The
runtime retries each failed job three times with a one-minute delay by default;
a step can override its retry count and delay with `onError.retries` and
`onError.backoff`. After retries are exhausted, open **Operations** and review
the **Failed service steps** table. It shows the case, workflow, step, error
summary, and failure time. Fix the underlying integration or data problem, then
select **Retry** to requeue the job with a fresh set of three attempts. Replays
are recorded in the local audit log. The local identity selector is not an
authorization boundary; production use requires host authentication and
authorization around queue inspection and replay.

If startup fails after a workflow edit, inspect the terminal for CWM lint,
action registration, YAML, or BPMN compilation errors. The app binds to
loopback by default and should not be exposed to a network in local-demo mode.
