import http from 'k6/http';
import { check } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

const CLIENT_ID = __ENV.CLIENT_ID || 'perf-test-service';
const CHANNEL_TYPE = __ENV.CHANNEL_TYPE || 'EMAIL';
const RECEIVER_COUNT = Number(__ENV.RECEIVER_COUNT || 20);
const API_KEY = __ENV.API_KEY || __ENV.X_API_KEY || CLIENT_ID;
const SCHEDULED_DELAY_SECONDS = Number(__ENV.SCHEDULED_DELAY_SECONDS || 0);

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

function formatUtcDateTime(date) {
  const year = date.getUTCFullYear();
  const month = String(date.getUTCMonth() + 1).padStart(2, '0');
  const day = String(date.getUTCDate()).padStart(2, '0');
  const hours = String(date.getUTCHours()).padStart(2, '0');
  const minutes = String(date.getUTCMinutes()).padStart(2, '0');
  const seconds = String(date.getUTCSeconds()).padStart(2, '0');

  return `${year}-${month}-${day}T${hours}:${minutes}:${seconds}`;
}

function buildRequestBody(label, vu, iter) {
  const scheduledAt = SCHEDULED_DELAY_SECONDS > 0
    ? formatUtcDateTime(new Date(Date.now() + (SCHEDULED_DELAY_SECONDS * 1000)))
    : undefined;

  return {
    clientId: CLIENT_ID,
    sender: 'ScenarioRunner',
    title: `${label} scenario`,
    content: `scenario=${label}, vu=${vu}, iter=${iter}`,
    channelType: CHANNEL_TYPE,
    receivers: buildEmailReceivers(RECEIVER_COUNT, vu, iter),
    idempotencyKey: `${label}-${Date.now()}-${vu}-${iter}`,
    scheduledAt,
  };
}

export function sendScenarioRequest(label, vu, iter) {
  const response = http.post(
    `${BASE_URL}/api/v1/notifications`,
    JSON.stringify(buildRequestBody(label, vu, iter)),
    { headers: DEFAULT_HEADERS, tags: { scenario: label } }
  );

  check(response, {
    'status is 201': (r) => r && r.status === 201,
    'response success true': (r) => {
      if (!r || r.status !== 201 || !r.body) {
        return false;
      }

      try {
        return r.json('success') === true;
      } catch (_error) {
        return false;
      }
    },
  });

  return response;
}
