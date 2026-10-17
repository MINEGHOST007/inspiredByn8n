import { test, expect } from '@playwright/test';

test('users can cancel a blank start form and complete an approval workflow', async ({ page }) => {
  await page.goto('/');

  await page.getByRole('button', { name: '＋ Start a workflow' }).click();
  const startDialog = page.getByRole('dialog');
  await expect(startDialog).toBeVisible();
  await startDialog.getByRole('button', { name: 'Cancel' }).click();
  await expect(startDialog).toBeHidden();
  await expect(page.getByRole('status')).not.toContainText('Associated Complaint ID');

  const suffix = `${Date.now()}-${Math.floor(Math.random() * 1_000_000)}`;
  await page.getByRole('button', { name: 'Start Refund Processing' }).click();
  const newCaseDialog = page.getByRole('dialog');
  await newCaseDialog.getByRole('textbox', { name: 'Associated Complaint ID *' }).fill(`CMP-E2E-${suffix}`);
  await newCaseDialog.getByRole('textbox', { name: 'Guest Name *' }).fill('Acceptance Test Guest');
  await newCaseDialog.getByRole('textbox', { name: 'Guest Contact Email' }).fill('acceptance@example.test');
  await newCaseDialog.getByRole('spinbutton', { name: 'Requested Refund Amount ($) *' }).fill('42.50');
  await newCaseDialog.getByRole('textbox', { name: 'Reason for Refund *' }).fill('Browser acceptance workflow');
  await newCaseDialog.getByLabel('Refund Disbursement Method *').selectOption('OriginalPaymentMethod');

  // Use the generated case id to follow this instance through each role queue.
  await newCaseDialog.getByRole('button', { name: 'Start workflow' }).click();
  await expect(page.getByRole('status')).toContainText('Workflow started');
  const caseDetails = page.getByRole('dialog');
  const caseId = (await caseDetails.getByRole('heading', { level: 2 }).innerText()).trim();
  await expect(caseId).toMatch(/^CASE-/);
  await caseDetails.getByRole('button', { name: 'Close' }).click();
  await page.getByRole('button', { name: 'My work' }).click();

  const intakeTask = page.getByRole('row').filter({ hasText: caseId });
  await expect(intakeTask).toBeVisible();
  await intakeTask.getByRole('button', { name: 'Open →' }).click();
  await page.getByRole('button', { name: 'Complete task' }).click();
  await expect(page.getByRole('status')).toContainText('Task completed');

  const eligibilityTask = page.getByRole('row').filter({ hasText: caseId });
  await expect(eligibilityTask).toBeVisible();
  await eligibilityTask.getByRole('button', { name: 'Open →' }).click();
  await page.getByRole('spinbutton', { name: 'Approved Refund Amount ($)' }).fill('42.50');
  await page.getByLabel('Additional Goodwill').selectOption('None');
  await page.getByRole('textbox', { name: 'Agent Review Notes' }).fill('Policy checked by browser acceptance test');
  await page.getByRole('button', { name: 'eligible', exact: true }).click();
  await page.getByRole('button', { name: 'Complete task' }).click();
  await expect(page.getByRole('status')).toContainText('Task completed');

  await page.getByLabel('Local demo identity').selectOption('Manager Jones · Property manager');
  const approvalTask = page.getByRole('row').filter({ hasText: caseId });
  await expect(approvalTask).toBeVisible();
  await approvalTask.getByRole('button', { name: 'Open →' }).click();
  await page.getByRole('button', { name: 'approve', exact: true }).click();
  await page.getByRole('button', { name: 'Complete task' }).click();
  await expect(page.getByRole('status')).toContainText('Task completed');

  await page.getByRole('button', { name: 'Cases' }).click();
  const completedCase = page.getByRole('row').filter({ hasText: caseId });
  await expect(completedCase).toContainText('COMPLETED');
  await expect(completedCase).toContainText('Approved');
  await completedCase.getByRole('button', { name: 'Details →' }).click();
  const details = page.getByRole('dialog');
  await expect(details).toContainText('Resolution: Approved');
  await expect(details).toContainText('Request Refund Approval');
});
