# FinanceFlow

A personal finance management REST API built with Java and Spring Boot. FinanceFlow provides APIs for managing financial transactions, importing transaction data from CSV files, and tracking budgets against spending.

The project is being developed as a hands-on learning journey covering REST API design, Spring Data JPA, PostgreSQL, validation, testing, database performance, authentication, and service-oriented architecture.

## Features

### Financial Transactions
- Create and retrieve financial transactions.
- Represent income and expenses using structured DTOs.
- Persist transaction data using Spring Data JPA and PostgreSQL.
- Filter transactions using supported search criteria.
- Handle API errors centrally using `@RestControllerAdvice`.

### CSV Import
- Import financial transactions from CSV files.
- Report successful imports and failed records.
- Process large CSV files and investigate persistence performance.
- Explore Hibernate JDBC batching and database ID-generation strategies.

### Budget Management
- Create, retrieve, update, and delete budgets.
- Aggregate transaction spending against budgets.
- Calculate remaining budget and percentage utilized.

### API Documentation
- Interactive API documentation using Swagger UI and OpenAPI.
- Test endpoints without needing a separate API client.

### Testing
- Controller and service tests.
- Integration testing with Spring Boot and PostgreSQL configuration.

## Tech Stack

| Technology | Purpose |
|---|---|
| Java 21 | Programming language |
| Spring Boot | Application framework |
| Spring Web | REST APIs |
| Spring Data JPA | Persistence and repository layer |
| Hibernate | ORM and SQL generation |
| PostgreSQL | Relational database |
| Swagger / OpenAPI | API documentation |
| Maven | Build and dependency management |
| JUnit / Spring Boot Test | Automated testing |
| Docker | Containerization |

## Architecture

The application follows a layered architecture:

```text
Client
  |
  v
REST Controllers
  |
  v
Services
  |
  v
Repositories (Spring Data JPA)
  |
  v
PostgreSQL
```

DTOs separate API request/response models from persistence entities. Centralized exception handling provides a consistent approach to API errors.

## Prerequisites

- Java 21
- PostgreSQL
- Git
- Maven, or the Maven Wrapper included in the repository

## Getting Started

### 1. Clone the repository

```bash
git clone https://github.com/bewidmj/financeflow.git
cd financeflow
```

### 2. Create the PostgreSQL database

Open PostgreSQL and create the database:

```sql
CREATE DATABASE financeflow_db;
```

Ensure your PostgreSQL user has permission to connect to this database.

### 3. Configure the database connection

Review `src/main/resources/application.yml` or `application.properties`, depending on the configuration file in your checkout.

Configure the JDBC URL, username, and password for your local PostgreSQL instance. Do not commit actual passwords or other secrets.

