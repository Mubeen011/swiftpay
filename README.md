# SwiftPay - Real-Time Payment Ledger

A resilient, event-driven P2P payment system built with Spring Boot, PostgreSQL, Kafka, Redis, Docker, and Kubernetes.

SwiftPay separates payment intake from ledger processing so that requests can be accepted quickly while the actual debit and credit operation is handled asynchronously and transactionally.

## Architecture

```text
                         +----------------------+
                         ¦       Client         ¦
                         +----------------------+
                                    ¦
                                    ?
                    +---------------------------+
                    ¦ Transaction Gateway :8080 ¦
                    ¦                           ¦
                    ¦ • Request validation      ¦
                    ¦ • Redis idempotency       ¦
                    ¦ • Payment persistence     ¦
                    ¦ • Balance validation      ¦
                    +---------------------------+
                                  ¦
                                  ¦ PaymentInitiated
                                  ?
                         +-----------------+
                         ¦      Kafka      ¦
                         +-----------------+
                                  ¦
                                  ?
                    +---------------------------+
                    ¦     Ledger Service :8081  ¦
                    ¦                           ¦
                    ¦ • Account validation      ¦
                    ¦ • Pessimistic locking     ¦
                    ¦ • Atomic debit / credit   ¦
                    ¦ • Transaction persistence ¦
                    +---------------------------+
                                  ¦
                       +---------------------+
                       ¦                     ¦
                       ?                     ?
              PaymentCompleted       PaymentFailed
                       ¦                     ¦
                       +---------------------+
                                  ?
                         Transaction Gateway
                         updates payment state
Design principles
PostgreSQL is the source of truth for account balances and transaction state.
Redis is used for 24-hour payment idempotency.
Kafka decouples payment intake from ledger processing.
Pessimistic row locking protects concurrent balance updates.
Payment creation is asynchronous and returns 202 Accepted.
Duplicate payment requests are handled using transaction_id.
Tech Stack
ComponentTechnology
LanguageJava 21
FrameworkSpring Boot
DatabasePostgreSQL 17
MessagingApache Kafka 4.0.1
Cache / IdempotencyRedis 7
BuildMaven
API DocumentationSwagger / OpenAPI
ContainersDocker / Docker Compose
OrchestrationKubernetes / Minikube
CIGitHub Actions
Load Testingk6
Network CaptureWireshark / TShark
Services
Transaction Gateway

Port: 8080

The Gateway handles the synchronous part of payment creation:

Validates the incoming request.
Checks payment idempotency using Redis.
Checks the sender's available balance.
Persists the payment as PENDING.
Publishes a PaymentInitiated Kafka event.
Updates the payment when the Ledger Service publishes the final result.
Ledger Service

Port: 8081

The Ledger Service owns the actual account movement:

Consumes PaymentInitiated.
Locks the sender and receiver account rows.
Validates accounts, currency, amount, and available balance.
Debits the sender and credits the receiver in one database transaction.
Persists the ledger transaction.
Publishes either PaymentCompleted or PaymentFailed.
API
Create Payment
POST /v1/payments

Example request:

{
  "sender_id": "ACC001",
  "receiver_id": "ACC002",
  "amount": 10.00,
  "currency": "INR",
  "transaction_id": "payment-001"
}

Successful requests return:

202 Accepted

The response contains the payment ID, transaction ID, payment status, and timestamps.

Get Account Balance
GET /v1/accounts/{accountId}/balance

Example:

GET /v1/accounts/ACC001/balance
Get Transaction
GET /v1/transactions/{transactionId}
Get User Transaction History
GET /v1/transactions/user/{userId}

Returns transactions where the specified user is either the sender or receiver.

Health

Both services expose:

GET /health

Response:

{
  "status": "UP"
}
Idempotency

Each payment has a transaction_id.

The Transaction Gateway uses Redis to store an idempotency key for 24 hours. If the same transaction is submitted again, the existing payment is returned instead of creating another payment.

PostgreSQL is also checked as a fallback when the Redis entry is unavailable.

The Ledger Service performs an additional transaction ID check before applying an account update. This protects the ledger from duplicate Kafka delivery.

Consistency and Concurrency

Account balances are stored in PostgreSQL and are updated inside a transactional operation.

The Ledger Service uses pessimistic row locking when loading the sender and receiver accounts:

Lock sender
Lock receiver
    ?
Validate balance
    ?
Debit sender
Credit receiver
    ?
Persist transaction

If the operation fails, the database transaction is rolled back rather than leaving the accounts in a partially updated state.

Failure Handling
Insufficient Funds

The sender's balance is checked before the transfer.

If the available balance is insufficient, the transaction is recorded as FAILED and a PaymentFailed event is published.

Temporary Database Failure

Kafka consumer processing uses retry handling with four additional attempts and a two-second fixed delay between retries.

This allows a temporarily unavailable database to recover without immediately losing the Kafka event.

Duplicate Events

The Ledger Service checks whether a transaction has already been recorded before applying the debit and credit operation.

Swagger

Swagger UI is available for both services:

Transaction Gateway

http://localhost:8080/swagger-ui/index.html

Ledger Service

http://localhost:8081/swagger-ui/index.html
Run Locally with Docker

Build the services:

cd transaction-gateway
.\mvnw.cmd clean package -DskipTests

cd ..\ledger-service
.\mvnw.cmd clean package -DskipTests

cd ..

Start the complete stack:

docker compose up -d --build

The Compose environment starts:

Transaction Gateway
Ledger Service
PostgreSQL
Kafka
Redis

Check the running containers:

docker ps

Stop the environment:

docker compose down

PostgreSQL data is persisted using a Docker volume.

Kubernetes / Minikube

Kubernetes manifests are available in:

k8s/

Start Minikube:

minikube start --driver=docker

Load the locally built application images:

minikube image load swiftpay-transaction-gateway:latest
minikube image load swiftpay-ledger-service:latest

Deploy:

kubectl apply -f k8s/

Check the deployment:

kubectl get pods
kubectl get services

The Kubernetes setup includes the application services and supporting PostgreSQL, Kafka, and Redis components.

Testing

Run the Transaction Gateway tests:

cd transaction-gateway
.\mvnw.cmd clean test

Run the Ledger Service tests:

cd ..\ledger-service
.\mvnw.cmd clean test

The project includes unit and integration tests covering application startup and payment/ledger behavior, including successful transfers and failure scenarios.

Load Test

The payment API was tested using k6 at the required target of 250 transactions per second for 1,000,000 transactions.

Configuration
Target rate:       250 TPS
Total iterations:  1,000,000
Duration:          4,000 seconds
Result
Completed:         1,000,000
Actual rate:       249.999863 req/s
HTTP failures:     0
Checks passed:     1,000,000 / 1,000,000
Dropped iterations: 0
Interrupted:       0
p95 latency:       8.42 ms
Max latency:       366.62 ms

The end-to-end database verification also showed all load-test transactions in COMPLETED state.

The dedicated load accounts ended with:

LOAD001    0.00 INR
LOAD002    1,000,000.00 INR
PCAP

Network traffic was captured using Wireshark dumpcap during the load test.

load-test/swiftpay-250tps-1m.pcapng

The resulting capture is approximately 914 MB.

The PCAP is kept outside Git because of its size and can be provided separately as part of the load-test evidence.

Load Test Script

The k6 script is available at:

load-test/payment-load.js

Run it with:

k6 run load-test/payment-load.js
CI/CD

GitHub Actions is configured for the project and validates the Maven services through compilation and testing, with Docker image build support.

Project Structure
swiftpay/
+-- transaction-gateway/
¦   +-- src/
¦   +-- Dockerfile
¦   +-- pom.xml
+-- ledger-service/
¦   +-- src/
¦   +-- Dockerfile
¦   +-- pom.xml
+-- k8s/
+-- load-test/
+-- .github/
+-- docker-compose.yml
+-- .gitignore
+-- README.md
What this project demonstrates
Event-driven payment processing with Kafka
Transactional financial state management with PostgreSQL
Idempotent payment APIs using Redis
Concurrent balance updates using database locking
Failure recovery through Kafka consumer retries
Containerized local development with Docker Compose
Kubernetes deployment with Minikube
Automated testing and CI
High-volume API load testing at 250 TPS

