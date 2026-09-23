# SwiftPay - Real-Time Payment Ledger

An event-driven P2P payment system built with Spring Boot, PostgreSQL, Kafka, Redis, Docker, and Kubernetes.

SwiftPay is a P2P payment system built with Spring Boot. Payment requests are accepted by the Transaction Gateway and processed asynchronously by the Ledger Service through Kafka.

---

## Architecture

    
                              +------------------+
                              |      Client      |
                              +--------+---------+
                                       |
                                       | POST /v1/payments
                                       v
                 +-----------------------------------------+
                 |          Transaction Gateway :8080      |
                 |                                         |
                 |  • Request validation                   |
                 |  • Redis idempotency                    |
                 |  • Balance validation                   |
                 |  • Save payment as PENDING              |
                 +------------+---------------+------------+
                              |               |
                         REST |               | PaymentInitiated
                  balance check               v
                              |          +-----------+
                              +--------->|   Kafka   |
                                         +-----+-----+
                                               |
                                               v
                                  +-------------------------+
                                  |    Ledger Service :8081 |
                                  |                         |
                                  | • Account validation    |
                                  | • Pessimistic locking   |
                                  | • Atomic debit / credit |
                                  | • Ledger persistence    |
                                  +-----------+-------------+
                                              |
                                              v
                                       +-------------+
                                       | PostgreSQL  |
                                       |             |
                                       | Payments    |
                                       | Accounts    |
                                       | Ledger      |
                                       +-------------+
                                              |
                                  +-----------+-----------+
                                  |                       |
                                  v                       v
                         PaymentCompleted          PaymentFailed
                                  |                       |
                                  +-----------+-----------+
                                              |
                                              v
                                   Transaction Gateway
                                   updates payment state

                              +----------------+
                              |     Redis      |
                              | 24h idempotency|
                              +----------------+


---

## Key Features

- Asynchronous payment processing with Kafka
- Transactional debit and credit using PostgreSQL
- Redis-based idempotency with a 24-hour key
- Pessimistic row locking for concurrent balance updates
- Kafka consumer retries for temporary database failures
- Docker Compose and Kubernetes / Minikube deployment
- Automated testing, GitHub Actions CI, and 1M-transaction load testing at 250 TPS

---

## Tech Stack

| Component | Technology |
| --- | --- |
| Language | Java 21 |
| Framework | Spring Boot |
| Database | PostgreSQL 17 |
| Messaging | Apache Kafka 4.0.1 |
| Cache / Idempotency | Redis 7 |
| Build | Maven |
| API Documentation | Swagger / OpenAPI |
| Containers | Docker / Docker Compose |
| Orchestration | Kubernetes / Minikube |
| CI | GitHub Actions |
| Load Testing | k6 |
| Network Capture | Wireshark / TShark |

---

## Requirement Coverage

| Challenge Requirement | Implementation |
|---|---|
| P2P payments | Transaction Gateway + Ledger |
| Idempotency | Redis, 24-hour window |
| Transaction persistence | PostgreSQL |
| Event-driven processing | Kafka |
| Atomic debit/credit | PostgreSQL transaction + row locking |
| Insufficient funds | Gateway + Ledger validation |
| Kafka retry | Consumer retry configuration |
| API documentation | Swagger / OpenAPI |
| Containerization | Docker Compose |
| Orchestration | Kubernetes / Minikube |
| CI/CD | GitHub Actions |
| Load testing | k6, 1,000,000 transactions at 250 TPS |
| PCAP | Wireshark / TShark |

---

## Services

### Transaction Gateway

**Port:** `8080`

Handles payment intake and the synchronous part of the request.

- Validates the incoming payment request.
- Checks idempotency using Redis.
- Checks the sender's available balance.
- Persists the payment as `PENDING`.
- Publishes a `PaymentInitiated` Kafka event.
- Updates the payment when the Ledger Service publishes the final result.

### Ledger Service

**Port:** `8081`

Handles the actual account movement.

- Consumes `PaymentInitiated` events.
- Locks the sender and receiver account rows.
- Validates accounts, currency, amount, and available balance.
- Debits the sender and credits the receiver in one database transaction.
- Persists the ledger transaction.
- Publishes `PaymentCompleted` or `PaymentFailed`.

---

## API

### Create Payment

`POST /v1/payments`

Example request:

    {
      "sender_id": "ACC001",
      "receiver_id": "ACC002",
      "amount": 10.00,
      "currency": "INR",
      "transaction_id": "payment-001"
    }

