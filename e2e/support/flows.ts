import { randomBytes } from 'node:crypto';
import { fileURLToPath } from 'node:url';
import { expect, type Page } from '@playwright/test';
import { API_URL, PANEL_URL, WIDGET_URL } from './env';

export interface Account {
  email: string;
  password: string;
  displayName: string;
  workspaceName: string;
}

/** A new account for one flow: test values only, never reused across runs. */
export function newAccount(): Account {
  const id = randomBytes(5).toString('hex');
  return {
    email: `e2e-${id}@example.test`,
    password: `e2e ${randomBytes(12).toString('base64url')}`,
    displayName: `Operador ${id}`,
    workspaceName: `Café E2E ${id}`,
  };
}

/** Signs up through the panel, which opens on the inbox. */
export async function signUp(page: Page, account: Account): Promise<void> {
  await page.goto(`${PANEL_URL}/register`);
  await page.getByLabel('Nombre de tu empresa').fill(account.workspaceName);
  await page.getByLabel('Tu nombre').fill(account.displayName);
  await page.getByLabel('Email').fill(account.email);
  await page.getByLabel('Contraseña').fill(account.password);
  await page.getByRole('checkbox', { name: /Acepto los términos/ }).check();
  await page.getByRole('button', { name: 'Crear cuenta' }).click();
  await expect(page.getByRole('heading', { level: 1, name: 'Conversaciones' })).toBeVisible();
}

/** Uploads a document from fixtures/ and waits until the assistant can use it. Its title is the file's name. */
export async function addKnowledge(page: Page, fileName: string): Promise<void> {
  await page.getByRole('navigation', { name: 'Secciones' }).getByRole('link', { name: 'Base de conocimiento' }).click();
  await page.getByLabel('Archivos para la base de conocimiento')
    .setInputFiles(fileURLToPath(new URL(`../fixtures/${fileName}`, import.meta.url)));
  const row = page.getByRole('row').filter({ hasText: fileName });
  await expect(row.getByText('Listo')).toBeVisible({ timeout: 60_000 });
}

/** Allows the widget's test page and returns the workspace's widget key. */
export async function allowWidgetPage(page: Page): Promise<string> {
  await page.getByRole('navigation', { name: 'Secciones' }).getByRole('link', { name: 'Widget' }).click();
  await page.getByLabel(/Sitios donde se puede instalar/).fill(new URL(WIDGET_URL).origin);
  await page.getByRole('button', { name: 'Guardar' }).click();
  await expect(page.getByRole('status').filter({ hasText: /guard/i })).toBeVisible();
  const key = await page.locator('code.key').textContent();
  expect(key).toMatch(/^wk_/);
  return key!;
}

/** Opens the widget's test page, which plays a customer's site, and the chat on it. */
export async function openWidget(page: Page, widgetKey: string): Promise<void> {
  await page.goto(`${WIDGET_URL}/?key=${encodeURIComponent(widgetKey)}&api=${encodeURIComponent(API_URL)}`);
  await page.getByRole('button', { name: 'Abrir chat de ayuda' }).click();
  await expect(page.getByRole('dialog')).toBeVisible();
}

export async function ask(page: Page, question: string): Promise<void> {
  await page.getByLabel('Escribe tu pregunta').fill(question);
  await page.getByRole('button', { name: 'Enviar' }).click();
}
