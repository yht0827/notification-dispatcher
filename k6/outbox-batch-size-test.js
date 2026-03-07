import { sleep } from 'k6';
import { sendScenarioRequest } from './common.js';

const LABEL = __ENV.LABEL || 'outbox-batch-size';

export const options = {
  vus: Number(__ENV.VUS || 15),
  duration: __ENV.DURATION || '60s',
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)', 'p(99.9)'],
  thresholds: {
    http_req_duration: ['p(95)<1200', 'p(99)<2500'],
    http_req_failed: ['rate<0.02'],
    checks: ['rate>0.98'],
  },
  tags: {
    scenario_group: LABEL,
  },
};

export default function () {
  sendScenarioRequest(LABEL, __VU, __ITER);
  sleep(Number(__ENV.SLEEP || 0.2));
}
