# FinanceFlow 💰

**FinanceFlow** is a personal finance and cashflow management platform built with Java, Spring Boot, and PostgreSQL.

The project is being developed as a hands-on backend engineering initiative to explore REST API design, database persistence, testing, scalable application architecture, event-driven processing, and microservices.

## 🎯 Project Goals

- Build clean, maintainable REST APIs using Spring Boot.
- Implement persistence with Spring Data JPA, Hibernate, and PostgreSQL.
- Apply DTOs, input validation, and consistent exception handling.
- Support financial transaction tracking, budgeting, and cashflow analytics.
- Explore asynchronous processing with Apache Kafka.
- Evolve from a modular monolith toward independently deployable services where justified.
- Practice production-oriented testing, observability, and deployment.

## 🛠️ Technology Stack

| Technology | Purpose |
|---|---|
| Java 21 | Backend development |
| Spring Boot | Application framework |
| Spring Web | REST API development |
| Spring Data JPA | Data access and persistence |
| Hibernate | ORM |
| PostgreSQL 16 | Relational database |
| Maven | Build and dependency management |
| Springdoc OpenAPI / Swagger UI | API documentation |
| Git and GitHub | Version control |

## 🏗️ Current Architecture

The application follows a layered architecture.

```text
Client / Swagger UI
        |
        v
    Controller
        |
        v
      Service
        |
        v
    Repository
        |
        v
    PostgreSQL
```

### Layer Responsibilities

- **Controller:** Exposes REST endpoints and handles HTTP requests and responses.
- **Service:** Contains business logic and transaction boundaries.
- **Repository:** Uses Spring Data JPA for database operations.
- **Entity:** Represents the persisted financial transaction.
- **DTOs:** Separate the API request and response contracts from the persistence model.

## ✅ Implemented Features

- Create a financial transaction.
- Retrieve all financial transactions.
- Retrieve a transaction by its ID.
- Persist transactions in PostgreSQL.
- Generate transaction IDs through database identity generation.
- Use request and response DTOs.
- Represent transactions using `INCOME` and `EXPENSE` types.
- Document REST endpoints through Swagger UI.

## 🔌 API Endpoints

Base URL: `http://localhost:8080`

| Method | Endpoint | Purpose |
|---|---|---|
| POST | `/api/v1/transactions` | Create a transaction |
| GET | `/api/v1/transactions` | Retrieve all transactions |
| GET | `/api/v1/transactions/{id}` | Retrieve a transaction by ID |

### Create a Transaction

**Request**

`POST /api/v1/transactions`

```json
{
  "amount": 1250.50,
  "type": "EXPENSE",
  "category": "GROCERIES",
  "description": "Weekly groceries",
  "transactionDate": "2026-10-12"
}
```

**Example response**

```json
{
  "id": 1,
  "amount": 1250.50,
  "type": "EXPENSE",
  "category": "GROCERIES",
  "description": "Weekly groceries",
  "transactionDate": "2026-10-12"
}
```

The database-generated `id` is included in the response.

### Transaction Types

- `INCOME` — Money received.
- `EXPENSE` — Money spent.

### API Documentation

Once the application is running, access Swagger UI at:

`http://localhost:8080/swagger-ui/index.html`

OpenAPI specification:

`http://localhost:8080/v3/api-docs`

## 🚀 Getting Started

### Prerequisites

Install the following:

- Java 21
- PostgreSQL 16
- Git

Maven is included through the Maven Wrapper, so a separate Maven installation is not required to run the project.

### 1. Clone the repository

```bash
git clone https://github.com/bewidmj/financeflow.git
cd financeflow
```

### 2. Create the database

Open PostgreSQL and create the database:

```sql
CREATE DATABASE financeflow_db;
```

Ensure your PostgreSQL user has permission to connect to and use the database.

### 3. Configure local properties

Create `src/main/resources/application-local.properties` and configure your local PostgreSQL connection:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/financeflow_db
spring.datasource.username=your_postgres_username
spring.datasource.password=your_postgres_password
```

Use your own local PostgreSQL credentials.

Ensure the application uses the `local` profile by default or activate it explicitly. Keep local credentials out of version control.

### 4. Run the application

On macOS or Linux:

```bash
chmod +x mvnw
./mvnw spring-boot:run
```

On Windows:

```bat
mvnw.cmd spring-boot:run
```

The application should start on port `8080`, unless configured otherwise.

### 5. Test the API

Open Swagger UI:

`http://localhost:8080/swagger-ui/index.html`

Use the interactive interface to create and retrieve transactions.

## 🧪 Testing

Run the automated test suite with:

```bash
./mvnw test
```

Build the project with:

```bash
./mvnw clean verify
```

## 🗺️ Development Roadmap

The following features are planned for future iterations.

- [ ] Global exception handling and standardized error responses
- [ ] Bean Validation for incoming requests
- [ ] Correct HTTP status codes and resource creation responses
- [ ] Automated unit and integration tests
- [ ] Pagination, sorting, and transaction filtering
- [ ] Category management
- [ ] Budget creation and tracking
- [ ] Monthly income and expense reports
- [ ] CSV import with duplicate detection and idempotency
- [ ] Database schema migrations using Flyway
- [ ] Authentication and authorization
- [ ] Docker-based local development and deployment
- [ ] Kafka-based asynchronous transaction processing
- [ ] Reliable event publishing and consumer retry handling
- [ ] Monitoring, logging, and observability
- [ ] Extract selected components into microservices when justified

## 🧠 Engineering Principles

FinanceFlow is being developed with an emphasis on:

- Separation of concerns and clean architecture.
- Immutable DTOs and explicit API contracts.
- Database consistency and transaction management.
- Input validation and predictable error handling.
- Automated testing and incremental delivery.
- Idempotency and reliability in distributed processing.
- Choosing architectural complexity based on actual requirements.

## 📌 Project Status

**Status:** Active development

The current implementation provides the foundation for financial transaction CRUD operations. Additional business features, production-quality error handling, automated tests, and distributed processing will be introduced incrementally.

---

Built as a hands-on Java backend engineering project.
