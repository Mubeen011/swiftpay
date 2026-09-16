import http from 'k6/http';
import { check } from 'k6';

export const options = {
    scenarios: {
        payments: {
            executor: 'constant-arrival-rate',
            rate: 250,
            timeUnit: '1s',
            duration: '4000s',
            preAllocatedVUs: 150,
            maxVUs: 500,
        },
    },
};

export default function () {
    const transactionId = `load-${__VU}-${__ITER}-${Date.now()}`;

    const payload = JSON.stringify({
        sender_id: 'LOAD001',
        receiver_id: 'LOAD002',
        amount: 1.00,
        currency: 'INR',
        transaction_id: transactionId,
    });

    const response = http.post(
        'http://localhost:8080/v1/payments',
        payload,
        {
            headers: {
                'Content-Type': 'application/json',
            },
        }
    );

    check(response, {
        'payment accepted': (r) => r.status === 202,
    });
}