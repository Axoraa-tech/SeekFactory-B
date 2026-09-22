-- ====================================================================
-- V3: SEED PRODUCTION-READY B2B DEMO DATA (POSTGRESQL)
-- Populates Users, Categories, Manufacturers, Products, Reels,
-- Comments, RFQs, Quotes, Conversations, Messages, & Notifications.
-- Password for all seed users is: Password@123
-- ====================================================================

-- 1. SEED USERS (Preserves existing user data, uses distinct demo accounts)
INSERT INTO users (id, email, phone, password_hash, role, auth_provider, name, company_name, avatar_url, industry, country, is_active, created_at, updated_at)
VALUES
-- Admin
('usr-admin', 'admin.demo@seekfactory.com', '+19000000001', '$2a$10$c.fGwrbzGJHEHwAd/Fwri.yW0xoIYtCRFVo9HpfPoVfYKzaR5Qwk6', 'ROLE_ADMIN', 'LOCAL', 'System Administrator', 'SeekFactory Global HQ', 'https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&w=200&q=80', 'Platform Operations', 'India', TRUE, NOW(), NOW()),

-- Verified Buyer (Arjun)
('usr-buyer-01', 'buyer.demo@seekfactory.com', '+19000000002', '$2a$10$c.fGwrbzGJHEHwAd/Fwri.yW0xoIYtCRFVo9HpfPoVfYKzaR5Qwk6', 'ROLE_BUYER', 'LOCAL', 'Arjun K. Sharma', 'Apex Precision Components Global Ltd', 'https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?auto=format&fit=crop&w=200&q=80', 'Aerospace & Automotive Tier-1', 'India', TRUE, NOW(), NOW()),

-- Guest Buyer
('usr-buyer-guest', 'guest.buyer.demo@seekfactory.com', '+19000000003', '$2a$10$c.fGwrbzGJHEHwAd/Fwri.yW0xoIYtCRFVo9HpfPoVfYKzaR5Qwk6', 'ROLE_BUYER', 'LOCAL', 'Guest Sourcing Executive', 'Global Sourcing Corp (Guest)', 'https://images.unsplash.com/photo-1500648767791-00dcc994a43e?auto=format&fit=crop&w=200&q=80', 'Industrial Sourcing', 'India', TRUE, NOW(), NOW()),

-- Supplier 1 (Dongguan Precision Mould)
('usr-supp-01', 'dongguan.supplier@seekfactory.com', '+19000000004', '$2a$10$c.fGwrbzGJHEHwAd/Fwri.yW0xoIYtCRFVo9HpfPoVfYKzaR5Qwk6', 'ROLE_SUPPLIER', 'LOCAL', 'Chen Wei', 'Dongguan Precision Mould Co., Ltd.', 'https://images.unsplash.com/photo-1472099645785-5658abf4ff4e?auto=format&fit=crop&w=200&q=80', 'Tooling & High-Precision CNC', 'China', TRUE, NOW(), NOW()),

-- Supplier 2 (Bharat Forgings)
('usr-supp-02', 'bharat.supplier@seekfactory.com', '+19000000005', '$2a$10$c.fGwrbzGJHEHwAd/Fwri.yW0xoIYtCRFVo9HpfPoVfYKzaR5Qwk6', 'ROLE_SUPPLIER', 'LOCAL', 'Rajesh K. Patil', 'Bharat Forgings & Heavy Engineering Ltd.', 'https://images.unsplash.com/photo-1519085360753-af0119f7cbe7?auto=format&fit=crop&w=200&q=80', 'Forging & Heavy Machinery', 'India', TRUE, NOW(), NOW()),

-- Supplier 3 (Osaka Precision CNC)
('usr-supp-03', 'osaka.supplier@seekfactory.com', '+19000000006', '$2a$10$c.fGwrbzGJHEHwAd/Fwri.yW0xoIYtCRFVo9HpfPoVfYKzaR5Qwk6', 'ROLE_SUPPLIER', 'LOCAL', 'Akira Tanaka', 'Osaka Precision CNC Machining Works', 'https://images.unsplash.com/photo-1506794778202-cad84cf45f1d?auto=format&fit=crop&w=200&q=80', '5-Axis Aerospace Components', 'Japan', TRUE, NOW(), NOW()),

