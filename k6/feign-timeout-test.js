import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const HEADERS = { 'Content-Type': 'application/json' };
const CHANNEL_TYPE = __ENV.CHANNEL_TYPE || 'EMAIL';
const RECEIVER_COUNT = Number(__ENV.RECEIVER_COUNT || 20);
const LABEL = __ENV.LABEL || 'feign-timeout';

export const options = {
  vus: Number(__ENV.VUS || 15),
  duration: __ENV.DURATION || '60s',
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)', 'p(99.9)'],
  thresholds: {
    http_req_duration: ['p(95)<2500', 'p(99)<4000'],
    http_req_failed: ['rate<0.20'],
    checks: ['rate>0.80'],
  },
  tags: {
    scenario_group: LABEL,
  },
};

function buildReceivers(count, vu, iter) {
  const safeCount = Math.max(1, count);
  const out = [];
  for (let i = 0; i < safeCount; i += 1) {
    const suffix = String(i + 1).padStart(2, '0');
    out.push(`user${suffix}-vu${vu}-it${iter}@example.com`);
  }
  return out;
}

function buildBody(vu, iter) {
  return {
    clientId: 'feign-timeout-test',
    sender: 'ScenarioRunner',
    title: `${LABEL} run`,
    content: `label=${LABEL}, vu=${vu}, iter=${iter}`,
    channelType: CHANNEL_TYPE,
    receivers: buildReceivers(RECEIVER_COUNT, vu, iter),
    idempotencyKey: `${LABEL}-${Date.now()}-${vu}-${iter}`,
  };
}

function safeSuccessFlag(res) {
  if (res.status !== 201 || !res.body || res.body.length === 0) {
    return false;
  }
  try {
    return res.json('success') === true;
  } catch (e) {
    return false;
  }
}

export default function () {
  const res = http.post(
    `${BASE_URL}/api/v1/notifications`,
    JSON.stringify(buildBody(__VU, __ITER)),
    { headers: HEADERS, tags: { scenario: LABEL } }
  );

  const okStatus = res.status === 201;
  const okSuccess = safeSuccessFlag(res);

  check(res, {
    'status is 201': () => okStatus,
    'response success true': () => okSuccess,
  });

  sleep(Number(__ENV.SLEEP || 0.2));
}
