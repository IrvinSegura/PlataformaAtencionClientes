import { expect, test } from '@playwright/test';
import { allowWidgetPage, ask, newAccount, openWidget, signUp } from '../support/flows';

test('a customer who asks for a person is answered live by the team, until the team closes the conversation', async ({
  browser,
}) => {
  const account = newAccount();
  const panel = await (await browser.newContext()).newPage();
  await signUp(panel, account);
  const widgetKey = await allowWidgetPage(panel);

  const site = await (await browser.newContext()).newPage();
  await openWidget(site, widgetKey);
  const chat = site.getByRole('dialog');
  await chat.getByRole('button', { name: 'Hablar con una persona' }).click();
  await expect(chat.getByRole('status')).toContainText('Te comunicamos con una persona del equipo');
  await ask(site, 'Quiero cambiar la dirección de mi pedido');

  // The panel shows who waits, live
  const nav = panel.getByRole('navigation', { name: 'Secciones' });
  await expect(nav.getByRole('link', { name: /Conversaciones.*1 en espera/ })).toBeVisible();
  await nav.getByRole('link', { name: /Conversaciones/ }).click();
  await panel.getByRole('tab', { name: 'En espera' }).click();
  await panel.locator('.list-item').first().click();
  await expect(panel.getByRole('list', { name: 'Mensajes de la conversación' }))
    .toContainText('Quiero cambiar la dirección de mi pedido');

  await panel.getByRole('button', { name: 'Tomar conversación' }).click();
  await expect(chat.getByRole('status')).toContainText(`Te atiende ${account.displayName}`);
  await panel.getByLabel('Respuesta al visitante').fill('Claro, ¿a qué dirección lo enviamos?');
  await panel.getByRole('button', { name: 'Enviar' }).click();

  // What the operator writes reaches the customer's chat without a reload
  const reply = chat.locator('.msg.agent').last();
  await expect(reply).toContainText('Claro, ¿a qué dirección lo enviamos?');
  await expect(reply).toContainText(account.displayName);

  panel.once('dialog', (dialog) => void dialog.accept());
  await panel.getByRole('button', { name: 'Cerrar conversación' }).click();
  await expect(chat.getByText('La conversación terminó. Escribe de nuevo para empezar otra.')).toBeVisible();
});