-- Supplier 4 (Bavaria Optics & Laser)
('usr-supp-04', 'bavaria.supplier@seekfactory.com', '+19000000007', '$2a$10$c.fGwrbzGJHEHwAd/Fwri.yW0xoIYtCRFVo9HpfPoVfYKzaR5Qwk6', 'ROLE_SUPPLIER', 'LOCAL', 'Dr. Hans Schmidt', 'Bavaria Industrial Laser & Optics GmbH', 'https://images.unsplash.com/photo-1560250097-0b93528c311a?auto=format&fit=crop&w=200&q=80', 'Laser Cutting & Automation', 'Germany', TRUE, NOW(), NOW())
ON CONFLICT (id) DO NOTHING;


-- 2. SEED CATEGORIES (Roots and Subcategories)
INSERT INTO categories (id, slug, name, icon, parent_id, listing_count, created_at, updated_at)
VALUES
('cat-cnc', 'cnc-machining', 'CNC Machining & Precision Parts', 'tool', NULL, 3420, NOW(), NOW()),
('cat-diecasting', 'die-casting-forging', 'Die Casting & Heavy Forging', 'flame', NULL, 2180, NOW(), NOW()),
('cat-moulds', 'injection-moulds', 'Plastic Injection Moulds & Tooling', 'box', NULL, 1890, NOW(), NOW()),
('cat-sheetmetal', 'sheet-metal-fabrication', 'Sheet Metal & Laser Fabrication', 'layers', NULL, 1540, NOW(), NOW()),
('cat-automation', 'industrial-automation', 'Industrial Automation & Robotics', 'cpu', NULL, 1260, NOW(), NOW()),
('cat-optics', 'laser-optical-equipment', 'Fiber Laser & Optics Machinery', 'zap', NULL, 980, NOW(), NOW()),

-- Subcategories
('cat-cnc-5axis', '5-axis-cnc-machining', '5-Axis High Precision Milling', 'tool', 'cat-cnc', 1240, NOW(), NOW()),
('cat-cnc-lathe', 'cnc-turning-lathe', 'CNC Turning & Swiss Lathe Parts', 'tool', 'cat-cnc', 980, NOW(), NOW()),
('cat-diecasting-al', 'aluminum-die-casting', 'High Pressure Aluminum Die Casting', 'flame', 'cat-diecasting', 1120, NOW(), NOW()),
('cat-diecasting-iron', 'ductile-iron-casting', 'Ductile Iron & Sand Casting', 'flame', 'cat-diecasting', 860, NOW(), NOW()),
('cat-moulds-auto', 'automotive-injection-moulds', 'Automotive Bumper & Door Moulds', 'box', 'cat-moulds', 640, NOW(), NOW())
ON CONFLICT (id) DO NOTHING;


-- 3. SEED MANUFACTURERS
INSERT INTO manufacturers (id, user_id, slug, name, logo_url, cover_url, country, location, verified, premium, years_established, factory_size, employees, description, follower_count, chairman_name, created_at, updated_at)
VALUES
('mfg-01', 'usr-supp-01', 'dongguan-precision-mould', 'Dongguan Precision Mould Co., Ltd.', 'https://images.unsplash.com/photo-1581091226825-a6a2a5aee158?auto=format&fit=crop&w=200&q=80', 'https://images.unsplash.com/photo-1581092160607-ee22621dd758?auto=format&fit=crop&w=1400&q=80', 'China', 'Dongguan Industrial High-Tech Park, Guangdong', TRUE, TRUE, 2008, '45,000 sq.m', '480+ Specialists', 'Leading Tier-1 exporter of automotive plastic injection moulds, 5-axis precision tooling, and multi-cavity hot runner systems for Indian, European, and US OEM manufacturers.', 3820, 'Chen Wei', NOW(), NOW()),

