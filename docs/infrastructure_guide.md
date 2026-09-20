# SeekFactory Infrastructure Guide — Supabase + Oracle Cloud (Industry-Level)

> **Goal:** Host the Spring Boot backend on Oracle Cloud (free forever) and the PostgreSQL database on Supabase (free tier), with separate dev and production environments.

---

## Table of Contents

1. [Architecture Overview](#1-architecture-overview)
2. [Why This Stack?](#2-why-this-stack)
3. [Part A: Supabase PostgreSQL Setup](#part-a-supabase-postgresql-setup)
4. [Part B: Oracle Cloud (OCI) Backend Server](#part-b-oracle-cloud-oci-backend-server)
5. [Part C: Connecting Everything Together](#part-c-connecting-everything-together)
6. [Part D: CI/CD with GitHub Actions](#part-d-cicd-with-github-actions)
7. [Part E: Common Problems & Solutions](#part-e-common-problems--solutions)

---

## 1. Architecture Overview

```
┌──────────────────────────────────────────────────────────────────────────┐
│                        PRODUCTION FLOW                                  │
│                                                                         │
│  [ Vercel ]  ──HTTPS──▶  [ Oracle Cloud VM ]  ──JDBC──▶  [ Supabase ]  │
│   Next.js                  Nginx → Spring Boot             PostgreSQL   │
│   Port 443                 Port 443 → 8080                 (PROD DB)    │
│                                                                         │
├──────────────────────────────────────────────────────────────────────────┤
│                        DEVELOPMENT FLOW                                 │
│                                                                         │
│  [ localhost:3000 ]  ──HTTP──▶  [ Oracle Cloud VM ]  ──JDBC──▶  [ Supabase ]
│   Next.js Dev                   Same VM, Port 8080               (DEV DB)
│                                                                         │
│  OR                                                                     │
│  [ localhost:3000 ]  ──HTTP──▶  [ localhost:8080 ]  ──JDBC──▶  [ Supabase ]
│                                  Local Spring Boot               (DEV DB)
└──────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Why This Stack?

### Why Supabase for Database?

| Reason | Explanation |
|--------|-------------|
| **No local PostgreSQL installation** | Your 7 developers don't need to install PostgreSQL on their laptops. Everyone connects to the same cloud database URL. |
| **Web Dashboard** | Supabase has a built-in table editor (like phpMyAdmin). Developers can view/edit data in the browser without installing pgAdmin or DBeaver. |
| **pgvector ready** | When you build AI features later, `pgvector` extension is pre-installed on Supabase. No extra setup needed. |
| **Free tier = 2 projects** | Supabase free tier allows 2 active projects. Perfect for 1 DEV database + 1 PROD database. |

### Why Oracle Cloud for Backend?

| Reason | Explanation |
|--------|-------------|
| **24 GB RAM for free** | Spring Boot needs ~400MB RAM. Render gives you 512MB (barely enough). Oracle gives you up to 24GB. You'll never crash. |
| **Never sleeps** | Render's free tier sleeps after 15 minutes of no traffic (40-60 second cold starts). Oracle's VM runs 24/7/365. |
| **Real DevOps experience** | Before moving to AWS, your team learns SSH, Linux, Docker, Nginx, firewall rules, and CI/CD — all skills required at any company. |
| **Free forever** | Oracle's "Always Free" tier doesn't expire. It's not a 12-month trial. It's permanently free. |

### Why NOT a single Supabase project with 2 schemas?

You might think: *"Can I create 1 Supabase project with a `dev` schema and a `prod` schema?"*

**Don't do this.** Here's why:

1. **One developer runs a bad migration** → It breaks both dev AND prod because they share the same database server.
2. **Connection pool contamination** → Dev queries slow down prod queries because they share the same PostgreSQL process and connection limits.
3. **Accidental data leaks** → A developer accidentally queries `prod.users` instead of `dev.users` and sees real customer data.
4. **Flyway confusion** → Flyway tracks migrations per database, not per schema. Managing 2 schemas in 1 database with Flyway is painful.

**Industry standard:** Always use **completely separate database instances** for dev and prod. Supabase free tier gives you 2 projects — use them.

---

## Part A: Supabase PostgreSQL Setup

### Step 1: Create Your Supabase Account

1. Go to [https://supabase.com](https://supabase.com)
2. Click **"Start your project"** → Sign in with **GitHub** (recommended, since your code is on GitHub)
3. You'll land on the Supabase Dashboard

### Step 2: Create the DEV Database Project

1. Click **"New Project"**
2. Fill in:
   - **Organization:** Select your default org (or create "SeekFactory")
   - **Project Name:** `seekfactory-dev`
   - **Database Password:** Generate a strong password → **SAVE THIS PASSWORD IMMEDIATELY** (you cannot retrieve it later!)
   - **Region:** Select **Southeast Asia (Singapore)** — closest to both India and China
   - **Pricing Plan:** Free
3. Click **"Create new project"**
4. Wait ~2 minutes for the database to provision

### Step 3: Create the PROD Database Project

1. Repeat the exact same steps but with:
   - **Project Name:** `seekfactory-prod`
   - **Database Password:** A DIFFERENT strong password
   - **Region:** Same region (Singapore)

### Step 4: Get Your Connection Strings

This is where most beginners make mistakes. Supabase gives you **3 types of connection strings**. Understanding the difference is critical:

Go to **Project Settings → Database** in each project.

#### Connection Type 1: Direct Connection (Port 5432)

```
postgresql://postgres.[project-ref]:[password]@aws-0-ap-southeast-1.pooler.supabase.com:5432/postgres
```

- **What it is:** A direct TCP connection to the PostgreSQL server.
- **When to use:** One-time tasks like running Flyway migrations manually from your laptop.
- **DO NOT use for your Spring Boot app** — it bypasses connection pooling and will exhaust the free tier's 60 connection limit.

#### Connection Type 2: Transaction Mode Pooler (Port 6543) ✅ USE THIS

```
postgresql://postgres.[project-ref]:[password]@aws-0-ap-southeast-1.pooler.supabase.com:6543/postgres
```

- **What it is:** Connections go through **PgBouncer** (a connection pooler). PgBouncer maintains a small pool of real database connections and multiplexes your app's requests through them.
- **When to use:** **THIS is what your Spring Boot `application.yaml` should use.**
- **Why:** Your 7 developers might all run Spring Boot locally, each opening 5 connections. That's 35 connections. PgBouncer multiplexes them into ~10 real connections, preventing "too many connections" errors.

#### Connection Type 3: Session Mode Pooler (Port 5432 with pooler domain)

- **When to use:** Only for tools that need persistent sessions (like pgAdmin). You don't need this for Spring Boot.

> [!IMPORTANT]
> **Always use the Transaction Mode Pooler (Port 6543) connection string in your Spring Boot app.**

### Step 5: Configure Spring Boot Profiles

Create separate config files for dev and prod. This is the industry standard — **NEVER hardcode database credentials in your code**.

#### `application.yaml` (Base — shared config)

```yaml
spring:
  application:
    name: axoraa

  # JPA / Hibernate
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
    open-in-view: false
    properties:
      hibernate:
        format_sql: true
        default_batch_fetch_size: 16
        dialect: org.hibernate.dialect.PostgreSQLDialect

  # Flyway
  flyway:
    enabled: true
    baseline-on-migrate: true
    locations: classpath:db/migration

  # Jackson
  jackson:
    serialization:
      write-dates-as-timestamps: false
    default-property-inclusion: non_null
    property-naming-strategy: SNAKE_CASE

# App custom properties
app:
  jwt:
    secret: ${JWT_SECRET}
    access-token-expiry: 86400000
    refresh-token-expiry: 604800000
    issuer: seekfactory-axoraa

# Server
server:
  port: 8080
  error:
    include-message: always
    include-binding-errors: always

# Actuator
management:
  endpoints:
    web:
      exposure:
        include: health,info
  endpoint:
    health:
      show-details: when_authorized

# Swagger
springdoc:
  api-docs:
    path: /api-docs
  swagger-ui:
    path: /swagger-ui.html
```

#### `application-dev.yaml` (Dev-specific overrides)

```yaml
spring:
  datasource:
    url: ${DEV_DB_URL}
    username: ${DEV_DB_USERNAME}
    password: ${DEV_DB_PASSWORD}
    hikari:
      maximum-pool-size: 3        # Free tier: keep this LOW (max 3-5)
      minimum-idle: 1
      connection-timeout: 20000

  jpa:
    show-sql: true                # Show SQL in dev for debugging

app:
  cors:
    allowed-origins:
      - http://localhost:3000
      - http://localhost:8081
```

#### `application-prod.yaml` (Prod-specific overrides)

```yaml
spring:
  datasource:
    url: ${PROD_DB_URL}
    username: ${PROD_DB_USERNAME}
    password: ${PROD_DB_PASSWORD}
    hikari:
      maximum-pool-size: 5        # Free tier: keep conservative
      minimum-idle: 2
      connection-timeout: 20000

  jpa:
    show-sql: false               # NEVER show SQL in production

app:
  cors:
    allowed-origins:
      - https://seekfactory.com
      - https://www.seekfactory.com
```

> [!WARNING]
> **Why `maximum-pool-size: 3` for dev?**
> Supabase free tier allows ~60 total connections. If 7 developers each run Spring Boot locally with `pool-size: 10` (the default), that's 70 connections — exceeding the limit. Everyone's app will crash with `FATAL: too many connections`. Keep it at 3-5 per developer.

#### How to Activate a Profile

When running locally:
```bash
# Run with dev profile
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# Or set as environment variable
export SPRING_PROFILES_ACTIVE=dev
./mvnw spring-boot:run
```

On the Oracle Cloud server:
```bash
# Set in Docker environment
SPRING_PROFILES_ACTIVE=prod
```

### Step 6: Set Environment Variables Locally

Each developer creates a `.env` file in the project root (this file is `.gitignore`'d — **NEVER commit it**):

```bash
# .env (add this to .gitignore!)
DEV_DB_URL=jdbc:postgresql://aws-0-ap-southeast-1.pooler.supabase.com:6543/postgres
DEV_DB_USERNAME=postgres.xxxxxxxxxxxx
DEV_DB_PASSWORD=your-dev-database-password
JWT_SECRET=your-256-bit-random-secret-minimum-32-characters-long
SPRING_PROFILES_ACTIVE=dev
```

Add to `.gitignore`:
```
.env
application-dev.yaml    # Optional: some teams gitignore this too
```

### Step 7: Run Flyway Migrations

When any developer starts the app with the dev profile, Flyway will automatically run all pending migrations from `src/main/resources/db/migration/` against the DEV Supabase database.

For the PROD database, migrations run automatically when the Docker container starts on Oracle Cloud with `SPRING_PROFILES_ACTIVE=prod`.

> [!IMPORTANT]
> **Migration discipline:** Once a migration file (e.g., `V1__init_schema.sql`) is deployed to production, **NEVER modify it**. Always create a new migration file (e.g., `V2__add_column.sql`). Modifying an existing migration causes Flyway checksum errors and deployment failures.

---

## Part B: Oracle Cloud (OCI) Backend Server

### Step 1: Create Your OCI Account

1. Go to [https://www.oracle.com/cloud/free/](https://www.oracle.com/cloud/free/)
2. Click **"Start for free"**
3. Fill in your details:
   - **Home Region:** Select **India South (Hyderabad)** or **AP Mumbai** — you CANNOT change this later!
   - **Account Type:** Individual (your credit card is required for identity verification but will NOT be charged)
4. Complete verification (email + phone + card)
5. Wait for account activation (~5-15 minutes)

> [!IMPORTANT]
> **Choose your Home Region carefully!** This cannot be changed after account creation. Select the region closest to your team (India South: Hyderabad is recommended for Indian developers).

### Step 2: Create an SSH Key Pair (On Your Windows Laptop)

You need an SSH key to securely connect to the cloud server. Open PowerShell:

```powershell
# Generate SSH key pair
ssh-keygen -t ed25519 -C "seekfactory-oci" -f $HOME\.ssh\oci_seekfactory

# This creates two files:
#   ~/.ssh/oci_seekfactory       (PRIVATE key — NEVER share this)
#   ~/.ssh/oci_seekfactory.pub   (PUBLIC key — upload this to Oracle)

# View the public key (you'll paste this into OCI)
cat $HOME\.ssh\oci_seekfactory.pub
```

**Why SSH keys instead of passwords?**
- Passwords can be brute-forced. SSH keys use 256-bit cryptography.
- Industry standard: No production server should allow password-based SSH login.

### Step 3: Create the Always Free VM Instance

1. In the OCI Console, click **"Create a VM instance"** (or go to Compute → Instances → Create Instance)
2. Configure:
   - **Name:** `seekfactory-backend`
   - **Compartment:** Default (root)
   - **Image:** **Ubuntu 22.04 Minimal** (under "Change image" → Ubuntu → 22.04)
   - **Shape:** Click **"Change shape"** → **Ampere** (ARM) → **VM.Standard.A1.Flex**
     - **OCPUs:** 2 (free tier allows up to 4)
     - **Memory:** 12 GB (free tier allows up to 24 GB)
   - **Networking:** Use default VCN or create new one. Ensure **"Assign a public IPv4 address"** is selected
   - **SSH keys:** Choose **"Paste public keys"** → Paste the contents of your `oci_seekfactory.pub` file
3. Click **"Create"**
4. Wait ~60 seconds. Note down the **Public IP Address** (e.g., `129.154.xx.xx`)

> [!TIP]
> **Why ARM (Ampere) and not AMD/Intel?**
> Oracle's Always Free tier gives you **4 ARM OCPUs + 24 GB RAM** for free, but only **1/8 OCPU + 1 GB RAM** for AMD/Intel. ARM is 24x more powerful for free. Java 25 runs perfectly on ARM.

### Step 4: SSH Into Your Server

From PowerShell:

```powershell
ssh -i $HOME\.ssh\oci_seekfactory ubuntu@129.154.xx.xx
```

You should see a Ubuntu terminal prompt. You're now inside your cloud server.

### Step 5: Initial Server Setup (Security Hardening)

Run these commands on the server:

```bash
# Update all packages
sudo apt update && sudo apt upgrade -y

# Set timezone to India
sudo timedatectl set-timezone Asia/Kolkata

# Install essential tools
sudo apt install -y curl wget git unzip htop net-tools ufw fail2ban

# ─── FIREWALL SETUP ───────────────────────────────────
# Allow SSH (port 22), HTTP (80), HTTPS (443), and Spring Boot (8080)
sudo ufw allow 22/tcp
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw allow 8080/tcp
sudo ufw enable
sudo ufw status

# ─── FAIL2BAN (Blocks brute-force SSH attacks) ───────
sudo systemctl enable fail2ban
sudo systemctl start fail2ban

# ─── SWAP FILE (Prevents out-of-memory crashes) ──────
# Even with 12GB RAM, a swap file prevents rare OOM kills
sudo fallocate -l 2G /swapfile
sudo chmod 600 /swapfile
sudo mkswap /swapfile
sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
```

**Why each step matters:**

| Step | Why |
|------|-----|
| `ufw` (firewall) | Blocks all ports except the ones we explicitly need. Without this, your server is exposed to the entire internet on all ports. |
| `fail2ban` | Monitors SSH login attempts. If someone tries to guess your password 5 times, their IP is automatically banned for 10 minutes. |
| Swap file | When RAM is 100% full, Linux kills random processes (OOM killer). A swap file gives it overflow space, preventing your Spring Boot app from being killed during traffic spikes. |

### Step 6: Install Java 25 via SDKMAN

```bash
# Install SDKMAN (Software Development Kit Manager)
curl -s "https://get.sdkman.io" | bash
source "$HOME/.sdkman/bin/sdkman-init.sh"

# List available Java versions
sdk list java | grep tem

# Install Java 25 (Eclipse Temurin — the industry standard OpenJDK distribution)
sdk install java 25-tem

# Verify
java -version
```

**Why SDKMAN instead of `apt install`?**
- Ubuntu's `apt` repository doesn't have Java 25 yet.
- SDKMAN lets you install any Java version instantly and switch between versions.
- Industry standard for managing JDK versions on Linux servers.

### Step 7: Install Docker & Docker Compose

```bash
# Install Docker
curl -fsSL https://get.docker.com | sudo sh

# Add your user to the docker group (so you don't need 'sudo' for every docker command)
sudo usermod -aG docker $USER

# Log out and back in for group change to take effect
exit
# SSH back in:
# ssh -i ~/.ssh/oci_seekfactory ubuntu@129.154.xx.xx

# Verify Docker
docker --version
docker compose version
```

### Step 8: Install and Configure Nginx

Nginx sits in front of Spring Boot as a **reverse proxy**. This is the industry standard architecture:

```
Internet → Nginx (port 80/443) → Spring Boot (port 8080)
```

**Why Nginx in front of Spring Boot?**

| Reason | Explanation |
|--------|-------------|
| **SSL Termination** | Nginx handles HTTPS certificates. Spring Boot only deals with plain HTTP internally. |
| **Static files** | Nginx serves static files (if any) 100x faster than Java. |
| **DDoS protection** | Nginx has built-in rate limiting and connection throttling. |
| **Zero-downtime deploys** | During deployment, Nginx holds connections while the new Spring Boot container starts. |
| **Industry standard** | Every production Java deployment uses Nginx or a load balancer in front. |

```bash
# Install Nginx
sudo apt install -y nginx

# Create Nginx config for SeekFactory
sudo nano /etc/nginx/sites-available/seekfactory
```

Paste this configuration:

```nginx
server {
    listen 80;
    server_name YOUR_DOMAIN_OR_IP;

    # Security headers
    add_header X-Content-Type-Options nosniff;
    add_header X-Frame-Options DENY;
    add_header X-XSS-Protection "1; mode=block";

    # Max upload size (for RFQ attachments and images)
    client_max_body_size 25M;

    # Proxy all /api requests to Spring Boot
    location /api/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;

        # Timeouts for large API responses
        proxy_connect_timeout 60s;
        proxy_read_timeout 120s;
    }

    # Swagger UI
    location /swagger-ui/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }

    # API docs
    location /api-docs {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
    }
    location /v3/api-docs {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
    }

    # Actuator health check
    location /actuator/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
    }

    # Root — simple health response
    location / {
        return 200 '{"status":"SeekFactory API is running"}';
        add_header Content-Type application/json;
    }
}
```

Enable the site:

```bash
# Enable the config
sudo ln -s /etc/nginx/sites-available/seekfactory /etc/nginx/sites-enabled/

# Remove the default Nginx page
sudo rm /etc/nginx/sites-enabled/default

# Test config for syntax errors
sudo nginx -t

# Restart Nginx
sudo systemctl restart nginx
sudo systemctl enable nginx
```

### Step 9: Open Ports in OCI Security List

**This is the step most people miss!** Even though `ufw` allows ports 80/443/8080 on the VM, OCI has its own firewall (Security Lists) that also blocks traffic by default.

1. In OCI Console → **Networking → Virtual Cloud Networks** → Click your VCN
2. Click on your **Subnet** → Click on your **Security List** (Default Security List)
3. Click **"Add Ingress Rules"** and add these 3 rules:

| Source CIDR | Protocol | Destination Port | Description |
|-------------|----------|------------------|-------------|
| `0.0.0.0/0` | TCP | 80 | HTTP |
| `0.0.0.0/0` | TCP | 443 | HTTPS |
| `0.0.0.0/0` | TCP | 8080 | Spring Boot (optional, for direct API access during dev) |

4. Click **"Add Ingress Rules"**

### Step 10: Create the Dockerfile

On your local machine, create this file at `axoraa/Dockerfile`:

```dockerfile
# ── Stage 1: Build ─────────────────────────────────────
FROM eclipse-temurin:25-jdk AS build
WORKDIR /app

# Copy Maven wrapper and pom.xml first (Docker layer caching)
# This means if only your Java code changes (not dependencies),
# Docker reuses the cached dependency layer — saving 2-3 minutes per build
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw dependency:resolve -q

# Now copy source code and build
COPY src/ src/
RUN ./mvnw clean package -DskipTests -q

# ── Stage 2: Run ──────────────────────────────────────
FROM eclipse-temurin:25-jre
WORKDIR /app

# Copy the built JAR from the build stage
COPY --from=build /app/target/*.jar app.jar

# JVM tuning for containerized environment
ENV JAVA_TOOL_OPTIONS="-XX:+UseG1GC -XX:MaxRAMPercentage=75.0 -XX:+UseContainerSupport"

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
```

**Why multi-stage build?**

| Stage | Size | Purpose |
|-------|------|---------|
| Build stage (JDK) | ~500 MB | Contains compiler, Maven, source code — only needed during build |
| Run stage (JRE) | ~150 MB | Contains only the Java runtime + your JAR — this is what runs in production |

Without multi-stage, your Docker image would be ~500 MB. With it, it's ~150 MB. Smaller images = faster deployments.

### Step 11: Create `docker-compose.yml`

Create this file on the Oracle Cloud server at `~/seekfactory/docker-compose.yml`:

```yaml
services:
  backend:
    image: ghcr.io/YOUR_GITHUB_USERNAME/seekfactory-backend:latest
    container_name: seekfactory-backend
    restart: unless-stopped
    ports:
      - "8080:8080"
    environment:
      SPRING_PROFILES_ACTIVE: prod
      PROD_DB_URL: ${PROD_DB_URL}
      PROD_DB_USERNAME: ${PROD_DB_USERNAME}
      PROD_DB_PASSWORD: ${PROD_DB_PASSWORD}
      JWT_SECRET: ${JWT_SECRET}
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 40s

  # Watchtower: Automatically pulls and redeploys new Docker images
  watchtower:
    image: containrrr/watchtower
    container_name: watchtower
    restart: unless-stopped
    volumes:
      - /var/run/docker.sock:/var/run/docker.sock
    environment:
      WATCHTOWER_CLEANUP: "true"
      WATCHTOWER_POLL_INTERVAL: 300    # Check for new images every 5 minutes
```

Create the `.env` file on the server:

```bash
nano ~/seekfactory/.env
```

```bash
PROD_DB_URL=jdbc:postgresql://aws-0-ap-southeast-1.pooler.supabase.com:6543/postgres
PROD_DB_USERNAME=postgres.your-prod-project-ref
PROD_DB_PASSWORD=your-prod-database-password
JWT_SECRET=your-production-256-bit-secret-key-minimum-32-chars
```

### Step 12: Add Free SSL with Let's Encrypt (Optional — Requires Domain)

If you have a domain name (e.g., `api.seekfactory.com`):

```bash
# Install Certbot
sudo apt install -y certbot python3-certbot-nginx

# Get SSL certificate (replace with your domain)
sudo certbot --nginx -d api.seekfactory.com

# Auto-renewal is set up automatically
# Test it:
sudo certbot renew --dry-run
```

If you don't have a domain yet, use the raw IP address with HTTP. You can add SSL later.

---

## Part C: Connecting Everything Together

### Step 1: Deploy and Start

On the Oracle Cloud server:

```bash
# Navigate to the project directory
cd ~/seekfactory

# Pull the latest image and start
docker compose up -d

# Check logs
docker compose logs -f backend

# Verify it's running
curl http://localhost:8080/actuator/health
```

### Step 2: Test from Your Browser

Open these URLs (replace with your server's public IP):

```
http://129.154.xx.xx/actuator/health          → Should return {"status":"UP"}
http://129.154.xx.xx/swagger-ui/index.html    → Swagger UI
http://129.154.xx.xx/api/v1/categories        → Categories API
```

### Step 3: Connect Vercel Frontend

In your Vercel project settings, add the environment variable:

```
NEXT_PUBLIC_API_URL=http://129.154.xx.xx
```

(Replace with `https://api.seekfactory.com` once you have SSL)

---

## Part D: CI/CD with GitHub Actions

This is how code goes from your laptop to the cloud server automatically on every `git push`:

```
Developer pushes to main
       ↓
GitHub Actions triggers
       ↓
Builds Docker image
       ↓
Pushes to GitHub Container Registry (ghcr.io)
       ↓
SSHs into Oracle Cloud server
       ↓
Pulls new image and restarts container
```

### Step 1: Add GitHub Secrets

Go to your GitHub repo → **Settings → Secrets and variables → Actions** → Add these:

| Secret Name | Value |
|-------------|-------|
| `OCI_SSH_PRIVATE_KEY` | Contents of your `~/.ssh/oci_seekfactory` private key file |
| `OCI_HOST` | Your Oracle Cloud VM public IP (e.g., `129.154.xx.xx`) |
| `OCI_USER` | `ubuntu` |

### Step 2: Create the Workflow File

Create `.github/workflows/deploy.yml` in your repo:

```yaml
name: Build & Deploy to Oracle Cloud

on:
  push:
    branches: [main]
    paths:
      - 'axoraa/**'       # Only trigger when backend code changes

jobs:
  build-and-deploy:
    runs-on: ubuntu-latest
    permissions:
      contents: read
      packages: write

    steps:
      # 1. Checkout code
      - name: Checkout repository
        uses: actions/checkout@v4

      # 2. Set up Java 25
      - name: Set up JDK 25
        uses: actions/setup-java@v4
        with:
          java-version: '25'
          distribution: 'temurin'

      # 3. Build with Maven
      - name: Build with Maven
        working-directory: ./axoraa
        run: ./mvnw clean package -DskipTests -q

      # 4. Login to GitHub Container Registry
      - name: Login to GHCR
        uses: docker/login-action@v3
        with:
          registry: ghcr.io
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}

      # 5. Build and push Docker image
      - name: Build and push Docker image
        uses: docker/build-push-action@v5
        with:
          context: ./axoraa
          push: true
          tags: |
            ghcr.io/${{ github.repository_owner }}/seekfactory-backend:latest
            ghcr.io/${{ github.repository_owner }}/seekfactory-backend:${{ github.sha }}

      # 6. Deploy to Oracle Cloud via SSH
      - name: Deploy to OCI
        uses: appleboy/ssh-action@v1
        with:
          host: ${{ secrets.OCI_HOST }}
          username: ${{ secrets.OCI_USER }}
          key: ${{ secrets.OCI_SSH_PRIVATE_KEY }}
          script: |
            cd ~/seekfactory
            docker compose pull
            docker compose up -d
            docker image prune -f
            echo "✅ Deployed successfully at $(date)"
```

Now every time you push to `main`, the backend automatically builds, containerizes, and deploys to your Oracle Cloud server!

---

## Part E: Common Problems & Solutions

### Problem 1: "FATAL: too many connections for role"
**Cause:** Multiple developers running Spring Boot locally, each opening 10+ connections to the same Supabase database.
**Solution:** Set `hikari.maximum-pool-size: 3` in `application-dev.yaml`. With 7 devs × 3 = 21 connections (well under the 60 limit).

### Problem 2: "Connection refused" when connecting to Supabase
**Cause:** You're using the Direct connection (port 5432) instead of the Transaction Pooler (port 6543), or Supabase paused your project due to inactivity.
**Solution:** Use port 6543 in your connection string. If the project is paused, go to the Supabase dashboard and click "Restore project".

### Problem 3: Flyway checksum mismatch
**Cause:** Someone modified an existing migration file (e.g., changed `V1__init_schema.sql` after it was already applied).
**Solution:** Never modify applied migrations. Create a new migration (`V2__fix_schema.sql`). If stuck, run `flyway repair` to reset checksums.

### Problem 4: Docker image too large / slow to push
**Cause:** Not using multi-stage build, or Docker cache not being utilized.
**Solution:** Use the multi-stage Dockerfile above. The dependency layer is cached separately, so only code changes trigger a rebuild (~30 seconds instead of 3 minutes).

### Problem 5: OCI instance won't start / "Out of capacity"
**Cause:** Oracle's free ARM instances are extremely popular. When demand is high in your region, new instances fail with "Out of capacity."
**Solution:** Try creating the instance at odd hours (early morning IST). Or try a different availability domain within your region. Once created, the instance is guaranteed — it won't be taken away.

### Problem 6: Cannot access the server on port 80/8080
**Cause:** OCI Security Lists are blocking traffic (separate from the VM's `ufw` firewall).
**Solution:** Add ingress rules for ports 80, 443, and 8080 in the OCI Console → Networking → VCN → Subnet → Security List (see Step 9 in Part B).

---

## Summary Checklist

```
[ ] 1. Create Supabase account
[ ] 2. Create seekfactory-dev project (Singapore region)
[ ] 3. Create seekfactory-prod project (Singapore region)
[ ] 4. Save both connection strings (port 6543!)
[ ] 5. Create application-dev.yaml and application-prod.yaml
[ ] 6. Add .env to .gitignore
[ ] 7. Create OCI account (Hyderabad region)
[ ] 8. Generate SSH key pair
[ ] 9. Create ARM A1 instance (2 OCPU, 12GB RAM, Ubuntu 22.04)
[ ] 10. SSH in and run security hardening
[ ] 11. Install Java 25 (SDKMAN), Docker, Nginx
[ ] 12. Configure Nginx reverse proxy
[ ] 13. Open ports 80/443/8080 in OCI Security Lists
[ ] 14. Create Dockerfile in axoraa/
[ ] 15. Create docker-compose.yml on server
[ ] 16. Create .env on server with prod credentials
[ ] 17. Set up GitHub Actions CI/CD
[ ] 18. Push to main → verify auto-deployment
[ ] 19. Connect Vercel frontend to Oracle Cloud backend URL
[ ] 20. (Optional) Add domain + SSL with Let's Encrypt
```
