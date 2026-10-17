import { test, expect } from '@playwright/test';
import fs from 'node:fs';
import path from 'node:path';

const chapterTargets = {
  problem: 55,
  boundary: 60,
  architecture: 70,
  definition: 65,
  runtime: 60,
  intake: 70,
  approval: 95,
  history: 65,
  migration: 90,
  governance: 85,
  closing: 40,
};

const measuredDurations = (() => {
  try {
    return JSON.parse(process.env.W6W_AUDIO_DURATIONS ?? '{}');
  } catch {
    throw new Error('W6W_AUDIO_DURATIONS must be valid JSON, for example {"problem":58}');
  }
})();

const chapterDuration = name => Number(measuredDurations[name] ?? chapterTargets[name]);
const pause = ms => new Promise(resolve => setTimeout(resolve, ms));
const outputRoot = path.resolve('../../../artifacts/demo_presentation/video-output');

async function capture(page, name) {
  fs.mkdirSync(outputRoot, { recursive: true });
  await page.screenshot({ path: path.join(outputRoot, `${name}.png`), fullPage: true });
}

async function chapter(page, name, ready, actions) {
  const startedAt = Date.now();
  await ready();
  await actions();
  const remaining = chapterDuration(name) * 1000 - (Date.now() - startedAt);
  if (remaining > 0) await pause(remaining);
}

async function openTaskForCase(page, caseId) {
  const row = page.getByRole('row').filter({ hasText: caseId });
  await expect(row).toBeVisible();
  await row.getByRole('button', { name: 'Open →' }).click();
  return page.getByRole('dialog');
}