('mfg-02', 'usr-supp-02', 'bharat-forgings', 'Bharat Forgings & Heavy Engineering Ltd.', 'https://images.unsplash.com/photo-1504917595217-d4dc5ebe6122?auto=format&fit=crop&w=200&q=80', 'https://images.unsplash.com/photo-1504307651254-35680f356dfd?auto=format&fit=crop&w=1400&q=80', 'India', 'Chakan Industrial Corridor, Pune, Maharashtra', TRUE, TRUE, 1996, '68,000 sq.m', '750+ Heavy Engineers', 'Premier Indian manufacturer of closed-die forgings, crankshafts, railway transmission gear blanks, and heavy structural components certified with ISO 9001 and IATF 16949.', 4210, 'Rajesh K. Patil', NOW(), NOW()),

('mfg-03', 'usr-supp-03', 'osaka-precision-cnc', 'Osaka Precision CNC Machining Works', 'https://images.unsplash.com/photo-1581092335397-9583fe92d232?auto=format&fit=crop&w=200&q=80', 'https://images.unsplash.com/photo-1581092580497-e0d23cbdf1dc?auto=format&fit=crop&w=1400&q=80', 'Japan', 'Higashiosaka Industrial Zone, Osaka', TRUE, TRUE, 1985, '32,000 sq.m', '260+ Master Machinists', 'Ultra-precision 5-axis CNC machining, micro-drilling, titanium aerospace brackets, and semiconductor chamber components with sub-micron tolerances (±0.002mm).', 2940, 'Akira Tanaka', NOW(), NOW()),

