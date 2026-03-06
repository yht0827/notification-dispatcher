import { sleep } from 'k6';
import { sendScenarioRequest } from './common.js';

export const options = {
  vus: Number(__ENV.VUS || 20),
  duration: __ENV.DURATION || '60s',
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)', 'p(99.9)'],
  thresholds: {
    http_req_duration: ['p(95)<1000', 'p(99)<2000'],
    http_req_failed: ['rate<0.01'],
    checks: ['rate>0.99'],
  },
  tags: {
    scenario_group: 'baseline',
  },
};

export default function () {
  sendScenarioRequest('baseline', __VU, __ITER);
  sleep(Number(__ENV.SLEEP || 0.2));
}
