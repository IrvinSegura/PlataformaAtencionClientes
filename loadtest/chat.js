// Load test of the platform's busiest path: visitors open the widget, start a conversation and ask questions that are
// answered over server-sent events, while operators keep their inbox open. Run it with loadtest/docker-compose.yml,
// which starts its own database and backend with the offline model, so it measures the platform (search, storage,
// streaming) and not the language model's latency.
//
//   docker compose -f loadtest/docker-compose.yml up --build --abort-on-container-exit --exit-code-from k6
//
// PEAK_RATE (conversations started per second at the peak) and HOLD (how long the peak lasts) shape the run.
import { check, fail, sleep } from 'k6';
import http from 'k6/http';
import { Rate, Trend } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://backend:8080';
const ORIGIN = 'https://shop.example.com';
const PEAK_RATE = Number(__ENV.PEAK_RATE || 10);
const HOLD = __ENV.HOLD || '2m';

const answerTime = new Trend('answer_time', true);
const firstEventTime = new Trend('answer_first_byte', true);
const answered = new Rate('answered');

export const options = {
  scenarios: {
    visitors: {
      executor: 'ramping-arrival-rate',
      exec: 'visitor',
      startRate: 1,
      timeUnit: '1s',
      preAllocatedVUs: 50,
      maxVUs: 400,
      stages: [
        { target: PEAK_RATE, duration: '1m' },
        { target: PEAK_RATE, duration: HOLD },
        { target: 0, duration: '30s' },
      ],
    },
    operators: {
      executor: 'constant-vus',
      exec: 'operator',
      vus: 10,
      duration: '3m30s',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    'http_req_duration{name:config}': ['p(95)<200'],
    'http_req_duration{name:start}': ['p(95)<300'],
    'http_req_duration{name:inbox}': ['p(95)<500'],
    answer_time: ['p(95)<1500'],
    answered: ['rate>0.99'],
  },
  summaryTrendStats: ['avg', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

const KNOWLEDGE = `# Envíos

El envío nacional tarda de 3 a 5 días hábiles y cuesta 99 pesos. Es gratis en compras mayores a 999 pesos.
Los pedidos se envían por DHL y Estafeta; el número de guía llega por correo el día del envío.

# Devoluciones

Tienes 30 días desde que recibes el pedido para devolverlo. El producto debe estar sin usar y con su empaque.
El reembolso se hace al mismo medio de pago en un máximo de 10 días hábiles.

# Pagos

Aceptamos tarjetas de crédito y débito, PayPal y pago en OXXO. Con tarjeta de crédito hay hasta 6 meses sin intereses
en compras mayores a 3000 pesos.

# Horario

El equipo de atención responde de lunes a viernes de 9 a 18 horas, hora del centro de México.
`;

const QUESTIONS = [
  '¿Cuánto tarda el envío nacional?',
  '¿Cuánto cuesta el envío?',
  '¿Cuántos días tengo para devolver un producto?',
  '¿Cuándo me regresan mi dinero?',
  '¿Aceptan pago en OXXO?',
  '¿Tienen meses sin intereses?',
  '¿Cuál es su horario de atención?',
  '¿Con qué paquetería envían?',
  '¿Venden boletos de avión?',
];

export function setup() {
  const email = `load-${Date.now()}@example.com`;
  const registered = http.post(`${BASE_URL}/api/v1/auth/register`, JSON.stringify({
    email,
    password: 'load-test-password-1',
    displayName: 'Load test',
    workspaceName: 'Load test',
    acceptTerms: true,
  }), { headers: { 'Content-Type': 'application/json' } });
  if (registered.status !== 201) {
    fail(`register answered ${registered.status}: ${registered.body}`);
  }
  const token = registered.json('accessToken');
  const auth = { Authorization: `Bearer ${token}` };

  const settings = http.put(`${BASE_URL}/api/v1/workspace/widget-settings`, JSON.stringify({
    botName: 'Ana',
    welcomeMessage: 'Hola, ¿en qué te ayudo?',
    allowedOrigins: [ORIGIN],
  }), { headers: { ...auth, 'Content-Type': 'application/json' } });
  if (settings.status !== 200) {
    fail(`widget settings answered ${settings.status}: ${settings.body}`);
  }

  const upload = http.post(`${BASE_URL}/api/v1/knowledge/documents`, {
    file: http.file(KNOWLEDGE, 'preguntas-frecuentes.md', 'text/markdown'),
  }, { headers: auth });
  if (upload.status !== 202) {
    fail(`upload answered ${upload.status}: ${upload.body}`);
  }
  const documentId = upload.json('id');
  for (let attempt = 0; attempt < 60; attempt++) {
    const document = http.get(`${BASE_URL}/api/v1/knowledge/documents/${documentId}`, { headers: auth });
    if (document.json('status') === 'READY') {
      return { token, widgetKey: settings.json('widgetKey') };
    }
    sleep(1);
  }
  fail('the document never became READY');
}

// One visitor: loads the widget, starts a conversation and asks two questions, reading each answer before the next
export function visitor(data) {
  const widget = `${BASE_URL}/api/v1/widget/${data.widgetKey}`;
  const headers = { Origin: ORIGIN };

  const config = http.get(`${widget}/config`, { headers, tags: { name: 'config' } });
  check(config, { 'widget config loads': (r) => r.status === 200 });

  const started = http.post(`${widget}/conversations`, null, { headers, tags: { name: 'start' } });
  if (!check(started, { 'conversation starts': (r) => r.status === 201 })) {
    return;
  }
  const conversationId = started.json('conversationId');
  const visitorToken = started.json('visitorToken');

  for (let i = 0; i < 2; i++) {
    const question = QUESTIONS[Math.floor(Math.random() * QUESTIONS.length)];
    const asked = http.post(`${widget}/conversations/${conversationId}/messages`, JSON.stringify({ question }), {
      headers: {
        ...headers,
        'Content-Type': 'application/json',
        Accept: 'text/event-stream',
        'X-Conversation-Token': visitorToken,
      },
      tags: { name: 'ask' },
      timeout: '60s',
    });
    // The stream ends with a done event once the answer is saved
    const done = asked.status === 200 && asked.body.includes('event: done');
    answered.add(done);
    answerTime.add(asked.timings.duration);
    firstEventTime.add(asked.timings.waiting);
    check(asked, { 'answer completes': () => done });
    sleep(2 + Math.random() * 3);
  }
}

// An operator with the inbox open: it refreshes the list now and then
export function operator(data) {
  const inbox = http.get(`${BASE_URL}/api/v1/conversations?page=0&size=20`, {
    headers: { Authorization: `Bearer ${data.token}` },
    tags: { name: 'inbox' },
  });
  check(inbox, { 'inbox loads': (r) => r.status === 200 });
  sleep(5);
}

export function handleSummary(data) {
  return {
    stdout: textSummary(data),
    '/results/summary.json': JSON.stringify(data, null, 2),
  };
}

// A compact table of the numbers that matter, besides k6's own summary in summary.json
function textSummary(data) {
  const metric = (name) => data.metrics[name];
  const trend = (name) => {
    const values = metric(name) && metric(name).values;
    if (!values) {
      return 'n/a';
    }
    return `p50 ${values.med.toFixed(0)} ms · p95 ${values['p(95)'].toFixed(0)} ms · p99 ${values['p(99)'].toFixed(0)} ms`;
  };
  const failed = metric('http_req_failed').values.rate;
  const lines = [
    '',
    `requests          ${metric('http_reqs').values.count} (${metric('http_reqs').values.rate.toFixed(1)}/s)`,
    `failed            ${(failed * 100).toFixed(2)}%`,
    `answers           ${metric('answered').values.passes} completed, ${metric('answered').values.fails} not`,
    `widget config     ${trend('http_req_duration{name:config}')}`,
    `start             ${trend('http_req_duration{name:start}')}`,
    `answer (full)     ${trend('answer_time')}`,
    `answer (1st byte) ${trend('answer_first_byte')}`,
    `operator inbox    ${trend('http_req_duration{name:inbox}')}`,
    '',
    'thresholds',
    ...Object.entries(data.metrics)
      .filter(([, value]) => value.thresholds)
      .flatMap(([name, value]) => Object.entries(value.thresholds)
        .map(([threshold, result]) => `  ${result.ok ? 'ok  ' : 'FAIL'} ${name} ${threshold}`)),
    '',
  ];
  return lines.join('\n');
}