('mfg-04', 'usr-supp-04', 'bavaria-optics', 'Bavaria Industrial Laser & Optics GmbH', 'https://images.unsplash.com/photo-1563986768609-322da13575f3?auto=format&fit=crop&w=200&q=80', 'https://images.unsplash.com/photo-1581092162384-8987c1d64718?auto=format&fit=crop&w=1400&q=80', 'Germany', 'Garching Technology Park, Munich, Bavaria', TRUE, FALSE, 2011, '18,000 sq.m', '140+ Optical Physicists', 'Industry 4.0 high-power 12kW fiber laser cutting heads, galvanometer optical scanners, and automated laser welding solutions engineered for high-throughput automotive manufacturing.', 1850, 'Dr. Hans Schmidt', NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

-- 3a. Export Countries
INSERT INTO manufacturer_export_countries (manufacturer_id, country_name)
VALUES
('mfg-01', 'India'), ('mfg-01', 'Germany'), ('mfg-01', 'USA'), ('mfg-01', 'Japan'), ('mfg-01', 'Vietnam'),
('mfg-02', 'India'), ('mfg-02', 'UAE'), ('mfg-02', 'Saudi Arabia'), ('mfg-02', 'USA'), ('mfg-02', 'UK'),
('mfg-03', 'Japan'), ('mfg-03', 'India'), ('mfg-03', 'Germany'), ('mfg-03', 'South Korea'),
('mfg-04', 'Germany'), ('mfg-04', 'India'), ('mfg-04', 'USA'), ('mfg-04', 'China')
ON CONFLICT DO NOTHING;

-- 3b. Manufacturer Categories
INSERT INTO manufacturer_categories (manufacturer_id, category_id)
VALUES
('mfg-01', 'cat-moulds'), ('mfg-01', 'cat-cnc'), ('mfg-01', 'cat-diecasting'),
('mfg-02', 'cat-diecasting'), ('mfg-02', 'cat-sheetmetal'),
('mfg-03', 'cat-cnc'), ('mfg-03', 'cat-automation'),
('mfg-04', 'cat-optics'), ('mfg-04', 'cat-sheetmetal')
ON CONFLICT DO NOTHING;


-- 4. SEED PRODUCTS
INSERT INTO products (id, slug, manufacturer_id, category_id, name, image_url, description, price_inr, unit, moq, specs, is_active, created_at, updated_at)
VALUES
('prod-01', '5-axis-cnc-machined-aerospace-bracket', 'mfg-03', 'cat-cnc', '5-Axis CNC Titanium Aerospace Structural Bracket', 'https://images.unsplash.com/photo-1581092160607-ee22621dd758?auto=format&fit=crop&w=800&q=80', 'Aerospace Grade Ti-6Al-4V machined with DMG Mori 5-axis machining center. Complete with CMM inspection report, anodized surface finish, and ultra-tight geometric tolerances.', 48500.00, 'Piece', '10 Pieces', '{"Material": "Titanium Grade 5 (Ti-6Al-4V)", "Tolerance": "±0.005 mm", "Surface Roughness": "Ra 0.4 µm", "Certification": "AS9100D, ISO 9001:2015", "Lead Time": "14 Days"}', TRUE, NOW(), NOW()),

('prod-02', 'automotive-aluminum-die-cast-transmission-housing', 'mfg-01', 'cat-diecasting', 'Automotive HPDC Aluminum Transmission Housing (ADC12)', 'https://images.unsplash.com/photo-1581092335397-9583fe92d232?auto=format&fit=crop&w=800&q=80', 'High-pressure die-cast aluminum housing for EV reduction gearboxes. High structural rigidity, vacuum impregnated to guarantee zero oil leakage under 450 kPa operating pressure.', 12800.00, 'Set', '50 Sets', '{"Alloy": "ADC12 / A380 Aluminum", "Clamping Force": "1600 Ton HPDC", "Pressure Test": "4.5 Bar Hydrostatic", "Machining": "Full CNC Machined Flanges", "Quality": "IATF 16949"}', TRUE, NOW(), NOW()),

('prod-03', 'high-precision-hot-runner-injection-mould', 'mfg-01', 'cat-moulds', '72-Cavity High-Speed PET Preform Injection Mould', 'https://images.unsplash.com/photo-1581091226825-a6a2a5aee158?auto=format&fit=crop&w=800&q=80', 'Precision hardened S136 stainless steel mould core (52-54 HRC) with balanced hot runner valve gate system. Cycle time under 9.5 seconds with guaranteed 3 million shots mold life.', 1850000.00, 'Unit', '1 Unit', '{"Core Steel": "Swedish Stavax S136", "Cavities": "72 Cavity", "Cycle Time": "9.2 Seconds", "Runner System": "Yudo Hot Runner Valve Gate", "Mould Life": "3,000,000 Shots"}', TRUE, NOW(), NOW()),

('prod-04', 'heavy-duty-forged-steel-crankshaft', 'mfg-02', 'cat-diecasting', 'Drop-Forged 42CrMo4 Heavy Truck Engine Crankshaft', 'https://images.unsplash.com/photo-1504917595217-d4dc5ebe6122?auto=format&fit=crop&w=800&q=80', 'Closed-die drop forged from vacuum degassed 42CrMo4 alloy steel. Induction hardened bearing journals with micro-crack magnetic particle inspection (MPI) for heavy commercial vehicles.', 95000.00, 'Piece', '25 Pieces', '{"Steel Grade": "42CrMo4 / AISI 4140", "Forging Press": "8,000 Ton Forging Press", "Heat Treatment": "Quenched & Tempered (28-32 HRC)", "Journal Hardness": "54-58 HRC Induction", "Balancing": "Dynamic < 5 g.cm"}', TRUE, NOW(), NOW()),

('prod-05', '12kw-fiber-laser-cutting-optical-head', 'mfg-04', 'cat-optics', 'ProCutter 12kW Ultra-High Power Auto-Focus Fiber Laser Head', 'https://images.unsplash.com/photo-1563986768609-322da13575f3?auto=format&fit=crop&w=800&q=80', 'German engineered cutting head designed for 0.5mm to 40mm carbon steel and stainless sheet processing with water-cooled double collimation optics and real-time piercing sensor.', 420000.00, 'Unit', '1 Unit', '{"Max Power": "12,000 Watts", "Focal Length": "150 mm / 200 mm", "Wavelength": "1064 nm - 1080 nm", "Focus Travel": "-12 mm to +14 mm", "Cooling": "Dual-Circuit Water Cooling"}', TRUE, NOW(), NOW()),

('prod-06', 'precision-swiss-lathe-turned-medical-screws', 'mfg-03', 'cat-cnc', 'Custom Medical Grade Titanium Bone Screws (Torx Plus)', 'https://images.unsplash.com/photo-1581092580497-e0d23cbdf1dc?auto=format&fit=crop&w=800&q=80', 'Manufactured on Citizen 7-axis Swiss CNC lathes using implant-grade ISO 5832-3 Ti6Al4V ELI titanium. 100% optical dimensional sorting under ISO Class 7 cleanroom conditions.', 650.00, 'Piece', '500 Pieces', '{"Material": "Ti6Al4V ELI Grade 23", "Drive": "Torx Plus T10 / T15", "Thread Pitch": "ISO 5835 Metric", "Cleaning": "Ultrasonic Passivation", "Standard": "ISO 13485:2016"}', TRUE, NOW(), NOW())
ON CONFLICT (id) DO NOTHING;


-- 5. SEED VIDEO REELS (Discovery Showcase)
INSERT INTO reels (id, manufacturer_id, title, description, poster_url, video_url, duration_sec, start_sec, views_count, likes_count, comments_count, shares_count, saves_count, feed_tab, created_at, updated_at)
VALUES
('reel-01', 'mfg-01', 'High-Speed 5-Axis CNC Milling of Automobile Engine Blocks', 'Watch our DMG MORI DMU 95 monoBLOCK machine complex aluminum transmission housing with mirror surface finish.', 'https://images.unsplash.com/photo-1581092160607-ee22621dd758?auto=format&fit=crop&w=600&q=80', 'https://assets.mixkit.co/videos/preview/mixkit-factory-worker-working-with-heavy-metal-machinery-41584-large.mp4', 45, 0, 48600, 3240, 182, 450, 890, 'FOR_YOU', NOW(), NOW()),

('reel-02', 'mfg-02', '8000-Ton Forging Press in Action: Heavy Truck Axle Forging', 'White-hot 42CrMo4 steel ingot shaped in single press stroke at 1250°C. Made for global commercial vehicle OEMs.', 'https://images.unsplash.com/photo-1504917595217-d4dc5ebe6122?auto=format&fit=crop&w=600&q=80', 'https://assets.mixkit.co/videos/preview/mixkit-laser-cutting-metal-sparks-41588-large.mp4', 38, 0, 62100, 4890, 246, 680, 1420, 'FOR_YOU', NOW(), NOW()),

('reel-03', 'mfg-03', 'Sub-Micron Swiss Lathe Micro-Machining for Aerospace Valves', 'Citizen L20 7-Axis Swiss turning titanium hydraulic spools with ±0.002mm geometric cylindrical tolerance.', 'https://images.unsplash.com/photo-1581092580497-e0d23cbdf1dc?auto=format&fit=crop&w=600&q=80', 'https://assets.mixkit.co/videos/preview/mixkit-sparks-flying-from-a-welding-tool-41590-large.mp4', 29, 0, 31400, 2150, 94, 310, 670, 'FOR_YOU', NOW(), NOW()),

('reel-04', 'mfg-04', '12kW Fiber Laser Slicing 35mm Stainless Steel Plate like Butter', 'High-speed nitrogen laser cutting with clean bevel-free edge. Ready for immediate welding without grinding.', 'https://images.unsplash.com/photo-1563986768609-322da13575f3?auto=format&fit=crop&w=600&q=80', 'https://assets.mixkit.co/videos/preview/mixkit-heavy-machinery-working-on-a-construction-site-41586-large.mp4', 34, 0, 54200, 3980, 160, 520, 1140, 'FOLLOWING', NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

-- 5a. Reel Hashtags
INSERT INTO reel_hashtags (reel_id, hashtag)
VALUES
('reel-01', '5AxisCNC'), ('reel-01', 'CNCMachining'), ('reel-01', 'AutomotiveTier1'), ('reel-01', 'Tooling'),
('reel-02', 'HeavyForging'), ('reel-02', 'MadeInIndia'), ('reel-02', 'AutomotiveOEM'), ('reel-02', 'DropForging'),
('reel-03', 'SwissLathe'), ('reel-03', 'AerospacePrecision'), ('reel-03', 'TitaniumMachining'),
('reel-04', 'FiberLaser'), ('reel-04', 'LaserCutting'), ('reel-04', 'SheetMetalFab')
ON CONFLICT DO NOTHING;

-- 5b. Reel Tagged Products
INSERT INTO reel_products (reel_id, product_id)
VALUES
('reel-01', 'prod-02'),
('reel-02', 'prod-04'),
('reel-03', 'prod-01'), ('reel-03', 'prod-06'),
('reel-04', 'prod-05')
ON CONFLICT DO NOTHING;


-- 6. SEED COMMENTS & REPLIES
INSERT INTO comments (id, reel_id, parent_id, user_id, author_name, author_avatar_url, author_company, author_country, is_verified, content, likes_count, created_at, updated_at)
VALUES
('com-01', 'reel-01', NULL, 'usr-buyer-01', 'Arjun K. Sharma', 'https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?auto=format&fit=crop&w=200&q=80', 'Apex Precision Components Ltd', 'India', TRUE, 'What is the maximum shot weight on your 1600-Ton HPDC die casting machine for A380 alloy?', 24, NOW() - INTERVAL '2 hours', NOW()),

('com-01-rep', 'reel-01', 'com-01', 'usr-supp-01', 'Chen Wei (Factory Lead)', 'https://images.unsplash.com/photo-1472099645785-5658abf4ff4e?auto=format&fit=crop&w=200&q=80', 'Dongguan Precision Mould Co.', 'China', TRUE, 'Hello Arjun! For A380 alloy our maximum shot capacity is 18.5 kg per cycle with real-time vacuum assist. We can ship DDP to Nhava Sheva or Chennai port.', 42, NOW() - INTERVAL '1 hour', NOW()),

('com-02', 'reel-02', NULL, 'usr-buyer-01', 'Arjun K. Sharma', 'https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?auto=format&fit=crop&w=200&q=80', 'Apex Precision Components Ltd', 'India', TRUE, 'Do you supply fully machined crankshafts with dynamic balancing reports included in the batch certificate?', 18, NOW() - INTERVAL '4 hours', NOW()),

('com-02-rep', 'reel-02', 'com-02', 'usr-supp-02', 'Rajesh K. Patil', 'https://images.unsplash.com/photo-1519085360753-af0119f7cbe7?auto=format&fit=crop&w=200&q=80', 'Bharat Forgings Ltd', 'India', TRUE, 'Yes Arjun! All crankshafts undergo 100% dynamic balancing (< 5 g.cm) and 3.1 Material Test Certificates are dispatched with each consignment.', 31, NOW() - INTERVAL '3 hours', NOW())
ON CONFLICT (id) DO NOTHING;


-- 7. SEED RFQS (Buyer Inquiries) & QUOTATIONS
INSERT INTO rfqs (id, reference_number, user_id, product_name, category_id, quantity, unit, target_price, currency, incoterm, company_name, details, status, created_at, updated_at)
VALUES
('rfq-01', 'SF-RFQ-9482', 'usr-buyer-01', 'Custom CNC Machined Aluminum Gearbox Housings', 'cat-cnc', '500', 'Pieces', '1450', 'INR', 'FOB', 'Apex Precision Components India', '5-Axis CNC Precision Aluminum Housings with anodized surface treatment. Must meet drawing spec SF-DRW-8902 with CMM inspection report per lot.', 'SUBMITTED', NOW() - INTERVAL '1 day', NOW()),

('rfq-02', 'SF-RFQ-8104', 'usr-buyer-01', 'High-Pressure Die-Cast Transmission Valve Bodies', 'cat-diecasting', '1500', 'Pieces', '3200', 'INR', 'CIF', 'Apex Precision Components India', 'Ductile iron / aluminum alloy die cast valve body with pressure testing up to 350 bar. Delivery required at Nhava Sheva Port, Mumbai within 45 days.', 'QUOTED', NOW() - INTERVAL '2 days', NOW())
ON CONFLICT (id) DO NOTHING;

-- 7a. Quotations from Suppliers
INSERT INTO rfq_quotes (id, rfq_id, manufacturer_id, quote_price, currency, lead_time_days, notes, status, created_at)
VALUES
('quote-01', 'rfq-01', 'mfg-01', 1380.00, 'INR', 18, 'Official Quote: ₹1,380/pc FOB Shenzhen. Includes anodizing and export-grade wooden packaging.', 'ACCEPTED', NOW() - INTERVAL '12 hours'),
('quote-02', 'rfq-02', 'mfg-02', 3050.00, 'INR', 25, 'Official Quote: ₹3,050/pc CIF Mumbai. In-house hydrostatic pressure testing and IATF 16949 inspection report included.', 'PENDING', NOW() - INTERVAL '18 hours')
ON CONFLICT (id) DO NOTHING;


-- 8. SEED B2B CONVERSATIONS & MESSAGES
INSERT INTO conversations (id, buyer_id, manufacturer_id, last_message_text, last_message_at, unread_count_buyer, unread_count_supplier, created_at, updated_at)
VALUES
('conv-01', 'usr-buyer-01', 'mfg-01', 'Formal Quote Sent: ₹1,380 / pc for 500 units', NOW() - INTERVAL '15 minutes', 1, 0, NOW() - INTERVAL '2 days', NOW()),
('conv-02', 'usr-buyer-01', 'mfg-02', 'Sample batch dispatched via BlueDart Express (Track: BD-98402)', NOW() - INTERVAL '1 hour', 0, 0, NOW() - INTERVAL '3 days', NOW())
ON CONFLICT (id) DO NOTHING;

INSERT INTO messages (id, conversation_id, sender_id, sender_type, message_text, attachment_name, attachment_size, is_read, created_at)
VALUES
('msg-01', 'conv-01', 'usr-buyer-01', 'USER', 'Hi Chen, we reviewed your 5-axis video reel. Can you fabricate 500 units of this gearbox housing with Ra 0.4 finish?', NULL, NULL, TRUE, NOW() - INTERVAL '2 hours'),
('msg-02', 'conv-01', 'usr-supp-01', 'FACTORY', 'Hello Arjun! Absolutely. We have 8 DMG Mori 5-axis machines available. Here is our official quotation.', 'Quotation_SF-RFQ-9482.pdf', '2.4 MB', FALSE, NOW() - INTERVAL '15 minutes'),
('msg-03', 'conv-02', 'usr-buyer-01', 'USER', 'Hello Rajesh, how is the testing progressing for our forged crankshaft samples?', NULL, NULL, TRUE, NOW() - INTERVAL '3 hours'),
('msg-04', 'conv-02', 'usr-supp-02', 'FACTORY', 'Sample batch dispatched via BlueDart Express (Track: BD-98402). 3.1 Material certs attached.', 'Material_Inspection_Cert_BD98402.pdf', '1.8 MB', TRUE, NOW() - INTERVAL '1 hour')
ON CONFLICT (id) DO NOTHING;


-- 9. SEED NOTIFICATIONS
INSERT INTO notifications (id, user_id, title, body, notification_type, reference_id, is_read, created_at)
VALUES
('notif-01', 'usr-buyer-01', 'Official Quote Received', 'Dongguan Precision Mould Co. submitted a formal quotation for RFQ #SF-RFQ-9482 (₹1,380 / pc).', 'RFQ_QUOTE', 'rfq-01', FALSE, NOW() - INTERVAL '15 minutes'),
('notif-02', 'usr-buyer-01', 'Consignment Dispatched', 'Bharat Forgings dispatched your sample batch (Tracking: BD-98402).', 'ORDER_STATUS', 'conv-02', FALSE, NOW() - INTERVAL '1 hour'),
('notif-03', 'usr-buyer-01', 'Welcome to SeekFactory', 'Your Enterprise Buyer account is verified with Global Pro Tier sourcing privileges.', 'WELCOME', NULL, TRUE, NOW() - INTERVAL '1 day'),
('notif-04', 'usr-supp-01', 'New RFQ Lead Matching Capabilities', 'Arjun Sharma (Apex Precision) submitted RFQ #SF-RFQ-9482 matching your 5-Axis CNC capabilities.', 'RFQ_MATCH', 'rfq-01', FALSE, NOW() - INTERVAL '1 day')
ON CONFLICT (id) DO NOTHING;
