# 📋 SeekFactory Backend — Documentation Index

> **Project:** Axoraa · **Stack:** Java 25 · Spring Boot 4.1.1 · PostgreSQL 15+ · JWT · Google OAuth2

---

## Documentation Files

All backend implementation code is split across 4 guide documents. Frontend research is in 2 additional documents.

### Backend Guides (All Code)

| Doc | File | Contents |
|-----|------|----------|
| **Part 1** | [`part1_architecture_entities_repositories.md`](./part1_architecture_entities_repositories.md) | Architecture overview, package structure, `pom.xml` additions, full `application.yaml`, 9 Enums, BaseEntity + AuditingConfig, 14 JPA Entities, 14 Repository interfaces |
| **Part 2** | [`part2_dtos_security_exceptions_services.md`](./part2_dtos_security_exceptions_services.md) | 30+ DTOs (request + response), 6 Custom Exceptions + GlobalExceptionHandler, Security stack (JwtConfig, JwtTokenProvider, JwtAuthenticationFilter, SecurityConfig, CorsConfig), Utility classes (SecurityUtils, SlugUtils, IdGenerator), ModelMapperConfig, OpenApiConfig, 10 Service interfaces, AuthServiceImpl, REST API endpoint map, ER diagram, step-by-step order |
| **Part 3** | [`part3_service_implementations.md`](./part3_service_implementations.md) | 9 remaining ServiceImpl classes: UserServiceImpl, ManufacturerServiceImpl, ProductServiceImpl, ReelServiceImpl, CategoryServiceImpl, CommentServiceImpl, RfqServiceImpl, ConversationServiceImpl, NotificationServiceImpl |
| **Part 4** | [`part4_controllers.md`](./part4_controllers.md) | ManufacturerUpdateRequest DTO, 11 Controller classes: AuthController, FeedController, CategoryController, ManufacturerController, ProductController, CommentController, RfqController, ConversationController, NotificationController, UserController, AdminController |

### Frontend Research & AI Proposal

| Doc | File | Contents |
|-----|------|----------|
| **Web** | [`web_frontend_research.md`](./web_frontend_research.md) | Next.js 15.5 architecture, all routes/pages, component inventory, API contract, auth flow, entity types, feature list |
| **App** | [`app_frontend_research.md`](./app_frontend_research.md) | Expo SDK 57 architecture, all screens/navigation, component inventory, API contract, session management, assets, EAS build config |
| **AI Strategy** | [`ai_integration_proposal.md`](./ai_integration_proposal.md) · [**Overview PDF**](./SeekFactory_AI_Integration_Proposal.pdf) | Architectural analysis, China–India GFW compliance, cloud API vs. GPU server |
| **AI Plans (90k/160k/200k)** | [`ai_integration_plans_90k_160k_200k.md`](./ai_integration_plans_90k_160k_200k.md) · [**Commercial PDF**](./SeekFactory_AI_Integration_Plans_90k_160k_200k.pdf) | **Client 3-Tier Proposal**: Plan 1 (₹90k), Plan 2 (₹160k), Plan 3 (₹200k) across all 8 features |
| **AI Features (Client Ready)** | [`ai_features_simplified.md`](./ai_features_simplified.md) · [**Clean 2-Page PDF**](./SeekFactory_AI_Features_Simplified.pdf) | **Short & Crisp**: Problem, Solution & Examples with Level tags (no prices/jargon) |

### Infrastructure & DevOps

| Doc | File | Contents |
|-----|------|----------|
| **Infrastructure Guide** | [`infrastructure_guide.md`](./infrastructure_guide.md) | Supabase PostgreSQL (dev + prod), Oracle Cloud Always Free setup, Docker, Nginx, SSL, GitHub Actions CI/CD, Spring profiles, common problems & solutions |

### API Testing & QA

