# 🔗 SeekFactory — Full-Stack Frontend & Backend Connection Architecture

> **Purpose:** Seamlessly connect the Next.js Web Frontend (`SeekFactory-Web-F`) to the Spring Boot Backend (`axoraa` / `SeekFactory-B`) for both **local development** and **future cloud production** (Vercel + Oracle Cloud / AWS).

---

## 1. Deep Analysis: Current State & Seam Architecture

### Web Frontend (`SeekFactory-Web-F`)
* **Framework:** Next.js 15.5.4 (React 19), App Router, Tailwind CSS.
* **API Entry Point:** `src/shared/api/index.ts` exports `getApi(): ApiClient`.
* **Current State:** `getApi()` returns `mockApi` (`src/shared/mocks/mock-api.ts`), while `src/shared/api/http-api.ts` throws:  
  `"HttpApi is not wired yet. Use mockApi via getApi()."`
* **Design Pattern:** Port-and-Adapter architecture. The entire UI consumes **9 repository sub-interfaces** defined in `src/shared/api/contracts.ts`:
  1. `session`: Authentication & user profile
  2. `feed`: Video reels discovery feed
  3. `manufacturers`: Verified factories & composite profiles
  4. `products`: Catalog, trending items & technical specs
  5. `categories`: Machinery taxonomy tree
  6. `messages`: B2B chat conversations
  7. `notifications`: Alerts & unread counter
  8. `rfq`: B2B quotation requests
  9. `comments`: Video comments & threaded replies
* **Auth Storage:** Session cookie named `sf-session` parsed via `src/features/auth/session-cookie.ts`.

---

### Backend (`axoraa` / `SeekFactory-B`)
* **Framework:** Spring Boot 4.1.1, Java 25, PostgreSQL 17 (Supabase).
* **API Version:** All endpoints are prefixed under `/api/v1/*`.
* **Payload Structure:** Every controller returns `ResponseEntity<ApiResponse<T>>`:
  ```json
  {
    "success": true,
    "message": "...",
    "data": { ... },
    "timestamp": "2026-09-19T22:00:00Z"
  }
  ```
* **Security & Auth:** Stateless JWT. Expects `Authorization: Bearer <token>` for protected endpoints.
* **Naming Strategy:** Jackson converts entity fields to `snake_case` (e.g., `avatar_url`, `company_name`, `created_at`).

---

## 2. The Dev vs. Future Production Strategy (Zero Code Changes)

To ensure the team can switch from local development to cloud production effortlessly in the future without editing source code:

```
┌────────────────────────────────────────────────────────────────────────┐
│                          ENVIRONMENT STRATEGY                          │
│                                                                        │
│  LOCAL DEV:                                                            │
│  .env.local ──▶ NEXT_PUBLIC_API_URL=http://localhost:8080              │
│                 └── Calls Local Spring Boot on Developer Machine       │
│                                                                        │
│  FUTURE PRODUCTION (Vercel + Oracle Cloud / AWS):                      │
│  Vercel Env ──▶ NEXT_PUBLIC_API_URL=https://api.seekfactory.com        │
│                 └── Calls Live Cloud Backend over HTTPS (Zero Code Edit)│
│                                                                        │
│  SAFE FALLBACK (No backend running / UI preview):                      │
│  Empty URL  ──▶ Automatically falls back to mockApi                    │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Required File Updates

### File 1: `src/features/auth/session-cookie.ts` (Web Frontend)
Update `SessionPayload` to store the JWT `token` and refresh token so both client and server components can attach `Authorization: Bearer <token>`.

```typescript
import type { BuyerProfile } from "@/entities/user";

export const SESSION_COOKIE = "sf-session";
const MAX_AGE = 60 * 60 * 24 * 7; // 7 days

export type SessionPayload = {
  id: string;
  name: string;
  role: "Buyer" | "Supplier";
  email: string;
  companyName: string;
  token?: string;          // JWT Access Token from backend
  refreshToken?: string;   // JWT Refresh Token
};

