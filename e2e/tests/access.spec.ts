import { expect, test } from '@playwright/test';
import { PANEL_URL, WIDGET_URL } from '../support/env';
import { newAccount, openWidget, signUp } from '../support/flows';

test('an operator signs out and back in; a wrong password is refused', async ({ page }) => {
  const account = newAccount();
  await signUp(page, account);
  await page.getByRole('button', { name: 'Cerrar sesión' }).click();
  await expect(page.getByRole('heading', { name: 'Iniciar sesión' })).toBeVisible();

  // A protected page without a session goes to sign in
  await page.goto(`${PANEL_URL}/knowledge`);
  await page.getByLabel('Email').fill(account.email);
  await page.getByLabel('Contraseña').fill(`${account.password}x`);
  await page.getByRole('button', { name: 'Entrar' }).click();
  await expect(page.getByRole('alert')).toHaveText('Email o contraseña incorrectos.');

  await page.getByLabel('Contraseña').fill(account.password);
  await page.getByRole('button', { name: 'Entrar' }).click();
  await expect(page.getByRole('heading', { level: 1, name: 'Base de conocimiento' })).toBeVisible();
});

test('the widget does not open a conversation on a site the workspace has not allowed', async ({ page, browser }) => {
  await signUp(page, newAccount());
  // The workspace allows no site yet
  const widgetKey = (await page.goto(`${PANEL_URL}/widget`), await page.locator('code.key').textContent())!;

  const site = await (await browser.newContext()).newPage();
  await openWidget(site, widgetKey);
  await expect(site.getByRole('dialog')).toContainText('El chat no está disponible en este momento.');
  await expect(site.getByLabel('Escribe tu pregunta')).toBeDisabled();
  expect(new URL(site.url()).origin).toBe(new URL(WIDGET_URL).origin);
});
