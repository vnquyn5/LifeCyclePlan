# AI Personal Lifestyle Planner

Foundation repository for a mobile-first AI personal lifestyle planner.

## Repository Structure

- `mobile/` - React Native, TypeScript, Expo foundation.
- `backend/` - Spring Boot modular monolith foundation.
- `ai/` - Provider-agnostic AI gateway and capability structure.
- `infra/` - Local infrastructure placeholders.
- `docs/` - Documentation placeholders.
- `scripts/` - Local setup and Docker helper scripts.

## Technology Stack

- Mobile: React Native, TypeScript, Expo, Expo Router, TanStack Query, Zustand.
- Backend: Java 17, Spring Boot, Maven, Spring Web, Spring Security, Spring Data JPA.
- Database: PostgreSQL.
- Object Storage: S3-compatible storage, MinIO for local development.
- AI: Provider-agnostic gateway structure.

## Prerequisites

- Node.js and npm.
- Java 17+.
- Maven 3.9+.
- Docker and Docker Compose.

## Environment Setup

Copy environment samples before running locally:

```sh
cp .env.example .env
cp mobile/.env.example mobile/.env
cp backend/.env.example backend/.env
cp ai/.env.example ai/.env
```

## Mobile Setup

```sh
cd mobile
npm install
```

## Backend Setup

```sh
cd backend
mvn dependency:resolve
mvn compile
```

## Infrastructure Setup

Local infrastructure is defined in `docker-compose.yml`.

## Docker

```sh
./scripts/start-local.sh
./scripts/stop-local.sh
```

## Development Convention

- Do not commit secrets.
- Keep module boundaries explicit.
- Add business source code only in dedicated feature tasks.
- Keep AI providers configurable and replaceable.

## Next Development Steps

- Define database schema in a separate task.
- Add backend application modules in feature tasks.
- Add mobile routes and screens in feature tasks.
- Define AI provider contracts and prompts in separate AI tasks.