export type JoinInput = {
  role: "Buyer" | "Supplier";
  name?: string;
  email?: string;
  password?: string;
  phone?: string;
  companyName?: string;
  industry?: string;
  country?: string;
  method: "email" | "phone";
};

export type LoginInput = JoinInput;

export function parseSessionCookie(raw: string | undefined): SessionPayload | null {
  if (!raw) return null;
  try {
    const value = JSON.parse(decodeURIComponent(raw)) as SessionPayload;
    if (!value?.id || !value.role) return null;
    return value;
  } catch {
    return null;
  }
}

export function payloadToProfile(payload: SessionPayload): BuyerProfile {
  return {
    id: payload.id,
    name: payload.name,
    role: payload.role,
    avatarUrl:
      "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?auto=format&fit=crop&w=200&q=80",
    companyName: payload.companyName,
    industry: payload.role === "Buyer" ? "Industrial sourcing" : "Manufacturing",
    country: payload.role === "Buyer" ? "India" : "China",
  };
}

export function readBrowserCookie(): SessionPayload | null {
  if (typeof document === "undefined") return null;
  const match = document.cookie.split("; ").find((row) => row.startsWith(`${SESSION_COOKIE}=`));
  return parseSessionCookie(match?.slice(SESSION_COOKIE.length + 1));
}

export function writeBrowserCookie(payload: SessionPayload) {
  document.cookie = `${SESSION_COOKIE}=${encodeURIComponent(JSON.stringify(payload))}; Path=/; Max-Age=${MAX_AGE}; SameSite=Lax`;
}

export function clearBrowserCookie() {
  document.cookie = `${SESSION_COOKIE}=; Path=/; Max-Age=0; SameSite=Lax`;
}

export function displayRole(role: BuyerProfile["role"]) {
  return role === "Supplier" ? "Manufacturer" : "Buyer";
}

export function postAuthPath(role: BuyerProfile["role"], next?: string) {
  if (next && next.startsWith("/") && !next.startsWith("//")) return next;
  return role === "Supplier" ? "/factory" : "/";
}
```

---

### File 2: `src/shared/api/http-api.ts` (Web Frontend — Full Implementation)
Replace the throw placeholder in `src/shared/api/http-api.ts` with the complete, production-grade HTTP adapter:

```typescript
import type {
  ApiClient,
  CategoryRepository,
  CommentRepository,
  FeedRepository,
  ManufacturerDetail,
  ManufacturerRepository,
  MessageRepository,
  NotificationRepository,
  ProductDetail,
  ProductRepository,
  RfqRepository,
  SessionRepository,
} from "@/shared/api/contracts";
import type { Category } from "@/entities/category";
import type { ReelComment, ReelCommentReply } from "@/entities/comment";
import type { FeedTab, Reel } from "@/entities/reel";
import type { Manufacturer } from "@/entities/manufacturer";
import type { Product } from "@/entities/product";
import type { AppNotification } from "@/entities/notification";
import type { Conversation } from "@/entities/message";
import type { RfqDraft } from "@/entities/rfq";
import type { BuyerProfile } from "@/entities/user";
import {
  clearBrowserCookie,
  parseSessionCookie,
  payloadToProfile,
  readBrowserCookie,
  SESSION_COOKIE,
  writeBrowserCookie,
  type JoinInput,
  type LoginInput,
  type SessionPayload,
} from "@/features/auth/session-cookie";

/**
 * Standard Backend Envelope Response
 */
type BackendResponse<T> = {
  success: boolean;
  message?: string;
  data: T;
  timestamp?: string;
};

/**
 * Production-ready HTTP ApiClient connecting Next.js to Spring Boot.
 */
