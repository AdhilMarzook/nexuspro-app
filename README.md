# NexusPro Platform — Backend

## Architecture Overview

```
                              ┌──────────────────┐
        Client (React)  ────► │   API Gateway    │ :8080
                              │  Rate Limit · JWT │
                              └────────┬─────────┘
                                       │  lb://
              ┌─────────────┬──────────┼──────────┬─────────────┐
              ▼             ▼          ▼           ▼             ▼
        user-service  profile-svc  wellbeing  contract-svc  social-svc
           :8081          :8082      :8083        :8084         :8085
              │             │          │            │             │
         Postgres       Postgres    Postgres    Postgres      Postgres
         (users)       (profiles)  (wellbeing) (contracts)    (social)
                                                               Redis
                                   ─────── Kafka ──────────
                           (user.verified, social.post.created, etc.)
```

## Services

| Service | Port | Responsibility |
|---------|------|----------------|
| api-gateway | 8080 | JWT auth, rate limiting, routing, circuit breakers |
| user-service | 8081 | Registration, login, MFA, JWT tokens, password reset |
| profile-service | 8082 | Career Passport, tournaments, certifications, career scoring |
| wellbeing-service | 8083 | Daily check-ins, burnout tracking, AI recommendations |
| contract-service | 8084 | PDF/DOCX upload, Claude AI contract analysis |
| social-service | 8085 | Feed, posts, connections, job board, applications |
| discovery-service | 8086 | AI career assessment, skills translation |
| notification-service | 8087 | Email/push notifications via Kafka consumers |

## Quick Start

### Prerequisites
- Java 21
- Maven 3.9+
- Docker + Docker Compose

### 1. Clone and configure

```bash
git clone <repo>
cd nexuspro

# Copy and fill in environment variables
cp .env.example .env
nano .env  # Fill in all CHANGE_ME values
```

### 2. Generate a secure JWT secret

```bash
openssl rand -base64 64
# Paste output into JWT_SECRET in .env
```

### 3. Start with Docker Compose

```bash
cd docker
docker-compose up -d
```

### 4. Build and run locally (development)

```bash
# Build all modules
mvn clean package -DskipTests

# Start user service only (example)
cd user-service
mvn spring-boot:run
```

## API Reference

### Authentication

All endpoints except `/api/v1/auth/**` require a valid JWT in the Authorization header:
```
Authorization: Bearer <access_token>
```

Access tokens expire in **15 minutes**. Use the refresh token (HttpOnly cookie) to get new ones.

#### Register
```http
POST /api/v1/auth/register
Content-Type: application/json

{
  "email": "player@example.com",
  "username": "nexusplayer",
  "password": "SecureP@ss123!",
  "fullName": "Alex Kim",
  "role": "PLAYER"
}
```

#### Login
```http
POST /api/v1/auth/login
Content-Type: application/json

{
  "email": "player@example.com",
  "password": "SecureP@ss123!",
  "totpCode": "123456"  // Optional: only if MFA enabled
}
```
**Response**: `{ accessToken, tokenType, expiresIn, user }` + HttpOnly cookie with refresh token

#### Refresh Token
```http
POST /api/v1/auth/refresh
// Refresh token sent automatically via cookie
```

#### Logout
```http
POST /api/v1/auth/logout
Authorization: Bearer <token>
// Blocks access token in Redis, deletes refresh token
```

### Career Passport

```http
GET  /api/v1/profiles/me                    // My profile
GET  /api/v1/profiles/{username}            // Public profile
PUT  /api/v1/profiles/me                    // Update my profile
POST /api/v1/tournaments                    // Add tournament result
GET  /api/v1/tournaments?userId=xxx         // Get tournament history
POST /api/v1/certifications                 // Add certification
GET  /api/v1/certifications?userId=xxx      // Get certifications
```

### Contract Analysis

```http
POST /api/v1/contracts/analyse              // Upload PDF/DOCX for analysis
// Multipart form: file + optional context string
// Returns contract ID immediately; analysis completes async (~30s)

GET  /api/v1/contracts/{id}                 // Poll for results
GET  /api/v1/contracts                      // My contract history
DELETE /api/v1/contracts/{id}/text          // GDPR: delete raw text
```

### Wellbeing

```http
POST /api/v1/wellbeing/entries              // Log daily check-in
PUT  /api/v1/wellbeing/entries/{date}       // Update entry
GET  /api/v1/wellbeing/entries              // History (paginated)
GET  /api/v1/wellbeing/summary?days=30      // Aggregated summary + trends
GET  /api/v1/wellbeing/recommendations      // AI-generated recommendations
```

### Social Network

```http
GET  /api/v1/social/feed                    // Personalised feed
GET  /api/v1/social/explore                 // Public posts
POST /api/v1/social/posts                   // Create post
POST /api/v1/social/posts/{id}/like         // Toggle like
POST /api/v1/social/posts/{id}/comments     // Add comment

POST /api/v1/social/connections/request/{userId}  // Send connection request
PATCH /api/v1/social/connections/{id}/respond?accept=true  // Accept/decline
GET  /api/v1/social/connections/pending     // Pending requests
GET  /api/v1/social/connections             // My connections
```

