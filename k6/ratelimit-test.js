import { sleep } from 'k6';
import { sendScenarioRequest } from './common.js';

export const options = {
  vus: Number(__ENV.VUS || 15),
  duration: __ENV.DURATION || '60s',
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)', 'p(99.9)'],
  thresholds: {
    http_req_failed: ['rate<0.10'],
    checks: ['rate>0.90'],
  },
  tags: {
    scenario_group: 'rate-limit',
  },
};

export default function () {
  sendScenarioRequest('rate-limit', __VU, __ITER);
  sleep(Number(__ENV.SLEEP || 0.2));
}