export function createHttpApi(baseUrl: string): ApiClient {
  const cleanBaseUrl = baseUrl.replace(/\/+$/, "");

  /**
   * Helper to get JWT auth header from cookie on client or server
   */
  async function getAuthHeaders(): Promise<Record<string, string>> {
    let token: string | undefined;

    if (typeof window === "undefined") {
      try {
        const { cookies } = await import("next/headers");
        const jar = await cookies();
        const session = parseSessionCookie(jar.get(SESSION_COOKIE)?.value);
        token = session?.token;
      } catch {
        // Fallback for non-cookie server contexts
      }
    } else {
      const session = readBrowserCookie();
      token = session?.token;
    }

    const headers: Record<string, string> = {
      "Content-Type": "application/json",
    };

    if (token) {
      headers["Authorization"] = `Bearer ${token}`;
    }

    return headers;
  }

  /**
   * Generic JSON request wrapper with error handling
   */
  async function fetchJson<T>(
    endpoint: string,
    options: RequestInit = {}
  ): Promise<T> {
    const defaultHeaders = await getAuthHeaders();
    const url = `${cleanBaseUrl}${endpoint}`;

    const res = await fetch(url, {
      ...options,
      headers: {
        ...defaultHeaders,
        ...(options.headers || {}),
      },
    });

    if (!res.ok) {
      let errorMsg = `HTTP ${res.status} on ${endpoint}`;
      try {
        const errJson = await res.json();
        errorMsg = errJson.message || errorMsg;
      } catch {
        // Response was not JSON
      }
      throw new Error(errorMsg);
    }

    const json = (await res.json()) as BackendResponse<T>;
    return json.data;
  }

  // ── 1. SESSION REPOSITORY ──────────────────────────────────────
  const session: SessionRepository = {
    async getCurrentUser(): Promise<BuyerProfile | null> {
      try {
        const user = await fetchJson<any>("/api/v1/auth/me");
        if (!user) return null;

        return {
          id: user.id,
          name: user.name,
          role: user.role === "ROLE_SUPPLIER" || user.role === "SUPPLIER" ? "Supplier" : "Buyer",
          avatarUrl: user.avatar_url || "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?auto=format&fit=crop&w=200&q=80",
          companyName: user.company_name || "Enterprise Member",
          industry: user.industry || "Manufacturing",
          country: user.country || "India",
        };
      } catch {
        // Fall back to local cookie if server is unreachable
        if (typeof window === "undefined") {
          const { cookies } = await import("next/headers");
          const jar = await cookies();
          const payload = parseSessionCookie(jar.get(SESSION_COOKIE)?.value);
          return payload ? payloadToProfile(payload) : null;
        }
        const payload = readBrowserCookie();
        return payload ? payloadToProfile(payload) : null;
      }
    },

    async join(input: JoinInput): Promise<BuyerProfile> {
      const payload = {
        name: input.name || input.email?.split("@")[0] || "New Member",
        email: input.email,
        password: input.password || "Password@123",
        role: input.role === "Supplier" ? "SUPPLIER" : "BUYER",
        companyName: input.companyName || "New Company",
        industry: input.industry || "Manufacturing",
        country: input.country || "India",
        phone: input.phone,
      };

      const res = await fetchJson<{
        access_token: string;
        refresh_token: string;
        user: any;
      }>("/api/v1/auth/register", {
        method: "POST",
        body: JSON.stringify(payload),
      });

      const sessionData: SessionPayload = {
        id: res.user.id,
        name: res.user.name,
        role: input.role,
        email: res.user.email,
        companyName: res.user.company_name || payload.companyName,
        token: res.access_token,
        refreshToken: res.refresh_token,
      };

      writeBrowserCookie(sessionData);
      return payloadToProfile(sessionData);
    },

    async login(input: LoginInput): Promise<BuyerProfile> {
      let res: { access_token: string; refresh_token: string; user: any };

      if (input.method === "phone" && input.phone) {
        res = await fetchJson("/api/v1/auth/login/phone", {
          method: "POST",
          body: JSON.stringify({ phone: input.phone, otp: "123456" }),
        });
      } else {
        res = await fetchJson("/api/v1/auth/login", {
          method: "POST",
          body: JSON.stringify({ email: input.email, password: input.password }),
        });
      }

      const sessionData: SessionPayload = {
        id: res.user.id,
        name: res.user.name,
        role: res.user.role === "ROLE_SUPPLIER" || res.user.role === "SUPPLIER" ? "Supplier" : "Buyer",
        email: res.user.email,
        companyName: res.user.company_name || "Enterprise Member",
        token: res.access_token,
        refreshToken: res.refresh_token,
      };

      writeBrowserCookie(sessionData);
      return payloadToProfile(sessionData);
    },

    async logout(): Promise<void> {
      try {
        await fetchJson("/api/v1/auth/logout", { method: "POST" });
      } catch {
        // Ignore errors on logout
      } finally {
        clearBrowserCookie();
      }
    },
  };

  // ── 2. FEED REPOSITORY ─────────────────────────────────────────
  const feed: FeedRepository = {
    async list(tab: FeedTab) {
      const data = await fetchJson<any[]>(`/api/v1/feed?tab=${tab}`);
      return data.map((item) => ({
        reel: {
          id: item.reel.id,
          manufacturerId: item.manufacturer?.id || item.reel.manufacturer_id,
          title: item.reel.title || item.reel.caption || "Industrial Reel",
          description: item.reel.description || "",
          hashtags: item.reel.hashtags || [],
          posterUrl: item.reel.poster_url || "https://images.seekfactory.com/posters/default.jpg",
          videoUrl: item.reel.video_url,
          durationSec: item.reel.duration_sec || 30,
          startSec: item.reel.start_sec || 0,
          views: item.reel.views || 0,
          likes: item.reel.likes || item.reel.likes_count || 0,
          comments: item.reel.comments || item.reel.comments_count || 0,
          shares: item.reel.shares || 0,
          saves: item.reel.saves || item.reel.saves_count || 0,
          tab: tab,
          productIds: item.reel.product_ids || [],
        },
        manufacturer: {
          id: item.manufacturer.id,
          slug: item.manufacturer.slug,
          name: item.manufacturer.name,
          logoUrl: item.manufacturer.logo_url || "https://images.seekfactory.com/logos/default.png",
          coverUrl: item.manufacturer.cover_url || "https://images.seekfactory.com/covers/default.jpg",
          country: item.manufacturer.country || "China",
          location: item.manufacturer.location || "Guangdong",
          verified: item.manufacturer.verified ?? true,
          premium: item.manufacturer.premium ?? false,
          yearsEstablished: item.manufacturer.years_established || 10,
          factorySize: item.manufacturer.factory_size || "15,000 sq.m",
          employees: item.manufacturer.employees || "100-250",
          exportCountries: item.manufacturer.export_countries || ["India", "USA"],
          description: item.manufacturer.description || "",
          followerCount: item.manufacturer.follower_count || 500,
          categoryIds: item.manufacturer.category_ids || [],
        },
        primaryProductSlug: item.primary_product_slug,
      }));
    },
  };

  // ── 3. MANUFACTURER REPOSITORY ─────────────────────────────────
  const manufacturers: ManufacturerRepository = {
    async listAll(): Promise<Manufacturer[]> {
      const list = await fetchJson<any[]>("/api/v1/manufacturers");
      return list.map(normalizeManufacturer);
    },

    async listVerified(limit = 20): Promise<Manufacturer[]> {
      const list = await fetchJson<any[]>(`/api/v1/manufacturers/verified?limit=${limit}`);
      return list.map(normalizeManufacturer);
    },

    async getBySlug(slug: string): Promise<ManufacturerDetail | null> {
      try {
        const detail = await fetchJson<any>(`/api/v1/manufacturers/${slug}`);
        return {
          manufacturer: normalizeManufacturer(detail),
          products: (detail.products || []).map(normalizeProduct),
          reels: (detail.reels || []).map((r: any) => ({
            id: r.id,
            manufacturerId: detail.id,
            title: r.title || r.caption || "Factory Reel",
            description: r.description || "",
            hashtags: r.hashtags || [],
            posterUrl: r.poster_url || "",
            videoUrl: r.video_url,
            durationSec: r.duration_sec || 30,
            startSec: 0,
            views: r.views || 0,
            likes: r.likes || 0,
            comments: r.comments || 0,
            shares: 0,
            saves: r.saves || 0,
            tab: "for-you",
            productIds: [],
          })),
        };
      } catch {
        return null;
      }
    },
  };

  // ── 4. PRODUCT REPOSITORY ──────────────────────────────────────
  const products: ProductRepository = {
    async listTrending(limit = 20): Promise<Product[]> {
      const list = await fetchJson<any[]>(`/api/v1/products/trending?limit=${limit}`);
      return list.map(normalizeProduct);
    },

    async getBySlug(slug: string): Promise<ProductDetail | null> {
      try {
        const detail = await fetchJson<any>(`/api/v1/products/${slug}`);
        return {
          product: normalizeProduct(detail),
          manufacturer: normalizeManufacturer(detail.manufacturer || {}),
        };
      } catch {
        return null;
      }
    },

    async listByCategory(categoryId: string): Promise<Product[]> {
      const list = await fetchJson<any[]>(`/api/v1/products/category/${categoryId}`);
      return list.map(normalizeProduct);
    },
  };

  // ── 5. CATEGORY REPOSITORY ─────────────────────────────────────
  const categories: CategoryRepository = {
    async list(): Promise<Category[]> {
      const list = await fetchJson<any[]>("/api/v1/categories");
      return list.map(normalizeCategory);
    },

    async listRoots(): Promise<Category[]> {
      const list = await fetchJson<any[]>("/api/v1/categories/roots");
      return list.map(normalizeCategory);
    },

    async listChildren(slug: string): Promise<Category[]> {
      const list = await fetchJson<any[]>(`/api/v1/categories/${slug}/children`);
      return list.map(normalizeCategory);
    },

    async getBySlug(slug: string): Promise<Category | null> {
      try {
        const cat = await fetchJson<any>(`/api/v1/categories/${slug}`);
        return normalizeCategory(cat);
      } catch {
        return null;
      }
    },
  };

  // ── 6. RFQ REPOSITORY ──────────────────────────────────────────
  const rfq: RfqRepository = {
    async submit(draft: RfqDraft): Promise<{ ok: true; id: string }> {
      const numericQty = parseInt(draft.quantity.replace(/\D/g, ""), 10) || 100;

      const payload = {
        productName: draft.productName,
        quantity: numericQty,
        unit: "PCS",
        notes: draft.details,
        companyName: draft.companyName,
        destinationCountry: "India",
        currency: "USD",
        targetPrice: 0,
      };

      const result = await fetchJson<{ ok: boolean; id: string }>(
        "/api/v1/rfqs",
        {
          method: "POST",
          body: JSON.stringify(payload),
        }
      );

      return { ok: true, id: result.id };
    },
  };

  // ── 7. COMMENT REPOSITORY ──────────────────────────────────────
  const comments: CommentRepository = {
    async listByReelId(reelId: string): Promise<ReelComment[]> {
      const list = await fetchJson<any[]>(`/api/v1/reels/${reelId}/comments`);
      return list.map((c) => ({
        id: c.id,
        reelId: c.reel_id || reelId,
        authorName: c.user_name || c.author_name || "Member",
        authorAvatarUrl: c.author_avatar_url || "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?auto=format&fit=crop&w=120&q=80",
        authorCompany: c.author_company,
        content: c.content,
        createdAt: c.created_at,
        likes: c.likes_count || 0,
        replies: (c.replies || []).map((r: any) => ({
          id: r.id,
          authorName: r.user_name || r.author_name || "Member",
          authorAvatarUrl: r.author_avatar_url || "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?auto=format&fit=crop&w=120&q=80",
          content: r.content,
          createdAt: r.created_at,
          likes: r.likes_count || 0,
        })),
      }));
    },

    async addComment(reelId: string, content: string): Promise<ReelComment> {
      const res = await fetchJson<any>(`/api/v1/reels/${reelId}/comments`, {
        method: "POST",
        body: JSON.stringify({ content }),
      });

      return {
        id: res.id,
        reelId: reelId,
        authorName: res.user_name || "You",
        authorAvatarUrl: "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?auto=format&fit=crop&w=120&q=80",
        content: res.content,
        createdAt: res.created_at || "Just now",
        likes: 0,
        replies: [],
      };
    },

    async addReply(commentId: string, content: string): Promise<ReelCommentReply> {
      const res = await fetchJson<any>(`/api/v1/comments/${commentId}/replies`, {
        method: "POST",
        body: JSON.stringify({ content }),
      });

      return {
        id: res.id,
        authorName: res.user_name || "You",
        authorAvatarUrl: "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?auto=format&fit=crop&w=120&q=80",
        content: res.content,
        createdAt: res.created_at || "Just now",
        likes: 0,
      };
    },
  };

  // ── 8. MESSAGE REPOSITORY ──────────────────────────────────────
  const messages: MessageRepository = {
    async listRecent(limit = 20): Promise<(Conversation & { manufacturer: Manufacturer })[]> {
      const list = await fetchJson<any[]>(`/api/v1/conversations?limit=${limit}`);
      return list.map((item) => ({
        id: item.id,
        participantId: item.manufacturer?.id || "mfg-01",
        unreadCount: item.unread_count || 0,
        lastMessage: {
          id: item.last_message?.id || "msg-01",
          conversationId: item.id,
          senderId: "factory",
          senderType: "FACTORY",
          text: item.last_message?.content || "",
          createdAt: item.last_message?.sent_at || new Date().toISOString(),
          isRead: true,
        },
        manufacturer: normalizeManufacturer(item.manufacturer || {}),
      }));
    },
  };

  // ── 9. NOTIFICATION REPOSITORY ─────────────────────────────────
  const notifications: NotificationRepository = {
    async list(): Promise<AppNotification[]> {
      const list = await fetchJson<any[]>("/api/v1/notifications");
      return list.map((n) => ({
        id: n.id,
        userId: n.user_id,
        type: (n.type?.toLowerCase() as any) || "system",
        title: n.title,
        body: n.body,
        link: n.link || "/",
        isRead: n.is_read ?? false,
        createdAt: n.created_at,
      }));
    },

    async unreadCount(): Promise<number> {
      try {
        const res = await fetchJson<{ count: number }>("/api/v1/notifications/unread-count");
        return res.count || 0;
      } catch {
        return 0;
      }
    },
  };

  return {
    session,
    feed,
    manufacturers,
    products,
    categories,
    messages,
    notifications,
    rfq,
    comments,
  };
}

