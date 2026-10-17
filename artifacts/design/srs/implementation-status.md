# W6W Implementation Status

Generated from [traceability.csv](traceability.csv). Total requirements: **109**.

| Status | Count |
|---|---:|
| Implemented | 8 |
| Partial | 54 |
| Not implemented | 47 |

| Priority | Total | Implemented | Partial | Not implemented |
|---|---:|---:|---:|---:|
| M | 65 | 8 | 50 | 7 |
| S | 23 | 0 | 2 | 21 |
| C | 7 | 0 | 0 | 7 |
| NFR | 14 | 0 | 2 | 12 |

## Remaining Work

| Requirement | Priority | Status | Evidence / owning module |
|---|---|---|---|
| WF-01 | M | Partial | w6w-model; code/schema/cwm/v1 (CwmParser; workflow.schema.json) |
| WF-02 | M | Partial | w6w-model; code/workflows (WorkflowApiControllerTest) |
| WF-03 | M | Partial | w6w-compiler (CwmToBpmnCompilerTest) |
| WF-05 | S | Partial | w6w-cli (W6wCliTest) |
| WF-06 | M | Partial | w6w-model; w6w-compiler (CwmLinterTest) |
| WF-07 | M | Partial | w6w-test (ScenarioRunnerTest) |
| WF-08 | M | Partial | code/workflows; .gitignore (repository inspection) |
| EX-01 | M | Partial | w6w-engine-port; w6w-engine-flowable (FlowableEngineIntegrationTest) |
| EX-02 | M | Partial | w6w-compiler; w6w-engine-flowable (CwmToBpmnCompilerTest.waitEventCompilesToNamedSignalCatchEvent; FlowableEngineIntegrationTest.inboundSignalResumesOnlyTheInstanceWithTheMatchingBusinessKey; FlowableEngineIntegrationTest.timerStepWaitsThenAllowsTheNextHumanTask) |
| EX-03 | M | Partial | w6w-compiler (CwmToBpmnCompilerTest) |
| EX-04 | M | Partial | w6w-compiler (CwmToBpmnCompilerTest) |
| EX-07 | S | Not implemented | w6w-engine-port / w6w-engine-flowable () |
| EX-08 | S | Not implemented | w6w-engine-port / w6w-engine-flowable () |
| EX-09 | C | Not implemented | w6w-engine-port / w6w-engine-flowable () |
| HT-01 | M | Partial | w6w-compiler; w6w-app (WorkflowApiControllerTest) |
| HT-02 | M | Partial | w6w-app (WorkflowApiControllerTest) |
| HT-03 | S | Not implemented | w6w-runtime-starter / w6w-engine-port () |
| HT-04 | M | Partial | w6w-compiler; w6w-app (WorkflowApiControllerTest.deadlineBreachMovesTaskToEscalationRoleAndAuditsTheChange) |
| HT-05 | M | Partial | w6w-runtime-starter; w6w-engine-flowable; w6w-app (WorkflowApiControllerTest.deadlineBreachMovesTaskToEscalationRoleAndAuditsTheChange) |
| HT-06 | S | Not implemented | w6w-runtime-starter / w6w-engine-port () |
| HT-07 | M | Partial | w6w-app (WorkflowApiControllerTest) |
| HT-08 | C | Not implemented | w6w-runtime-starter / w6w-engine-port () |
| DF-01 | M | Partial | w6w-app; w6w-runtime-starter; w6w-engine-flowable (WorkflowApiControllerTest; WorkflowDataSchemaRegistryTest; FlowableEngineIntegrationTest) |
| DF-02 | M | Partial | w6w-app; code/schema/cwm/v1/form.schema.json (manual Playwright: layout rows, labels, help text, conditional visibility, hidden-value exclusion, and hidden required fields; WorkflowApiControllerTest) |
| DF-03 | S | Not implemented | w6w-model / workflow forms () |
| DF-04 | S | Not implemented | w6w-model / workflow forms () |
| DF-05 | S | Not implemented | w6w-model / workflow forms () |
| DC-03 | S | Not implemented | w6w-compiler () |
| IN-01 | M | Partial | w6w-runtime-starter; w6w-app (WorkflowApiControllerTest) |
| IN-02 | M | Partial | w6w-runtime-starter (StandardWorkflowActionsTest; FlowableEngineIntegrationTest.serviceTaskReceivesVersionedWorkflowActionConfiguration) |
| IN-05 | M | Partial | w6w-runtime-starter (CwmLinterTest.sensitiveActionValuesMustReferenceHostSecrets; StandardWorkflowActionsTest.httpRequestUsesConfiguredMethodSecretAndWritesResponseVariables) |
| IN-06 | S | Not implemented | w6w-runtime-starter () |
| API-01 | M | Partial | w6w-app; w6w-engine-port (WorkflowApiControllerTest) |
| API-02 | M | Partial | w6w-app (WorkflowApiControllerTest) |
| API-03 | M | Partial | w6w-app (WorkflowApiControllerTest) |
| API-04 | S | Not implemented | w6w-api (planned) () |
| API-05 | S | Not implemented | w6w-api (planned) () |
| UI-02 | M | Partial | w6w-app (WorkflowApiControllerTest; Playwright workflow-lifecycle.spec.js; workflow-identity.spec.js) |
| UI-03 | M | Partial | w6w-app (WorkflowApiControllerTest; Playwright workflow-lifecycle.spec.js) |
| UI-04 | M | Partial | w6w-app (WorkflowApiControllerTest.servesWorkspaceRunsApprovalWorkflowAndExposesDeadLetterQueueApi; FlowableEngineIntegrationTest.failedServiceTaskRetriesThenAppearsInDeadLetterQueueAndCanBeReplayed) |
| UI-05 | S | Not implemented | w6w-ui-kit (planned) () |
| UI-06 | S | Not implemented | w6w-ui-kit (planned) () |
| UI-07 | C | Not implemented | w6w-ui-kit (planned) () |
| VD-01 | M | Not implemented | CI/CD (partial) () |
| VD-02 | M | Partial | w6w-app; w6w-engine-flowable (local deployment version check) |
| VD-03 | C | Not implemented | CI/CD (partial) () |
| VD-04 | M | Not implemented | CI/CD (partial) () |
| VD-05 | S | Not implemented | CI/CD (partial) () |
| AU-01 | M | Partial | w6w-app; w6w-runtime-starter (WorkflowApiControllerTest) |
| AU-02 | M | Partial | w6w-app (WorkflowApiControllerTest) |
| AU-03 | S | Not implemented | w6w-runtime-starter (partial) () |
| AU-04 | C | Not implemented | w6w-runtime-starter (partial) () |
| SE-01 | M | Not implemented | host integration (planned) () |
| SE-02 | M | Not implemented | host integration (planned) () |
| SE-03 | M | Not implemented | host integration (planned) () |
| SE-04 | M | Not implemented | host integration (planned) () |
| SE-05 | M | Not implemented | host integration (planned) () |
| SE-06 | S | Not implemented | host integration (planned) () |
| OP-01 | M | Partial | w6w-app; Spring Boot Actuator (manual local check) |
| OP-02 | M | Partial | ops (WorkflowTraceFilterTest; FlowableEngineIntegrationTest.serviceTaskReceivesVersionedWorkflowActionConfiguration) |
| OP-03 | M | Partial | w6w-app (WorkflowApiControllerTest) |
| OP-04 | M | Partial | artifacts/playbook/local-operations.md (documented) |
| OP-05 | S | Not implemented | ops (planned) () |
| DX-01 | M | Partial | w6w-cli (WorkflowScaffolderTest) |
| DX-02 | M | Partial | w6w-cli; w6w-app (W6wCliTest; WorkflowApiControllerTest) |
| DX-03 | M | Partial | run-local.ps1; w6w-test (run-local.ps1) |
| DX-04 | S | Not implemented | w6w-cli (planned) () |
| DX-05 | S | Partial | artifacts/playbook/workflow-author-quick-start.md (documented) |
| GV-01 | M | Partial | w6w-governance (EligibilityCheckerTest) |
| GV-02 | M | Partial | w6w-governance; w6w-migrate (EligibilityCheckerTest; BlueprintMigrationServiceTest) |
| GV-03 | M | Partial | w6w-compiler; w6w-migrate (CwmLinterTest; CwmToBpmnCompilerTest) |
| GV-04 | S | Not implemented | w6w-governance () |
| GV-05 | C | Not implemented | w6w-governance () |
| MG-01 | M | Partial | w6w-migrate; w6w-cli (PdfBlueprintExtractorTest; BlueprintMigrationServiceTest; W6wCliTest) |
| MG-02 | M | Partial | w6w-migrate (PdfBlueprintExtractorTest; BlueprintMappingRulesTest) |
| MG-03 | M | Partial | w6w-migrate (PdfBlueprintExtractorTest; BlueprintMigrationServiceTest; W6wCliTest) |
| MG-04 | M | Partial | w6w-migrate (BlueprintMappingRulesTest) |
| MG-05 | M | Partial | w6w-migrate; w6w-governance (EligibilityCheckerTest; BlueprintMigrationServiceTest; W6wCliTest) |
| MG-06 | M | Partial | w6w-migrate; w6w-cli (BlueprintMigrationServiceTest; W6wCliTest) |
| MG-07 | M | Partial | w6w-migrate; w6w-cli (BlueprintMigrationServiceTest; W6wCliTest) |
| MG-08 | S | Not implemented | w6w-migrate () |
| MG-09 | M | Partial | w6w-migrate; w6w-cli (BlueprintMigrationServiceTest; W6wCliTest) |
| MG-10 | M | Partial | w6w-test; w6w-migrate (BlueprintMigrationServiceTest; ScenarioRunnerTest) |
| MG-11 | S | Not implemented | w6w-migrate () |
| MG-12 | M | Partial | artifacts/design/architecture/agent-modes.md (documented) |
| MG-13 | C | Not implemented | w6w-migrate () |
| MG-14 | M | Partial | w6w-migrate (BlueprintMigrationServiceTest) |
| NF-01 | NFR | Not implemented | platform-wide () |
| NF-02 | NFR | Not implemented | platform-wide () |
| NF-03 | NFR | Not implemented | platform-wide () |
| NF-04 | NFR | Not implemented | platform-wide () |
| NF-05 | NFR | Not implemented | platform-wide () |
| NF-06 | NFR | Not implemented | platform-wide () |
| NF-07 | NFR | Partial | w6w-compiler; w6w-migrate (Maven unit/fixture suite) |
| NF-08 | NFR | Not implemented | platform-wide () |
| NF-09 | NFR | Not implemented | .github/workflows/; code/platform/pom.xml () |
| NF-10 | NFR | Not implemented | platform-wide () |
| NF-11 | NFR | Not implemented | platform-wide () |
| NF-12 | NFR | Not implemented | platform-wide () |
| NF-13 | NFR | Not implemented | platform-wide () |
| NF-14 | NFR | Partial | code/schema/cwm/v1/workflow.schema.json () |

> Partial means a local implementation exists but the SRS acceptance criteria are not fully met. Not implemented means the capability is still open; local-demo behavior must not be treated as production support.
