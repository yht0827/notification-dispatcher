import { sleep } from 'k6';
import { sendScenarioRequest } from './common.js';

const LABEL = __ENV.LABEL || 'fixed-workload';
const TOTAL_NOTIFICATIONS = Number(__ENV.TOTAL_NOTIFICATIONS || 10000);
const RECEIVER_COUNT = Math.max(1, Number(__ENV.RECEIVER_COUNT || 1));
const TOTAL_REQUESTS = Math.ceil(TOTAL_NOTIFICATIONS / RECEIVER_COUNT);

export const options = {
  scenarios: {
    fixed_workload: {
      executor: 'shared-iterations',
      vus: Number(__ENV.VUS || 50),
      iterations: TOTAL_REQUESTS,
      maxDuration: __ENV.MAX_DURATION || '30m',
    },
  },
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)', 'p(99.9)'],
  thresholds: {
    http_req_failed: ['rate<0.05'],
  },
  tags: {
    scenario_group: LABEL,
  },
};

export default function () {
  sendScenarioRequest(LABEL, __VU, __ITER);
  sleep(Number(__ENV.SLEEP || 0));
}