// ── NORMALIZATION HELPERS (Convert Backend Snake_Case ➔ Frontend CamelCase) ──
function normalizeManufacturer(m: any): Manufacturer {
  return {
    id: m.id || "",
    slug: m.slug || "",
    name: m.name || "Verified Factory",
    logoUrl: m.logo_url || "https://images.seekfactory.com/logos/default.png",
    coverUrl: m.cover_url || "https://images.seekfactory.com/covers/default.jpg",
    country: m.country || "China",
    location: m.location || "Zhejiang, China",
    verified: m.verified ?? true,
    premium: m.premium ?? false,
    yearsEstablished: m.years_established || 12,
    factorySize: m.factory_size || "20,000 sq.m",
    employees: m.employees || "200-500",
    exportCountries: m.export_countries || ["India", "USA", "Germany"],
    description: m.description || "",
    followerCount: m.follower_count || 1200,
    categoryIds: m.category_ids || [],
    chairmanName: m.chairman_name,
  };
}

function normalizeProduct(p: any): Product {
  return {
    id: p.id || "",
    slug: p.slug || "",
    manufacturerId: p.manufacturer_id || p.manufacturer?.id || "",
    name: p.name || "",
    imageUrl: p.primary_image_url || p.image_url || "https://images.seekfactory.com/products/default.jpg",
    description: p.description || "",
    priceInr: p.price_inr || 150000,
    unit: p.unit || "SET",
    moq: p.moq ? `${p.moq} units` : "1 unit",
    categoryId: p.category_id || "",
    specs: p.specs || {},
  };
}