test('records the W6W Tier 1 workflow proof of concept', async ({ page }) => {
  test.setTimeout(900_000);

  await test.step('Chapter 01 - problem and promise', async () => {
    await chapter(page, 'problem', async () => {
      await page.goto('/');
      await expect(page).toHaveTitle('W6W Workflow Workspace');
    }, async () => {
      await capture(page, '01-overview');
    });
  });

  await test.step('Chapter 02 - boundary and responsibility', async () => {
    await chapter(page, 'boundary', async () => {
      await expect(page.getByText('Leave Request', { exact: true }).first()).toBeVisible();
      await page.getByRole('button', { name: 'Help' }).click();
      await expect(page.getByRole('dialog')).toContainText('Create and maintain a workflow');
    }, async () => {
      await capture(page, '02-boundary-and-authoring');
      await page.getByRole('dialog').getByRole('button', { name: 'Close help' }).click();
    });
  });

  await test.step('Chapter 03 - Git-first architecture', async () => {
    await chapter(page, 'architecture', async () => {
      await page.getByRole('button', { name: 'Start Leave Request' }).click();
      await expect(page.getByRole('dialog')).toContainText('Choose a workflow');
    }, async () => {
      await capture(page, '03-workflow-definition-entry');
      await page.getByRole('dialog').getByRole('button', { name: 'Cancel' }).click();
    });
  });

  let caseId;

  await test.step('Chapter 04 - reference workflow', async () => {
    await chapter(page, 'definition', async () => {
      await page.getByRole('button', { name: 'Start Leave Request' }).click();
      await expect(page.getByRole('dialog')).toContainText('Choose a workflow');
    }, async () => {
      await capture(page, '04-workflow-definition');
      await page.getByRole('dialog').getByRole('button', { name: 'Cancel' }).click();
    });
  });

  await test.step('Chapter 05 - running application', async () => {
    await chapter(page, 'runtime', async () => {
      await page.getByRole('button', { name: 'Start Leave Request' }).click();
      await expect(page.getByRole('dialog')).toContainText('Choose a workflow');
    }, async () => {
      await capture(page, '05-runtime-catalog');
      const start = page.getByRole('dialog');
      await start.getByRole('button', { name: 'Cancel' }).click();
    });
  });

  await test.step('Chapter 06 - employee intake', async () => {
    await chapter(page, 'intake', async () => {
      await page.getByRole('button', { name: 'Start Leave Request' }).click();
      const start = page.getByRole('dialog');
      await start.getByRole('textbox', { name: 'Employee name *' }).fill('Avery Chen');
      await start.getByRole('textbox', { name: 'Employee email *' }).fill('avery.chen@example.test');
      await start.getByLabel('First day *').fill('2026-11-02');
      await start.getByLabel('Last day *').fill('2026-11-06');
      await start.getByLabel('Leave type *').selectOption('Annual leave');
      await start.getByRole('textbox', { name: 'Reason *' }).fill('Family travel');
    }, async () => {
      await capture(page, '06-employee-intake-form');
      const start = page.getByRole('dialog');
      await start.getByRole('button', { name: 'Start workflow' }).click();
      await expect(page.getByRole('status')).toContainText('Workflow started');
      const details = page.getByRole('dialog');
      caseId = (await details.getByRole('heading', { level: 2 }).innerText()).trim();
      await expect(caseId).toMatch(/^CASE-/);
      await details.getByRole('button', { name: 'Close' }).click();
    });
  });

  await test.step('Chapter 07 - worklist and approval', async () => {
    await chapter(page, 'approval', async () => {
      await page.getByLabel('Local demo identity').selectOption({ label: 'Employee · employee' });
      await page.getByRole('button', { name: 'My work' }).click();
      const task = await openTaskForCase(page, caseId);
      await expect(task).toContainText('Enter Leave Details');
      await task.getByRole('button', { name: 'Complete task' }).click();
      await expect(page.getByRole('status')).toContainText('Task completed');
      await page.getByLabel('Local demo identity').selectOption({ label: 'Manager · manager' });
      await page.getByRole('button', { name: 'My work' }).click();
      const approval = await openTaskForCase(page, caseId);
      await expect(approval).toContainText('Review Leave Request');
    }, async () => {
      await capture(page, '07-manager-approval');
      const task = page.getByRole('dialog');
      await task.getByRole('button', { name: 'approve', exact: true }).click();
      await task.getByRole('button', { name: 'Complete task' }).click();
      await expect(page.getByRole('status')).toContainText('Task completed');
    });
  });

  await test.step('Chapter 08 - case history and auditability', async () => {
    await chapter(page, 'history', async () => {
      await page.getByRole('button', { name: 'Cases' }).click();
      const row = page.getByRole('row').filter({ hasText: caseId });
      await expect(row).toContainText('COMPLETED');
      await expect(row).toContainText('Approved');
      await row.getByRole('button', { name: 'Details →' }).click();
      await expect(page.getByRole('dialog')).toContainText('Approved');
    }, async () => {
      await capture(page, '08-case-timeline');
      await page.getByRole('dialog').getByRole('button', { name: 'Close' }).click();
    });
  });

  await test.step('Chapter 09 - migration skeleton', async () => {
    await chapter(page, 'migration', async () => {
      await page.getByRole('button', { name: 'Operations' }).click();
      await expect(page.getByText('Workflow operations')).toBeVisible();
    }, async () => {
      await capture(page, '09-operations-and-audit');
    });
  });

  await test.step('Chapter 10 - governance and next gates', async () => {
    await chapter(page, 'governance', async () => {
      await page.getByRole('button', { name: 'Help' }).click();
      await expect(page.getByRole('dialog')).toContainText('deploy-local');
    }, async () => {
      await capture(page, '10-migration-governance');
      await page.getByRole('dialog').getByRole('button', { name: 'Close help' }).click();
    });
  });

  await test.step('Chapter 11 - closing proof point', async () => {
    await chapter(page, 'closing', async () => {
      await page.getByRole('button', { name: 'Overview' }).click();
      await expect(page.getByText('Leave Request', { exact: true }).first()).toBeVisible();
    }, async () => {
      await capture(page, '11-closing-proof-point');
    });
  });
});
