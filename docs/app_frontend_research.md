# SeekFactory Mobile App — Research Report

> **Project:** `SeekFactory-App-F` · **Stack:** Expo SDK 57 · React Native 0.86 · React 19 · TypeScript

---

## 1. Product Mission

Same as the web — **India–China B2B manufacturing marketplace** with video-first discovery. The mobile app provides the native iOS/Android experience with fullscreen vertical video reels, Apple Dynamic Island–inspired floating tab bar, and liquid glass pill UI elements.

---

## 2. Tech Stack

| Technology | Version | Purpose |
|---|---|---|
| Expo | ~57.0.19 | Managed workflow, build tooling |
| React Native | 0.86.3 | Native mobile framework |
| React | 19.2.3 | UI library |
| Expo Router | ~57.0.18 | File-based routing (typed routes) |
| expo-video | ~57.0.3 | Modern video player (loop, mute, fullscreen) |
| expo-image | ~57.0.4 | Optimized image loading |
| expo-blur | ~57.0.2 | Glass pill / BlurView effects |
| expo-secure-store | ~57.0.3 | Encrypted session storage |
| react-native-reanimated | 4.5.1 | Smooth animations |
| react-native-gesture-handler | ~2.32.0 | Touch gestures |

### Design Tokens
Same as web: Brand Blue `#1A73E8`, Brand Orange `#F26B21`, Canvas `#F3F2EF`

### China-Safe Architecture
- No Google Play Services, Firebase, Google Maps, or FCM dependencies
- Auth supports email/password and SMS OTP with WeChat/Google stubs

---

## 3. Architecture Pattern

Same clean architecture as web:

```
Screens → getApi() → mockApi (Today)
                   → createHttpApi(EXPO_PUBLIC_API_URL) (Production)
```

- Screens NEVER call `SecureStore` or import mock fixtures directly
- All data access goes through `getApi()` and feature hooks

---

## 4. Navigation Structure

```
app/
├── _layout.tsx                    # Root GestureHandlerRootView + Stack
├── index.tsx                      # Session gate → auth or buyer
├── (auth)/
│   ├── join.tsx                   # Registration
│   └── login.tsx                  # Sign in + demo access
├── (buyer)/                       # Tab Navigator
│   ├── _layout.tsx                # FloatingIslandTabBar
│   ├── index.tsx                  # Home: Fullscreen Reels Feed
│   ├── explore.tsx                # Category explorer + search
│   ├── messages.tsx               # B2B chat threads
│   └── profile.tsx                # User dashboard
├── factory/index.tsx              # Manufacturer landing
├── manufacturer/[slug].tsx        # Factory profile detail
├── product/[slug].tsx             # Product detail
├── rfq/new.tsx                    # RFQ creation wizard
└── notifications.tsx              # Notification hub
```

### Route Behavior
- **`/` (index):** Session check → Buyer goes to `/(buyer)`, Supplier to `/factory`, Unauthenticated to `/(auth)/login`
- **`/(buyer)/index`:** Fullscreen vertical paging `FlatList` with autoplay video reels
- **`/(buyer)/explore`:** Search bar + category grid + subcategory chips + factory cards + trending products
- **`/(buyer)/messages`:** Conversation list → `InteractiveChatView` with simulated factory responses
- **`/(buyer)/profile`:** `UserProfileDashboard` with 5 tabs (Details, RFQs, Saved, Following, Premium)

---

## 5. Component Inventory

### UI Primitives (`src/components/ui/`)
| Component | Purpose |
|---|---|
| `Button` | Variants: primary, secondary, accent, danger, ghost + loading spinner |
| `Badge` | Status tags (premium, quote, soft, blue) |
| `VerifiedBadge` | Blue checkmark for verified suppliers |
| `SurfaceCard` | Rounded white card container |
| `TextField` | Styled input with placeholder |
| `GlassPill` | Liquid-glass pill using `expo-blur` BlurView |
| `PageHeader` | Title + subtitle block |
| `LoadingState` | Centered spinner + status text |
| `EmptyState` | Empty list placeholder with CTA |

### Layout (`src/components/layout/`)
| Component | Purpose |
|---|---|
| `FloatingIslandTabBar` | Apple Dynamic Island floating tab bar with active pill highlight |

