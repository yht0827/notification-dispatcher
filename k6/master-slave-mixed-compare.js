import http from 'k6/http';
import { check, sleep } from 'k6';
import { sendScenarioRequest } from './common.js';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const API_KEY = __ENV.API_KEY || 'dev-api-key-001';
const RECEIVER = __ENV.RECEIVER || 'readuser01@example.com';
const PAGE_SIZE = Number(__ENV.PAGE_SIZE || 20);
const GROUP_WEIGHT = Number(__ENV.GROUP_WEIGHT || 35);
const RECEIVER_WEIGHT = Number(__ENV.RECEIVER_WEIGHT || 25);
const UNREAD_WEIGHT = Number(__ENV.UNREAD_WEIGHT || 10);
const WRITE_WEIGHT = Number(__ENV.WRITE_WEIGHT || 30);

const HEADERS = {
  'X-Api-Key': API_KEY,
  Accept: 'application/json',
};

export const options = {
  vus: Number(__ENV.VUS || 50),
  duration: __ENV.DURATION || '60s',
  thresholds: {
    http_req_failed: ['rate<0.01'],
  },
};

function randomInt(maxExclusive) {
  return Math.floor(Math.random() * maxExclusive);
}

function pickOperation() {
  const bound = GROUP_WEIGHT + RECEIVER_WEIGHT + UNREAD_WEIGHT + WRITE_WEIGHT;
  const random = randomInt(bound);

  if (random < GROUP_WEIGHT) {
    return 'groups';
  }
  if (random < GROUP_WEIGHT + RECEIVER_WEIGHT) {
    return 'receiver';
  }
  if (random < GROUP_WEIGHT + RECEIVER_WEIGHT + UNREAD_WEIGHT) {
    return 'unread';
  }
  return 'write';
}

function requestRead(operation) {
  if (operation === 'groups') {
    return http.get(
      `${BASE_URL}/api/v1/notifications/groups?size=${PAGE_SIZE}`,
      { headers: HEADERS, tags: { endpoint: 'groups', mix: 'read' } }
    );
  }

  if (operation === 'receiver') {
    return http.get(
      `${BASE_URL}/api/v1/notifications/receiver?receiver=${encodeURIComponent(RECEIVER)}&size=${PAGE_SIZE}`,
      { headers: HEADERS, tags: { endpoint: 'receiver', mix: 'read' } }
    );
  }

  return http.get(
    `${BASE_URL}/api/v1/notifications/unread-count?receiver=${encodeURIComponent(RECEIVER)}`,
    { headers: HEADERS, tags: { endpoint: 'unread-count', mix: 'read' } }
  );
}

function checkReadResponse(response) {
  check(response, {
    'read status is 200': (r) => r.status === 200,
    'read response success true': (r) => {
      if (!r.body) {
        return false;
      }
      try {
        return r.json('success') === true;
      } catch (_error) {
        return false;
      }
    },
  });
}

export default function () {
  const operation = pickOperation();

  if (operation === 'write') {
    sendScenarioRequest('master-slave-mixed', __VU, __ITER);
  } else {
    const response = requestRead(operation);
    checkReadResponse(response);
  }

  sleep(Number(__ENV.SLEEP || 0.2));
}
