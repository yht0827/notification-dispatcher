import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const ENDPOINT = __ENV.ENDPOINT || '/actuator/health';
const API_KEY = __ENV.API_KEY || 'dev-api-key-001';

export const options = {
  vus: Number(__ENV.VUS || 15),
  duration: __ENV.DURATION || '20s',
  thresholds: {
    http_req_failed: ['rate<0.01'],
  },
};

export default function () {
  const response = http.get(`${BASE_URL}${ENDPOINT}`, {
    headers: {
      'X-API-Key': API_KEY,
      Accept: 'application/json',
    },
  });

  check(response, {
    'status is 200': (r) => r.status === 200,
    'response success true': (r) => r.json('success') === true,
  });

  sleep(Number(__ENV.SLEEP_SEC || 0.1));
}
