import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export const options = {
    vus: __ENV.VUS || 30,
    duration: __ENV.DURATION || '30s',
    thresholds: {
        http_req_duration: ['p(95)<1000', 'p(99)<2000'],
        http_req_failed: ['rate<0.05'],
    },
};

const HEADERS = { 'Content-Type': 'application/json' };

const CLIENTS = ['order-service', 'payment-service', 'delivery-service'];
const CHANNELS = ['EMAIL', 'SMS', 'KAKAO'];
const RECEIVERS = ['user1@example.com', '01011112222', 'kakao-001'];

function randomItem(arr) {
    return arr[Math.floor(Math.random() * arr.length)];
}

export default function () {
    const res = http.post(
        `${BASE_URL}/api/v1/notifications`,
        JSON.stringify({
            clientId: randomItem(CLIENTS),
            sender: 'LoadTest',
            title: '부하 테스트 알림',
            content: `VU ${__VU} / ITER ${__ITER}`,
            channelType: randomItem(CHANNELS),
            receivers: [randomItem(RECEIVERS)],
            idempotencyKey: `simple-${Date.now()}-${__VU}-${__ITER}`,
        }),
        { headers: HEADERS }
    );

    check(res, {
        'status 201': (r) => r.status === 201,
        'success true': (r) => r.json('success') === true,
    });

    sleep(1);
}
