# W6W Local Application

The local web workspace is the user-facing entry point for the current W6W
vertical slice. Start it from the repository root with `.\run-app.ps1`, then
open <http://127.0.0.1:8080>.

The application runs only in `local-demo` mode. It loads workflow definitions
from `code/workflows/`, compiles and deploys them to embedded Flowable, and
stores engine state in the local H2 database under `data/w6w-app`. The browser
assets are bundled in this module and use no remote scripts or stylesheets.

The workspace includes a workflow catalog and start form, task worklist with
schema-driven forms, case list and activity history, and local operator actions
for pause, resume, cancel and reassignment through the REST API. User and
operator actions are appended to the local `w6w_audit_event` SQL table and can
be queried per case at `/api/instances/{instanceId}/audit` or globally at
`/api/audit/events`; `/api/audit.csv` exports the action log. It is bound to
loopback by default. The local identity selector is a development convenience,
not authentication. Configure host identity, authorization, real action
handlers, backup and deployment controls before exposing the application to
other machines or production traffic.

REST endpoints are available at `/api/v1` (with `/api` retained as a local
compatibility alias); the health endpoint is `/api/v1/health` and the local
metrics endpoints are provided by Spring Boot Actuator. Interactive
API documentation is available at `/swagger-ui/index.html` and the OpenAPI
document at `/v3/api-docs`.
