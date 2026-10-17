import { test, expect } from '@playwright/test';

const pause = ms => new Promise(resolve => setTimeout(resolve, ms));

async function capture(page, name) {
  await page.screenshot({ path: `../../../artifacts/demo_presentation/video-output/${name}.png`, fullPage: true });
}

async function openTaskForCase(page, caseId) {
  const row = page.getByRole('row').filter({ hasText: caseId });
  await expect(row).toBeVisible();
  await row.getByRole('button', { name: 'Open →' }).click();
  return page.getByRole('dialog');
}

test('records the W6W Tier 1 workflow proof of concept', async ({ page }) => {
  test.setTimeout(180_000);

  await test.step('Page 1 - problem and boundary', async () => {
    await page.goto('/');
    await expect(page).toHaveTitle('W6W Workflow Workspace');
    await capture(page, '01-overview');
    await pause(2500);
  });

  await test.step('Page 2 - explain the workflow catalog', async () => {
    await expect(page.getByText('Leave Request', { exact: true }).first()).toBeVisible();
    await page.getByRole('button', { name: 'Help' }).click();
    const help = page.getByRole('dialog');
    await expect(help).toContainText('Create and maintain a workflow');
    await capture(page, '02-boundary-and-authoring');
    await pause(2500);
    await help.getByRole('button', { name: 'Close help' }).click();
  });

  await test.step('Page 3 - show the Git-first workflow entry point', async () => {
    await page.getByRole('button', { name: 'Start Leave Request' }).click();
    const start = page.getByRole('dialog');
    await expect(start).toContainText('Choose a workflow');
    await capture(page, '03-workflow-definition-entry');
    await pause(2500);
    await start.getByRole('button', { name: 'Cancel' }).click();
  });

  let caseId;

  await test.step('Page 4 - start the employee request', async () => {
    await page.getByRole('button', { name: 'Start Leave Request' }).click();
    const start = page.getByRole('dialog');
    await start.getByRole('textbox', { name: 'Employee name *' }).fill('Avery Chen');
    await start.getByRole('textbox', { name: 'Employee email *' }).fill('avery.chen@example.test');
    await start.getByLabel('First day *').fill('2026-11-02');
    await start.getByLabel('Last day *').fill('2026-11-06');
    await start.getByLabel('Leave type *').selectOption('Annual leave');
    await start.getByRole('textbox', { name: 'Reason *' }).fill('Family travel');
    await capture(page, '04-employee-intake-form');
    await pause(3000);
    await start.getByRole('button', { name: 'Start workflow' }).click();
    await expect(page.getByRole('status')).toContainText('Workflow started');
    const details = page.getByRole('dialog');
    caseId = (await details.getByRole('heading', { level: 2 }).innerText()).trim();
    await expect(caseId).toMatch(/^CASE-/);
    await details.getByRole('button', { name: 'Close' }).click();
  });

  await test.step('Page 5 - employee worklist task', async () => {
    await page.getByLabel('Local demo identity').selectOption({ label: 'Employee · employee' });
    await page.getByRole('button', { name: 'My work' }).click();
    const task = await openTaskForCase(page, caseId);
    await expect(task).toContainText('Enter Leave Details');
    await capture(page, '05-employee-worklist');
    await pause(2500);
    await task.getByRole('button', { name: 'Complete task' }).click();
    await expect(page.getByRole('status')).toContainText('Task completed');
  });

  await test.step('Page 6 - manager approval', async () => {
    await page.getByLabel('Local demo identity').selectOption({ label: 'Manager · manager' });
    await page.getByRole('button', { name: 'My work' }).click();
    const task = await openTaskForCase(page, caseId);
    await expect(task).toContainText('Review Leave Request');
    await capture(page, '06-manager-approval');
    await pause(3000);
    await task.getByRole('button', { name: 'approve', exact: true }).click();
    await task.getByRole('button', { name: 'Complete task' }).click();
    await expect(page.getByRole('status')).toContainText('Task completed');
  });

  await test.step('Page 7 - case resolution and timeline', async () => {
    await page.getByRole('button', { name: 'Cases' }).click();
    const row = page.getByRole('row').filter({ hasText: caseId });
    await expect(row).toContainText('COMPLETED');
    await expect(row).toContainText('Approved');
    await capture(page, '07-completed-case');
    await pause(2500);
    await row.getByRole('button', { name: 'Details →' }).click();
    const details = page.getByRole('dialog');
    await expect(details).toContainText('Approved');
    await capture(page, '08-case-timeline');
    await pause(3000);
    await details.getByRole('button', { name: 'Close' }).click();
  });

  await test.step('Page 8 - operations and audit boundary', async () => {
    await page.getByRole('button', { name: 'Operations' }).click();
    await expect(page.getByText('Workflow operations')).toBeVisible();
    await capture(page, '09-operations-and-audit');
    await pause(2500);
  });

  await test.step('Page 9 - migration and governance handoff', async () => {
    await page.getByRole('button', { name: 'Help' }).click();
    const help = page.getByRole('dialog');
    await expect(help).toContainText('deploy-local');
    await capture(page, '10-migration-governance');
    await pause(2500);
    await help.getByRole('button', { name: 'Close help' }).click();
  });

  await test.step('Page 10 - closing proof point', async () => {
    await page.getByRole('button', { name: 'Overview' }).click();
    await expect(page.getByText('Leave Request', { exact: true }).first()).toBeVisible();
    await capture(page, '11-closing-proof-point');
    await pause(3500);
  });
});
