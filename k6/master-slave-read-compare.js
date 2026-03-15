import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const API_KEY = __ENV.API_KEY || 'dev-api-key-001';
const RECEIVER = __ENV.RECEIVER || 'readuser01@example.com';
const PAGE_SIZE = Number(__ENV.PAGE_SIZE || 20);
const GROUP_WEIGHT = Number(__ENV.GROUP_WEIGHT || 50);
const RECEIVER_WEIGHT = Number(__ENV.RECEIVER_WEIGHT || 35);
const UNREAD_WEIGHT = Number(__ENV.UNREAD_WEIGHT || 15);

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
  const bound = GROUP_WEIGHT + RECEIVER_WEIGHT + UNREAD_WEIGHT;
  const random = randomInt(bound);

  if (random < GROUP_WEIGHT) {
    return 'groups';
  }
  if (random < GROUP_WEIGHT + RECEIVER_WEIGHT) {
    return 'receiver';
  }
  return 'unread';
}

function requestByOperation(operation) {
  if (operation === 'groups') {
    return http.get(
      `${BASE_URL}/api/v1/notifications/groups?size=${PAGE_SIZE}`,
      { headers: HEADERS, tags: { endpoint: 'groups' } }
    );
  }

  if (operation === 'receiver') {
    return http.get(
      `${BASE_URL}/api/v1/notifications/receiver?receiver=${encodeURIComponent(RECEIVER)}&size=${PAGE_SIZE}`,
      { headers: HEADERS, tags: { endpoint: 'receiver' } }
    );
  }

  return http.get(
    `${BASE_URL}/api/v1/notifications/unread-count?receiver=${encodeURIComponent(RECEIVER)}`,
    { headers: HEADERS, tags: { endpoint: 'unread-count' } }
  );
}

export default function () {
  const operation = pickOperation();
  const response = requestByOperation(operation);

  check(response, {
    'status is 200': (r) => r.status === 200,
    'response success true': (r) => {
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

  sleep(Number(__ENV.SLEEP || 0.2));
}
