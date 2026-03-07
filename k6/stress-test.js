import { sleep } from 'k6';
import { sendScenarioRequest } from './common.js';

export const options = {
  vus: Number(__ENV.VUS || 30),
  duration: __ENV.DURATION || '120s',
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)', 'p(99.9)'],
  thresholds: {
    http_req_duration: ['p(95)<1500', 'p(99)<3000'],
    http_req_failed: ['rate<0.03'],
    checks: ['rate>0.97'],
  },
  tags: {
    scenario_group: 'circuit-open',
  },
};

export default function () {
  sendScenarioRequest('circuit-open', __VU, __ITER);
  sleep(Number(__ENV.SLEEP || 0.1));
}
