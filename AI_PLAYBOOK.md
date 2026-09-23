# SwiftPay AI Playbook

## What I used AI for

I used AI throughout the SwiftPay hackathon while building the project, mainly to break down the requirements, work through the architecture, implement and review parts of the code, debug issues, work on tests, and check the project before submission.

The project was built around the hackathon requirements:

- Java 21 / Spring Boot
- PostgreSQL
- Kafka
- Redis
- Swagger/OpenAPI
- Docker
- Kubernetes / Minikube
- GitHub Actions
- Unit and integration testing
- 250 TPS load test for 1 million transactions
- PCAP capture from the load test

The development process was mostly:

```text
Requirement
   ↓
Discuss the approach with AI
   ↓
Implement
   ↓
Run it
   ↓
Check the result / logs
   ↓
Debug or review with AI when needed
   ↓
Fix and test again
```

I worked on the project incrementally rather than asking AI to generate the whole application at once.

---

## SwiftPay context

SwiftPay has two main services:

**Transaction Gateway**

- `POST /v1/payments`
- Request validation
- Redis-based 24-hour idempotency
- Balance validation
- Saves the initial payment as `PENDING`
- Publishes `PaymentInitiated` to Kafka

**Ledger Service**

- Consumes `PaymentInitiated`
- Processes the debit and credit in PostgreSQL
- Handles concurrent balance updates
- Updates the payment status
- Publishes completion/failure events
- Provides transaction history

The repository also contains the Docker/Kubernetes setup, GitHub Actions CI, Swagger/OpenAPI documentation, tests, load-testing setup and the PCAP from the 1M transaction run.

---

# Prompts used during development

## 1. Understanding the hackathon

```text
Here's the hackathon requirement. Break it down into the actual things I need to implement. Keep it within the required stack and don't add unnecessary components.
```

This was used at the beginning to turn the challenge into smaller pieces instead of trying to build everything at once.

---

## 2. Planning the architecture

```text
I need a P2P payment flow using Spring Boot, PostgreSQL, Kafka and Redis. The API needs idempotency and reliable transaction processing. Walk me through the request flow and where each technology should be used.
```

---

## 3. Splitting the services

```text
I have transaction-gateway and ledger-service. What should each service be responsible for and what should happen through Kafka between them?
```

This helped work through the boundary between the REST API and the asynchronous ledger processing.

---

## 4. Redis idempotency

```text
I need Redis-based idempotency for the payment API for 24 hours. What should I store against the idempotency key, when should it be created, and what should happen if the same request comes again?
```

---

## 5. Balance validation and concurrent payments

```text
Where should balance validation happen in the payment flow if PostgreSQL is responsible for the transaction? I also need to make sure two concurrent payments don't incorrectly use the same balance.
```

This was used to work through the database transaction and concurrent balance-update behavior.

---

## 6. Kafka events and retries

```text
The payment is stored in PostgreSQL and then needs to be processed through Kafka. What should the event contain and how should the consumer handle retries without processing the same payment incorrectly?
```

---

## 7. Implementing changes without changing unrelated code

```text
I already have this structure. Help me implement this requirement without changing unrelated parts.
```

This was useful when adding individual pieces to the existing services instead of rewriting the project.

---

## 8. Debugging an actual application error

```text
This is the error I'm getting when I start the application. Here is the relevant configuration and log. Find the actual cause first and tell me the smallest change needed to fix it.
```

Actual application logs and configuration were provided when debugging Spring Boot, PostgreSQL, Redis and Kafka issues.

---

## 9. Reviewing payment behavior

```text
Look at the current payment flow and check for problems with duplicate requests, insufficient balance and transaction failures. Don't rewrite everything. Tell me what is actually wrong and the smallest fix.
```

---

## 10. Test cases

```text
The payment flow is working. What test cases should I cover for successful payments, duplicate requests, insufficient balance, and failure cases?
```

This was used to identify cases worth testing rather than just trying to increase test coverage.

---

## 11. Testing against the actual requirements