### Reels (`src/components/reels/`)
| Component | Purpose |
|---|---|
| `VariantVerticalShopReel` | Full-screen video with engagement rail + product overlay |
| `ReelCard` | Paging card wrapper |
| `CommentsSheet` | Slide-up modal with threaded comments |
| `ReelHeader` | Factory badge + follow toggle overlay |
| `FeedTabs` | For You / Following tab switcher |
| `FeaturedReel` | Preview card on manufacturer page |
| `EngagementRail` | Vertical like/comment/share/save buttons |
| `ReelScrubber` | Video progress bar |

### Explore (`src/components/explore/`)
| Component | Purpose |
|---|---|
| `CategoryGrid` | 2-column grid of category tiles |
| `CategoryImageTile` | Image tile with dark scrim + icon + title |
| `ExploreSearchBar` | Glass pill search with clear button |
| `SubcategoryChips` | Horizontal scroll subcategory row |
| `VerifiedFactoriesRow` | Horizontal verified factory cards |
| `ManufacturerResultRow` | Search result factory row |
| `ProductResultCard` | 2-column product search result card |

### Messages (`src/components/messages/`)
| Component | Purpose |
|---|---|
| `InteractiveChatView` | Full B2B chat: bubbles, timestamps, receipts, typing indicator, smart inquiry chips, CAD file attachment toggle, simulated factory responses |

### Manufacturer & Product
| Component | Purpose |
|---|---|
| `ManufacturerHero` | Cover image + logo + badges + CTA |
| `ManufacturerStats` | Factory size, employees, export markets |
| `ProductHero` | Large image with factory follow pill |
| `ProductInfo` | Price, MOQ, specs, social proof |
| `ProductActionBar` | Floating glass pill: Message, Save, Select Later, Request Quote |
| `ProductRelated` | Horizontal scroll related products |

### Profile (`src/components/profile/`)
| Component | Purpose |
|---|---|
| `UserProfileDashboard` | 5-tab buyer hub: Details (editable), RFQs (status tracking), Saved Products, Followed Factories, Premium Concierge |

---

## 6. Domain Entities

Identical to web frontend — same TypeScript types:
- `UserProfile` (id, name, role, avatarUrl, companyName, industry, country)
- `Manufacturer`, `Product`, `Reel`, `Category`, `ReelComment`, `Conversation`, `AppNotification`, `RfqDraft`

---

## 7. API Contract

Same `ApiClient` interface as web with identical repository methods. The only difference:
- App's `ProductRepository.getBySlug()` returns `{ product, manufacturer, related }` (includes `related` products)
- App defines `JoinInput`, `LoginInput`, `SessionPayload` directly in `contracts.ts` instead of a separate session file

---

## 8. Authentication & Session

- **Storage:** `expo-secure-store` with key `sf-session` (encrypted on device)
- **Payload:** `{ id, name, role, email, companyName }`
- **Login methods:** Email/password, Phone OTP (`123456` mock), Google/WeChat stubs
- **Demo accounts:** `buyer@seekfactory.com` / `factory@seekfactory.com` (password: `demo1234`)
- **Guest access:** Single-tap bypass for instant evaluation

---

## 9. Assets

### Videos (Bundled MP4s)
| File | Size | Content |
|---|---|---|
| `reel-1-agri-machinery.mp4` | 47.8 MB | Agricultural harvesters & assembly |
| `reel-2-factory-tour.mp4` | 10.2 MB | Precision forging & heavy press |
| `reel-3-cnc-milling.mp4` | 9.1 MB | 5-axis CNC milling |
| `reel-4-laser-cutting.mp4` | 15.7 MB | Fiber laser cutting & bending |
| `reel-5-automated-assembly.mp4` | 11.4 MB | Robotic assembly & vision inspection |
| `reel-6-hydraulic-testing.mp4` | 11.4 MB | High-pressure hydraulic calibration |

### Brand
- `seekfactory-logo.png`
- App icons for iOS and Android (with monochrome variant)

---

## 10. Build Configuration

- **Bundle ID:** `com.seekfactory.mobile`
- **EAS builds:** Internal (dev client), Preview (APK), Production (auto-increment)
- **Custom plugin:** `withMavenMirror.js` — injects Google Cloud Maven mirror to prevent Gradle 429 errors
- **Typed routes:** Enabled via `experiments.typedRoutes: true`
