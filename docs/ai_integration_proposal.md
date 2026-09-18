# 🤖 SeekFactory AI Integration Strategy & Client Proposal

> **Project:** SeekFactory (Axoraa) · **Base Platform Fee:** ₹1,50,000 INR · **AI Upsell:** ₹45,000 – ₹1,20,000 INR  
> **Compiled PDF Document:** [SeekFactory_AI_Integration_Proposal.pdf](./SeekFactory_AI_Integration_Proposal.pdf)

---

## 1. Executive Summary & Strategic Positioning

In cross-border B2B manufacturing between Indian buyers and Chinese factories, standard search and basic text chat suffer from heavy friction: language barriers, specialized engineering terminology (tolerances, surface roughness, Incoterms, material grades), and buyer difficulty in formulating complete Requests for Quotation (RFQs).

Integrating domain-specific AI transforms SeekFactory from a passive catalog into an **intelligent, self-service industrial sourcing broker**. Crucially, because mainland China operates behind the **Great Firewall (GFW)**, mainstream Western APIs (OpenAI, Claude, Gemini) fail when called from Chinese IP addresses.

---

## 2. Top 5 High-Impact AI Features

### 1. "Ask SeekFactory" — Natural Language AI Sourcing Concierge
- **What it does:** A persistent conversational AI assistant available on web and mobile.
- **Example Query:** *"I need a factory in Zhejiang or Guangdong with 5-axis CNC milling for aerospace aluminum 7075, tolerance ±0.005mm, batch of 500 units with CMM inspection certs."*
- **How it works:** Leverages **RAG (Retrieval-Augmented Generation)** on PostgreSQL with the pgvector extension. Finds verified suppliers and generates a 1-click pre-filled RFQ.

### 2. Real-time Technical Translation in B2B Chat (EN/HI ⇄ ZH)
- **What it does:** Indian buyers type naturally in English or Hindi; the Chinese supplier sees technically accurate Simplified Chinese. When the supplier replies in Mandarin, the buyer sees precise English.
- **Advantage:** Preserves specialized mechanical terms (e.g. *EDM wire cutting*, *anodizing thickness in microns*, *draft angles*).

### 3. AI Smart RFQ Drafter & Technical Spec Extractor
- **What it does:** The buyer uploads a 2D engineering drawing (PDF/image) or types rough notes.
- **Output:** AI auto-fills Product Name, Suggested Category, MOQ, Recommended Incoterm, Target Currency, and builds the structured specs JSONB dictionary automatically.

### 4. Reels Video Auto-Transcription & Bilingual Subtitles
- **What it does:** Chinese plant managers narrate machine tours in Mandarin. AI transcribes Chinese factory audio, translates it into synchronized English captions, and auto-generates SEO titles, technical descriptions, and hashtags.

### 5. Supplier Quotation Comparison & Decision Assistant
- **What it does:** When an RFQ receives 3–5 quotes with disparate terms (different currencies, FOB vs. CIF, varying tooling charges), AI produces a comparison matrix highlighting best landed cost, fastest lead time, and supplier reliability ratings.

---

## 3. China ⇄ India Model Reality & Architecture

| Model / Provider | Mainland China Safe? | India Safe? | Technical EN-ZH Quality | Token Cost (per 1M tokens) | Verdict for SeekFactory |
| :--- | :---: | :---: | :---: | :---: | :--- |
| **Alibaba Qwen (DashScope API)** | ✅ 100% Legal & Safe | ✅ Yes | ⭐⭐⭐⭐⭐ Exceptional (Built for B2B) | ~.20 (₹16 / 1M tokens) | **Top Choice #1 (Recommended)** |
| **DeepSeek (V3 / R1 API)** | ✅ 100% Legal & Safe | ✅ Yes | ⭐⭐⭐⭐⭐ Superb Reasoning & Code | ~.14 (₹11 / 1M tokens) | **Top Choice #2 (Best Price/Perf)** |
| **OpenAI (GPT-4o / Mini)** | ❌ Blocked by GFW | ✅ Yes | ⭐⭐⭐⭐ Very Good | .50 – .00 | **Avoid (Fails in China)** |
| **Self-Hosted (Ollama / vLLM)** | Depends on Server | Depends on Server | ⭐⭐⭐ Hardware Dependent | –/mo server cost | **Avoid (Prohibitive Server Cost)** |

### Recommended Architecture: Spring Boot Backend Proxy
- Frontend (Next.js & Expo Mobile) **never calls AI APIs directly**.
- Frontend calls /api/v1/ai/chat or /api/v1/ai/translate on the Spring Boot backend.
- Backend (hosted in neutral hub like Singapore AWS or Mumbai with China peering) connects to **Alibaba Qwen** or **DeepSeek**.
- Responses are streamed back via **Server-Sent Events (SSE)** for real-time typewriter rendering.

---

## 4. Infrastructure: Cloud API vs. Self-Hosted Ollama

| Metric | Cloud API (DeepSeek / Alibaba Qwen) | Self-Hosted (Ollama on Dedicated GPU) |
| :--- | :--- | :--- |
| **Monthly Hosting Cost** | **₹500 – ₹2,000 / month** (Pay as you go) | **₹35,000 – ₹65,000 / month** (AWS EC2 g5.xlarge) |
| **Implementation Effort** | Fast (2–3 days via WebClient) | Slow (2–3 weeks for CUDA/VRAM tuning) |
| **Maintenance** | Zero maintenance | High maintenance (OOM crashes, bottlenecks) |
| **Verdict** | **100% Recommended** | **Reject for this budget** |

---

## 5. Client Quotation Packages

### 🥉 Package 1: Starter AI (₹45,000 – ₹60,000)
- "Ask SeekFactory" RAG AI Assistant
- Database Vector Search on Products & Plants
- Real-Time Technical Chat Translation (EN ⇄ ZH)
- Timeline: ~1.5 to 2 Weeks

### 🥈 Package 2: Pro Sourcing AI (₹85,000 – ₹1,10,000) ★ Recommended Pitch
- **Everything in Starter AI**
- AI Smart RFQ Auto-Drafter from Specs
- Drawing & Specs JSONB Extractor
- Video Reels Auto-Tagging & Descriptions
- Dual-Language Industrial Prompt Chips
- Timeline: ~3 to 4 Weeks

### 🥇 Package 3: Enterprise AI Suite (₹1,40,000 – ₹1,80,000)
- **Everything in Pro Sourcing AI**
- Supplier Quote Comparison Engine
- Video Reels Audio Subtitling (Mandarin to EN)
- Supplier Capability Verification Scoring
- Timeline: ~5 to 6 Weeks

> 💡 **Pitch Strategy:** Pitch **Package 2 at ₹95,000 INR**. If the client negotiates, close on **Package 1 at ₹50,000 INR**.