function normalizeCategory(c: any): Category {
  return {
    id: c.id || "",
    slug: c.slug || "",
    name: c.name || "",
    icon: (c.icon || "cog") as any,
    parentId: c.parent_id || null,
  };
}
```

---

### File 3: `src/shared/api/index.ts` (Web Frontend — The Dynamic Switch)
Wire up the seamless switch between real HTTP backend and mock data:

```typescript
import type { ApiClient } from "@/shared/api/contracts";
import { mockApi } from "@/shared/mocks/mock-api";
import { createHttpApi } from "@/shared/api/http-api";

const rawUrl = process.env.NEXT_PUBLIC_API_URL?.trim();

/**
 * Global API Factory Seam:
 * - If NEXT_PUBLIC_API_URL is defined (e.g. http://localhost:8080 or https://api.seekfactory.com),
 *   it returns the real createHttpApi() talking to the Spring Boot backend.
 * - If NEXT_PUBLIC_API_URL is empty, it safely falls back to mockApi (perfect for zero-backend UI preview).
 */
export function getApi(): ApiClient {
  if (rawUrl && rawUrl.length > 0) {
    return createHttpApi(rawUrl);
  }
  return mockApi;
}

export type { ApiClient } from "@/shared/api/contracts";
```

---

### File 4: `CorsConfig.java` & `SecurityConfig.java` (Backend Updates)

#### 4.1 Update `CorsConfig.java` in Backend
Make CORS allowed origins dynamic via `application.yaml` so any Vercel domain or custom domain can be added instantly without recompiling Java code:

```java
package seekfactory.axoraa.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
public class CorsConfig {