```text
Based on the SwiftPay hackathon requirements and the current Java/Spring Boot code, propose tests that prove the business behaviour rather than merely increasing coverage.

Required scenarios:
- accepted payment becomes terminally completed after ledger processing;
- insufficient funds creates no debit/credit;
- reusing the same transaction_id does not move money twice;
- duplicate Kafka delivery does not create a second ledger entry;
- invalid request returns a standard client error;
- health endpoint is available.

Use the test libraries already present in the repository. For each test, identify setup, assertion, and why it protects a real payment-system failure mode. Keep the tests deterministic.
```

JUnit tests were then run against the project.

---

## 12. Docker setup

```text
These are the services I need to run locally: PostgreSQL, Redis and Kafka. Help me structure the Docker Compose setup and the application configuration so the services can communicate correctly.
```

---

## 13. Kubernetes

```text
I need to run the SwiftPay services with Kubernetes/Minikube. What Kubernetes resources do I actually need for this project and how should the services connect to each other?
```

The Kubernetes configuration was then applied and checked locally.

---

## 14. GitHub Actions

```text
I need a GitHub Actions workflow for this Spring Boot project. What should the CI pipeline run for a basic submission: build, tests and anything else that is actually useful?
```

---

## 15. Swagger / OpenAPI

```text
Here is the payment API and its request/response model. Check whether the Swagger/OpenAPI documentation covers the important request fields, responses and error cases.
```

---

## 16. Load testing

```text
I need to load test the payment API at 250 TPS for 1 million transactions. Help me create the load test and calculate how long it should run.
```

The actual load test was then run against the application.

The resulting run processed:

- 1,000,000 transactions
- 250 TPS
- 0% HTTP failures
- 8.42 ms p95 latency

The PCAP was captured from the actual load-test run.

---

## 17. PCAP

```text
I need a PCAP file for the 250 TPS load test. What's the simplest way to capture the traffic and verify that the PCAP contains the expected traffic?
```

The resulting PCAP was added to the repository using Git LFS because of its size.

---

## 18. Final project review

```text
Act as a reviewer for the SwiftPay hackathon. Compare the repository README, Docker Compose, Kubernetes manifests, GitHub Actions workflow, test suite, and load-test evidence against the task.

Give only evidence-based findings in these categories:
- requirement fully demonstrated;
- implemented but weakly evidenced;
- not implemented or not safe to claim;
- documentation inconsistency;
- likely reviewer question.

Do not rewrite the project. Give the smallest changes that increase reviewer confidence before submission.
```

---

# How I used the responses

The AI responses were used differently depending on the task.

For architecture and implementation questions, I used them to decide what to build and then implemented the change in the project.

For debugging, I provided the actual error or logs and checked the proposed fix against the running application.

For tests, I used the suggested cases to decide what needed coverage and then ran the tests.

For Docker, Kubernetes and CI, the generated configuration was actually run or checked against the local environment.

For the load test, AI helped with the setup and checks, but the performance numbers came from the actual run.

---

# What the final project was checked against

Before submission, the project was checked against the main hackathon requirements:

- P2P payment flow
- PostgreSQL transactions
- Redis 24-hour idempotency
- Balance validation
- Kafka event processing
- Kafka consumer retries
- Swagger/OpenAPI
- Health endpoint
- Docker
- Kubernetes / Minikube
- GitHub Actions
- Unit/integration testing
- 250 TPS load test
- 1 million transactions
- PCAP evidence

The README and repository were also reviewed against the implementation so that the documentation matched what was actually present in the project.

---

# What I learned from using AI on this project

### Give it the actual project context

The useful answers came when the existing code, configuration, requirement or error was included in the prompt. Generic questions usually needed more follow-up.

### Work on one problem at a time

Breaking the project into things like idempotency, Kafka processing, database transactions, testing and deployment made it easier to implement and check each part.

### Run the thing

For backend and infrastructure problems, an answer can sound correct and still be wrong for the current project. Running the application, tests and containers was the quickest way to find that out.

### Use AI again when something fails

When a test, build, container or application failed, the error itself became the next input. That made the process iterative rather than treating the first generated solution as final.
