import { test, expect } from '@playwright/test';

test('custom workflow roles are available as local demo identities', async ({ page }) => {
  await page.route('**/api/workflows', async route => {
    const response = await route.fetch();
    const workflows = await response.json();
    workflows[0].stages[0].steps.push({
      id: 'custom-review',
      name: 'Custom Review',
      type: 'userTask',
      assignee: { role: 'custom-reviewers' },
    });
    await route.fulfill({ response, body: JSON.stringify(workflows) });
  });

  await page.goto('/');

  await expect(page.locator('#identity option[value="demo.custom.reviewers|custom-reviewers"]'))
    .toContainText('Custom Reviewers');
});
