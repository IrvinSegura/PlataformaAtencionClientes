import { expect, test } from '@playwright/test';
import { addKnowledge, allowWidgetPage, ask, newAccount, openWidget, signUp } from '../support/flows';

test('a document the business uploads answers its customers on its site, with the source cited', async ({ browser }) => {
  const panel = await (await browser.newContext()).newPage();
  await signUp(panel, newAccount());
  await addKnowledge(panel, 'shipping-payments-and-returns.md');
  const widgetKey = await allowWidgetPage(panel);

  const site = await (await browser.newContext()).newPage();
  await openWidget(site, widgetKey);
  const chat = site.getByRole('dialog');
  await ask(site, '¿Cuánto cuesta el envío?');

  const answer = chat.locator('.msg.assistant').last();
  await expect(answer).toContainText('99 pesos');
  await expect(answer.getByText('Fuentes')).toBeVisible();
  await expect(answer.getByText('shipping-payments-and-returns')).toBeVisible();

  await answer.getByRole('button', { name: 'Me ayudó', exact: true }).click();
  await expect(answer.getByRole('button', { name: 'Me ayudó', exact: true })).toHaveAttribute('aria-pressed', 'true');
  await chat.getByRole('group', { name: '¿Cómo te atendimos?' }).getByRole('button', { name: '5 de 5' }).click();
  await expect(chat.getByText('¡Gracias por tu opinión!')).toBeVisible();

  // The team sees the conversation, the answer and what the customer thought of it
  await panel.getByRole('navigation', { name: 'Secciones' }).getByRole('link', { name: 'Conversaciones' }).click();
  await panel.locator('.list-item').first().click();
  const transcript = panel.getByRole('list', { name: 'Mensajes de la conversación' });
  await expect(transcript).toContainText('¿Cuánto cuesta el envío?');
  await expect(transcript.getByText('Le ayudó')).toBeVisible();
  await expect(panel.getByLabel('5 de 5')).toBeVisible();
});

test('a question the documents do not cover gets an honest answer and reaches the unanswered list', async ({ browser }) => {
  const panel = await (await browser.newContext()).newPage();
  await signUp(panel, newAccount());
  await addKnowledge(panel, 'shipping-payments-and-returns.md');
  const widgetKey = await allowWidgetPage(panel);

  const site = await (await browser.newContext()).newPage();
  await openWidget(site, widgetKey);
  await ask(site, '¿Venden boletos de avión a Cancún?');
  await expect(site.getByRole('dialog').locator('.msg.assistant').last()).toContainText('No tengo información');

  await panel.getByRole('navigation', { name: 'Secciones' }).getByRole('link', { name: 'Preguntas sin respuesta' }).click();
  await expect(panel.getByText('¿Venden boletos de avión a Cancún?')).toBeVisible();
});