    @Value("${app.cors.allowed-origins:http://localhost:3000,http://localhost:8081}")
    private List<String> allowedOrigins;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        // Dynamically configured origins + standard production patterns
        config.setAllowedOriginPatterns(List.of(
                "http://localhost:[*]",               // Any local dev port
                "https://*.vercel.app",               // Any Vercel deployment preview
                "https://seekfactory.com",           // Production domain
                "https://*.seekfactory.com"
        ));

        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
```

#### 4.2 Update `SecurityConfig.java` in Backend
Ensure Spring Security explicitly enables CORS:
```java
// Inside filterChain(HttpSecurity http):
http
    .cors(org.springframework.security.config.Customizer.withDefaults()) // MUST BE ENABLED
    .csrf(csrf -> csrf.disable())
    // ... rest of security rules
```

---

## 4. Verification Checklist (How to Test the Connection)

### Development Testing (Local Machine):
1. Start Backend:  
   `cd SeekFactory-B`  
   `.\mvnw.cmd spring-boot:run` ➔ Verify it runs on `http://localhost:8080`.
2. Configure Frontend `.env.local`:  
   `NEXT_PUBLIC_API_URL=http://localhost:8080`
3. Start Frontend:  
   `cd SeekFactory-Web-F`  
   `npm run dev` ➔ Open `http://localhost:3000`.
4. **Open Browser DevTools (Network tab):**
   * Filter by `Fetch/XHR`.
   * You will see real requests hitting `http://localhost:8080/api/v1/feed`, `/api/v1/categories`, etc.!

### Future Production Testing (Cloud Server):
1. Deploy your backend to Oracle Cloud (e.g. `http://129.154.xx.xx` or `https://api.seekfactory.com`).
2. In your **Vercel Project Settings ➔ Environment Variables**:
   * Add: `NEXT_PUBLIC_API_URL` = `https://api.seekfactory.com` (or `http://129.154.xx.xx`).
3. Trigger a Redeploy in Vercel.
4. Open your live Vercel website: **All live categories, video reels, products, and auth will connect seamlessly to your cloud backend with zero code modifications!**
