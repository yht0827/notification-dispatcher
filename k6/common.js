import http from 'k6/http';
import { check } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

const CLIENT_ID = __ENV.CLIENT_ID || 'perf-test-service';
const CHANNEL_TYPE = __ENV.CHANNEL_TYPE || 'EMAIL';
const RECEIVER_COUNT = Number(__ENV.RECEIVER_COUNT || 20);
const API_KEY = __ENV.API_KEY || __ENV.X_API_KEY || CLIENT_ID;

const DEFAULT_HEADERS = {
  'Content-Type': 'application/json',
  'X-Api-Key': API_KEY,
};

function buildEmailReceivers(count, vu, iter) {
  const receivers = [];
  const safeCount = Math.max(1, count);

  for (let i = 0; i < safeCount; i += 1) {
    const suffix = String(i + 1).padStart(2, '0');
    receivers.push(`user${suffix}-vu${vu}-it${iter}@example.com`);
  }

  return receivers;
}

function buildRequestBody(label, vu, iter) {
  return {
    clientId: CLIENT_ID,
    sender: 'ScenarioRunner',
    title: `${label} scenario`,
    content: `scenario=${label}, vu=${vu}, iter=${iter}`,
    channelType: CHANNEL_TYPE,
    receivers: buildEmailReceivers(RECEIVER_COUNT, vu, iter),
    idempotencyKey: `${label}-${Date.now()}-${vu}-${iter}`,
  };
}

export function sendScenarioRequest(label, vu, iter) {
  const response = http.post(
    `${BASE_URL}/api/v1/notifications`,
    JSON.stringify(buildRequestBody(label, vu, iter)),
    { headers: DEFAULT_HEADERS, tags: { scenario: label } }
  );

  check(response, {
    'status is 201': (r) => r.status === 201,
    'response success true': (r) => r.json('success') === true,
  });

  return response;
}
