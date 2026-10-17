# W6W browser acceptance tests

These browser tests exercise the local workspace through the same pages used by
workflow participants. They check that a blank start form can be cancelled,
then run a refund request through intake, eligibility, manager approval, the
case timeline, and the terminal resolution.

## Run locally

```powershell
cd code/tests/acceptance
npm install
npx playwright install chromium
npm test
```

The test runner starts the local app with `run-app.ps1` when nothing is already
listening at `http://127.0.0.1:8080`. Set `W6W_BASE_URL` to use another local
instance. Browser traces for failures are written under `test-results/` and are
not source files.

The app uses its local H2 database, so each test creates a uniquely identified
case. It does not call hosted services or use remote scripts, fonts, or assets.
