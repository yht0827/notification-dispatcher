import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const ADMIN_KEY = __ENV.ADMIN_KEY || 'dev-admin-key-001';
const ENDPOINT = __ENV.ENDPOINT || '/api/admin/v1/stats';
const VUS = Number(__ENV.VUS || 50);
const DURATION = __ENV.DURATION || '60s';
const SLEEP_SEC = Number(__ENV.SLEEP_SEC || 0.2);

export const options = {
  vus: VUS,
  duration: DURATION,
  thresholds: {
    http_req_failed: ['rate<0.01'],
  },
};

export default function () {
  const response = http.get(`${BASE_URL}${ENDPOINT}`, {
    headers: {
      'X-Admin-Key': ADMIN_KEY,
      Accept: 'application/json',
    },
  });

  check(response, {
    'status is 200': (r) => r.status === 200,
    'response success true': (r) => r.json('success') === true,
  });

  sleep(SLEEP_SEC);
}
