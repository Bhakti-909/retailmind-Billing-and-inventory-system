# Smart Retail - System Architecture

## 1

–––

. Architecture Overview

Smart Retail follows a Microservices Architecture.

The frontend communicates with backend services through an API Gateway.

```mermaid
flowchart TB

    User["Business Users"]

    Frontend["React Frontend<br/>React + Tailwind CSS"]

    Gateway["API Gateway<br/>Spring Cloud Gateway"]

    Auth["Authentication Service"]
    Inventory["Inventory Service"]
    Billing["Billing Service"]
    Customer["Customer Service"]
    Supplier["Supplier Service"]
    AI["AI Service"]
    Notification["Notification Service"]
    Report["Report Service"]

    PostgreSQL[(PostgreSQL)]

    RabbitMQ["RabbitMQ"]

    Ollama["Ollama Local LLM"]

    User --> Frontend
    Frontend --> Gateway

    Gateway --> Auth
    Gateway --> Inventory
    Gateway --> Billing
    Gateway --> Customer
    Gateway --> Supplier
    Gateway --> AI
    Gateway --> Notification
    Gateway --> Report

    Auth --> PostgreSQL
    Inventory --> PostgreSQL
    Billing --> PostgreSQL
    Customer --> PostgreSQL
    Supplier --> PostgreSQL
    Report --> PostgreSQL

    Inventory --> RabbitMQ
    Billing --> RabbitMQ

    RabbitMQ --> Notification

    AI --> Ollama
```

---

# 2. Architecture Principles

The system follows:

- Separation of Concerns
- Single Responsibility
- Loose Coupling
- High Cohesion
- Independent Service Deployment
- Stateless REST APIs
- Secure Communication
- Database Isolation
- Horizontal Scalability
- Asynchronous Processing
- Multi-Tenant Data Isolation

---

# 3. Frontend Architecture

The frontend is built with:

- React.js
- Tailwind CSS
- Axios
- React Router

Responsibilities:

- UI
- Navigation
- Authentication State
- Dashboard
- Forms
- Tables
- Charts
- API Communication
- AI Chat
- Client-Side Routing

```mermaid
flowchart LR

    React["React Application"]
++
    Components["Components"]

    Pages["Pages"]

    Context["Context"]

    Services["Axios API Services"]

    Routes["React Router"]

    Gateway["API Gateway"]

    React --> Components
    React --> Pages
    React --> Context
    React --> Routes

    Pages --> Services
    Services --> Gateway
```

---

# 4. Frontend Navigation

The main dashboard navigation contains:

```text
Dashboard
Inventory
Billing
Customers
Suppliers
Reports
AI Assistant
Profile
```

Example:

```mermaid
flowchart TD

    Login["Login"]

    Dashboard["Dashboard"]

    Inventory["Inventory"]
    Billing["Billing"]
    Customers["Customers"]
    Suppliers["Suppliers"]
    Reports["Reports"]
    AI["AI Assistant"]
    Profile["Profile"]

    Login --> Dashboard

    Dashboard --> Inventory
    Dashboard --> Billing
    Dashboard --> Customers
    Dashboard --> Suppliers
    Dashboard --> Reports
    Dashboard --> AI
    Dashboard --> Profile
```

---

# 5. API Gateway

Spring Cloud Gateway is the single entry point for backend APIs.

Responsibilities:

- Request Routing
- JWT Validation
- CORS
- Request Filtering
- Rate Limiting
- Service Routing
- Centralized Security

```mermaid
flowchart LR

    Frontend["React Frontend"]

    Gateway["API Gateway"]

    Auth["Auth"]
    Inventory["Inventory"]
    Billing["Billing"]
    Customer["Customer"]
    Supplier["Supplier"]
    AI["AI"]
    Reports["Reports"]
    Notification["Notification"]

    Frontend --> Gateway

    Gateway --> Auth
    Gateway --> Inventory
    Gateway --> Billing
    Gateway --> Customer
    Gateway --> Supplier
    Gateway --> AI
    Gateway --> Reports
    Gateway --> Notification
```

---

# 6. Authentication Service

Responsibilities:

- Registration
- Login
- Logout
- Password Hashing
- JWT Generation
- JWT Validation
- User Management
- Role Management
- Authorization
- Business Registration

Main entities:

```text
Business
User
Role
```

Authentication flow:

```mermaid
sequenceDiagram

    actor User
    participant Frontend
    participant Gateway as API Gateway
    participant Auth as Auth Service
    participant DB as PostgreSQL

    User->>Frontend: Enter credentials
    Frontend->>Gateway: Login Request
    Gateway->>Auth: Forward Request
    Auth->>DB: Find User
    DB-->>Auth: User Data
    Auth->>Auth: Verify Password
    Auth->>Auth: Generate JWT
    Auth-->>Gateway: JWT
    Gateway-->>Frontend: Login Success
    Frontend-->>User: Open Dashboard
```

---

# 7. Inventory Service

The Inventory Service manages:

- Products
- Categories
- Barcode
- Stock
- Batch
- Manufacturing Date
- Expiry Date
- Stock In
- Stock Out
- Stock History
- Low Stock
- Expiry Detection

Main entities:

```text
Product
Category
Batch
Inventory
StockHistory
```

---

# 8. Billing Service

Responsibilities:

- Invoice
- Invoice Items
- GST
- Discount
- Payment
- Invoice History
- Stock Deduction

Billing communicates with Inventory Service.

```mermaid
flowchart LR

    Billing["Billing Service"]

    Feign["OpenFeign / REST"]

    Inventory["Inventory Service"]

    Billing --> Feign
    Feign --> Inventory
```

---

# 9. Customer Service

Responsibilities:

- Customer CRUD
- Customer Search
- Customer Information
- Purchase History
- Invoice History

Entity:

```text
Customer
```

---

# 10. Supplier Service

Responsibilities:

- Supplier CRUD
- Supplier Information
- Purchase Orders
- Purchase Order History
- Supplier Performance

Entities:

```text
Supplier
PurchaseOrder
PurchaseOrderItem
```

---

# 11. Report Service

Responsibilities:

- Daily Sales
- Monthly Sales
- Revenue
- Product Sales
- Inventory Report
- Customer Report
- Supplier Report
- Purchase Report
- Low Stock Report
- Expiry Report
- Dashboard Statistics
- PDF Export
- Excel Export

---

# 12. Notification Service

Notification Service handles asynchronous notifications.

```mermaid
flowchart LR

    Inventory["Inventory Service"]
    Billing["Billing Service"]

    RabbitMQ["RabbitMQ"]

    Notification["Notification Service"]

    Email["Email"]
    WhatsApp["WhatsApp"]
    Dashboard["Dashboard Alert"]

    Inventory --> RabbitMQ
    Billing --> RabbitMQ

    RabbitMQ --> Notification

    Notification --> Email
    Notification --> WhatsApp
    Notification --> Dashboard
```

---

# 13. AI Service

The AI Service is responsible for Business Intelligence.

The AI service uses read-only, fixed SQL tools for tenant-scoped inventory and invoice data. It never accepts SQL from a user. The service combines those live tools with the retrieval and forecast components below.

```mermaid
flowchart TD

    User["User"]

    Chat["AI Chat UI"]

    Router["AI Agent Router"]
    RAG["RAG Search"]
    Knowledge["Retail Guidance"]
    SQL["Tenant-scoped SQL Tools"]
    Live["Live Inventory and Invoice Data"]
    Forecast["Sales Forecast Engine"]
    History["28-day Sales History and Weekday Patterns"]
    Analysis["Business Analysis"]
    Ollama["Ollama LLM"]
    Recommendations["AI Recommendations"]

    User --> Chat
    Chat --> Router
    Router --> RAG
    Router --> SQL
    Router --> Forecast
    RAG --> Knowledge
    SQL --> Live
    Forecast --> History
    RAG --> Analysis
    SQL --> Analysis
    Forecast --> Analysis
    Analysis --> Ollama
    Ollama --> Recommendations
    Recommendations --> Chat
```

---

# 14. AI Business Intelligence

The AI Agent currently handles:

```text
Which products are low in stock?

What were my sales today?

What are the current stock, expiry, or below-cost risks?

What were recent invoice sales and gross profit?

What is the estimated sales total for the next seven days?

Which products may expire soon?

What should I stock before Diwali?
```

The AI Agent follows:

```text
User Question
      |
      v
Agent Router
      |
      v
RAG Search + Read-only SQL Tools + Forecast Engine
      |
      v
Build Tenant-scoped Business Context
      |
      v
Ollama LLM (with a live-data fallback)
      |
      v
Business Answer
```

---

# 15. AI Service Safety and Control

The AI Service controls what information is sent to Ollama.

The service should:

1. Authenticate the user.
2. Identify the user's business.
3. Validate the requested operation.
4. Retrieve only required business data.
5. Remove unnecessary sensitive information.
6. Build structured business context.
7. Send the minimum relevant context to Ollama.
8. Return the generated response.

