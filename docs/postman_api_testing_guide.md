# 🧪 SeekFactory REST API — Complete Postman Testing Guide

> **Stack:** Spring Boot 4.1.1 · Java 25 · PostgreSQL (Supabase) · JWT Security  
> **Base URL:** `http://localhost:8080` (Local) or `http://<YOUR_OCI_IP>` (Cloud)  
> **API Version Prefix:** `/api/v1`

---

## Table of Contents

1. [Postman Environment Setup & Automation](#1-postman-environment-setup--automation)
2. [Recommended Testing Sequence](#2-recommended-testing-sequence)
3. [Health & Diagnostics APIs](#3-health--diagnostics-apis)
4. [Authentication APIs (`/api/v1/auth`)](#4-authentication-apis-apiv1auth)
5. [User Profile APIs (`/api/v1/users`)](#5-user-profile-apis-apiv1users)
6. [Category & Taxonomy APIs (`/api/v1/categories`)](#6-category--taxonomy-apis-apiv1categories)
7. [Manufacturer Profiles (`/api/v1/manufacturers`)](#7-manufacturer-profiles-apiv1manufacturers)
8. [Product Catalog (`/api/v1/products`)](#8-product-catalog-apiv1products)
9. [Video Reels Feed (`/api/v1/feed`)](#9-video-reels-feed-apiv1feed)
10. [Reel Comments & Replies (`/api/v1/comments` & `/reels`)](#10-reel-comments--replies-apiv1comments--reels)
11. [RFQ Management (`/api/v1/rfqs`)](#11-rfq-management-apiv1rfqs)
12. [B2B Conversations (`/api/v1/conversations`)](#12-b2b-conversations-apiv1conversations)
13. [User Notifications (`/api/v1/notifications`)](#13-user-notifications-apiv1notifications)
14. [Admin Management (`/api/v1/admin`)](#14-admin-management-apiv1admin)
15. [Negative Testing & Error Handling](#15-negative-testing--error-handling)

---

## 1. Postman Environment Setup & Automation

Instead of copying and pasting JWT tokens manually for every request, configure a **Postman Environment**.

### Step 1: Create an Environment in Postman
1. Open Postman → Click **Environments** on the left sidebar → Click **+** (New Environment).
2. Name it: `SeekFactory - Local` (or `SeekFactory - Cloud`).
3. Add the following variables:

| Variable | Initial Value | Current Value | Description |
|---|---|---|---|
| `baseUrl` | `http://localhost:8080` | `http://localhost:8080` | Backend host |
| `authToken` | *(leave blank)* | *(leave blank)* | Auto-populated by login/register |
| `refreshToken` | *(leave blank)* | *(leave blank)* | Auto-populated by login |
| `adminToken` | *(leave blank)* | *(leave blank)* | Auto-populated by admin login |
| `userId` | *(leave blank)* | *(leave blank)* | Auto-populated user ID |
| `sampleReelId` | `reel-cnc-01` | `reel-cnc-01` | Seed data reel ID |
| `sampleCommentId`| *(leave blank)* | *(leave blank)* | Created during comment test |
| `sampleCategoryId`| `cnc-machining` | `cnc-machining` | Seed data category slug |

### Step 2: Auto-Save JWT Script (The Pro Trick)
In Postman, for the **Register**, **Login**, and **Refresh Token** requests, click the **Scripts → Post-response** (or **Tests**) tab and paste this script:

```javascript
if (pm.response.code === 200 || pm.response.code === 201) {
    const json = pm.response.json();
    if (json.data && json.data.access_token) {
        pm.environment.set("authToken", json.data.access_token);
        pm.environment.set("refreshToken", json.data.refresh_token);
        if (json.data.user) {
            pm.environment.set("userId", json.data.user.id);
        }
        console.log("✅ Auth Token saved to environment successfully!");
    }
}
```

Now, every subsequent request with header `Authorization: Bearer {{authToken}}` will automatically be authenticated!

---

## 2. Recommended Testing Sequence

Execute tests in this exact order so dependent data exists:

```
[1. Health Check] ➔ [2. Taxonomy / Categories] ➔ [3. Manufacturers & Products]
       ↓
[4. Register Buyer] ➔ [5. Login Buyer] ➔ [6. View Profile (/me)]
       ↓
[7. Video Feed] ➔ [8. Post Comment & Reply] ➔ [9. Submit RFQ]
       ↓
[10. View Conversations] ➔ [11. Check Notifications] ➔ [12. Admin Actions]
```

---

## 3. Health & Diagnostics APIs

### 3.1 Actuator Health Check
* **Method:** `GET`
* **URL:** `{{baseUrl}}/actuator/health`
* **Auth:** None (Public)
* **Headers:** None
* **Expected Status:** `200 OK`
* **Expected Response:**
```json
{
  "status": "UP"
}
```
* **What it validates:** Spring Boot is up and database connection pool is healthy.

### 3.2 Swagger UI Docs
* **Method:** `GET`
* **URL:** `{{baseUrl}}/swagger-ui/index.html`
* **Expected Status:** `200 OK` (renders HTML page in browser)
* **OpenAPI Spec:** `{{baseUrl}}/api-docs`

---

## 4. Authentication APIs (`/api/v1/auth`)

### 4.1 Register a New Buyer
* **Method:** `POST`
* **URL:** `{{baseUrl}}/api/v1/auth/register`
* **Auth:** None
* **Headers:** `Content-Type: application/json`
* **Body (Raw JSON):**
```json
{
  "name": "Arjun Sharma",
  "email": "arjun.buyer@example.com",
  "password": "Password@123",
  "role": "BUYER",
  "companyName": "Apex Precision Components India",
  "industry": "Automotive Tier-2",
  "country": "India",
  "phone": "+919876543210"
}
```
* **Expected Status:** `201 Created`
* **Expected Response:**
```json
{
  "success": true,
  "message": "Registration successful",
  "data": {
    "access_token": "eyJhbGciOiJIUzI1NiJ9...",
    "refresh_token": "eyJhbGciOiJIUzI1NiJ9...",
    "token_type": "Bearer",
    "expires_in": 86400,
    "user": {
      "id": "c1f76020-0326-4fa2-9b24-5d55b8ef68e1",
      "email": "arjun.buyer@example.com",
      "name": "Arjun Sharma",
      "role": "BUYER",
      "company_name": "Apex Precision Components India",
      "industry": "Automotive Tier-2",
      "country": "India",
      "avatar_url": null
    }
  },
  "timestamp": "2026-09-18T22:45:10Z"
}
```

---

### 4.2 Register a Supplier (Manufacturer)
* **Method:** `POST`
* **URL:** `{{baseUrl}}/api/v1/auth/register`
* **Body (Raw JSON):**
```json
{
  "name": "Chen Wei",
  "email": "chen.wei@dongguanmould.com",
  "password": "Password@123",
  "role": "SUPPLIER",
  "companyName": "Dongguan Precision Mould Co., Ltd.",
  "industry": "Tooling & CNC Machining",
  "country": "China",
  "phone": "+8613800138000"
}
```
* **Expected Status:** `201 Created`

---

### 4.3 Login with Email and Password
* **Method:** `POST`
* **URL:** `{{baseUrl}}/api/v1/auth/login`
* **Body (Raw JSON):**
```json
{
  "email": "arjun.buyer@example.com",
  "password": "Password@123"
}
```
* **Expected Status:** `200 OK`
* **Expected Response:**
```json
{
  "success": true,
  "message": "Login successful",
  "data": {
    "access_token": "eyJhbGciOiJIUzI1NiJ9...",
    "refresh_token": "eyJhbGciOiJIUzI1NiJ9...",
    "token_type": "Bearer",
    "expires_in": 86400,
    "user": {
      "id": "c1f76020-0326-4fa2-9b24-5d55b8ef68e1",
      "email": "arjun.buyer@example.com",
      "name": "Arjun Sharma",
      "role": "BUYER"
    }
  }
}
```

---

### 4.4 Login with Phone & Mock OTP
* **Method:** `POST`
* **URL:** `{{baseUrl}}/api/v1/auth/login/phone`
* **Body (Raw JSON):**
```json
{
  "phone": "+919876543210",
  "otp": "123456"
}
```
* **Expected Status:** `200 OK` (OTP `123456` is standard development mock)

---

### 4.5 Refresh Access Token
* **Method:** `POST`
* **URL:** `{{baseUrl}}/api/v1/auth/refresh`
* **Body (Raw JSON):**
```json
{
  "refresh_token": "{{refreshToken}}"
}
```
* **Expected Status:** `200 OK`
* **Expected Response:** Generates a new `access_token`.

---

### 4.6 Get Current User (`/auth/me`)
* **Method:** `GET`
* **URL:** `{{baseUrl}}/api/v1/auth/me`
* **Headers:** `Authorization: Bearer {{authToken}}`
* **Expected Status:** `200 OK`
* **Expected Response:**
```json
{
  "success": true,
  "data": {
    "id": "{{userId}}",
    "email": "arjun.buyer@example.com",
    "name": "Arjun Sharma",
    "role": "BUYER"
  }
}
```

---

### 4.7 Logout
* **Method:** `POST`
* **URL:** `{{baseUrl}}/api/v1/auth/logout`
* **Headers:** `Authorization: Bearer {{authToken}}`
* **Expected Status:** `200 OK`
* **Expected Response:**
```json
{
  "success": true,
  "message": "Logout successful"
}
```

---

## 5. User Profile APIs (`/api/v1/users`)

### 5.1 Get Profile
* **Method:** `GET`
* **URL:** `{{baseUrl}}/api/v1/users/me`
* **Headers:** `Authorization: Bearer {{authToken}}`
* **Expected Status:** `200 OK`

### 5.2 Update Profile
* **Method:** `PUT`
* **URL:** `{{baseUrl}}/api/v1/users/me`
* **Headers:** 
  * `Authorization: Bearer {{authToken}}`
  * `Content-Type: application/json`
* **Body (Raw JSON):**
```json
{
  "name": "Arjun K. Sharma",
  "companyName": "Apex Precision Components Global Ltd",
  "industry": "Aerospace & Automotive",
  "country": "India",
  "avatarUrl": "https://images.seekfactory.com/avatars/arjun.jpg"
}
```
* **Expected Status:** `200 OK`
* **Expected Response:**
```json
{
  "success": true,
  "message": "Profile updated",
  "data": {
    "name": "Arjun K. Sharma",
    "company_name": "Apex Precision Components Global Ltd",
    "avatar_url": "https://images.seekfactory.com/avatars/arjun.jpg"
  }
}
```

---

## 6. Category & Taxonomy APIs (`/api/v1/categories`)

### 6.1 List All Categories (Flat list)
* **Method:** `GET`
* **URL:** `{{baseUrl}}/api/v1/categories`
* **Auth:** None (Public)
* **Expected Status:** `200 OK`
* **Expected Response:**
```json
{
  "success": true,
  "data": [
    {
      "id": "cat-cnc",
      "name": "CNC Machining",
      "slug": "cnc-machining",
      "icon": "cog",
      "parent_id": null
    }
  ]
}
```

### 6.2 List Root Categories Only
* **Method:** `GET`
* **URL:** `{{baseUrl}}/api/v1/categories/roots`
* **Auth:** None (Public)
* **Expected Status:** `200 OK` (Only items where `parentId == null`)

### 6.3 List Subcategories under a Parent Slug
* **Method:** `GET`
* **URL:** `{{baseUrl}}/api/v1/categories/cnc-machining/children`
* **Auth:** None (Public)
* **Expected Status:** `200 OK` (Returns items like 5-Axis Milling, Lathe Turning)

### 6.4 Get Single Category by Slug
* **Method:** `GET`
* **URL:** `{{baseUrl}}/api/v1/categories/cnc-machining`
* **Auth:** None (Public)
* **Expected Status:** `200 OK`

---

## 7. Manufacturer Profiles (`/api/v1/manufacturers`)

### 7.1 List All Manufacturers
* **Method:** `GET`
* **URL:** `{{baseUrl}}/api/v1/manufacturers`
* **Auth:** None (Public)
* **Expected Status:** `200 OK`

### 7.2 List Verified Manufacturers
* **Method:** `GET`
* **URL:** `{{baseUrl}}/api/v1/manufacturers/verified?limit=10`
* **Auth:** None (Public)
* **Expected Status:** `200 OK`
* **Validation:** All items in `data` array must have `"verified": true`.

### 7.3 Get Manufacturer Detail by Slug (Composite Profile)
* **Method:** `GET`
* **URL:** `{{baseUrl}}/api/v1/manufacturers/dongguan-precision-mould`
* **Auth:** None (Public)
* **Expected Status:** `200 OK`
* **Expected Response:**
```json
{
  "success": true,
  "data": {
    "id": "mfg-01",
    "name": "Dongguan Precision Mould Co., Ltd.",
    "slug": "dongguan-precision-mould",
    "verified": true,
    "location": "Dongguan, Guangdong, China",
    "factory_size": "25,000 sq.m",
    "employees": "250-500",
    "export_countries": ["India", "Germany", "USA", "Japan"],
    "products": [
      {
        "id": "prod-01",
        "name": "Custom Precision Aluminum Injection Die",
        "slug": "custom-precision-aluminum-die"
      }
    ],
    "reels": [
      {
        "id": "reel-01",
        "video_url": "https://video.seekfactory.com/reels/mfg01_cnc_demo.mp4"
      }
    ]
  }
}
```

---

## 8. Product Catalog (`/api/v1/products`)

### 8.1 List Trending Products
* **Method:** `GET`
* **URL:** `{{baseUrl}}/api/v1/products/trending?limit=15`
* **Auth:** None (Public)
* **Expected Status:** `200 OK`

### 8.2 Get Product Detail by Slug
* **Method:** `GET`
* **URL:** `{{baseUrl}}/api/v1/products/5-axis-cnc-machined-aerospace-bracket`
* **Auth:** None (Public)
* **Expected Status:** `200 OK`
* **Expected Response:**
```json
{
  "success": true,
  "data": {
    "id": "prod-aerospace-bracket",
    "name": "5-Axis CNC Machined Aerospace Bracket",
    "slug": "5-axis-cnc-machined-aerospace-bracket",
    "description": "High-tolerance aircraft aluminum bracket",
    "primary_image_url": "https://images.seekfactory.com/products/bracket_1.jpg",
    "moq": 50,
    "lead_time_days": 14,
    "specs": {
      "Material": "AL 7075-T6",
      "Tolerance": "±0.005 mm",
      "Surface Treatment": "Hard Anodizing Type III (50 microns)",
      "Certifications": "AS9100D, ISO 9001:2015"
    },
    "manufacturer": {
      "id": "mfg-01",
      "name": "Dongguan Precision Mould Co., Ltd.",
      "slug": "dongguan-precision-mould",
      "verified": true
    },
    "related_products": []
  }
}
```

### 8.3 List Products by Category
* **Method:** `GET`
* **URL:** `{{baseUrl}}/api/v1/products/category/cat-cnc`
* **Auth:** None (Public)
* **Expected Status:** `200 OK`

---

## 9. Video Reels Feed (`/api/v1/feed`)

### 9.1 Get "For You" Feed
* **Method:** `GET`
* **URL:** `{{baseUrl}}/api/v1/feed?tab=for-you`
* **Auth:** None (Public)
* **Expected Status:** `200 OK`
* **Expected Response:**
```json
{
  "success": true,
  "data": [
    {
      "id": "feed-item-01",
      "reel": {
        "id": "reel-01",
        "video_url": "https://video.seekfactory.com/reels/die_casting_600t.mp4",
        "caption": "High pressure 600-ton aluminum die casting live run",
        "likes_count": 342,
        "comments_count": 28,
        "saves_count": 89
      },
      "manufacturer": {
        "id": "mfg-01",
        "name": "Dongguan Precision Mould Co., Ltd.",
        "slug": "dongguan-precision-mould",
        "verified": true
      },
      "primary_product_slug": "die-casting-casing"
    }
  ]
}
```

### 9.2 Get "Following" Feed
* **Method:** `GET`
* **URL:** `{{baseUrl}}/api/v1/feed?tab=following`
* **Auth:** None (Public)
* **Expected Status:** `200 OK`

---

## 10. Reel Comments & Replies (`/api/v1/comments` & `/reels`)

### 10.1 List Comments on a Reel
* **Method:** `GET`
* **URL:** `{{baseUrl}}/api/v1/reels/{{sampleReelId}}/comments`
* **Auth:** None (Public)
* **Expected Status:** `200 OK`

### 10.2 Add a Comment to a Reel
* **Method:** `POST`
* **URL:** `{{baseUrl}}/api/v1/reels/{{sampleReelId}}/comments`
* **Headers:** 
  * `Authorization: Bearer {{authToken}}`
  * `Content-Type: application/json`
* **Body (Raw JSON):**
```json
{
  "content": "What is the maximum shot weight on this 600-ton die casting machine?"
}
```
* **Postman Test Script:** Add this to test script to capture `sampleCommentId`:
```javascript
if (pm.response.code === 201) {
    pm.environment.set("sampleCommentId", pm.response.json().data.id);
}
```
* **Expected Status:** `201 Created`
* **Expected Response:**
```json
{
  "success": true,
  "message": "Comment added",
  "data": {
    "id": "comment-9821",
    "reel_id": "reel-cnc-01",
    "user_id": "{{userId}}",
    "user_name": "Arjun K. Sharma",
    "content": "What is the maximum shot weight on this 600-ton die casting machine?",
    "likes_count": 0,
    "replies_count": 0
  }
}
```

### 10.3 Reply to a Comment
* **Method:** `POST`
* **URL:** `{{baseUrl}}/api/v1/comments/{{sampleCommentId}}/replies`
* **Headers:** 
  * `Authorization: Bearer {{authToken}}`
  * `Content-Type: application/json`
* **Body (Raw JSON):**
```json
{
  "content": "The maximum shot weight for aluminum alloy is 6.5 kg per cycle."
}
```
* **Expected Status:** `201 Created`

---

## 11. RFQ Management (`/api/v1/rfqs`)

### 11.1 Submit a New RFQ
* **Method:** `POST`
* **URL:** `{{baseUrl}}/api/v1/rfqs`
* **Headers:** 
  * `Authorization: Bearer {{authToken}}`
  * `Content-Type: application/json`
* **Body (Raw JSON):**
```json
{
  "productName": "Custom Transmission Valve Body",
  "targetPrice": 45.50,
  "currency": "USD",
  "quantity": 1500,
  "unit": "PCS",
  "incoterm": "FOB",
  "destinationCountry": "India",
  "notes": "Must meet ASTM A536 Grade 65-45-12 ductile iron. Full CMM dimensional report required per batch.",
  "specs": {
    "Material": "Ductile Iron GGG40",
    "Bore Tolerance": "ISO H7",
    "Surface Finish": "Ra 0.8",
    "Pressure Rating": "350 Bar"
  }
}
```
* **Expected Status:** `201 Created`
* **Expected Response (matching frontend contract):**
```json
{
  "success": true,
  "message": "RFQ submitted successfully",
  "data": {
    "ok": true,
    "id": "rfq-34091-uuid",
    "referenceNumber": "RFQ-2026-0001"
  }
}
```

### 11.2 List Current User's Submitted RFQs
* **Method:** `GET`
* **URL:** `{{baseUrl}}/api/v1/rfqs`
* **Headers:** `Authorization: Bearer {{authToken}}`
* **Expected Status:** `200 OK`
* **Expected Response:**
```json
{
  "success": true,
  "data": [
    {
      "id": "rfq-34091-uuid",
      "reference_number": "RFQ-2026-0001",
      "product_name": "Custom Transmission Valve Body",
      "status": "SUBMITTED",
      "quantity": 1500,
      "quotes_count": 0
    }
  ]
}
```

---

## 12. B2B Conversations (`/api/v1/conversations`)

### 12.1 List Recent Conversations
* **Method:** `GET`
* **URL:** `{{baseUrl}}/api/v1/conversations?limit=20`
* **Headers:** `Authorization: Bearer {{authToken}}`
* **Expected Status:** `200 OK`
* **Expected Response:**
```json
{
  "success": true,
  "data": [
    {
      "id": "conv-101",
      "manufacturer": {
        "id": "mfg-01",
        "name": "Dongguan Precision Mould Co., Ltd.",
        "slug": "dongguan-precision-mould",
        "avatar_url": "https://images.seekfactory.com/logos/mfg01.png"
      },
      "last_message": {
        "content": "We have reviewed your CAD model and can offer $42.00 FOB.",
        "sent_at": "2026-09-18T18:30:00Z"
      },
      "unread_count": 1
    }
  ]
}
```

---

## 13. User Notifications (`/api/v1/notifications`)

### 13.1 List All User Notifications
* **Method:** `GET`
* **URL:** `{{baseUrl}}/api/v1/notifications`
* **Headers:** `Authorization: Bearer {{authToken}}`
* **Expected Status:** `200 OK`
* **Expected Response:**
```json
{
  "success": true,
  "data": [
    {
      "id": "notif-01",
      "type": "QUOTE",
      "title": "New Quotation Received",
      "body": "Dongguan Precision Mould submitted a quote for RFQ-2026-0001.",
      "is_read": false,
      "created_at": "2026-09-18T20:15:00Z"
    }
  ]
}
```

### 13.2 Get Unread Notification Count
* **Method:** `GET`
* **URL:** `{{baseUrl}}/api/v1/notifications/unread-count`
* **Headers:** `Authorization: Bearer {{authToken}}`
* **Expected Status:** `200 OK`
* **Expected Response:**
```json
{
  "success": true,
  "data": {
    "count": 3
  }
}
```

### 13.3 Mark All Notifications as Read
* **Method:** `PUT`
* **URL:** `{{baseUrl}}/api/v1/notifications/mark-read`
* **Headers:** `Authorization: Bearer {{authToken}}`
* **Expected Status:** `200 OK`
* **Expected Response:**
```json
{
  "success": true,
  "message": "All notifications marked as read"
}
```

---

## 14. Admin Management (`/api/v1/admin`)

> ⚠️ **Note:** These endpoints require `ROLE_ADMIN` in the JWT token. Attempting to call these with a standard `BUYER` or `SUPPLIER` token MUST return `403 Forbidden`.

### 14.1 List All Users (Admin)
* **Method:** `GET`
* **URL:** `{{baseUrl}}/api/v1/admin/users`
* **Headers:** `Authorization: Bearer {{adminToken}}`
* **Expected Status:** `200 OK`
* **Expected Response:** List of all registered buyers and suppliers with email, name, role, company, and registration dates.

### 14.2 Toggle User Activation Status (Deactivate / Reactivate)
* **Method:** `PUT`
* **URL:** `{{baseUrl}}/api/v1/admin/users/{{userId}}/activate?active=false`
* **Headers:** `Authorization: Bearer {{adminToken}}`
* **Expected Status:** `200 OK`
* **Expected Response:**
```json
{
  "success": true,
  "message": "User deactivated successfully"
}
```

### 14.3 Verify a Manufacturer Profile
* **Method:** `PUT`
* **URL:** `{{baseUrl}}/api/v1/admin/manufacturers/mfg-01/verify?verified=true`
* **Headers:** `Authorization: Bearer {{adminToken}}`
* **Expected Status:** `200 OK`
* **Expected Response:**
```json
{
  "success": true,
  "message": "Manufacturer verified successfully"
}
```

---

## 15. Negative Testing & Error Handling

To ensure enterprise-grade security and reliability, test these failure scenarios:

| # | Test Scenario | Endpoint | Request Details | Expected Status | Expected Error Code |
|---|---|---|---|:---:|---|
| **1** | Missing Auth Header | `POST /api/v1/rfqs` | No `Authorization` header | `401` | `UNAUTHORIZED` |
| **2** | Malformed JWT Token | `GET /api/v1/users/me` | `Authorization: Bearer invalid.token.xyz` | `401` | `UNAUTHORIZED` |
| **3** | Non-Admin hitting Admin API | `GET /api/v1/admin/users` | Token with `ROLE_BUYER` | `403` | `FORBIDDEN` |
| **4** | Duplicate Email Register | `POST /api/v1/auth/register` | Existing email address | `409` | `DUPLICATE_RESOURCE` |
| **5** | Wrong Login Password | `POST /api/v1/auth/login` | Valid email, wrong password | `401` | `INVALID_CREDENTIALS` |
| **6** | Validation Error (Blank Name) | `POST /api/v1/auth/register` | `"name": ""` | `400` | `VALIDATION_FAILED` |
| **7** | Non-existent Resource | `GET /api/v1/products/fake-slug` | Slug that doesn't exist | `404` | `NOT_FOUND` |

### Sample Error Response Format (GlobalExceptionHandler)
```json
{
  "success": false,
  "message": "Validation failed for one or more fields",
  "status": 400,
  "errors": [
    {
      "field": "email",
      "message": "Must be a well-formed email address"
    },
    {
      "field": "password",
      "message": "Password must be at least 8 characters"
    }
  ],
  "timestamp": "2026-09-18T22:50:00Z"
}
```
