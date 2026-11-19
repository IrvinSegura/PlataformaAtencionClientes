// The chat load test (../chat.js) against the production-like environment of docker-compose.yml: through Caddy over
// HTTPS, to two backend instances that share their rate limits in Redis. Each visitor comes from an address of its
// own, as real ones do, so the per-address limits stay on; one abuser starting conversations from a single address
// must be stopped by them, whichever instance its requests reach.
//
// PEAK_RATE (conversations started per second at the peak) and HOLD (how long the peak lasts) shape the run;
// ANSWER_P95 is the slowest the 95th percentile answer may take, in ms (higher with docker-compose.model.yml).
import { check, fail, sleep } from 'k6';
import exec from 'k6/execution';
import http from 'k6/http';
import { Counter, Rate, Trend } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'https://web';
const ORIGIN = 'https://shop.example.com';
const PEAK_RATE = Number(__ENV.PEAK_RATE || 10);
const HOLD = __ENV.HOLD || '3m';
const ANSWER_P95 = Number(__ENV.ANSWER_P95 || 1500);
// What application.yml allows one address: 10 new conversations per 10 minutes, refilled one a minute
const CONVERSATIONS_PER_ADDRESS = 10;

const answerTime = new Trend('answer_time', true);
const firstEventTime = new Trend('answer_first_byte', true);
const answered = new Rate('answered');
const abuserAdmitted = new Counter('abuser_admitted');
const abuserLimited = new Counter('abuser_limited');

export const options = {
  insecureSkipTLSVerify: true,
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
      duration: '4m30s',
    },
    abuser: {
      executor: 'constant-arrival-rate',
      exec: 'abuser',
      rate: 2,
      timeUnit: '1s',
      duration: '1m',
      startTime: '30s',
      preAllocatedVUs: 2,
    },
  },
  thresholds: {
    // The abuser's 429s are expected and do not count (see abuser())
    http_req_failed: ['rate<0.01'],
    'http_req_duration{name:config}': ['p(95)<200'],
    'http_req_duration{name:start}': ['p(95)<300'],
    'http_req_duration{name:inbox}': ['p(95)<500'],
    answer_time: [`p(95)<${ANSWER_P95}`],
    answered: ['rate>0.99'],
    // Shared limits: 10, plus the one a minute refills, however the requests are spread over the instances
    abuser_admitted: [`count<=${CONVERSATIONS_PER_ADDRESS + 2}`],
    abuser_limited: ['count>100'],
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
`;

const QUESTIONS = [
  '¿Cuánto tarda el envío nacional?',
  '¿Cuánto cuesta el envío?',
  '¿Cuántos días tengo para devolver un producto?',
  '¿Cuándo me regresan mi dinero?',
  '¿Aceptan pago en OXXO?',
  '¿Tienen meses sin intereses?',
  '¿Con qué paquetería envían?',
  '¿Venden boletos de avión?',
];

/**
 * A public address of its own for each iteration: 11.0.0.0/8 is not private, so Caddy and the backend take it as the
 * client's, as they do with a real visitor's address.
 */
function address(n) {
  return `11.${(n >> 16) & 255}.${(n >> 8) & 255}.${n & 255}`;
}

export function setup() {
  const from = { 'X-Forwarded-For': address(1) };
  const registered = http.post(`${BASE_URL}/api/v1/auth/register`, JSON.stringify({
    email: `load-${Date.now()}@example.com`,
    password: 'load-test-password-1',
    displayName: 'Load test',
    workspaceName: 'Load test',
    acceptTerms: true,
  }), { headers: { ...from, 'Content-Type': 'application/json' } });
  if (registered.status !== 201) {
    fail(`register answered ${registered.status}: ${registered.body}`);
  }
  const auth = { ...from, Authorization: `Bearer ${registered.json('accessToken')}` };

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
  for (let attempt = 0; attempt < 60; attempt++) {
    const document = http.get(`${BASE_URL}/api/v1/knowledge/documents/${upload.json('id')}`, { headers: auth });
    if (document.json('status') === 'READY') {
      return { auth, widgetKey: settings.json('widgetKey') };
    }
    sleep(1);
  }
  fail('the document never became READY');
}

// One visitor: loads the widget, starts a conversation and asks two questions, reading each answer before the next
export function visitor(data) {
  const widget = `${BASE_URL}/api/v1/widget/${data.widgetKey}`;
  // Leaves the first addresses to setup and the abuser
  const headers = { Origin: ORIGIN, 'X-Forwarded-For': address(1000 + exec.scenario.iterationInTest) };

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
    headers: data.auth,
    tags: { name: 'inbox' },
  });
  check(inbox, { 'inbox loads': (r) => r.status === 200 });
  sleep(5);
}

// Starts two conversations a second from one address; round robin spreads them over both instances
export function abuser(data) {
  const started = http.post(`${BASE_URL}/api/v1/widget/${data.widgetKey}/conversations`, null, {
    headers: { Origin: ORIGIN, 'X-Forwarded-For': address(2) },
    tags: { name: 'abuse' },
    responseCallback: http.expectedStatuses(201, 429),
  });
  if (started.status === 201) {
    abuserAdmitted.add(1);
  } else if (started.status === 429) {
    abuserLimited.add(1);
  }
}

export function handleSummary(data) {
  return {
    stdout: textSummary(data),
    '/results/production-summary.json': JSON.stringify(data, null, 2),
  };
}

function textSummary(data) {
  const metric = (name) => data.metrics[name];
  const count = (name) => (metric(name) ? metric(name).values.count : 0);
  const trend = (name) => {
    const values = metric(name) && metric(name).values;
    if (!values) {
      return 'n/a';
    }
    return `p50 ${values.med.toFixed(0)} ms · p95 ${values['p(95)'].toFixed(0)} ms · p99 ${values['p(99)'].toFixed(0)} ms`;
  };
  const lines = [
    '',
    `requests          ${count('http_reqs')} (${metric('http_reqs').values.rate.toFixed(1)}/s)`,
    `failed            ${(metric('http_req_failed').values.rate * 100).toFixed(2)}%`,
    `answers           ${metric('answered').values.passes} completed, ${metric('answered').values.fails} not`,
    `abuser            ${count('abuser_admitted')} admitted, ${count('abuser_limited')} limited (429)`,
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
