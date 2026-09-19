# 🚀 SeekFactory — Developer Setup & Git Workflow Guide

> **Official Team PDF Document:** [SeekFactory_Developer_Setup_Guide.pdf](./SeekFactory_Developer_Setup_Guide.pdf)

---

## Tech Stack Overview
* **Backend:** Java 25 (with --enable-preview) · Spring Boot 4.1.1 · PostgreSQL 17 (Supabase) · Flyway · Maven
* **Web Frontend:** Next.js 15 (React 19) · TypeScript · Tailwind CSS
* **Mobile App:** Expo SDK 57 · React Native · TypeScript *(Mobile setup covered in next phase)*

---

## 1. Prerequisites (Install Before Starting)
Make sure every developer has installed the following on their laptop:
1. **Git:** [git-scm.com](https://git-scm.com/)
2. **Node.js (v20+ LTS):** [nodejs.org](https://nodejs.org/)
3. **Java 25 JDK (Eclipse Temurin / OpenJDK 25.0.4.1+):** [adoptium.net](https://adoptium.net/) or via SDKMAN (sdk install java 25-tem).
4. **IDE:** 
   * **IntelliJ IDEA** (Community or Ultimate) — *Strongly recommended for Spring Boot*
   * Or **VS Code** with *Extension Pack for Java* and *Spring Boot Extension Pack*.
5. **Postman:** [postman.com/downloads](https://www.postman.com/downloads/) for testing API endpoints.

---

## 2. Web Frontend Setup (SeekFactory-Web-F)

### Repository Link:
https://github.com/Axoraa-tech/SeekFactory-Web-F.git

### Step 2.1: Clone and Switch to dev Branch
`ash
git clone https://github.com/Axoraa-tech/SeekFactory-Web-F.git
cd SeekFactory-Web-F
git checkout dev
git pull origin dev
`

### Step 2.2: Install Dependencies & Configure Environment
`ash
npm install
`
Create a file named **.env.local** in the root of SeekFactory-Web-F:
`env
NEXT_PUBLIC_API_URL=http://localhost:8080
`

### Step 2.3: Run the Web Frontend
`ash
npm run dev
`
Open your browser at: **http://localhost:3000**

### Step 2.4: Frontend Git Workflow (Branch ➔ Commit ➔ Push ➔ PR)
> 🚫 **Golden Rule:** NEVER push directly to dev or main. Always create a new feature branch from dev!
`ash
# 1. Update dev branch
git checkout dev
git pull origin dev

# 2. Create feature branch
git checkout -b feature/your-feature-name

# 3. Stage and commit changes
git add .
git commit -m "feat: implement product category filter on catalog page"

# 4. Push to remote repository
git push -u origin feature/your-feature-name

# 5. Open Pull Request on GitHub:
# Set BASE branch to 'dev' (base: dev ⬅️ compare: feature/your-feature-name)
`

---

## 3. Backend Setup (SeekFactory-B)

### Repository Link:
https://github.com/Axoraa-tech/SeekFactory-B.git

### Step 3.1: Clone and Switch to dev Branch
`ash
git clone https://github.com/Axoraa-tech/SeekFactory-B.git
cd SeekFactory-B
git checkout dev
git pull origin dev
`

### Step 3.2: Critical Rules on Configuration Files
> ⚠️ **IMPORTANT CONFIGURATION RULE:**
> 1. src/main/resources/application.yaml is provided by default. **DO NOT TOUCH OR MODIFY pplication.yaml!**
> 2. You must **CREATE A NEW FILE** named: **src/main/resources/application-dev.yaml**

Paste the following into src/main/resources/application-dev.yaml:
`yaml
spring:
  datasource:
    url: 
    username: 
    password: 
    driver-class-name: org.postgresql.Driver
    hikari:
      maximum-pool-size: 3
      minimum-idle: 1
      idle-timeout: 300000
      connection-timeout: 20000
      max-lifetime: 1200000

  jpa:
    show-sql: true

app:
  cors:
    allowed-origins:
      - http://localhost:3000
      - http://localhost:8081
`

### Step 3.3: Configure the Root .env File
Create a file named **.env** in the root of SeekFactory-B (ensure it is in .gitignore):
`env
# ============================================================
# SPRING BOOT PROFILE
# ============================================================
SPRING_PROFILES_ACTIVE=dev

# ============================================================
# DEVELOPMENT DATABASE - SUPABASE (Used for local dev)
# ============================================================
DEV_DB_URL=jdbc:postgresql://aws-0-ap-northeast-1.pooler.supabase.com:6543/postgres
DEV_DB_USERNAME=postgres.vvjeqfektsfrfunksslp
DEV_DB_PASSWORD=Axoraa@1967@

# ============================================================
# PRODUCTION DATABASE - SUPABASE (Used ONLY on cloud server)
# ============================================================
PROD_DB_URL=jdbc:postgresql://aws-0-ap-southeast-1.pooler.supabase.com:6543/postgres
PROD_DB_USERNAME=
PROD_DB_PASSWORD=

# ============================================================
# JWT SECURITY KEY & GOOGLE OAUTH
# ============================================================
JWT_SECRET=dGhpc2lzYXZlcnlzZWN1cmVhbmRsb25namd3dHNlY3JldGtleWZvcnNlZWtmYWN0b3J5MjAyNgKK
GOOGLE_CLIENT_ID=YOUR_GOOGLE_OAUTH_CLIENT_ID
`

### Step 3.4: Run the Backend Locally
* **Terminal (Windows):** .\mvnw.cmd clean compile then .\mvnw.cmd spring-boot:run
* **Terminal (Mac/Linux):** chmod +x mvnw && ./mvnw clean compile && ./mvnw spring-boot:run
* **IntelliJ IDEA:** Set Project SDK to Java 25. Open AxoraaApplication.java. Add VM options: --enable-preview and Active Profile: dev. Click Run.

---

## 4. Console Verification Checklist (Startup Logs)

When running the application, developers must observe the following actual startup sequence:

`	ext
  .   ____          _            __ _ _
 /\\ / ___'_ __ _ _(_)_ __  __ _ \ \ \ \
( ( )\___ | '_ | '_| | '_ \/ _ | \ \ \ \
 \\/  ___)| |_)| | | | | || (_| |  ) ) ) )
  '  |____| .__|_| |_|_| |_\__, | / / / /
 =========|_|==============|___/=/_/_/_/
 :: Spring Boot ::                (v4.1.1)

INFO ... Starting AxoraaApplication using Java 25.0.4.1 ...
INFO ... The following 1 profile is active: "dev"
INFO ... Finished Spring Data repository scanning. Found 14 JPA repository interfaces.
INFO ... Tomcat initialized with port 8080 (http)
INFO ... HikariPool-1 - Added connection org.postgresql.jdbc.PgConnection@...
INFO ... HikariPool-1 - Start completed.
INFO ... org.flywaydb.core.FlywayExecutor : Database: jdbc:postgresql://aws-0-ap-northeast-1.pooler.supabase.com:6543/postgres (PostgreSQL 17.6)
INFO ... o.f.core.internal.command.DbValidate : Successfully validated 1 migration (execution time 00:00.772s)
INFO ... o.f.core.internal.command.DbMigrate  : Current version of schema "public": 1
INFO ... o.f.core.internal.command.DbMigrate  : Schema "public" is up to date. No migration necessary.
INFO ... Initialized JPA EntityManagerFactory for persistence unit 'default'
INFO ... Tomcat started on port 8080 (http) with context path '/'
INFO ... Started AxoraaApplication in 25.595 seconds
`

### Developer Verification Matrix:
| Item | Expected Console Message | Status |
|---|---|:---:|
| **1. Active Profile** | The following 1 profile is active: "dev" | ✅ MUST SEE |
| **2. HikariCP Pool** | HikariPool-1 - Added connection ... Start completed. | ✅ MUST SEE |
| **3. Database Version** | Database: jdbc:postgresql://...pooler.supabase.com:6543/postgres (PostgreSQL 17.6) | ✅ MUST SEE |
| **4. Flyway Migrations** | Successfully validated 1 migration, Schema "public" is up to date. | ✅ MUST SEE |
| **5. Tomcat Port** | Tomcat started on port 8080 (http) with context path '/' | ✅ MUST SEE |
| **6. NO Table Alters** | **You should NEVER see Hibernate executing ALTER TABLE or CREATE TABLE!** | 🚫 MUST NOT SEE |

> 🚨 **Why must developers NEVER see ALTER TABLE in the logs?**  
> We use **Flyway Database Migrations** with hibernate.ddl-auto: validate. Hibernate is strictly forbidden from modifying cloud database tables directly. If schema changes are needed, write a new migration file (e.g., V2__add_column.sql) in src/main/resources/db/migration/.

### Quick Links:
* **Health Check:** [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health) ➔ {"status":"UP"}
* **Interactive Swagger UI:** [http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html)

---

## 5. API Testing in Postman (Automated Setup)
1. Open **Postman** ➔ Click **Import** ➔ Drag and drop both SeekFactory_API_Collection.postman_collection.json and SeekFactory_Environment.postman_environment.json.
2. In the top-right environment dropdown, select: **SeekFactory - Local / Cloud**.
3. Run ** 0. Health & Docs ➔ Actuator Health Check** (200 OK, {"status":"UP"}).
4. Run ** 1. Authentication ➔ Register Buyer** (201 Created). The test script automatically saves the JWT ccess_token into {{authToken}}.
5. Run ** 2. User Profile ➔ Get User Profile** — it authenticates automatically!

---

## 6. Developer Quick-Reference Summary

| Action | Web Frontend (SeekFactory-Web-F) | Backend (SeekFactory-B) |
|---|---|---|
| **Git Clone** | git clone https://github.com/Axoraa-tech/SeekFactory-Web-F.git | git clone https://github.com/Axoraa-tech/SeekFactory-B.git |
| **Base Branch** | git checkout dev | git checkout dev |
| **Config Files** | Create .env.local (NEXT_PUBLIC_API_URL=http://localhost:8080) | Keep pplication.yaml untouched; create pplication-dev.yaml & .env |
| **Active Profile** | N/A | dev (SPRING_PROFILES_ACTIVE=dev) |
| **Cloud Database** | N/A | Supabase PostgreSQL 17 (PgBouncer port 6543) |
| **Migrations** | N/A | Flyway auto-applies V1__init_schema.sql on startup |
| **Run Command** | 
pm run dev (http://localhost:3000) | .\mvnw.cmd spring-boot:run (http://localhost:8080) |
| **PR Target** | Always target **dev** | Always target **dev** |
