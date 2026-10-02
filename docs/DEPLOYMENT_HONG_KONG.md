# SeekFactory Backend: Production Hosting in Hong Kong (Alibaba Cloud or AWS)

This guide moves the Spring Boot backend (`axoraa`) off Render onto an always-on server in
Hong Kong, so there is no cold start. It covers two providers step by step:

- **Part A: Alibaba Cloud, region China (Hong Kong)** (recommended for SeekFactory)
- **Part B: AWS, region Asia Pacific (Hong Kong) `ap-east-1`**

Both parts end in the same setup: one Docker container running on a virtual machine, HTTPS in
front of it, a managed PostgreSQL database and object storage in the same region.

> Prices, instance names and console menus change often. Where this guide gives sizes or costs,
> treat them as a starting point and confirm in the provider's pricing calculator before buying.

---

## Contents

0. [Recommendation in one page](#0-recommendation-in-one-page)
1. [Fix these before launch (applies to both clouds)](#1-fix-these-before-launch-applies-to-both-clouds)
2. [Environment variables the backend needs](#2-environment-variables-the-backend-needs)
3. [Part A: Alibaba Cloud (Hong Kong)](#part-a-alibaba-cloud-hong-kong)
4. [Part B: AWS (Hong Kong, ap-east-1)](#part-b-aws-hong-kong-ap-east-1)
5. [Moving the database off Supabase](#5-moving-the-database-off-supabase)
6. [Connecting the Vercel frontend](#6-connecting-the-vercel-frontend)
7. [Checks after every deployment](#7-checks-after-every-deployment)
8. [Deploying updates and rolling back](#8-deploying-updates-and-rolling-back)
9. [Scaling beyond one server](#9-scaling-beyond-one-server)
10. [Troubleshooting](#10-troubleshooting)

---

## 0. Recommendation in one page

### Hong Kong or Singapore?

**Hong Kong.** Most users, and nearly all uploads, come from mainland China.

| From | To Hong Kong | To Singapore |
|---|---|---|
| Shenzhen / Guangzhou | very close (often 10–30 ms) | noticeably farther |
| Shanghai / Hangzhou | close (often 30–50 ms) | farther |
| India | reasonable | slightly closer |

Hong Kong servers **do not need an ICP licence** (ICP filing is only required for servers inside
mainland China). Singapore is only the better choice if India becomes the main audience.

### Alibaba Cloud or AWS?

**Alibaba Cloud Hong Kong** is the better fit for users in China:

- Alibaba's Hong Kong network has strong routes into mainland carriers (China Telecom, Unicom,
  Mobile), which matters most for large video uploads.
- **OSS Transfer Acceleration** lets uploads from the mainland enter Alibaba's network at a nearby
  mainland entry point and travel on Alibaba's backbone to the Hong Kong bucket.
- Chinese-language support and billing that suppliers in China already know.

AWS Hong Kong works well too, but mainland users get less consistent routes, and AWS edge
locations inside mainland China are only available through a separate AWS China account (which
needs an ICP licence). Choose AWS if the team already runs on AWS or needs AWS-specific services.

### Target architecture (same shape on both clouds)

```
 Buyers / suppliers (China, India)
            │
            ▼
  Vercel (Next.js web app)  ──HTTPS──►  api.seekfactory.com
                                              │
                                   ┌──────────▼───────────┐
                                   │  VM in Hong Kong      │
                                   │  Caddy (HTTPS, :443)  │
                                   │    └► Docker: Spring  │
                                   │       Boot on :8080   │
                                   └───┬──────────────┬────┘
                                       │ private net  │ private net
                            ┌──────────▼───┐   ┌──────▼──────────────┐
                            │ PostgreSQL   │   │ Object storage       │
                            │ (RDS, HK)    │   │ (OSS / S3, HK)       │
                            └──────────────┘   │ + CDN for media      │
                                               └──────────────────────┘
```

**No cold start:** the VM and the container run 24/7. Docker's `--restart unless-stopped`
brings the container back after a crash or a reboot.

### Suggested sizes to start with

| Piece | Alibaba Cloud | AWS |
|---|---|---|
| App server | ECS, 2 vCPU / 8 GiB (general purpose, e.g. `g7` family) | EC2 `m6i.large` (2 vCPU / 8 GiB) or `m7g.large` (ARM, cheaper) |
| Database | ApsaraDB RDS for PostgreSQL, 2 vCPU / 4 GiB, High-availability edition | RDS for PostgreSQL `db.t4g.medium` (Multi-AZ when revenue allows) |
| Media | OSS bucket in `cn-hongkong` + Alibaba CDN | S3 bucket in `ap-east-1` + CloudFront |
| Container images | Container Registry (ACR), Personal Edition | ECR |
| HTTPS | Caddy on the VM (free Let's Encrypt), or ALB + certificate | Caddy on the VM, or ALB + ACM certificate |

Spring Boot with this codebase is comfortable in 8 GiB. Do not go below 4 GiB.

---

## 1. Fix these before launch (applies to both clouds)

These are properties of the current code, not of the cloud. Hosting alone will not fix them.

### 1.1 Large uploads cannot pass through Vercel (blocker for videos)

Every browser upload currently goes to the web app's `/api/proxy/...` route, which forwards it to
the backend. On Vercel that route is a serverless function, and **Vercel limits a function's
request body to about 4.5 MB**. Seek videos (up to 100 MB) and large photos will fail in
production even though they work locally.

**Fix (recommended): direct uploads to object storage.**

1. The browser asks the backend for a short-lived upload URL: a pre-signed PUT URL (S3) or a
   signed URL / STS token (OSS).
2. The browser uploads the file **straight to OSS/S3**. With OSS Transfer Acceleration this is the
   fastest path from mainland China.
3. The browser tells the backend the object key, and the backend records it.

This needs a new storage implementation (`OssMediaStorageService` or `S3MediaStorageService`,
implementing the existing `MediaStorageService`) plus a small change in the web upload code. Until
that ships, keep the media on a data disk on the VM (see A8 / B7). Uploads will still hit the
Vercel limit, so this fix is required before suppliers upload videos in production.

### 1.2 Uploaded files are stored on the server's local disk

`LocalMediaStorageService` writes to `app.media.storage-dir` (default `uploads/`). In a container
that directory disappears when the container is replaced. This guide mounts a **persistent data
disk** at `/data/uploads` so files survive deployments. Moving media to OSS/S3 (1.1) removes this
dependency and lets a CDN serve the files.

### 1.3 Vercel reachability from mainland China

`*.vercel.app` addresses are frequently blocked or very slow in mainland China, and custom domains
on Vercel can also be unreliable there. The repo's own `AGENTS.md` lists "Vercel-only for China
suppliers" as something to avoid. Before launch, test the production domain from a mainland
network (for example with a China-based uptime checker or a colleague in Shenzhen).
If it is slow, run the Next.js app in Hong Kong as well: it can run on the same ECS/EC2 server as a
second container (`next build && next start`) behind the same Caddy, on `seekfactory.com`.

### 1.4 Production settings that must be set explicitly

| Setting | Why |
|---|---|
| `JWT_SECRET` | The built-in default is not valid base64 and is public in the repo. Generate a long random base64 secret (command in section 2). |
| `SPRING_PROFILES_ACTIVE=prod` | Loads the production profile. The dev profile enables a fixed phone OTP (`123456`). |
| `APP_CORS_ALLOWEDORIGINPATTERNS` | `application-prod.yaml` is **git-ignored**, so it is **not** inside an image built from GitHub. Set CORS origins through the environment instead. |
| `LOGGING_LEVEL_SEEKFACTORY_AXORAA=INFO` | The base config logs the app at DEBUG, which is too noisy for production. |
| `SPRINGDOC_API_DOCS_ENABLED=false`, `SPRINGDOC_SWAGGER_UI_ENABLED=false` | Hide Swagger / API docs in production. |
| `SPRING_MAIL_*`, `APP_FRONTEND_URL` | Password reset and email verification only work with SMTP configured, and links point at `APP_FRONTEND_URL`. |

---

## 2. Environment variables the backend needs

Keep these in one file on the server, e.g. `/opt/seekfactory/backend.env`, readable only by root
(`chmod 600`). **Never commit this file.**

```bash
# ── Runtime ─────────────────────────────────────────────
SPRING_PROFILES_ACTIVE=prod
PORT=8080
LOGGING_LEVEL_SEEKFACTORY_AXORAA=INFO
SPRINGDOC_API_DOCS_ENABLED=false
SPRINGDOC_SWAGGER_UI_ENABLED=false

# ── Database (private endpoint of RDS in the same region) ──
PROD_DB_URL=jdbc:postgresql://<rds-private-endpoint>:5432/seekfactory?sslmode=require
PROD_DB_USERNAME=seekfactory_app
PROD_DB_PASSWORD=<strong password>
# Connections from this server; stay under the database's max_connections
SPRING_DATASOURCE_HIKARI_MAXIMUMPOOLSIZE=20

# ── Security ───────────────────────────────────────────
JWT_SECRET=<output of: openssl rand -base64 64 | tr -d '\n'>
# Browsers that may call the API directly (the web app's own proxy is server-to-server)
APP_CORS_ALLOWEDORIGINPATTERNS=https://seekfactory.com,https://www.seekfactory.com
APP_FRONTEND_URL=https://seekfactory.com
GOOGLE_CLIENT_ID=<google oauth client id, if Google sign-in is used>

# ── Media (until OSS/S3 storage is implemented, see 1.1/1.2) ──
APP_MEDIA_STORAGE_DIR=/data/uploads
APP_MEDIA_MAX_VIDEO_SIZE=100MB
APP_MEDIA_MAX_IMAGE_SIZE=20MB
APP_MEDIA_MAX_DOCUMENT_SIZE=50MB

# ── Email (password reset, verification) ───────────────
SPRING_MAIL_HOST=<smtp host, e.g. smtpdm.aliyun.com (Alibaba DirectMail) or email-smtp.ap-east-1.amazonaws.com (SES)>
SPRING_MAIL_PORT=465
SPRING_MAIL_USERNAME=<smtp user>
SPRING_MAIL_PASSWORD=<smtp password>
SPRING_MAIL_PROPERTIES_MAIL_SMTP_SSL_ENABLE=true
APP_MAIL_FROM=SeekFactory <no-reply@seekfactory.com>

# ── Must stay empty in production (enables a fixed test OTP when set) ──
# APP_AUTH_MOCK_OTP=
```

Generate the JWT secret on any Linux/macOS machine (or Git Bash on Windows):

```bash
openssl rand -base64 64 | tr -d '\n'; echo
```

> Changing `JWT_SECRET` signs everyone out. Set it once and keep it in a password manager.

---

## Part A: Alibaba Cloud (Hong Kong)

Use the **international site** `https://www.alibabacloud.com` (console: `https://home.console.alibabacloud.com`).
The mainland site (`aliyun.com`) works too, but its accounts need Chinese real-name verification.

Region for everything below: **China (Hong Kong)**, region ID `cn-hongkong`.

### A1. Account and access

1. Create the account, add a payment method, and complete account verification.
2. **Do not use the root account day to day.** Open **RAM (Resource Access Management)**:
   - Create a RAM user `seekfactory-admin` with console access and MFA, and attach the
     `AdministratorAccess` policy (tighten it later).
   - Create a RAM user `seekfactory-ci` with **API access only** (AccessKey). You will use it for
     GitHub Actions to push images. Attach `AliyunContainerRegistryFullAccess`.
3. Sign in as `seekfactory-admin` from now on.

### A2. Network: VPC and vSwitches

1. Console → **VPC** → region **China (Hong Kong)** → **Create VPC**.
   - Name `seekfactory-vpc`, IPv4 CIDR `10.10.0.0/16`.
   - vSwitch 1: `seekfactory-a`, zone **Hong Kong Zone B**, CIDR `10.10.1.0/24`.
   - vSwitch 2: `seekfactory-b`, zone **Hong Kong Zone C**, CIDR `10.10.2.0/24`
     (needed later for a high-availability database and load balancer).

### A3. Security group (the server's firewall)

Console → **ECS** → **Network & Security** → **Security Groups** → **Create**, VPC `seekfactory-vpc`,
name `seekfactory-api-sg`. Inbound rules:

| Port | Source | Purpose |
|---|---|---|
| 443/TCP | 0.0.0.0/0 | HTTPS API |
| 80/TCP | 0.0.0.0/0 | Let's Encrypt certificate check + redirect to HTTPS |
| 22/TCP | **your office IP /32 only** | SSH (or leave closed and use Workbench / Cloud Assistant) |

Do **not** open 8080 or 5432 to the internet.

### A4. Database: ApsaraDB RDS for PostgreSQL

1. Console → **ApsaraDB RDS** → **Create Instance**.
   - Billing: Subscription (cheaper for 24/7) or Pay-as-you-go while testing.
   - Region **China (Hong Kong)**, engine **PostgreSQL 16 or 17** (the app is tested on 17).
   - Edition **High-availability** (primary + standby in two zones) for production;
     **Basic** is acceptable for staging.
   - Specs: **2 vCPU / 4 GiB** general purpose to start. Storage **ESSD, 50–100 GB**, enable
     storage auto-scaling.
   - Network: VPC `seekfactory-vpc`, vSwitch `seekfactory-a` (standby in `seekfactory-b`).
2. After it is created, open the instance:
   - **Whitelist / Security**: add `10.10.0.0/16` (the VPC only). No public endpoint.
   - **Accounts** → create a **privileged** account `seekfactory_admin`, and a **standard**
     account `seekfactory_app` for the application.
   - **Databases** → create database `seekfactory`, owner `seekfactory_app`, encoding UTF8.
   - **Backup and Restoration**: keep automatic backups at least 7 days (30 for production).
3. Copy the **internal endpoint** (looks like `pgm-xxxx.pg.rds.aliyuncs.com`). This is the
   `PROD_DB_URL` host.
4. Flyway creates all tables on the first start of the backend. To bring existing data over from
   Supabase instead, follow [section 5](#5-moving-the-database-off-supabase) **before** the first
   start.

### A5. Object storage for media: OSS (+ CDN)

Needed now for backups and ready for the upload fix in 1.1.

1. Console → **Object Storage Service** → **Create Bucket**.
   - Name `seekfactory-media` (bucket names are global; add a suffix if taken).
   - Region **China (Hong Kong)**, storage class **Standard**, redundancy **ZRS** if offered.
   - ACL **Private** (serve through CDN / signed URLs).
2. Bucket → **Transfer Acceleration** → **Enable**. Mainland uploads then use the endpoint
   `oss-accelerate.aliyuncs.com`.
3. Bucket → **Data Security → CORS**: allow origin `https://seekfactory.com`, methods
   `GET, PUT, POST, HEAD`, headers `*`, expose header `ETag`. This is needed for direct browser
   uploads (1.1).
4. **CDN** (Alibaba Cloud CDN): add domain `media.seekfactory.com` with origin = the OSS bucket,
   and enable **private bucket back-to-origin** so the bucket can stay private.
   - Acceleration region: **Outside Chinese mainland** works without ICP. "Global" /
     "Chinese mainland" acceleration (nodes inside the mainland, much faster for mainland users)
     **requires an ICP-filed domain** with a mainland entity. Plan the ICP filing if media speed
     inside China becomes critical.
5. Endpoints for the backend (when the OSS storage class is implemented):
   - Internal (server → OSS, free traffic): `oss-cn-hongkong-internal.aliyuncs.com`
   - Browser uploads: `oss-accelerate.aliyuncs.com`

### A6. Container registry: ACR

1. Console → **Container Registry** → **Instances** → **Personal Edition** (free) → region
   **China (Hong Kong)**.
2. Set a **registry password** (Instance → Access Credential).
3. Create namespace `seekfactory`, repository `axoraa-api`, type **Private**, code source **Local**.
4. Your image address is:
   - Public: `registry.cn-hongkong.aliyuncs.com/seekfactory/axoraa-api`
   - Inside the VPC (the ECS server pulls from here, faster and free):
     `registry-vpc.cn-hongkong.aliyuncs.com/seekfactory/axoraa-api`

### A7. App server: ECS instance

1. Console → **Elastic Compute Service** → **Instances** → **Create Instance**.
   - Billing **Subscription** (1 year is much cheaper than pay-as-you-go for a 24/7 server).
   - Region **China (Hong Kong)**, zone **B**, VPC `seekfactory-vpc`, vSwitch `seekfactory-a`.
   - Instance type: general purpose **2 vCPU / 8 GiB** (e.g. `ecs.g7.large`).
   - Image: **Ubuntu 22.04 64-bit** (or 24.04).
   - System disk: **ESSD 40 GiB**. Data disk: **ESSD 100 GiB** (for `/data/uploads` until OSS
     storage ships), tick "Release with instance" **off**.
   - Public IP: assign, billing **pay-by-traffic**, peak bandwidth **100 Mbit/s**
     (you pay for outbound GB, not for the peak).
   - Security group `seekfactory-api-sg`. Login: **key pair** (download the `.pem`).
2. Convert the public IP to an **Elastic IP (EIP)** so it survives instance changes
   (ECS → instance → More → Network → Convert to EIP).
3. Enable **snapshots**: ECS → Storage → Automatic Snapshot Policy → daily, keep 7, apply to both
   disks.

### A8. Prepare the server

SSH in (replace the IP):

```bash
ssh -i seekfactory.pem root@<EIP>
```

Update the system, install Docker, create a non-root user:

```bash
apt-get update && apt-get -y upgrade
curl -fsSL https://get.docker.com | sh
systemctl enable --now docker

adduser --disabled-password --gecos "" deploy
usermod -aG docker deploy
```

Mount the data disk at `/data` (check the device name with `lsblk`; usually `/dev/vdb`):

```bash
lsblk
mkfs.ext4 /dev/vdb
mkdir -p /data
echo "/dev/vdb /data ext4 defaults,nofail 0 2" >> /etc/fstab
mount -a
mkdir -p /data/uploads
```

Give the upload folder to the user the container runs as (the image uses a non-root `appuser`):

```bash
# Find the container user's UID/GID once the image is pulled (step A9):
#   docker run --rm --entrypoint id <image>
# then, e.g. if it prints uid=100 gid=101:
chown -R 100:101 /data/uploads
```

Create the environment file from [section 2](#2-environment-variables-the-backend-needs):

```bash
mkdir -p /opt/seekfactory
nano /opt/seekfactory/backend.env      # paste and fill in the variables
chmod 600 /opt/seekfactory/backend.env
```

Add swap as a safety net (helps during image builds or memory spikes):

```bash
fallocate -l 4G /swapfile && chmod 600 /swapfile && mkswap /swapfile && swapon /swapfile
echo "/swapfile none swap sw 0 0" >> /etc/fstab
```

Set the server clock to UTC and keep it synced (the app stores UTC timestamps):

```bash
timedatectl set-timezone UTC
```

### A9. Build the image and run the container

**First time, by hand** (later this is automated in A12). On your laptop, in the `axoraa` repo:

```bash
docker login --username=<registry username> registry.cn-hongkong.aliyuncs.com
docker build -t registry.cn-hongkong.aliyuncs.com/seekfactory/axoraa-api:v1 .
docker push registry.cn-hongkong.aliyuncs.com/seekfactory/axoraa-api:v1
```

> If the server is `x86_64` and your laptop is an Apple Silicon Mac, add
> `--platform linux/amd64` to `docker build`.

On the server:

```bash
docker login --username=<registry username> registry-vpc.cn-hongkong.aliyuncs.com
docker pull registry-vpc.cn-hongkong.aliyuncs.com/seekfactory/axoraa-api:v1
docker run --rm --entrypoint id registry-vpc.cn-hongkong.aliyuncs.com/seekfactory/axoraa-api:v1   # for the chown in A8

docker run -d --name seekfactory-api \
  --restart unless-stopped \
  -p 127.0.0.1:8080:8080 \
  --env-file /opt/seekfactory/backend.env \
  -v /data/uploads:/data/uploads \
  --log-opt max-size=50m --log-opt max-file=5 \
  registry-vpc.cn-hongkong.aliyuncs.com/seekfactory/axoraa-api:v1

docker logs -f seekfactory-api      # wait for "Started AxoraaApplication"
curl -s http://127.0.0.1:8080/actuator/health   # {"status":"UP"}
```

Notes:

- `-p 127.0.0.1:8080:8080` exposes the app **only** to the local Caddy, never to the internet.
- On first start Flyway runs every migration (`V1` … latest) against the empty database.

### A10. HTTPS with Caddy (free, automatic certificates)

1. Point DNS first: create an **A record** `api.seekfactory.com` → `<EIP>` (in Alibaba Cloud DNS or
   wherever `seekfactory.com` is managed). Wait until `nslookup api.seekfactory.com` returns the EIP.
2. Install Caddy on the server:

```bash
apt-get install -y debian-keyring debian-archive-keyring apt-transport-https curl
curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/gpg.key' | gpg --dearmor -o /usr/share/keyrings/caddy-stable-archive-keyring.gpg
curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/debian.deb.txt' > /etc/apt/sources.list.d/caddy-stable.list
apt-get update && apt-get install -y caddy
```

3. Replace `/etc/caddy/Caddyfile` with:

```caddyfile
api.seekfactory.com {
    encode gzip

    # Uploads: videos up to 100 MB plus form overhead
    request_body {
        max_size 110MB
    }

    reverse_proxy 127.0.0.1:8080 {
        # Chat uses Server-Sent Events: send every event immediately, never buffer
        flush_interval -1
        transport http {
            read_timeout 35m
        }
    }

    log {
        output file /var/log/caddy/api-access.log {
            roll_size 50mb
            roll_keep 10
        }
    }
}
```

4. Reload and test:

```bash
systemctl reload caddy
curl -s https://api.seekfactory.com/actuator/health
```

Caddy obtains and renews the Let's Encrypt certificate by itself (ports 80 and 443 must be open).

> **Alternative: Application Load Balancer (ALB)** with a certificate from Alibaba Cloud Certificate
> Management Service. Use this when you add a second server (section 9). Set the idle timeout
> to at least 900 s so chat streams are not cut, and point the health check at
> `/actuator/health`.

### A11. Monitoring and alerts

1. **CloudMonitor** → create alert rules for the ECS instance: CPU > 80 % for 5 min,
   memory > 85 %, disk `/data` > 80 %, and for RDS: CPU, connections, storage.
2. **Site monitoring**: CloudMonitor → Site Monitoring → HTTP check of
   `https://api.seekfactory.com/actuator/health` every minute from several mainland and overseas
   locations, alert by SMS/email/DingTalk.
3. Optional: ship container logs to **Log Service (SLS)** with Logtail for search and retention.

### A12. Automatic deploys with GitHub Actions

Repository → Settings → Secrets and variables → Actions → add:

| Secret | Value |
|---|---|
| `ACR_USERNAME` / `ACR_PASSWORD` | ACR registry credentials (A6) |
| `DEPLOY_HOST` | the EIP |
| `DEPLOY_USER` | `deploy` |
| `DEPLOY_SSH_KEY` | a private key whose public key is in `/home/deploy/.ssh/authorized_keys` |

Create `.github/workflows/deploy-alibaba.yml`:

```yaml
name: Deploy backend (Alibaba HK)

on:
  push:
    branches: [main]          # deploy when main is updated
  workflow_dispatch: {}

env:
  IMAGE: registry.cn-hongkong.aliyuncs.com/seekfactory/axoraa-api
  IMAGE_VPC: registry-vpc.cn-hongkong.aliyuncs.com/seekfactory/axoraa-api

jobs:
  build-and-deploy:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: "25"
          cache: maven

      - name: Unit tests
        run: ./mvnw -B test -Dtest='!AxoraaApplicationTests' -Dsurefire.failIfNoSpecifiedTests=false

      - name: Log in to ACR
        run: echo "${{ secrets.ACR_PASSWORD }}" | docker login -u "${{ secrets.ACR_USERNAME }}" --password-stdin registry.cn-hongkong.aliyuncs.com

      - name: Build and push
        run: |
          docker build -t $IMAGE:${{ github.sha }} -t $IMAGE:latest .
          docker push $IMAGE:${{ github.sha }}
          docker push $IMAGE:latest

      - name: Deploy on the server
        uses: appleboy/ssh-action@v1.2.0
        with:
          host: ${{ secrets.DEPLOY_HOST }}
          username: ${{ secrets.DEPLOY_USER }}
          key: ${{ secrets.DEPLOY_SSH_KEY }}
          script: |
            set -e
            TAG=${{ github.sha }}
            docker pull $IMAGE_VPC:$TAG
            docker rm -f seekfactory-api || true
            docker run -d --name seekfactory-api --restart unless-stopped \
              -p 127.0.0.1:8080:8080 --env-file /opt/seekfactory/backend.env \
              -v /data/uploads:/data/uploads \
              --log-opt max-size=50m --log-opt max-file=5 \
              $IMAGE_VPC:$TAG
            for i in $(seq 1 60); do
              curl -fs http://127.0.0.1:8080/actuator/health && exit 0
              sleep 3
            done
            echo "Health check failed"; docker logs --tail 200 seekfactory-api; exit 1
          envs: IMAGE_VPC
```

On the server, log the `deploy` user in to the registry once:
`docker login --username=<registry username> registry-vpc.cn-hongkong.aliyuncs.com`.

This restarts the single container, so each deploy has roughly 20–40 s of downtime while Spring
starts. Deploy outside peak hours, or use two servers behind ALB (section 9) for zero downtime.

### A13. Backups

- **RDS**: automatic backups (A4) plus a manual backup before each release that has a migration.
- **ECS**: automatic snapshots (A7) cover `/data/uploads` until media moves to OSS.
- **OSS**: enable **versioning** on the bucket, and a lifecycle rule to expire old versions after 30 days.

### A14. Rough monthly cost (verify in the Alibaba Cloud pricing calculator)

| Item | Size | Order of magnitude |
|---|---|---|
| ECS | 2 vCPU / 8 GiB, subscription | tens of USD |
| RDS PostgreSQL | 2 vCPU / 4 GiB, HA | higher than the ECS; Basic edition roughly halves it |
| EIP traffic | pay per outbound GB | depends on video views; serve media via CDN to keep it low |
| OSS + CDN | per GB stored and delivered | low at launch, grows with video views |
| ACR Personal, CloudMonitor basic, Caddy certificates | | free |

---

## Part B: AWS (Hong Kong, ap-east-1)

### B1. Account, region and access

1. Sign in to the AWS console as the account owner, enable **MFA** on the root user, then stop
   using root.
2. **Enable the Hong Kong region**: Account settings → **AWS Regions** → **Asia Pacific (Hong Kong)
   ap-east-1** → **Enable**. It is an opt-in region; enabling takes a few minutes.
3. **IAM Identity Center** (or IAM) → create an admin user for yourself with MFA.
4. Select region **ap-east-1** in the console for everything below.

### B2. Network and security groups

The default VPC in `ap-east-1` is fine to start. Create two security groups (EC2 → Security Groups):

- `seekfactory-api-sg`: inbound 443 and 80 from `0.0.0.0/0`; 22 only from your office IP
  (or no SSH at all, using SSM Session Manager in B6).
- `seekfactory-db-sg`: inbound 5432 **only from** `seekfactory-api-sg` (choose the security group
  as the source, not an IP).

### B3. Database: Amazon RDS for PostgreSQL

1. RDS → **Create database** → Standard create → **PostgreSQL** (16 or 17).
2. Template **Production** (Multi-AZ) or **Dev/Test** (Single-AZ) to start.
3. Identifier `seekfactory-db`, master user `seekfactory_admin`, store the password in
   **Secrets Manager** (option in the form) or a password manager.
4. Instance `db.t4g.medium` (2 vCPU / 4 GiB). Storage gp3, 50–100 GiB, enable storage autoscaling.
5. Connectivity: default VPC, **Public access: No**, security group `seekfactory-db-sg`.
6. Additional configuration: initial database name `seekfactory`, backups 7–30 days,
   enable deletion protection.
7. After it is available, connect once from the EC2 server (B7) and create the app user:

```sql
CREATE USER seekfactory_app WITH PASSWORD '<strong password>';
GRANT ALL PRIVILEGES ON DATABASE seekfactory TO seekfactory_app;
\c seekfactory
GRANT ALL ON SCHEMA public TO seekfactory_app;
```

The endpoint (e.g. `seekfactory-db.xxxx.ap-east-1.rds.amazonaws.com`) goes in `PROD_DB_URL`.

### B4. Media: S3 (+ CloudFront)

1. S3 → **Create bucket** `seekfactory-media-<suffix>`, region **ap-east-1**,
   **Block all public access: on**, versioning on, default encryption SSE-S3.
2. Permissions → **CORS** (for direct browser uploads, section 1.1):

```json
[
  {
    "AllowedOrigins": ["https://seekfactory.com", "https://www.seekfactory.com"],
    "AllowedMethods": ["GET", "PUT", "POST", "HEAD"],
    "AllowedHeaders": ["*"],
    "ExposeHeaders": ["ETag"],
    "MaxAgeSeconds": 3000
  }
]
```

3. **CloudFront** → create a distribution with the bucket as origin, **Origin access control (OAC)**
   so the bucket stays private, alternate domain `media.seekfactory.com` with a certificate from
   **ACM in us-east-1** (CloudFront requires that region). CloudFront has edge locations in
   Hong Kong and Taiwan; locations inside mainland China require a separate AWS China account and ICP.
4. **S3 Transfer Acceleration**: check whether it is offered for buckets in `ap-east-1` in your
   account (bucket → Properties). If it is not, direct uploads still go to the Hong Kong
   endpoint, which is close to South China.

### B5. Container registry: ECR

```bash
aws ecr create-repository --repository-name seekfactory/axoraa-api --region ap-east-1
```

The image address is `<account-id>.dkr.ecr.ap-east-1.amazonaws.com/seekfactory/axoraa-api`.

### B6. IAM role for the server

IAM → Roles → **Create role** → trusted entity **EC2** → attach:

- `AmazonEC2ContainerRegistryReadOnly` (pull images)
- `AmazonSSMManagedInstanceCore` (Session Manager shell and remote deploys, no SSH needed)
- `CloudWatchAgentServerPolicy` (metrics and logs)

Name it `seekfactory-api-role`.

### B7. App server: EC2 instance

1. EC2 → **Launch instance**.
   - Name `seekfactory-api`, AMI **Ubuntu Server 22.04 LTS** (x86_64).
   - Type **m6i.large** (2 vCPU / 8 GiB).
     *Cheaper option:* `m7g.large` (Graviton / ARM). The Java 25 base image supports ARM; build
     the image with `--platform linux/arm64`.
   - Key pair: create and download; network: default VPC, security group `seekfactory-api-sg`.
   - Storage: root **gp3 30 GiB**, plus an extra **gp3 100 GiB** volume for `/data/uploads`
     (delete on termination: **no**).
   - Advanced → IAM instance profile `seekfactory-api-role`.
2. Allocate an **Elastic IP** and associate it with the instance.
3. **Data Lifecycle Manager** → daily snapshots of both volumes, keep 7.

Prepare the server exactly as in **A8** (Docker, `deploy` user, mount the extra volume at `/data` —
on EC2 the device is usually `/dev/nvme1n1`, check with `lsblk` —, swap, env file, UTC).
Install the AWS CLI for ECR login:

```bash
apt-get install -y unzip
curl "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o awscliv2.zip   # aarch64 for Graviton
unzip awscliv2.zip && ./aws/install
```

### B8. Build, push and run

On your laptop (AWS CLI configured for your account):

```bash
ACCOUNT=<account-id>
REPO=$ACCOUNT.dkr.ecr.ap-east-1.amazonaws.com/seekfactory/axoraa-api
aws ecr get-login-password --region ap-east-1 | docker login --username AWS --password-stdin $ACCOUNT.dkr.ecr.ap-east-1.amazonaws.com
docker build --platform linux/amd64 -t $REPO:v1 .
docker push $REPO:v1
```

On the server (the instance role grants pull access, no stored password):

```bash
ACCOUNT=<account-id>
REPO=$ACCOUNT.dkr.ecr.ap-east-1.amazonaws.com/seekfactory/axoraa-api
aws ecr get-login-password --region ap-east-1 | docker login --username AWS --password-stdin $ACCOUNT.dkr.ecr.ap-east-1.amazonaws.com
docker pull $REPO:v1
docker run --rm --entrypoint id $REPO:v1        # then chown /data/uploads (see A8)

docker run -d --name seekfactory-api \
  --restart unless-stopped \
  -p 127.0.0.1:8080:8080 \
  --env-file /opt/seekfactory/backend.env \
  -v /data/uploads:/data/uploads \
  --log-opt max-size=50m --log-opt max-file=5 \
  $REPO:v1

curl -s http://127.0.0.1:8080/actuator/health
```

### B9. HTTPS and DNS

- DNS: **Route 53** hosted zone for `seekfactory.com` (or your current DNS provider), A record
  `api.seekfactory.com` → Elastic IP.
- HTTPS: install **Caddy** with the same `Caddyfile` as **A10**.

> **Alternative: Application Load Balancer + ACM.** Request a certificate for `api.seekfactory.com`
> in ACM (ap-east-1), create a target group (HTTP 8080, health check `/actuator/health`), an ALB
> with an HTTPS listener, and set the ALB **idle timeout to 900 s or more** so chat streams stay
> open. The container then publishes `-p 8080:8080` and the instance security group allows 8080
> only from the ALB's security group.

### B10. Monitoring and alerts

1. Install the **CloudWatch agent** (memory and disk metrics are not collected by default) and send
   `docker` logs with the `awslogs` driver or the agent.
2. CloudWatch **alarms**: EC2 CPU > 80 %, memory > 85 %, disk > 80 %; RDS CPU, free storage,
   connections. Notify through SNS (email / SMS).
3. **Route 53 health check** (or CloudWatch Synthetics) on
   `https://api.seekfactory.com/actuator/health`.

### B11. Automatic deploys with GitHub Actions (no SSH)

Use GitHub **OIDC** so no long-lived AWS keys are stored:

1. IAM → Identity providers → add `token.actions.githubusercontent.com` (audience `sts.amazonaws.com`).
2. Create role `github-deploy` trusted by that provider, limited to your repository, with
   ECR push permissions on the repository and `ssm:SendCommand` on the instance.

`.github/workflows/deploy-aws.yml`:

```yaml
name: Deploy backend (AWS HK)

on:
  push:
    branches: [main]
  workflow_dispatch: {}

permissions:
  id-token: write
  contents: read

env:
  AWS_REGION: ap-east-1
  REPO: <account-id>.dkr.ecr.ap-east-1.amazonaws.com/seekfactory/axoraa-api
  INSTANCE_ID: <i-xxxxxxxxxxxx>

jobs:
  build-and-deploy:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: "25"
          cache: maven

      - name: Unit tests
        run: ./mvnw -B test -Dtest='!AxoraaApplicationTests' -Dsurefire.failIfNoSpecifiedTests=false

      - uses: aws-actions/configure-aws-credentials@v4
        with:
          role-to-assume: arn:aws:iam::<account-id>:role/github-deploy
          aws-region: ${{ env.AWS_REGION }}

      - uses: aws-actions/amazon-ecr-login@v2

      - name: Build and push
        run: |
          docker build --platform linux/amd64 -t $REPO:${{ github.sha }} .
          docker push $REPO:${{ github.sha }}

      - name: Deploy through SSM
        run: |
          aws ssm send-command --instance-ids "$INSTANCE_ID" \
            --document-name AWS-RunShellScript \
            --comment "deploy ${{ github.sha }}" \
            --parameters commands="[
              \"set -e\",
              \"aws ecr get-login-password --region $AWS_REGION | docker login --username AWS --password-stdin ${REPO%%/*}\",
              \"docker pull $REPO:${{ github.sha }}\",
              \"docker rm -f seekfactory-api || true\",
              \"docker run -d --name seekfactory-api --restart unless-stopped -p 127.0.0.1:8080:8080 --env-file /opt/seekfactory/backend.env -v /data/uploads:/data/uploads --log-opt max-size=50m --log-opt max-file=5 $REPO:${{ github.sha }}\"
            ]"
```

### B12. Rough monthly cost (verify in the AWS Pricing Calculator)

`ap-east-1` is priced above most other Asia regions. Main items: EC2 `m6i.large` (or cheaper
`m7g.large`), RDS `db.t4g.medium` (Multi-AZ doubles it), gp3 storage, **data transfer out**
(usually the largest item once videos are watched; serve media through CloudFront), and
CloudWatch logs. Consider a 1-year **Savings Plan / Reserved Instance** once the size is stable.

---

## 5. Moving the database off Supabase

Supabase currently runs in Tokyo (`aws-0-ap-northeast-1`). Keep the database in the **same region
as the backend**: every API call makes several queries, and a Hong Kong ↔ Tokyo round trip on each
one adds up quickly.

1. **Freeze writes**: announce a short maintenance window and stop the old backend (Render).
2. **Dump** from Supabase. Use the **direct** connection (port 5432, not the 6543 pooler):

```bash
pg_dump "postgresql://postgres:<password>@db.<project>.supabase.co:5432/postgres" \
  --schema=public --no-owner --no-privileges -Fc -f seekfactory.dump
```

3. **Restore** into the new database from the app server (it can reach the private endpoint):

```bash
docker run --rm -it -v $PWD:/work postgres:17 pg_restore \
  -d "postgresql://seekfactory_app:<password>@<rds-endpoint>:5432/seekfactory?sslmode=require" \
  --no-owner --no-privileges /work/seekfactory.dump
```

   The dump includes Flyway's `flyway_schema_history` table, so Flyway continues from the same
   version on the new database.
4. Start the new backend (A9 / B8). Check that `docker logs` shows no migration errors.
5. Copy uploaded files from the old server/volume into `/data/uploads` (or into OSS/S3 later).
6. Switch the frontend to the new API (section 6), test, then shut down Render.

> Fresh database instead of a migration: the repository's migrations `V3` and `V4` currently fail on
> an empty database (UUID vs VARCHAR id mismatch in the admin tables). This must be fixed in the
> code before starting on an empty database; migrating the existing Supabase data avoids it.

---

## 6. Connecting the Vercel frontend

In Vercel → Project → Settings → **Environment Variables** (Production):

| Variable | Value |
|---|---|
| `NEXT_PUBLIC_API_URL` | `https://api.seekfactory.com` |
| `BACKEND_URL` | `https://api.seekfactory.com` |

Redeploy the frontend after changing them. The web app talks to the backend through its own
`/api/proxy` route (server to server), so browser CORS only matters for direct requests such as
uploads to OSS/S3.

Remember the two frontend limits from section 1: uploads larger than about 4.5 MB fail through a
Vercel function (1.1), and Vercel reachability from mainland China must be tested (1.3).
Chat streams through the proxy are also bound by the Vercel function time limit; the browser
reconnects automatically, but a Hong Kong–hosted frontend avoids that entirely.

---

## 7. Checks after every deployment

Run from your laptop:

```bash
API=https://api.seekfactory.com

# 1. Up and healthy
curl -s $API/actuator/health                      # {"status":"UP"}

# 2. Public data served
curl -s "$API/api/v1/feed?tab=for-you&page=0&size=3" | head -c 300

# 3. Auth rules: anonymous write is refused with 401 (not 403 / 500)
curl -s -o /dev/null -w "%{http_code}\n" -X POST $API/api/v1/feed/any-id/like

# 4. Swagger hidden in production (expect 401 or 404, never 200)
curl -s -o /dev/null -w "%{http_code}\n" $API/swagger-ui.html

# 5. HTTPS certificate valid and HTTP redirects
curl -sI http://api.seekfactory.com | head -3
```

Then in the browser on the production site: sign in, like a seek, send a chat message (it must
appear instantly in a second browser signed in as the factory), upload a photo, and place an
order request.

---

## 8. Deploying updates and rolling back

- Every image is tagged with the Git commit (`github.sha`). **Never deploy `latest` by hand.**
- **Before a release with a new Flyway migration**, take a manual database backup (RDS snapshot).
  Migrations only move forward. Rolling back the image does not undo a migration, so keep
  migrations additive (new columns / tables) so the previous image still runs.
- **Rollback**: re-run the `docker run` command with the previous tag:

```bash
docker rm -f seekfactory-api
docker run -d --name seekfactory-api --restart unless-stopped \
  -p 127.0.0.1:8080:8080 --env-file /opt/seekfactory/backend.env \
  -v /data/uploads:/data/uploads <registry>/axoraa-api:<previous-sha>
```

- List available tags: ACR console → repository → Tags, or
  `aws ecr list-images --repository-name seekfactory/axoraa-api --region ap-east-1`.

---

## 9. Scaling beyond one server

One 2 vCPU / 8 GiB server handles a launch comfortably. Before running **two or more** backend
servers behind a load balancer, three things in the code must change, otherwise features break:

1. **Chat live updates** (`SseServiceImpl`) keep open connections in the server's memory. A
   message sent through server 1 would not reach a buyer connected to server 2. Fix: publish chat
   events through **Redis pub/sub** (ApsaraDB for Redis / ElastiCache) so every server delivers them.
2. **Uploads** must be in OSS/S3 (sections 1.1 and 1.2), not on one server's disk.
3. **Scheduled jobs**, if any are added later, must run on one server only (e.g. ShedLock).

After that: put an **ALB** in front (idle timeout ≥ 900 s for chat), run two servers in two zones,
and deploy one at a time for zero-downtime releases. Or move to a managed container service
(Alibaba **ACK / SAE**, AWS **ECS on Fargate**). Keep at least one instance always running, with no
scale-to-zero, so there are no cold starts.

---

## 10. Troubleshooting

| Symptom | Likely cause and fix |
|---|---|
| Container restarts in a loop, log says `Schema validation: missing table` | The database is behind the code's migrations, or `SPRING_FLYWAY_ENABLED` is off. Check `flyway_schema_history`; start with the image that matches the database. |
| `Connection refused` / timeouts to the database | RDS whitelist / security group does not include the server; wrong (public vs private) endpoint; `sslmode` mismatch. |
| Everyone is logged out after a deploy | `JWT_SECRET` changed or is missing in the env file. |
| Chat messages only appear after a refresh | A proxy is buffering the stream: Caddy needs `flush_interval -1`; Nginx needs `proxy_buffering off`; the load balancer idle timeout is too short. |
| Uploads fail with `413` | Caddy `request_body max_size` or load balancer limit; through Vercel, the ~4.5 MB function limit (section 1.1). |
| Uploads fail with `403 Invalid CORS request` | The calling origin is not in `APP_CORS_ALLOWEDORIGINPATTERNS`. |
| `OutOfMemoryError` | Increase the instance size; the image already uses 75 % of container memory for the Java heap. |
| Uploaded files disappear after a deploy | `/data/uploads` is not mounted into the container (`-v /data/uploads:/data/uploads`) or `APP_MEDIA_STORAGE_DIR` is not `/data/uploads`. |
| Slow for users in mainland China | Test from mainland networks; enable OSS Transfer Acceleration; serve media through CDN; check Vercel reachability (1.3); consider ICP filing for mainland CDN nodes. |
| Password reset emails never arrive | `SPRING_MAIL_*` not set; the log shows `SMTP not configured`. |

---

### Summary checklist

- [ ] Region chosen: Hong Kong (`cn-hongkong` or `ap-east-1`)
- [ ] VPC, security groups (no public 8080 / 5432)
- [ ] Managed PostgreSQL in the same region, backups on, private endpoint only
- [ ] Data migrated from Supabase (section 5)
- [ ] Object storage bucket (+ CDN) created
- [ ] VM with Docker, data disk at `/data`, env file with **all** variables from section 2
- [ ] Container running with `--restart unless-stopped`, health `UP`
- [ ] Caddy (or ALB) with HTTPS on `api.seekfactory.com`, chat streaming verified
- [ ] Monitoring and alerts, snapshots, GitHub Actions deploy
- [ ] Vercel env vars point to the new API; Render shut down
- [ ] Before real video traffic: direct-to-OSS/S3 uploads (1.1) and a mainland reachability test (1.3)