Successful requests return **202 Accepted**.

### Get Account Balance

`GET /v1/accounts/{accountId}/balance`

Example:

    GET /v1/accounts/ACC001/balance

### Get Transaction

`GET /v1/transactions/{transactionId}`

### Get User Transaction History

`GET /v1/transactions/user/{userId}`

Returns transactions where the specified user is either the sender or receiver.

### Health

Both services expose:

`GET /health`

Response:

    {
      "status": "UP"
    }

---

## Consistency and Idempotency

### Payment Idempotency

Each payment has a `transaction_id`.

The Transaction Gateway stores an idempotency key in Redis for 24 hours. If the same transaction is submitted again, the existing payment is returned instead of creating another payment.

PostgreSQL is also checked as a fallback when the Redis entry is unavailable.

The Ledger Service performs an additional transaction ID check before applying an account update, protecting against duplicate Kafka delivery.

### Concurrent Balance Updates

Account balances are stored in PostgreSQL and updated inside a database transaction.

The Ledger Service uses pessimistic row locking when loading the sender and receiver accounts:

    Lock sender
        |
    Lock receiver
        |
    Validate balance
        |
    Debit sender
    Credit receiver
        |
    Persist transaction

If the operation fails, the database transaction is rolled back rather than leaving the accounts in a partially updated state.

---

## Failure Handling

### Insufficient Funds

The sender's balance is checked before the transfer.

If the available balance is insufficient, the transaction is recorded as `FAILED` and a `PaymentFailed` event is published.

### Temporary Database Failure

Kafka consumer processing uses four additional retry attempts with a two-second fixed delay between retries.

This allows a temporarily unavailable database to recover without immediately losing the Kafka event.

### Duplicate Events

The Ledger Service checks whether a transaction has already been recorded before applying the debit and credit operation.

---

## Swagger

Swagger UI is available for both services.

**Transaction Gateway**

`http://localhost:8080/swagger-ui/index.html`

**Ledger Service**

`http://localhost:8081/swagger-ui/index.html`

---

## Docker

The complete application stack can be started with Docker Compose:


```bash
docker compose up --build
```

---

## Kubernetes / Minikube

Kubernetes manifests are available in `k8s/`.

```bash
minikube start --driver=docker
kubectl apply -f k8s/
kubectl get pods
```

---

## Testing

Run the Transaction Gateway tests:

    cd transaction-gateway
    .\mvnw.cmd clean test

Run the Ledger Service tests:

    cd ..\ledger-service
    .\mvnw.cmd clean test

The project includes unit and integration tests covering application startup and payment/ledger behavior, including successful transfers and failure scenarios.

---

## Load Test

The payment API was tested using k6 at the required target of **250 transactions per second for 1,000,000 transactions**.

### Results

| Metric | Result |
| --- | ---: |
| Target rate | 250 TPS |
| Total transactions | 1,000,000 |
| Duration | 4,000 seconds |
| Actual rate | 249.999863 req/s |
| HTTP failures | 0 |
| Checks passed | 1,000,000 / 1,000,000 |
| Dropped iterations | 0 |
| Interrupted | 0 |
| p95 latency | 8.42 ms |
| Max latency | 366.62 ms |

The end-to-end database verification showed the load-test transactions in `COMPLETED` state.

The dedicated load accounts ended with:

    LOAD001    0.00 INR
    LOAD002    1,000,000.00 INR

### Load Test Script

The k6 script is available at:

`load-test/payment-load.js`

Run it with:

    k6 run load-test/payment-load.js

---

## PCAP

Network traffic was captured using Wireshark `dumpcap` during the 250 TPS load test.

The capture contains traffic from the 1M transaction load test:

[`swiftpay-250tps-1m.pcapng`](load-test/swiftpay-250tps-1m.pcapng)

The resulting capture is approximately **914 MB** and is stored in the repository using **Git LFS** because of its size.

---

## CI/CD

GitHub Actions is configured for the project and validates the Maven services through compilation and testing, with Docker image build support.

---

## Project Structure

    swiftpay/
    ├── transaction-gateway/
    │   ├── src/
    │   ├── Dockerfile
    │   └── pom.xml
    ├── ledger-service/
    │   ├── src/
    │   ├── Dockerfile
    │   └── pom.xml
    ├── k8s/
    ├── load-test/
    ├── .github/
    ├── docker-compose.yml
    ├── .gitignore
    └── README.md