The AI should not receive unrestricted database access.

---

# 16. Service-to-Service Communication

## REST

Used for synchronous communication.

```text
Billing
   |
   | REST
   v
Inventory
```

## OpenFeign

Used to simplify REST communication between Spring Boot services.

```text
Billing
   |
   v
InventoryClient
   |
   v
Inventory Service
```

## RabbitMQ

Used for asynchronous events.

```text
Inventory
   |
   v
RabbitMQ
   |
   v
Notification
```

---

# 17. Database Architecture

For the college project, PostgreSQL can initially be used as one database with logical separation.

```mermaid
flowchart TB

    PostgreSQL[(PostgreSQL)]

    AuthDB["Authentication Tables"]
    InventoryDB["Inventory Tables"]
    BillingDB["Billing Tables"]
    CustomerDB["Customer Tables"]
    SupplierDB["Supplier Tables"]
    ReportDB["Report Tables"]

    PostgreSQL --> AuthDB
    PostgreSQL --> InventoryDB
    PostgreSQL --> BillingDB
    PostgreSQL --> CustomerDB
    PostgreSQL --> SupplierDB
    PostgreSQL --> ReportDB
```

---

# 18. Production Database Architecture

For production, each service can own its database.

```mermaid
flowchart TB

    Auth["Auth Service"] --> AuthDB[(Auth DB)]

    Inventory["Inventory Service"] --> InventoryDB[(Inventory DB)]

    Billing["Billing Service"] --> BillingDB[(Billing DB)]

    Customer["Customer Service"] --> CustomerDB[(Customer DB)]

    Supplier["Supplier Service"] --> SupplierDB[(Supplier DB)]

    Report["Report Service"] --> ReportDB[(Report DB)]
```

This provides stronger service independence.

---

# 19. Multi-Tenant Architecture

The platform supports multiple businesses.

Every business-related record should contain:

```text
business_id
```

Example:

```text
Business A
business_id = 1

Business B
business_id = 2
```

Data flow:

```mermaid
flowchart TD

    User["Authenticated User"]

    JWT["JWT"]

    Gateway["API Gateway"]

    Service["Microservice"]

    Business["Extract business_id"]

    DB[(Database)]

    User --> JWT
    JWT --> Gateway
    Gateway --> Service
    Service --> Business
    Business --> DB

    DB -->|Only matching business_id data| Service
```

---

# 20. Concurrency Architecture

The application must support multiple users simultaneously.

Example:

```text
User A ----+
           |
User B ----+----> API Gateway
           |
User C ----+
```

For stock updates:

```text
Initial Stock = 10

User A wants 7
User B wants 5
```

Only one transaction should update the stock when required.

Recommended:

- Transactions
- Atomic Updates
- Optimistic Locking
- Pessimistic Locking where required
- Transaction Isolation
- Database Constraints

Example:

```sql
UPDATE inventory
SET available_quantity = available_quantity - :quantity
WHERE product_id = :productId
AND available_quantity >= :quantity;
```

The application should check whether the update was successful.

---

# 21. Billing Transaction Architecture

```mermaid
flowchart TD

    Invoice["Create Invoice"]

    Stock["Check Stock"]

    Amount["Calculate Amount"]

    GST["Calculate GST"]

    Payment["Save Payment"]

    SaveInvoice["Save Invoice"]

    Reduce["Reduce Stock"]

    Commit["Commit Transaction"]

    Rollback["Rollback Transaction"]

    Invoice --> Stock

    Stock --> Amount
    Amount --> GST
    GST --> Payment
    Payment --> SaveInvoice
    SaveInvoice --> Reduce
    Reduce --> Commit

    Stock -. Failure .-> Rollback
    Payment -. Failure .-> Rollback
    SaveInvoice -. Failure .-> Rollback
    Reduce -. Failure .-> Rollback
```

---

# 22. Scalability

Services can be horizontally scaled.

```mermaid
flowchart TB

    Gateway["API Gateway"]

    B1["Billing Instance 1"]
    B2["Billing Instance 2"]
    B3["Billing Instance 3"]

    Gateway --> B1
    Gateway --> B2
    Gateway --> B3
```

The same pattern can be applied to:

- Inventory
- AI
- Reports
- Notifications

---

# 23. Caching

Redis can be added in the future.

Potential cached information:

- Products
- Categories
- Dashboard Statistics
- Frequently Requested Reports
- Permissions