### Job Board

```http
GET  /api/v1/jobs?keyword=coach&jobType=FULL_TIME&remote=true  // Search
GET  /api/v1/jobs/{id}                      // Job details
POST /api/v1/jobs                           // Post a job (ORG_ADMIN/COACH only)
POST /api/v1/jobs/{id}/apply                // Apply for job
GET  /api/v1/jobs/my-applications           // My applications
GET  /api/v1/jobs/{id}/applications         // Applications to my job (recruiter)
PATCH /api/v1/jobs/applications/{id}/status // Update application status
```

## Security Architecture

### Authentication Flow
1. Login → server issues **15-min access token** (JWT body) + **7-day refresh token** (HttpOnly cookie)
2. Client stores access token in **memory only** (not localStorage — XSS safe)
3. Refresh token rotates on every use; reuse detection revokes all sessions
4. On logout: access token blocked in Redis until natural expiry

### Security Controls Implemented
- ✅ **BCrypt** password hashing (cost factor 12)
- ✅ **HS512** JWT signing (stronger than HS256)
- ✅ **Account lockout** — 5 failed attempts → 30-minute lockout
- ✅ **TOTP MFA** — Google Authenticator compatible
- ✅ **Refresh token rotation** with theft detection
- ✅ **JWT blocklist** in Redis for immediate revocation
- ✅ **Rate limiting** per IP (auth) and per user (API) via Redis + Bucket4j
- ✅ **Circuit breakers** — Resilience4j with fallbacks
- ✅ **CORS** locked to specific origins (no wildcard)
- ✅ **HttpOnly + SameSite=Strict** cookies for refresh tokens
- ✅ **Security headers**: HSTS, X-Frame-Options, CSP, Referrer-Policy
- ✅ **SQL injection** prevention — JPA parameterised queries only
- ✅ **XSS prevention** — input sanitisation in all services
- ✅ **File upload security** — magic byte validation, size limits, type allowlist
- ✅ **User enumeration** prevention — same error for wrong email vs wrong password
- ✅ **Constant-time** credential checking
- ✅ **Flyway** migrations — schema never auto-created
- ✅ **SQL logging disabled** in production (no data leaks in logs)
- ✅ **GDPR** — soft delete, right-to-erasure for contract text
- ✅ **Secrets via environment** variables — never hardcoded
- ✅ **OWASP dependency** scanner in build pipeline
- ✅ **Non-root Docker** containers
- ✅ **No stack traces** returned to clients

### Service-to-Service Security
- JWT validated at gateway; downstream services trust `X-User-*` headers
- Raw JWT stripped before forwarding to downstream services
- All inter-service communication within Docker network only

## Environment Variables (Required)

| Variable | Description | How to generate |
|----------|-------------|-----------------|
| `JWT_SECRET` | HS512 signing key (min 64 chars) | `openssl rand -base64 64` |
| `DB_PASSWORD` | PostgreSQL password | `openssl rand -base64 32` |
| `REDIS_PASSWORD` | Redis auth password | `openssl rand -base64 24` |
| `ANTHROPIC_API_KEY` | Claude API key | https://console.anthropic.com |
| `MAIL_PASSWORD` | SMTP password | Your email provider |

## Kafka Topics

| Topic | Published by | Consumed by |
|-------|-------------|-------------|
| `user.email.verification` | user-service | notification-service |
| `user.verified` | user-service | profile-service (creates empty profile) |
| `user.password.reset` | user-service | notification-service |
| `wellbeing.burnout.alert` | wellbeing-service | notification-service |
| `social.post.created` | social-service | notification-service |
| `social.connection.accepted` | social-service | notification-service |
| `jobs.application.received` | social-service | notification-service |
| `profile.tournament.verify_requested` | profile-service | admin workers |

## Production Deployment (AWS)

Refer to `/k8s` directory for Kubernetes manifests:
- EKS cluster with auto-scaling
- RDS PostgreSQL (Multi-AZ) per service
- ElastiCache Redis
- MSK (Managed Kafka)
- ALB with WAF rules
- Secrets in AWS Secrets Manager

## Tech Stack

- **Java 21** + **Spring Boot 3.2**
- **Spring Cloud Gateway** (routing + rate limiting)
- **Spring Security** (auth)
- **Spring Data JPA** + **Hibernate** (ORM)
- **PostgreSQL 16** (each service has its own DB — true microservice isolation)
- **Redis 7** (token blocklist, rate limiting, like tracking)
- **Apache Kafka** (async events)
- **Flyway** (schema migrations)
- **Anthropic Claude API** (contract analysis, wellbeing AI, career matching)
- **Apache PDFBox + POI** (document parsing)
- **Resilience4j** (circuit breakers)
- **Netflix Eureka** (service discovery)
- **Micrometer + Prometheus** (metrics)
- **Passay** (password strength validation)
- **TOTP** (MFA)
- **Bucket4j** (rate limiting)
- **MapStruct** (DTO mapping)
- **Lombok** (boilerplate reduction)
- **Testcontainers** (integration tests with real PostgreSQL)
