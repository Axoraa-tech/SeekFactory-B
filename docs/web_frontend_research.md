# SeekFactory Web Frontend — Research Report

> **Project:** `SeekFactory-Web-F` · **Stack:** Next.js 15.5.4 · React 19 · TypeScript 5.9 · Tailwind CSS 3.4

---

## 1. Product Mission

**SeekFactory** is an **India–China B2B manufacturing marketplace** with a video-first discovery mechanism (LinkedIn visual aesthetic + TikTok/Reels-style feed). It connects Indian and global industrial buyers with verified Chinese and global manufacturing plants.

---

## 2. Tech Stack

| Technology | Version | Purpose |
|---|---|---|
| Next.js | 15.5.4 | App Router, SSR, Turbopack |
| React | 19.1.0 | UI library |
| TypeScript | 5.9.2 | Type safety (strict mode) |
| Tailwind CSS | 3.4.17 | Utility-first styling |
| Lucide React | 0.544.0 | Icon library |
| clsx + tailwind-merge | Latest | Conditional class utilities |
| Plus Jakarta Sans | Via next/font | Self-hosted typography |

### Design Tokens
- Brand Blue: `#1A73E8`
- Brand Orange: `#F26B21`
- Canvas: `#F3F2EF` (LinkedIn-style)
- Surface: `#FFFFFF`
- Line: `#E6E8EB`

---

## 3. Architecture Pattern

**Clean Architecture / Repository Pattern** with an explicit API seam:

```
UI Components → getApi() → mockApi (Today)
                         → createHttpApi(NEXT_PUBLIC_API_URL) (Production)
```

The `getApi()` factory checks for `NEXT_PUBLIC_API_URL` environment variable. If set, it returns the HTTP API client that calls the Spring Boot backend. Otherwise, it returns the in-memory mock API.

---

## 4. Directory Structure

```
src/
├── app/                    # Next.js App Router (file-system routing)
│   ├── (auth)/             # Auth & Legal route group (clean layout)
│   ├── (buyer)/            # Buyer portal (3-column AppShell)
│   ├── (profile)/          # Full-width detail pages
│   └── factory/            # Manufacturer workspace
├── components/
│   ├── ui/                 # Atomic primitives (Button, Card, Badge, Avatar)
│   ├── layout/             # Shell (TopNav, LeftSidebar, RightAside, MobileNav)
│   ├── reels/              # Video player, feed, engagement, variants
│   ├── messages/           # Chat interface
│   ├── notifications/      # Notification center
│   ├── profile/            # User dashboard
│   └── widgets/            # Right-rail widgets
├── entities/               # Pure TypeScript domain models
├── features/               # Business logic (auth, feed, explore, rfq)
├── shared/                 # API contracts, mocks, config, lib
│   ├── api/                # contracts.ts, mock-api.ts, http-api.ts
│   ├── mocks/              # Fixtures, taxonomy data
│   └── config/             # Brand colors, feature flags
└── styles/                 # globals.css with design tokens
```

---

## 5. All Routes & Pages

| Route | Access | Description |
|---|---|---|
| `/` | Public | Video reels feed (For You / Following tabs, Landscape/Vertical views) |
| `/explore` | Public | Taxonomy explorer with category grid, subcategory chips, search |
| `/messages` | Auth required | B2B buyer-supplier chat with interactive responses |
| `/notifications` | Auth required | Activity center (All, Unread, Quotes, System tabs) |
| `/rfq/new` | Auth required | RFQ creation form (quantity, incoterms, CAD attachment) |
| `/manufacturers/[slug]` | Public | Factory profile (stats, products, reels, certifications) |
| `/products/[slug]` | Public | Product detail (specs, pricing, bulk tiers, related) |
| `/profile` | Auth required | Buyer dashboard (5 tabs: Details, RFQs, Saved, Following, Premium) |
| `/join` | Public | Registration (Buyer/Manufacturer toggle, email/phone/Google) |
| `/login` | Public | Sign in + demo accounts + guest access |
| `/factory` | Supplier role | Manufacturer landing/onboarding stub |
| `/legal/*` | Public | Terms, Privacy, Cookies, Accessibility |