```mermaid
flowchart LR

    Service["Service"]

    Redis["Redis Cache"]

    DB[(PostgreSQL)]

    Service --> Redis

    Redis -->|Cache Hit| Service

    Redis -->|Cache Miss| DB

    DB --> Redis
```

---

# 24. Security Architecture

```mermaid
flowchart TB

    Frontend["React Frontend"]

    HTTPS["HTTPS"]

    Gateway["API Gateway"]

    JWT["JWT Validation"]

    Security["Spring Security"]

    Role["Role Validation"]

    Tenant["Business ID Validation"]

    DB[(PostgreSQL)]

    Frontend --> HTTPS
    HTTPS --> Gateway
    Gateway --> JWT
    JWT --> Security
    Security --> Role
    Role --> Tenant
    Tenant --> DB
```

---

# 25. Deployment Architecture

```mermaid
flowchart TB

    Internet["Internet"]

    Vercel["Vercel<br/>React Frontend"]

    Gateway["API Gateway"]

    Services["Microservices"]

    PostgreSQL[(PostgreSQL)]

    RabbitMQ["RabbitMQ"]

    Ollama["Ollama Local LLM"]

    Internet --> Vercel
    Vercel --> Gateway

    Gateway --> Services

    Services --> PostgreSQL

    Services --> RabbitMQ

    RabbitMQ --> Services

    Services --> Ollama
```

---

# 26. Production Architecture

```mermaid
flowchart TB

    Internet["Internet"]

    LoadBalancer["Load Balancer"]

    Gateway["API Gateway"]

    Auth["Auth Instances"]
    Inventory["Inventory Instances"]
    Billing["Billing Instances"]
    Customer["Customer Instances"]
    Supplier["Supplier Instances"]
    AI["AI Instances"]
    Report["Report Instances"]

    PostgreSQL[(PostgreSQL)]
    RabbitMQ["RabbitMQ Cluster"]

    Ollama["Ollama Local LLM"]

    Internet --> LoadBalancer
    LoadBalancer --> Gateway

    Gateway --> Auth
    Gateway --> Inventory
    Gateway --> Billing
    Gateway --> Customer
    Gateway --> Supplier
    Gateway --> AI
    Gateway --> Report

    Auth --> PostgreSQL
    Inventory --> PostgreSQL
    Billing --> PostgreSQL
    Customer --> PostgreSQL
    Supplier --> PostgreSQL
    Report --> PostgreSQL

    Inventory --> RabbitMQ
    Billing --> RabbitMQ

    AI --> Ollama
```

---

# 27. Observability

Future production deployment should include:

- Spring Boot Actuator
- Centralized Logging
- Metrics
- Health Checks
- Error Monitoring
- Distributed Tracing
- Prometheus
- Grafana
- OpenTelemetry

---

# 28. Recommended Development Order

```text
Phase 1
Authentication
JWT
Roles
Dashboard

        ↓

Phase 2
Products
Categories
Inventory
Stock
Batch
Expiry

        ↓

Phase 3
Billing
Invoice
GST
Payment
Stock Deduction

        ↓

Phase 4
Customers
Suppliers
Purchase Orders

        ↓

Phase 5
Reports
PDF
Excel
Dashboard Analytics

        ↓

Phase 6
AI Service
Ollama
AI Chatbot
Business Intelligence

        ↓

Phase 7
RabbitMQ
Notifications
Alerts

        ↓

Phase 8
Docker
Caching
Concurrency
Monitoring
Scalability
```

---

# 29. Architecture Summary

```mermaid
flowchart TB

    User["User"]

    Frontend["React Frontend"]

    Gateway["API Gateway"]

    Auth["Authentication"]
    Inventory["Inventory"]
    Billing["Billing"]
    Customer["Customers"]
    Supplier["Suppliers"]
    Report["Reports"]
    AI["AI"]
    Notification["Notifications"]

    DB[(PostgreSQL)]

    RabbitMQ["RabbitMQ"]

    Ollama["Ollama Local LLM"]

    User --> Frontend
    Frontend --> Gateway

    Gateway --> Auth
    Gateway --> Inventory
    Gateway --> Billing
    Gateway --> Customer
    Gateway --> Supplier
    Gateway --> Report
    Gateway --> AI
    Gateway --> Notification

    Auth --> DB
    Inventory --> DB
    Billing --> DB
    Customer --> DB
    Supplier --> DB
    Report --> DB

    Inventory --> RabbitMQ
    Billing --> RabbitMQ
    RabbitMQ --> Notification

    AI --> Ollama
```
