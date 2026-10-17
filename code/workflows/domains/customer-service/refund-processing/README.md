# Workflow: Refund Processing

New workflow authors can follow the repository's [workflow quick start](../../../../../artifacts/playbook/quick-start.md) and fill in the [intake worksheet](../../../../../artifacts/playbook/workflow-intake-template.md) before copying this example.

- **Key:** `refund-processing`
- **Tier:** 1 (Eligible - single-level approval with linear stages and alternate rejection path)
- **Originating Blueprint:** `blueprint:BP-1766322:RefundProcessing`
- **Owner:** Customer Service Operations (`customer-service-ops`)
- **Version:** `1.0.0`

## Overview
This workflow automates the validation, approval, and disbursement of monetary refunds and goodwill compensation for customer service issues originating from complaint resolutions.

## Stages
1. **Request Intake**: Gathers customer issue details, validates input data, generates a tracking refund ID, and acknowledges receipt.
2. **Eligibility Check**: Retrieves historical reservation and payment records, verifies refund policy rules, and presents eligibility details for agent review.
3. **Refund Approval**: Routes to the designated manager for approval with a 4-hour target SLA and 24-hour escalation deadline.
4. **Disbursement**: Executes automated payment transaction via the registered payment gateway handler and records journal entries in ledger.
5. **Closure**: Generates confirmation receipt, notifies the guest via email, and marks case as `Approved`.
6. **Refund Denied (Alternate)**: Records the denial reason, drafts and transmits formal denial correspondence, and concludes as `Denied`.

## Registered Actions Required
- `refund.validateRequest`
- `refund.generateId`
- `refund.retrieveRecords`
- `refund.checkPolicyAdherence`
- `refund.initiateTransaction` (idempotent payment execution)
- `refund.updateLedger`
- `refund.generateReceipt`
- `refund.recordDenial`
- `refund.generateDenialLetter`
- `notify.email`