| Doc | File | Contents |
|-----|------|----------|
| **Postman Testing Guide** | [`postman_api_testing_guide.md`](./postman_api_testing_guide.md) | Complete Postman testing suite: all 11 controllers, 34+ endpoints, JSON payloads, auto-JWT environment scripts, and negative tests |
| **Postman Collection (Import)** | [`SeekFactory_API_Collection.postman_collection.json`](./SeekFactory_API_Collection.postman_collection.json) | **1-Click Import**: Ready-to-use Postman collection with organized folders, pre-filled JSON payloads, and test scripts |
| **Postman Environment (Import)** | [`SeekFactory_Environment.postman_environment.json`](./SeekFactory_Environment.postman_environment.json) | Pre-configured environment variables (`baseUrl`, `authToken`, `refreshToken`, `userId`, etc.) |

### Developer Onboarding & Workflow

| Doc | File | Contents |
|-----|------|----------|
| **Developer Setup Guide** | [`developer_setup_guide.md`](./developer_setup_guide.md) · [**Team PDF**](./SeekFactory_Developer_Setup_Guide.pdf) | Complete local setup for Web (`SeekFactory-Web-F`) & Backend (`SeekFactory-B`), Git branching from `dev`, `.env` Supabase config, Flyway verification checklist |

## File Count Summary

| Layer | Files | Status |
|-------|-------|--------|
| Enums | 9 | ✅ Complete (Part 1) |
| Config | 6 | ✅ Complete (Part 1 + Part 2) |
| Entities | 15 (Base + 14) | ✅ Complete (Part 1) |
| Repositories | 14 | ✅ Complete (Part 1) |
| DTOs (Request) | 11 | ✅ Complete (Part 2 + Part 4) |
| DTOs (Response) | 16 | ✅ Complete (Part 2) |
| Exceptions | 7 | ✅ Complete (Part 2) |
| Utilities | 3 | ✅ Complete (Part 2) |
| Services (Interface) | 10 | ✅ Complete (Part 2) |
| Services (Impl) | 10 | ✅ Complete (Part 2 + Part 3) |
| Controllers | 11 | ✅ Complete (Part 4) |
| **TOTAL** | **~112** | **✅ 100% Coverage** |

---

## Quick Start Checklist

```
Phase 1:  Create all 9 enums                          → Part 1
Phase 2:  Create BaseEntity + AuditingConfig           → Part 1
Phase 3:  Create all 14 JPA entities                   → Part 1
Phase 4:  Create all 14 repositories                   → Part 1
Phase 5:  Update pom.xml (add 3 dependencies)          → Part 1
Phase 6:  Update application.yaml                      → Part 1
Phase 7:  Create common DTOs (ApiResponse, etc.)       → Part 2
Phase 8:  Create exceptions + GlobalExceptionHandler   → Part 2
Phase 9:  Create utility classes                       → Part 2
Phase 10: Create config classes                        → Part 2
Phase 11: Create auth DTOs                             → Part 2
Phase 12: Create AuthService + AuthServiceImpl         → Part 2
Phase 13: Create AuthController — ★ TEST HERE ★        → Part 4
Phase 14: Create remaining DTOs                        → Part 2 + Part 4
Phase 15: Create remaining services + implementations  → Part 2 + Part 3
Phase 16: Create remaining controllers                 → Part 4
```

---

## Technology Overview

```
┌─────────────────────────────────────────────────────────────┐
│                    SeekFactory Platform                      │
├──────────────┬──────────────────┬───────────────────────────┤
│  Web (Next.js) │  App (Expo RN)  │  Backend (Spring Boot)   │
│  Port: 3000    │  Port: 8081     │  Port: 8080              │
│  TypeScript    │  TypeScript     │  Java 25                 │
│  React 19      │  React Native   │  Spring Boot 4.1.1       │
│  Tailwind CSS  │  expo-blur      │  PostgreSQL 15+          │
│  App Router    │  Expo Router    │  Flyway migrations       │
│  Mock API →    │  Mock API →     │  ← REST API (JWT)        │
│  HTTP API      │  HTTP API       │  ← Swagger UI            │
└──────────────┴──────────────────┴───────────────────────────┘
```
