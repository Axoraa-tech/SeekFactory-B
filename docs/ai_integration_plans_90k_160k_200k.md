# 📑 SeekFactory AI Integration Roadmap: Plan 1, Plan 2 & Plan 3

> **Project:** SeekFactory (Axoraa) · **Base Core Platform:** ₹1,50,000 INR  
> **Official PDF Proposal:** [SeekFactory_AI_Integration_Plans_90k_160k_200k.pdf](./SeekFactory_AI_Integration_Plans_90k_160k_200k.pdf)

---

## 1. Executive Investment Tiers

| Tier | Name | Investment | Included Capabilities | Delivery Timeline |
| :--- | :--- | :---: | :--- | :---: |
| **Plan 1** | **Core AI Sourcing** | **₹90,000 INR** | Features 1 & 2 (Search Concierge + Real-time Technical Chat Translation) | ~2 Weeks |
| **Plan 2** | **Growth AI Suite** | **₹1,60,000 INR** | Features 1, 2, 3, 4 & 5 (Plan 1 + Smart RFQ + Video Subtitles + Quote Comparator) | ~3.5 Weeks |
| **Plan 3** | **Enterprise AI Ecosystem** | **₹2,00,000 INR** | Complete 8-Feature Suite (Plan 1 & 2 + Visual Search + Customs Calculator + Video Algorithm) | ~5 Weeks |

---

## 2. Feature Comparison Matrix

| # | Feature Module | Plan 1 (₹90k) | Plan 2 (₹160k) | Plan 3 (₹200k) |
|---|:---|:---:|:---:|:---:|
| **1** | **AI Sourcing Concierge** ("Ask SeekFactory" / Smart Matchmaker) | ✅ Included | ✅ Included | ✅ Included |
| **2** | **Real-time Technical Translation in B2B Chat** (EN/HI ⇄ ZH) | ✅ Included | ✅ Included | ✅ Included |
| **3** | **Smart RFQ Builder** (Spec & Drawing Extractor to JSONB) | — | ✅ Included | ✅ Included |
| **4** | **Reels Video Auto-Transcription & Bilingual Subtitles** (ZH → EN) | — | ✅ Included | ✅ Included |
| **5** | **Supplier Quotation Comparator & Recommendation Engine** | — | ✅ Included | ✅ Included |
| **6** | **Visual "Snap to Source"** (Reverse Image & Part Matcher) | — | — | ✅ Included |
| **7** | **India–China Landed Cost & Customs Duty Calculator** (HS Code AI) | — | — | ✅ Included |
| **8** | **Video Reels Algorithmic Personalization Engine** | — | — | ✅ Included |

---

## 3. Detailed Specifications of the 8 Features

### Feature 1: AI Sourcing Concierge ("Ask SeekFactory" / Smart Matchmaker)
* **Included in:** Plan 1, Plan 2, Plan 3
* **What it does:** Allows buyers to type natural language industrial prompts (e.g., *"Need 5-axis CNC machining for AL7075 with tolerance ±0.005mm in batch of 500 pcs"*).
* **Mechanism:** Uses PostgreSQL pgvector extension for semantic embeddings directly inside the database, returning verified matching factories with a 1-click RFQ generator.

### Feature 2: Real-time Technical Translation in B2B Chat (EN/HI ⇄ ZH)
* **Included in:** Plan 1, Plan 2, Plan 3
* **What it does:** Integrated inside the existing InteractiveChatView. Indian buyers type in English/Hindi; Chinese plant managers see grammatically precise Simplified Chinese with preserved mechanical engineering nomenclature (EDM cutting, anodizing microns, draft angles).

### Feature 3: Smart RFQ Builder (Spec & Drawing Extractor)
* **Included in:** Plan 2, Plan 3
* **What it does:** Buyer uploads a 2D PDF drawing or rough technical notes. The AI automatically parses dimensions, materials, and tolerances, populating the structured specs JSONB fields, suggested category, and Incoterms (FOB/CIF) to eliminate RFQ abandonment.

### Feature 4: Reels Video Auto-Transcription & Bilingual Subtitles
* **Included in:** Plan 2, Plan 3
* **What it does:** Chinese factory owners narrate equipment tours in Mandarin. AI transcribes the audio, generates synchronized English subtitles, and creates SEO titles and hashtags (e.g., #PrecisionMilling, #FiberLaser).

### Feature 5: Supplier Quotation Comparator & Recommendation
* **Included in:** Plan 2, Plan 3
* **What it does:** Analyzes multiple quotes on an RFQ and constructs an objective score matrix comparing landed cost, production lead time, factory verified credentials, and potential delivery risks.

### Feature 6: Visual "Snap to Source" (Reverse Image & Part Matcher)
* **Included in:** Plan 3 Only
* **What it does:** A factory engineer takes a photo of a broken gear, hydraulic valve, or custom casting via the mobile app. Multimodal Vision AI detects the part geometry, manufacturing process, and metal alloy, immediately matching capable suppliers.

### Feature 7: India–China Landed Cost & Customs Duty Calculator (HS Code AI)
* **Included in:** Plan 3 Only
* **What it does:** Overcomes buyer fear of hidden port tariffs. For any FOB quote, AI predicts the 8-digit Indian Customs ITC-HS code and calculates Basic Customs Duty (BCD), Social Welfare Surcharge (SWS), IGST, and ocean freight to show the final landed cost in INR.

### Feature 8: Video Reels Algorithmic Personalization Engine
* **Included in:** Plan 3 Only
* **What it does:** Personalized industrial TikTok-style recommendation feed. Adapts each buyer's "For You" reels feed based on watch time, rewind behavior, and past RFQ categories.

---

## 4. Cross-Border Architecture (China ⇄ India)

* **Why Western APIs Fail:** OpenAI, Claude, and Gemini are 100% blocked in mainland China by the Great Firewall (GFW).
* **The Solution:** Our Spring Boot backend acts as a secure proxy utilizing **Alibaba Cloud Qwen** or **DeepSeek** models.
* **Benefits:** 100% legal, zero VPN needed for Chinese factories, native technical English–Chinese fluency, and extremely low token costs (< ₹1,500/month in operations).
