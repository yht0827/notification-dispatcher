import { sleep } from 'k6';
import { sendScenarioRequest } from './common.js';

export const options = {
  vus: Number(__ENV.VUS || 20),
  duration: __ENV.DURATION || '90s',
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)', 'p(99.9)'],
  thresholds: {
    http_req_duration: ['p(95)<1200', 'p(99)<2500'],
    http_req_failed: ['rate<0.02'],
    checks: ['rate>0.98'],
  },
  tags: {
    scenario_group: 'timeout-retry',
  },
};

export default function () {
  sendScenarioRequest('timeout-retry', __VU, __ITER);
  sleep(Number(__ENV.SLEEP || 0.2));
}