---

## 6. Domain Entities (TypeScript Types)

```typescript
// user.ts
type BuyerProfile = { id, name, role: "Buyer"|"Supplier", avatarUrl, companyName, industry, country }

// manufacturer.ts
type Manufacturer = { id, slug, name, logoUrl, coverUrl, country, location, verified, premium,
  yearsEstablished, factorySize, employees, exportCountries[], description, followerCount,
  categoryIds[], chairmanName? }

// product.ts
type Product = { id, slug, manufacturerId, name, imageUrl, description, priceInr, unit, moq,
  categoryId, specs: Record<string, string> }

// reel.ts
type Reel = { id, manufacturerId, title, description, hashtags[], posterUrl, videoUrl?,
  durationSec, startSec, views, likes, comments, shares, saves, tab: "for-you"|"following",
  productIds[] }

// category.ts
type Category = { id, slug, name, listingCount, parentId: string|null, icon: CategoryIconKey }

// comment.ts
type ReelComment = { id, reelId, authorName, authorAvatarUrl, authorCompany?, authorCountry?,
  isVerified?, content, createdAt, likes, replies: ReelCommentReply[] }

// message.ts
type Conversation = { id, manufacturerId, lastMessage, lastMessageAt, unreadCount }

// notification.ts
type AppNotification = { id, title, body, createdAt, read }

// rfq.ts
type RfqDraft = { productName, quantity, details, companyName }
```

---

## 7. API Contract Interface

```typescript
interface ApiClient {
  session:       SessionRepository    // getCurrentUser, join, login, logout
  feed:          FeedRepository       // list(tab)
  manufacturers: ManufacturerRepository // listVerified, getBySlug, listAll
  products:      ProductRepository    // listTrending, getBySlug, listByCategory
  messages:      MessageRepository    // listRecent
  categories:    CategoryRepository   // list, listRoots, listChildren, getBySlug
  notifications: NotificationRepository // list, unreadCount
  rfq:           RfqRepository        // submit
  comments:      CommentRepository    // listByReelId, addComment, addReply
}
```

---

## 8. Authentication

- **Session storage:** `sf-session` cookie (Max-Age 7 days, SameSite=Lax)
- **Payload:** `{ id, name, role, email, companyName }`
- **Login methods:** Email/Password, Mock Phone OTP (`123456`), Google/WeChat stubs, instant Guest Demo
- **Route protection:** Server-side redirect via `requireUser()` helper
- **Timed auth prompt:** Auto-modal after 10s for unauthenticated users

---

## 9. Key Features

1. **Video-First Discovery** — Full-screen industrial reels with 6 layout variants
2. **Dual-Orientation Feed** — Landscape (16:9) and Vertical (9:16) modes
3. **Interactive B2B Commerce** — Add to Cart, Buy Now, Chat with Supplier
4. **2-Tier Taxonomy** — 20 root sectors + 187 subcategories
5. **RFQ Engine** — Full procurement workflow with STEP/CAD attachments
6. **Live In-App Chat** — Multi-conversation buyer-supplier messaging
7. **User Dashboard** — RFQ tracking, saved products, followed factories
8. **Audited Supplier Profiles** — ISO badges, factory stats, video catalogs
9. **Trade Assurance** — Escrow, pre-shipment inspection, shipping guarantees
10. **Dual-Region Compliance** — India–China corridor, no blocked CDNs

---

## 10. i18n / Localization

- **Languages:** English (`en.json`) and Chinese Simplified (`zh.json`)
- **Currencies:** EUR, USD, GBP, INR, CAD, AUD, JPY, CNY, AED, SGD
- **Fonts:** Self-hosted via `next/font` (China-safe, no Google Fonts runtime requests)