Example configuration:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/financeflow_db
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
```

Use the configuration properties already defined by the project; adapt the example if your existing configuration uses different names.

### 4. Configure JWT verification

FinanceFlow verifies RS256 access tokens using the Identity Service's public JWKS. It never receives or stores the RSA private key and does not call the Identity Service for every API request. The local profile defaults to the Identity Service JWKS at port 8081; override it for other deployments:

```bash
export JWT_JWKS_URI="http://localhost:8081/.well-known/jwks.json"
```

The URI is trusted application configuration, not read from the incoming token. FinanceFlow pins verification to RS256, validates standard token time claims, and selects the public key using the token `kid`. It uses a 2-second connect timeout, 5-second read timeout, and Nimbus's default JWKS cache/refresh behavior (approximately five minutes). A running instance can use a cached key during a temporary JWKS outage until a refresh is needed; a cold instance without a cached key cannot validate tokens until the Identity Service becomes reachable. Keep network access and readiness expectations aligned with this behavior.

Swagger/OpenAPI remains public; API endpoints require a valid bearer token. FinanceFlow ownership is derived from the verified token's `sub` UUID, and normal transaction and budget records continue to be scoped to that identity. Admin transaction browsing is additionally restricted to a verified `role: ADMIN` JWT claim and is exposed only through `/api/v1/admin/**`.

Transactions are associated with the authenticated JWT subject. When ownership is first added, existing database rows are retained with no owner and are excluded from user queries. They are not automatically assigned to an account. Before making the owner column mandatory, backfill only from a verified transaction-to-user mapping (or explicitly archive/remove legacy rows), then apply a versioned migration and the non-null constraint.

Budgets are also scoped to the JWT subject, and their spending totals include only that user's transactions. Existing budgets remain unassigned and are excluded from user-facing budget queries; they are never assigned to an arbitrary account. Because this project does not yet run a migration framework, apply `src/main/resources/db/manual/V1__budget_user_ownership.sql` to an existing PostgreSQL database before deploying this change. It adds the nullable owner column and replaces the old global category/month unique constraint with a user-scoped constraint. Backfill legacy budgets only from a verified budget-to-user mapping; otherwise leave them unassigned or archive them before making ownership mandatory in a future versioned migration.

### Identity Service key setup and local end-to-end use

Generate and retain one local RSA key pair outside both repositories. Do not generate a new key on every application start:

```bash
mkdir -p "$HOME/.config/financeflow"
chmod 700 "$HOME/.config/financeflow"
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 \
  -out "$HOME/.config/financeflow/identity-private.pem"
openssl pkey -in "$HOME/.config/financeflow/identity-private.pem" \
  -pubout -out "$HOME/.config/financeflow/identity-public.pem"
chmod 600 "$HOME/.config/financeflow/identity-private.pem"
```

Start the Identity Service in one terminal with its private PKCS#8 PEM path and stable key ID:

```bash
cd ../financeflow-identity-service
export JWT_PRIVATE_KEY_LOCATION="file:$HOME/.config/financeflow/identity-private.pem"
export JWT_KEY_ID="financeflow-local-2026-01"
mvn spring-boot:run
```

Then start FinanceFlow in another terminal:

```bash
cd ../financeflow
export JWT_JWKS_URI="http://localhost:8081/.well-known/jwks.json"
./mvnw spring-boot:run
```

The Identity Service publishes public keys at `http://localhost:8081/.well-known/jwks.json`; login is at `http://localhost:8081/api/v1/auth/login`. Log in with a registered user, then open `http://localhost:8080/swagger-ui/index.html`, choose **Authorize**, and enter the returned bearer token. To inspect just the JWT header without printing the complete token:

```bash
ACCESS_TOKEN="$TOKEN" python3 -c 'import os,json,base64; h=os.environ["ACCESS_TOKEN"].split(".")[0]; d=json.loads(base64.urlsafe_b64decode(h+"="*((4-len(h)%4)%4))); print("alg:",d.get("alg"),"kid:",d.get("kid"))'
```

FinanceFlow does not need `JWT_SECRET`. After the cutover, all existing HS384 tokens are rejected; log in again to obtain an RS256 token. There is no automatic or indefinite HS384 fallback.

Identity Service tests and FinanceFlow tests each use generated test-only RSA key pairs. They do not require the local private key, a manually exported secret, or a running Identity Service. Run them with `mvn test -q` in `financeflow-identity-service` and `./mvnw test -q` here. Cross-application verification is documented as a repeatable manual procedure rather than a coupled cross-repository test: start both applications, register and log in, call a protected FinanceFlow endpoint with the returned token, alter the token signature and confirm `401`, then repeat ownership checks with a second account.

### Key rotation and Kubernetes

The Identity Service publishes the active key plus configured overlap keys through JWKS. Set `JWT_ADDITIONAL_PUBLIC_KEYS` to comma-separated `kid=file:/path/to/public.pem` entries. For rotation, first publish the next key's public PEM under its future `kid` while the current key still signs, then allow FinanceFlow JWKS caches to refresh. Next switch the Identity Service to that private key and new stable `JWT_KEY_ID`, retaining the former public key in the additional-key list. Retain each old public key until all tokens it signed have expired and deployment/cache propagation windows have passed. Removal is an explicit operator action; automated key rotation is not implemented.

For Kubernetes, provide the Identity Service private key through a Kubernetes Secret or external secret manager, mount it read-only, and restrict access with namespace-scoped RBAC. Never bake the key into an image. Expose the JWKS endpoint using the Identity Service ClusterIP Service. Configure FinanceFlow with a stable in-cluster URI such as `http://financeflow-identity-service.<namespace>.svc.cluster.local:8081/.well-known/jwks.json`; it receives only this URI, not the private key or old shared secret. Use TLS outside a trusted cluster network and network policies that permit JWKS access. No production Kubernetes resources are created by this project.

### 5. Run the application

If the repository includes the Maven Wrapper:

```bash
./mvnw spring-boot:run
```

Alternatively, if Maven is installed:

```bash
mvn spring-boot:run
```

The default application URL is:

```text
http://localhost:8080
```

### 6. Open Swagger UI

When the application is running, open:

```text
http://localhost:8080/swagger-ui/index.html
```

Use Swagger UI to explore the available endpoints and test requests.

## API Overview

The following endpoint groups are implemented in the current project.

### Transactions

| Method | Endpoint | Purpose |
|---|---|---|
| POST | `/api/v1/transactions` | Create a transaction |
| GET | `/api/v1/transactions` | Retrieve transactions |
| GET | `/api/v1/transactions/{id}` | Retrieve an owned transaction |
| PUT | `/api/v1/transactions/{id}` | Update an owned transaction |
| DELETE | `/api/v1/transactions/{id}` | Delete an owned transaction |
| POST | `/api/v1/transactions/import` | Import transactions from CSV |

Transaction filtering is supported through the implemented query parameters. Refer to Swagger UI for the current request schemas and exact parameter names.

The transaction-by-ID read, update, and delete operations use both transaction ID and the authenticated subject UUID; another user's record is reported as not found. The separate `GET /api/v1/admin/users/{ownerId}/transactions` endpoint is read-only and requires the verified `ADMIN` role.

### Budgets

| Method | Endpoint | Purpose |
|---|---|---|
| POST | `/api/v1/budgets` | Create a budget |
| GET | `/api/v1/budgets` | Retrieve budgets |
| GET | `/api/v1/budgets/{id}` | Retrieve a budget by ID |
| PUT | `/api/v1/budgets/{id}` | Update a budget |
| DELETE | `/api/v1/budgets/{id}` | Delete a budget |

Budget responses include spending information aggregated from the transaction data.

## Running Tests

Run the test suite using the Maven Wrapper:

```bash
./mvnw test
```

Or, with Maven installed:

```bash
mvn test
```

## Related Project: Identity Service

The separate [FinanceFlow Identity Service](https://github.com/bewidmj/financeflow-identity-service) provides:

- User registration and login.
- Password hashing with BCrypt.
- RS256 JWT access tokens and a public JWKS endpoint.
- Protected user-profile endpoints.
- Stateless request authentication.

Registration, login, and JWT-protected profile access are covered by Identity Service tests. FinanceFlow independently tests RS256/JWKS verification and transaction/budget ownership. A repeatable manual procedure is documented above for verifying login-to-API interoperability across the two separately built applications.

## Roadmap

- [x] Transaction REST API and persistence
- [x] DTO-based request and response handling
- [x] Centralized exception handling
- [x] CSV transaction import
- [x] Transaction filtering
- [x] Budget management and spending aggregation
- [x] Initial automated tests
- [x] Separate Identity Service with JWT authentication
- [ ] Swagger Bearer-token authorization for the Identity Service
- [x] Integrate authentication with FinanceFlow transaction APIs using RS256/JWKS
- [x] Associate transactions and budgets with authenticated users
- [x] Expand validation and automated security tests
- [ ] Database migrations and schema versioning
- [ ] Benchmark and optimize bulk CSV imports using Hibernate JDBC batching
- [ ] Improve deployment and container orchestration
- [ ] Explore event-driven processing with Kafka

## Learning Goals

FinanceFlow is also a practical learning project for understanding:

- REST API design and layered application architecture
- DTOs, Java records, and bean validation
- JPA entity mapping and Hibernate behavior
- Transaction boundaries and `@Transactional`
- PostgreSQL indexing and query performance
- JDBC batching and bulk-import optimization
- Spring Security, JWT, and authentication
- Automated testing, Docker, and deployment

## License

No license has been specified yet.