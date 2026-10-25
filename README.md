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

FinanceFlow requires `JWT_SECRET` in its environment. Set it to the same Base64-encoded secret used by the Identity Service; it must decode to exactly 48 bytes. Do not generate a separate key for FinanceFlow or store the secret in source control. The application verifies HS384 bearer tokens locally and does not call the Identity Service for each request. Swagger/OpenAPI documentation remains public for development; API endpoints require a valid bearer token.

Transactions are associated with the authenticated JWT subject. When ownership is first added, existing database rows are retained with no owner and are excluded from user queries. They are not automatically assigned to an account. Before making the owner column mandatory, backfill only from a verified transaction-to-user mapping (or explicitly archive/remove legacy rows), then apply a versioned migration and the non-null constraint.

Budgets are also scoped to the JWT subject, and their spending totals include only that user's transactions. Existing budgets remain unassigned and are excluded from user-facing budget queries; they are never assigned to an arbitrary account. Because this project does not yet run a migration framework, apply `src/main/resources/db/manual/V1__budget_user_ownership.sql` to an existing PostgreSQL database before deploying this change. It adds the nullable owner column and replaces the old global category/month unique constraint with a user-scoped constraint. Backfill legacy budgets only from a verified budget-to-user mapping; otherwise leave them unassigned or archive them before making ownership mandatory in a future versioned migration.

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
| POST | `/api/v1/transactions/import` | Import transactions from CSV |

Transaction filtering is supported through the implemented query parameters. Refer to Swagger UI for the current request schemas and exact parameter names.

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

The separate [FinanceFlow Identity Service](https://github.com/bewidmj/financeflow-identity-service) is being developed to provide:

- User registration and login.
- Password hashing with BCrypt.
- JWT access tokens.
- Protected user-profile endpoints.
- Stateless request authentication.

Registration, login, and JWT-protected profile access have been tested independently. Integration between the Identity Service and the main FinanceFlow transaction APIs remains part of the development roadmap.

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
- [ ] Integrate authentication with FinanceFlow transaction APIs
- [ ] Associate transactions with authenticated users
- [ ] Expand validation and automated security tests
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