-- Authorized historical-label restoration: 164 exact IDs, 14 additive children.
-- Known risk accepted by owner: old updateProduct could leave stale child labels.
-- Does NOT infer semantic correctness. 50 unsupported pairs and existing children stay unchanged.
-- AUTOMATIC ON DEPLOYMENT when Flyway runs. Drift aborts startup; do not bypass.
-- Flyway PostgreSQL runs this migration transactionally. No explicit COMMIT here.
SET LOCAL standard_conforming_strings=on;
SET LOCAL search_path=pg_catalog,public;
SET LOCAL lock_timeout='5s';
SET LOCAL statement_timeout='60s';
LOCK TABLE public.categories, public.products IN SHARE ROW EXCLUSIVE MODE;
CREATE SCHEMA IF NOT EXISTS seekfactory_migration_audit;
REVOKE ALL ON SCHEMA seekfactory_migration_audit FROM PUBLIC;
CREATE TABLE IF NOT EXISTS seekfactory_migration_audit.legacy_subcategories_v33 (
 product_id text PRIMARY KEY,
 before_row jsonb NOT NULL,
 after_row jsonb NOT NULL,
 applied_at timestamptz NOT NULL
);
REVOKE ALL ON seekfactory_migration_audit.legacy_subcategories_v33 FROM PUBLIC;
-- No user payload, credentials or connection strings are included.
CREATE TEMP TABLE sf_v33_children(id text PRIMARY KEY,slug text UNIQUE,name text,parent_id text) ON COMMIT DROP;
INSERT INTO sf_v33_children VALUES
('cat-legacy-aae2dc3ddf9d3ede1e9b33f765fec664','sub-legacy-aae2dc3ddf9d3ede1e9b33f765fec664','Printing machines & printers','cat-printing'),
('cat-legacy-81ebbe51afc518ad87193146042e2588','sub-legacy-81ebbe51afc518ad87193146042e2588','Metals & Mining','cat-oil-gas-mining'),
('cat-legacy-932f0b95cc0c73cf42334d1270932117','sub-legacy-932f0b95cc0c73cf42334d1270932117','Machine For Food Industry','cat-food-beverage-processing'),
('cat-legacy-e5b89554e221fcfd6d91ef8cc8d721c6','sub-legacy-e5b89554e221fcfd6d91ef8cc8d721c6','Food Technology','cat-food-beverage-processing'),
('cat-legacy-184b3c358b12621bf31e7492c88e67e6','sub-legacy-184b3c358b12621bf31e7492c88e67e6','Infrastructure & Real Estate','cat-construction'),
('cat-legacy-1b75c05886e761a17c1bf3945535d107','sub-legacy-1b75c05886e761a17c1bf3945535d107','Electronics System Design & Manufacturing','cat-semiconductors'),
('cat-legacy-49967a119a74c3bc7effd0187e2a2a5f','sub-legacy-49967a119a74c3bc7effd0187e2a2a5f','Power, Renewable Energy','cat-energy'),
('cat-legacy-b1925ba28537d311452dbcfa52d44c20','sub-legacy-b1925ba28537d311452dbcfa52d44c20','Consumer Durables','cat-other'),
('cat-legacy-719b5deb83f6915a233d66511b96f5f9','sub-legacy-719b5deb83f6915a233d66511b96f5f9','Roads, Rails & Ports','cat-construction'),
('cat-legacy-37f54dab36f08e86d4375f8f66ee4bf3','sub-legacy-37f54dab36f08e86d4375f8f66ee4bf3','Cement Industry','cat-construction'),
('cat-legacy-706dda478843e196e8cba43d93c54cea','sub-legacy-706dda478843e196e8cba43d93c54cea','Agricultural Machinery Equipement','cat-agriculture'),
('cat-legacy-d035ff2a7eaada6c0ceed17a3a079367','sub-legacy-d035ff2a7eaada6c0ceed17a3a079367','Oil Extraction Machine','cat-food-beverage-processing'),
('cat-legacy-b50a6054cf8886c6587b04eabf876d84','sub-legacy-b50a6054cf8886c6587b04eabf876d84','Metal Engraving Machine','cat-machine-tools'),
('cat-legacy-6c07044de19161096335abec1e014d8e','sub-legacy-6c07044de19161096335abec1e014d8e','Die Casting Machine','cat-machine-tools');
CREATE TEMP TABLE sf_v33_products(id text PRIMARY KEY,old_category text,old_updated_at timestamptz,new_category text) ON COMMIT DROP;
INSERT INTO sf_v33_products VALUES
('lgp-69076d711cb97f9c7d3ab648','cat-printing','2025-12-03T10:42:33.634+00:00','cat-legacy-aae2dc3ddf9d3ede1e9b33f765fec664'),
('lgp-693ac2fb9e3eec60978bd09d','cat-printing','2025-12-12T06:39:00.131+00:00','cat-legacy-aae2dc3ddf9d3ede1e9b33f765fec664'),
('lgp-693ac4bf9e3eec60978bd0c3','cat-printing','2025-12-12T06:08:46.992+00:00','cat-legacy-aae2dc3ddf9d3ede1e9b33f765fec664'),
('lgp-693ba5849e3eec60978bde96','cat-printing','2025-12-12T06:39:33.569+00:00','cat-legacy-aae2dc3ddf9d3ede1e9b33f765fec664'),
('lgp-6965f30dc8315e7e68249649','cat-printing','2026-01-13T07:23:57.387+00:00','cat-legacy-aae2dc3ddf9d3ede1e9b33f765fec664'),
('lgp-6aa4ead4fd9900f51c7b99f2','cat-printing','2026-09-12T06:01:56.532+00:00','cat-legacy-aae2dc3ddf9d3ede1e9b33f765fec664'),
('lgp-6aa4eb81fd9900f51c7b9a00','cat-printing','2026-09-12T06:04:49.807+00:00','cat-legacy-aae2dc3ddf9d3ede1e9b33f765fec664'),
('lgp-6ab7e89b57b700dbacdefe47','cat-printing','2026-09-26T15:45:31.077+00:00','cat-legacy-aae2dc3ddf9d3ede1e9b33f765fec664'),
('lgp-690775241cb97f9c7d3ab8ae','cat-oil-gas-mining','2025-12-11T07:57:56.975+00:00','cat-legacy-81ebbe51afc518ad87193146042e2588'),
('lgp-693c46d8d09b3313c7f914ea','cat-oil-gas-mining','2026-05-06T13:22:26.26+00:00','cat-legacy-81ebbe51afc518ad87193146042e2588'),
('lgp-69455cedded6fb166e7aefa8','cat-oil-gas-mining','2025-12-20T03:50:44.044+00:00','cat-legacy-81ebbe51afc518ad87193146042e2588'),
('lgp-69455ebeded6fb166e7aeffc','cat-oil-gas-mining','2025-12-19T14:18:38.992+00:00','cat-legacy-81ebbe51afc518ad87193146042e2588'),
('lgp-69456096ded6fb166e7af00a','cat-oil-gas-mining','2025-12-19T14:26:30.506+00:00','cat-legacy-81ebbe51afc518ad87193146042e2588'),
('lgp-69456b2eded6fb166e7af25a','cat-oil-gas-mining','2025-12-19T15:11:42.33+00:00','cat-legacy-81ebbe51afc518ad87193146042e2588'),
('lgp-69456caaded6fb166e7af268','cat-oil-gas-mining','2025-12-19T22:19:42.401+00:00','cat-legacy-81ebbe51afc518ad87193146042e2588'),
('lgp-69457181ded6fb166e7af31b','cat-oil-gas-mining','2025-12-19T15:38:41.176+00:00','cat-legacy-81ebbe51afc518ad87193146042e2588'),
('lgp-69457de5ded6fb166e7af4c2','cat-oil-gas-mining','2025-12-20T03:51:58.022+00:00','cat-legacy-81ebbe51afc518ad87193146042e2588'),
('lgp-6952217943aea4aa90261ad4','cat-oil-gas-mining','2025-12-29T06:36:41.358+00:00','cat-legacy-81ebbe51afc518ad87193146042e2588'),
('lgp-6952241b43aea4aa90261ae2','cat-oil-gas-mining','2025-12-29T08:37:12.751+00:00','cat-legacy-81ebbe51afc518ad87193146042e2588'),
('lgp-6952252743aea4aa90261af0','cat-oil-gas-mining','2025-12-31T09:40:01.204+00:00','cat-legacy-81ebbe51afc518ad87193146042e2588'),
('lgp-6965fa63c8315e7e68249723','cat-oil-gas-mining','2026-01-13T07:55:15.113+00:00','cat-legacy-81ebbe51afc518ad87193146042e2588'),
('lgp-69803d2af796da20750bffc4','cat-oil-gas-mining','2026-02-02T05:59:06.04+00:00','cat-legacy-81ebbe51afc518ad87193146042e2588'),
('lgp-69803f27f796da20750bffe0','cat-oil-gas-mining','2026-02-02T06:07:35.866+00:00','cat-legacy-81ebbe51afc518ad87193146042e2588'),
('lgp-69aadceb417c423916ec2e9d','cat-oil-gas-mining','2026-03-06T13:55:55.592+00:00','cat-legacy-81ebbe51afc518ad87193146042e2588'),
('lgp-69aadf566f3a781e7c2fa342','cat-oil-gas-mining','2026-03-06T14:06:14.597+00:00','cat-legacy-81ebbe51afc518ad87193146042e2588'),
('lgp-69ac251d8cf155058b7a1713','cat-oil-gas-mining','2026-03-07T13:16:13.777+00:00','cat-legacy-81ebbe51afc518ad87193146042e2588'),
('lgp-69ac341b8cf155058b7a17b4','cat-oil-gas-mining','2026-03-07T14:20:11.321+00:00','cat-legacy-81ebbe51afc518ad87193146042e2588'),
('lgp-69aedb5fdfbbf0573538f7d5','cat-oil-gas-mining','2026-03-09T14:38:23.741+00:00','cat-legacy-81ebbe51afc518ad87193146042e2588'),
('lgp-69aedcaedfbbf0573538f7e3','cat-oil-gas-mining','2026-03-09T14:43:58.887+00:00','cat-legacy-81ebbe51afc518ad87193146042e2588'),
('lgp-69aede0ddfbbf0573538f842','cat-oil-gas-mining','2026-03-09T14:49:49.12+00:00','cat-legacy-81ebbe51afc518ad87193146042e2588'),
('lgp-69b03188e8420e3e98893c2f','cat-oil-gas-mining','2026-03-10T14:58:17.097+00:00','cat-legacy-81ebbe51afc518ad87193146042e2588'),
('lgp-69b0354ae8420e3e98893c3d','cat-oil-gas-mining','2026-03-10T15:14:18.718+00:00','cat-legacy-81ebbe51afc518ad87193146042e2588'),
('lgp-69b0373ee8420e3e98893c4b','cat-oil-gas-mining','2026-03-10T15:22:38.304+00:00','cat-legacy-81ebbe51afc518ad87193146042e2588'),
('lgp-6a1a9786dbbe0506a653a4dd','cat-oil-gas-mining','2026-05-30T07:53:42.142+00:00','cat-legacy-81ebbe51afc518ad87193146042e2588'),
('lgp-6a1a9dd4dbbe0506a653a5db','cat-oil-gas-mining','2026-05-30T08:20:36.101+00:00','cat-legacy-81ebbe51afc518ad87193146042e2588'),
('lgp-6abe095b63db1bdd5d6a8c39','cat-oil-gas-mining','2026-10-01T07:18:51.088+00:00','cat-legacy-81ebbe51afc518ad87193146042e2588'),
('lgp-6abe0a2263db1bdd5d6a8c47','cat-oil-gas-mining','2026-10-01T07:22:10.944+00:00','cat-legacy-81ebbe51afc518ad87193146042e2588'),
('lgp-6abe0e7163db1bdd5d6a8d3e','cat-oil-gas-mining','2026-10-01T07:40:33.861+00:00','cat-legacy-81ebbe51afc518ad87193146042e2588'),
('lgp-6936cc20868fa424130350d5','cat-food-beverage-processing','2025-12-08T14:25:44.278+00:00','cat-legacy-932f0b95cc0c73cf42334d1270932117'),
('lgp-6936e3cd868fa42413035438','cat-food-beverage-processing','2025-12-10T07:14:04.138+00:00','cat-legacy-932f0b95cc0c73cf42334d1270932117'),
('lgp-69397a424c14c08ef8952207','cat-food-beverage-processing','2025-12-10T14:02:53.479+00:00','cat-legacy-932f0b95cc0c73cf42334d1270932117'),
('lgp-69397d134c14c08ef8952225','cat-food-beverage-processing','2025-12-12T06:09:12.471+00:00','cat-legacy-932f0b95cc0c73cf42334d1270932117'),
('lgp-6939873e4c14c08ef8952320','cat-food-beverage-processing','2026-07-15T05:44:14.357+00:00','cat-legacy-932f0b95cc0c73cf42334d1270932117'),
('lgp-693996ae4c14c08ef8952427','cat-food-beverage-processing','2025-12-14T16:42:12.898+00:00','cat-legacy-932f0b95cc0c73cf42334d1270932117'),
('lgp-69399a584c14c08ef8952436','cat-food-beverage-processing','2026-03-30T15:27:14.034+00:00','cat-legacy-932f0b95cc0c73cf42334d1270932117'),
('lgp-6961e61effd539fea2a7cbe8','cat-food-beverage-processing','2026-03-06T09:22:06.13+00:00','cat-legacy-932f0b95cc0c73cf42334d1270932117'),
('lgp-6965f202c8315e7e6824963b','cat-food-beverage-processing','2026-01-13T07:19:30.971+00:00','cat-legacy-932f0b95cc0c73cf42334d1270932117'),
('lgp-69a1334e7086f85c16f0f5fc','cat-food-beverage-processing','2026-03-30T15:17:37.179+00:00','cat-legacy-932f0b95cc0c73cf42334d1270932117'),
('lgp-69fb39c5987c8aab3fd25b9c','cat-food-beverage-processing','2026-05-06T12:53:25.631+00:00','cat-legacy-932f0b95cc0c73cf42334d1270932117'),
('lgp-69fb3c4f987c8aab3fd25baa','cat-food-beverage-processing','2026-05-06T13:04:15.625+00:00','cat-legacy-932f0b95cc0c73cf42334d1270932117'),
('lgp-69fb3df5987c8aab3fd25bb8','cat-food-beverage-processing','2026-05-06T13:11:17.953+00:00','cat-legacy-932f0b95cc0c73cf42334d1270932117'),
('lgp-69fc8557a164bf8225e11260','cat-food-beverage-processing','2026-05-07T12:28:07.279+00:00','cat-legacy-932f0b95cc0c73cf42334d1270932117'),
('lgp-6a02be7b8bde982d11df5799','cat-food-beverage-processing','2026-05-12T05:45:31.824+00:00','cat-legacy-932f0b95cc0c73cf42334d1270932117'),
('lgp-6a02c03b8bde982d11df57b1','cat-food-beverage-processing','2026-05-12T05:52:59.504+00:00','cat-legacy-932f0b95cc0c73cf42334d1270932117'),
('lgp-6a02c1e38bde982d11df57bf','cat-food-beverage-processing','2026-07-12T17:51:12.431+00:00','cat-legacy-932f0b95cc0c73cf42334d1270932117'),
('lgp-6a02c9658bde982d11df59e1','cat-food-beverage-processing','2026-06-02T22:58:58.213+00:00','cat-legacy-932f0b95cc0c73cf42334d1270932117'),
('lgp-6a02ca568bde982d11df5a50','cat-food-beverage-processing','2026-05-12T06:36:06.12+00:00','cat-legacy-932f0b95cc0c73cf42334d1270932117'),
('lgp-6a1ac6a0dbbe0506a653a892','cat-food-beverage-processing','2026-05-30T11:14:40.973+00:00','cat-legacy-932f0b95cc0c73cf42334d1270932117'),
('lgp-6a5f5a731b422c8f75758403','cat-food-beverage-processing','2026-09-04T02:55:05.936+00:00','cat-legacy-932f0b95cc0c73cf42334d1270932117'),
('lgp-6a69b36927872f5d6996109c','cat-food-beverage-processing','2026-09-04T02:27:03.018+00:00','cat-legacy-932f0b95cc0c73cf42334d1270932117'),
('lgp-6a69b42e27872f5d699610aa','cat-food-beverage-processing','2026-09-21T02:56:20.647+00:00','cat-legacy-932f0b95cc0c73cf42334d1270932117'),
('lgp-6936e978868fa4241303544d','cat-food-beverage-processing','2026-06-12T05:46:25.939+00:00','cat-legacy-e5b89554e221fcfd6d91ef8cc8d721c6'),
('lgp-693972e14c14c08ef8952198','cat-food-beverage-processing','2026-01-02T11:47:49.014+00:00','cat-legacy-e5b89554e221fcfd6d91ef8cc8d721c6'),
('lgp-693c42c5d09b3313c7f914b6','cat-food-beverage-processing','2026-05-06T13:23:30.126+00:00','cat-legacy-e5b89554e221fcfd6d91ef8cc8d721c6'),
('lgp-69a132507086f85c16f0f5ee','cat-food-beverage-processing','2026-02-27T05:57:36.893+00:00','cat-legacy-e5b89554e221fcfd6d91ef8cc8d721c6'),
('lgp-69a148667086f85c16f0f8ed','cat-food-beverage-processing','2026-02-27T07:31:50.278+00:00','cat-legacy-e5b89554e221fcfd6d91ef8cc8d721c6'),
('lgp-69a9a7f1fc73b5e84d265cc4','cat-food-beverage-processing','2026-05-27T19:23:24.815+00:00','cat-legacy-e5b89554e221fcfd6d91ef8cc8d721c6'),
('lgp-69fc833da164bf8225e1123a','cat-food-beverage-processing','2026-06-14T07:21:04.007+00:00','cat-legacy-e5b89554e221fcfd6d91ef8cc8d721c6'),
('lgp-6a58c1e4af44c7ccb1bc01b4','cat-food-beverage-processing','2026-07-16T11:35:00.141+00:00','cat-legacy-e5b89554e221fcfd6d91ef8cc8d721c6'),
('lgp-6a5f59b21b422c8f757583f0','cat-food-beverage-processing','2026-09-05T13:06:06.113+00:00','cat-legacy-e5b89554e221fcfd6d91ef8cc8d721c6'),
('lgp-6a69b4e727872f5d699610b8','cat-food-beverage-processing','2026-07-29T08:08:07.649+00:00','cat-legacy-e5b89554e221fcfd6d91ef8cc8d721c6'),
('lgp-6aa43556fd9900f51c7b8d1d','cat-food-beverage-processing','2026-09-11T17:07:34.424+00:00','cat-legacy-e5b89554e221fcfd6d91ef8cc8d721c6'),
('lgp-6aa43610fd9900f51c7b8d2b','cat-food-beverage-processing','2026-09-11T17:10:40.407+00:00','cat-legacy-e5b89554e221fcfd6d91ef8cc8d721c6'),
('lgp-6aa4370bfd9900f51c7b8d39','cat-food-beverage-processing','2026-09-11T17:14:51.025+00:00','cat-legacy-e5b89554e221fcfd6d91ef8cc8d721c6'),
('lgp-6aa63cb3fd9900f51c7baf58','cat-food-beverage-processing','2026-10-12T15:41:42.92+00:00','cat-legacy-e5b89554e221fcfd6d91ef8cc8d721c6'),
('lgp-6aa63dd4fd9900f51c7baf66','cat-food-beverage-processing','2026-09-13T06:08:20.009+00:00','cat-legacy-e5b89554e221fcfd6d91ef8cc8d721c6'),
('lgp-6aa63efbfd9900f51c7baf74','cat-food-beverage-processing','2026-09-13T06:13:15.372+00:00','cat-legacy-e5b89554e221fcfd6d91ef8cc8d721c6'),
('lgp-6940ff2ca2e6d5821887b4f1','cat-construction','2025-12-16T06:41:48.348+00:00','cat-legacy-184b3c358b12621bf31e7492c88e67e6'),
('lgp-694103dea2e6d5821887b53e','cat-construction','2025-12-16T07:01:50.237+00:00','cat-legacy-184b3c358b12621bf31e7492c88e67e6'),
('lgp-6941057da2e6d5821887b54c','cat-construction','2025-12-16T07:08:45.475+00:00','cat-legacy-184b3c358b12621bf31e7492c88e67e6'),
('lgp-694599c4ded6fb166e7af69b','cat-construction','2025-12-19T18:30:28.128+00:00','cat-legacy-184b3c358b12621bf31e7492c88e67e6'),
('lgp-694a88fefc81c165d5bf3d0f','cat-construction','2025-12-23T12:20:14.2+00:00','cat-legacy-184b3c358b12621bf31e7492c88e67e6'),
('lgp-694a9a94fc81c165d5bf3d20','cat-construction','2025-12-23T13:35:16.258+00:00','cat-legacy-184b3c358b12621bf31e7492c88e67e6'),
('lgp-6965fc6bc8315e7e68249732','cat-construction','2026-01-13T08:03:55.469+00:00','cat-legacy-184b3c358b12621bf31e7492c88e67e6'),
('lgp-697e0b75ddcb706059d8f07b','cat-construction','2026-01-31T14:02:29.184+00:00','cat-legacy-184b3c358b12621bf31e7492c88e67e6'),
('lgp-697e0d67ddcb706059d8f097','cat-construction','2026-01-31T14:10:47.525+00:00','cat-legacy-184b3c358b12621bf31e7492c88e67e6'),
('lgp-698f3dd0b621ff0b5bc5e838','cat-construction','2026-03-04T12:00:29.489+00:00','cat-legacy-184b3c358b12621bf31e7492c88e67e6'),
('lgp-698f54c7b621ff0b5bc5ea76','cat-construction','2026-02-13T16:43:51.033+00:00','cat-legacy-184b3c358b12621bf31e7492c88e67e6'),
('lgp-698f60a7b621ff0b5bc5ecd6','cat-construction','2026-02-20T06:33:28.706+00:00','cat-legacy-184b3c358b12621bf31e7492c88e67e6'),
('lgp-69a138af7086f85c16f0f741','cat-construction','2026-02-27T06:24:47.176+00:00','cat-legacy-184b3c358b12621bf31e7492c88e67e6'),
('lgp-69a13f3f7086f85c16f0f842','cat-construction','2026-02-27T06:52:47.303+00:00','cat-legacy-184b3c358b12621bf31e7492c88e67e6'),
('lgp-69a14be57086f85c16f0f9db','cat-construction','2026-02-27T07:46:45.628+00:00','cat-legacy-184b3c358b12621bf31e7492c88e67e6'),
('lgp-69a14c917086f85c16f0f9e9','cat-construction','2026-02-27T07:49:37.06+00:00','cat-legacy-184b3c358b12621bf31e7492c88e67e6'),
('lgp-69a9ad4dfc73b5e84d265ce1','cat-construction','2026-03-05T16:20:29.76+00:00','cat-legacy-184b3c358b12621bf31e7492c88e67e6'),
('lgp-69ac3acd16938b992d283ddb','cat-construction','2026-03-07T14:48:45.356+00:00','cat-legacy-184b3c358b12621bf31e7492c88e67e6'),
('lgp-69ff1fdab4b9ecc2512b2ff3','cat-construction','2026-05-09T11:51:54.528+00:00','cat-legacy-184b3c358b12621bf31e7492c88e67e6'),
('lgp-69ff20ddb4b9ecc2512b3001','cat-construction','2026-05-09T11:56:13.804+00:00','cat-legacy-184b3c358b12621bf31e7492c88e67e6'),
('lgp-69ff21abb4b9ecc2512b300f','cat-construction','2026-05-09T11:59:39.417+00:00','cat-legacy-184b3c358b12621bf31e7492c88e67e6'),
('lgp-6a1aa32cdbbe0506a653a64a','cat-construction','2026-05-30T08:43:24.712+00:00','cat-legacy-184b3c358b12621bf31e7492c88e67e6'),
('lgp-6a1ac88adbbe0506a653a8ae','cat-construction','2026-05-30T11:22:50.684+00:00','cat-legacy-184b3c358b12621bf31e7492c88e67e6'),
('lgp-6a1acd76dbbe0506a653a9b3','cat-construction','2026-06-14T07:16:04.567+00:00','cat-legacy-184b3c358b12621bf31e7492c88e67e6'),
('lgp-6ab7b7a857b700dbacdef89a','cat-construction','2026-09-26T12:16:40.081+00:00','cat-legacy-184b3c358b12621bf31e7492c88e67e6'),
('lgp-69410b47a2e6d5821887b676','cat-semiconductors','2025-12-16T07:33:27.999+00:00','cat-legacy-1b75c05886e761a17c1bf3945535d107'),
('lgp-69410d46a2e6d5821887b684','cat-semiconductors','2025-12-16T07:43:21.811+00:00','cat-legacy-1b75c05886e761a17c1bf3945535d107'),
('lgp-69411340a2e6d5821887b6cd','cat-semiconductors','2025-12-16T08:07:28.934+00:00','cat-legacy-1b75c05886e761a17c1bf3945535d107'),
('lgp-69411425a2e6d5821887b6db','cat-semiconductors','2025-12-16T09:16:13.431+00:00','cat-legacy-1b75c05886e761a17c1bf3945535d107'),
('lgp-6941151da2e6d5821887b6e9','cat-semiconductors','2025-12-16T08:15:25.078+00:00','cat-legacy-1b75c05886e761a17c1bf3945535d107'),
('lgp-69459abbded6fb166e7af6a9','cat-semiconductors','2025-12-19T18:34:35.55+00:00','cat-legacy-1b75c05886e761a17c1bf3945535d107'),
('lgp-69459bc4ded6fb166e7af6b7','cat-semiconductors','2025-12-23T09:04:02.52+00:00','cat-legacy-1b75c05886e761a17c1bf3945535d107'),
('lgp-69aefda5ebb82ab4ab13b6a6','cat-semiconductors','2026-03-09T17:04:38.033+00:00','cat-legacy-1b75c05886e761a17c1bf3945535d107'),
('lgp-69aefe61ebb82ab4ab13b6b4','cat-semiconductors','2026-03-09T17:07:51.275+00:00','cat-legacy-1b75c05886e761a17c1bf3945535d107'),
('lgp-69aefef5ebb82ab4ab13b6d2','cat-semiconductors','2026-06-11T22:15:14.938+00:00','cat-legacy-1b75c05886e761a17c1bf3945535d107'),
('lgp-69458135ded6fb166e7af4e6','cat-energy','2025-12-19T16:45:41.523+00:00','cat-legacy-49967a119a74c3bc7effd0187e2a2a5f'),
('lgp-6961eb68ffd539fea2a7cbf6','cat-energy','2026-01-10T06:02:16.099+00:00','cat-legacy-49967a119a74c3bc7effd0187e2a2a5f'),
('lgp-696b3c59f6b851f02c34dff8','cat-energy','2026-01-17T07:38:01.234+00:00','cat-legacy-49967a119a74c3bc7effd0187e2a2a5f'),
('lgp-698f3e8cb621ff0b5bc5e846','cat-energy','2026-02-13T15:09:00.394+00:00','cat-legacy-49967a119a74c3bc7effd0187e2a2a5f'),
('lgp-69a131917086f85c16f0f5e0','cat-energy','2026-02-27T05:54:25.192+00:00','cat-legacy-49967a119a74c3bc7effd0187e2a2a5f'),
('lgp-6a1a9fc9dbbe0506a653a63c','cat-energy','2026-05-30T08:28:57.947+00:00','cat-legacy-49967a119a74c3bc7effd0187e2a2a5f'),
('lgp-6a1ac794dbbe0506a653a898','cat-energy','2026-05-30T11:18:44.52+00:00','cat-legacy-49967a119a74c3bc7effd0187e2a2a5f'),
('lgp-6952140b43aea4aa902619b4','cat-other','2025-12-29T05:39:23.221+00:00','cat-legacy-b1925ba28537d311452dbcfa52d44c20'),
('lgp-6961f591ffd539fea2a7cd12','cat-other','2026-01-10T06:45:37.563+00:00','cat-legacy-b1925ba28537d311452dbcfa52d44c20'),
('lgp-696b39b1f6b851f02c34dfea','cat-other','2026-01-17T07:26:41.584+00:00','cat-legacy-b1925ba28537d311452dbcfa52d44c20'),
('lgp-699ff32917f3d138e9ea9b30','cat-other','2026-06-12T05:45:56.375+00:00','cat-legacy-b1925ba28537d311452dbcfa52d44c20'),
('lgp-699ff7ac17f3d138e9ea9b8f','cat-other','2026-02-26T07:35:08.404+00:00','cat-legacy-b1925ba28537d311452dbcfa52d44c20'),
('lgp-699ffbf517f3d138e9ea9b9d','cat-other','2026-02-26T07:53:25.277+00:00','cat-legacy-b1925ba28537d311452dbcfa52d44c20'),
('lgp-6a184ce06ec7c1449be33533','cat-other','2026-06-14T07:15:29.973+00:00','cat-legacy-b1925ba28537d311452dbcfa52d44c20'),
('lgp-69521b3843aea4aa90261a8c','cat-construction','2026-05-07T12:03:10.479+00:00','cat-legacy-719b5deb83f6915a233d66511b96f5f9'),
('lgp-69521c2943aea4aa90261a9a','cat-construction','2025-12-29T06:14:01.893+00:00','cat-legacy-719b5deb83f6915a233d66511b96f5f9'),
('lgp-69521d7643aea4aa90261aa8','cat-construction','2025-12-29T06:19:34.767+00:00','cat-legacy-719b5deb83f6915a233d66511b96f5f9'),
('lgp-696b77d3f6b851f02c34e1a5','cat-construction','2026-01-17T11:51:47.492+00:00','cat-legacy-719b5deb83f6915a233d66511b96f5f9'),
('lgp-696b78b8f6b851f02c34e1b3','cat-construction','2026-01-17T11:55:36.536+00:00','cat-legacy-719b5deb83f6915a233d66511b96f5f9'),
('lgp-696b7a28f6b851f02c34e1c2','cat-construction','2026-01-17T12:01:44.669+00:00','cat-legacy-719b5deb83f6915a233d66511b96f5f9'),
('lgp-6a1acbf3dbbe0506a653a997','cat-construction','2026-05-30T11:37:23.821+00:00','cat-legacy-719b5deb83f6915a233d66511b96f5f9'),
('lgp-6983020ad9aceab2c878e2ff','cat-construction','2026-03-04T11:55:45.275+00:00','cat-legacy-37f54dab36f08e86d4375f8f66ee4bf3'),
('lgp-698302a5d9aceab2c878e315','cat-construction','2026-03-04T11:53:08.868+00:00','cat-legacy-37f54dab36f08e86d4375f8f66ee4bf3'),
('lgp-6a1a98a5dbbe0506a653a4eb','cat-construction','2026-05-30T07:58:29.144+00:00','cat-legacy-37f54dab36f08e86d4375f8f66ee4bf3'),
('lgp-6a1a9975dbbe0506a653a4fa','cat-construction','2026-05-30T08:01:57.456+00:00','cat-legacy-37f54dab36f08e86d4375f8f66ee4bf3'),
('lgp-6a1accb9dbbe0506a653a9a5','cat-construction','2026-05-30T11:40:41.84+00:00','cat-legacy-37f54dab36f08e86d4375f8f66ee4bf3'),
('lgp-6a58c2d0af44c7ccb1bc01c2','cat-agriculture','2026-07-16T11:38:56.495+00:00','cat-legacy-706dda478843e196e8cba43d93c54cea'),
('lgp-6a58c3c4af44c7ccb1bc01d0','cat-agriculture','2026-07-16T11:43:00.891+00:00','cat-legacy-706dda478843e196e8cba43d93c54cea'),
('lgp-6a69946827872f5d69960de3','cat-agriculture','2026-07-29T05:49:28.355+00:00','cat-legacy-706dda478843e196e8cba43d93c54cea'),
('lgp-6a69951b27872f5d69960df1','cat-agriculture','2026-07-29T05:52:27.02+00:00','cat-legacy-706dda478843e196e8cba43d93c54cea'),
('lgp-6a6995d027872f5d69960dff','cat-agriculture','2026-07-29T05:55:28.861+00:00','cat-legacy-706dda478843e196e8cba43d93c54cea'),
('lgp-6a699c0027872f5d69960f73','cat-agriculture','2026-07-29T06:21:52.229+00:00','cat-legacy-706dda478843e196e8cba43d93c54cea'),
('lgp-6a699dd627872f5d69960f81','cat-agriculture','2026-07-29T06:29:42.02+00:00','cat-legacy-706dda478843e196e8cba43d93c54cea'),
('lgp-6a699f4f27872f5d69960f90','cat-agriculture','2026-09-19T12:45:10.591+00:00','cat-legacy-706dda478843e196e8cba43d93c54cea'),
('lgp-6a69c44e2dab83c3139b7b0f','cat-agriculture','2026-07-29T09:13:50.268+00:00','cat-legacy-706dda478843e196e8cba43d93c54cea'),
('lgp-6a69c4fc2dab83c3139b7b1d','cat-agriculture','2026-07-29T09:16:44.749+00:00','cat-legacy-706dda478843e196e8cba43d93c54cea'),
('lgp-6a69c5c52dab83c3139b7b2b','cat-agriculture','2026-07-29T09:20:05.097+00:00','cat-legacy-706dda478843e196e8cba43d93c54cea'),
('lgp-6a6b2c13e497c201a7d07a07','cat-agriculture','2026-07-30T10:49:19.577+00:00','cat-legacy-706dda478843e196e8cba43d93c54cea'),
('lgp-6abe37e763db1bdd5d6a96c3','cat-agriculture','2026-10-01T10:37:27.928+00:00','cat-legacy-706dda478843e196e8cba43d93c54cea'),
('lgp-6abe389563db1bdd5d6a96d9','cat-agriculture','2026-10-01T10:40:21.665+00:00','cat-legacy-706dda478843e196e8cba43d93c54cea'),
('lgp-6a63a4af5195e5be03667e2c','cat-food-beverage-processing','2026-07-24T17:45:19.547+00:00','cat-legacy-d035ff2a7eaada6c0ceed17a3a079367'),
('lgp-6a63a5705195e5be03667e3a','cat-food-beverage-processing','2026-07-24T17:48:32.715+00:00','cat-legacy-d035ff2a7eaada6c0ceed17a3a079367'),
('lgp-6a63a6345195e5be03667e48','cat-food-beverage-processing','2026-07-24T17:51:48.169+00:00','cat-legacy-d035ff2a7eaada6c0ceed17a3a079367'),
('lgp-6a7fe30c816b2b360419579b','cat-food-beverage-processing','2026-08-15T03:54:52.89+00:00','cat-legacy-d035ff2a7eaada6c0ceed17a3a079367'),
('lgp-6a7fe599816b2b36041957b2','cat-food-beverage-processing','2026-08-15T04:05:45.754+00:00','cat-legacy-d035ff2a7eaada6c0ceed17a3a079367'),
('lgp-6a7fe6b3816b2b36041957c0','cat-food-beverage-processing','2026-08-21T12:38:17.919+00:00','cat-legacy-d035ff2a7eaada6c0ceed17a3a079367'),
('lgp-6a66e889eeb3568d515f15fd','cat-machine-tools','2026-07-27T05:11:37.599+00:00','cat-legacy-b50a6054cf8886c6587b04eabf876d84'),
('lgp-6a66e984eeb3568d515f160b','cat-machine-tools','2026-07-27T05:15:48.716+00:00','cat-legacy-b50a6054cf8886c6587b04eabf876d84'),
('lgp-6a66ea2beeb3568d515f1619','cat-machine-tools','2026-07-27T05:18:35.907+00:00','cat-legacy-b50a6054cf8886c6587b04eabf876d84'),
('lgp-6a802566816b2b3604195bcd','cat-machine-tools','2026-08-15T08:37:58.349+00:00','cat-legacy-6c07044de19161096335abec1e014d8e'),
('lgp-6a8025f8816b2b3604195bdb','cat-machine-tools','2026-08-15T08:40:24.634+00:00','cat-legacy-6c07044de19161096335abec1e014d8e'),
('lgp-6a802674816b2b3604195be9','cat-machine-tools','2026-08-21T12:39:33.205+00:00','cat-legacy-6c07044de19161096335abec1e014d8e');
DO $restore$
DECLARE r record; n integer; stamp timestamptz:=clock_timestamp();
BEGIN
 IF (SELECT count(*) FROM sf_v33_children)<>14 OR (SELECT count(*) FROM sf_v33_products)<>164 THEN
  RAISE EXCEPTION 'Unexpected reviewed dataset count';
 END IF;
 -- Explicit rerun is a no-op only while the complete recorded post-state remains unchanged.
 IF EXISTS(SELECT 1 FROM seekfactory_migration_audit.legacy_subcategories_v33) THEN
  IF (SELECT count(*) FROM seekfactory_migration_audit.legacy_subcategories_v33)<>164
    OR EXISTS(SELECT 1 FROM sf_v33_products w LEFT JOIN seekfactory_migration_audit.legacy_subcategories_v33 a ON a.product_id=w.id LEFT JOIN public.products p ON p.id=w.id
      WHERE a.product_id IS NULL OR p.id IS NULL OR a.after_row<>to_jsonb(p)
      OR a.before_row->>'category_id'<>w.old_category OR a.after_row->>'category_id'<>w.new_category)
    OR EXISTS(SELECT 1 FROM sf_v33_children w LEFT JOIN public.categories c ON c.id=w.id
      WHERE c.id IS NULL OR c.slug IS DISTINCT FROM w.slug OR c.name IS DISTINCT FROM w.name OR c.parent_id IS DISTINCT FROM w.parent_id)
  THEN RAISE EXCEPTION 'V33 replay drift; manual review required'; END IF;
  RETURN;
 END IF;
 -- Do all preflight checks before categories or products are changed.
 IF EXISTS(SELECT 1 FROM sf_v33_products w LEFT JOIN public.products p ON p.id=w.id LEFT JOIN public.categories c ON c.id=w.old_category
   WHERE p.id IS NULL OR p.category_id IS DISTINCT FROM w.old_category OR p.updated_at IS DISTINCT FROM w.old_updated_at
      OR c.id IS NULL OR c.parent_id IS NOT NULL)
 THEN RAISE EXCEPTION 'V33 product missing/drifted/not root; refresh review, never overwrite a child'; END IF;
 IF EXISTS(SELECT 1 FROM sf_v33_products p LEFT JOIN sf_v33_children c ON c.id=p.new_category
    WHERE c.id IS NULL OR c.parent_id IS DISTINCT FROM p.old_category)
 THEN RAISE EXCEPTION 'V33 destination does not retain current parent'; END IF;
 IF EXISTS(SELECT 1 FROM sf_v33_children w LEFT JOIN public.categories root ON root.id=w.parent_id
    WHERE root.id IS NULL OR root.parent_id IS NOT NULL)
 THEN RAISE EXCEPTION 'V33 parent missing/not root'; END IF;
 IF EXISTS(SELECT 1 FROM sf_v33_children w JOIN public.categories c ON c.id=w.id OR c.slug=w.slug OR (c.parent_id=w.parent_id AND c.name=w.name))
 THEN RAISE EXCEPTION 'V33 category conflict; review exact existing taxonomy'; END IF;
 INSERT INTO public.categories(id,slug,name,parent_id,listing_count,created_at,updated_at)
 SELECT id,slug,name,parent_id,0,stamp,stamp FROM sf_v33_children;
 -- Capture complete actual rows in a non-public audit schema; retain unrelated fields.
 INSERT INTO seekfactory_migration_audit.legacy_subcategories_v33(product_id,before_row,after_row,applied_at)
 SELECT p.id,to_jsonb(p),to_jsonb(p),stamp FROM public.products p JOIN sf_v33_products w ON w.id=p.id;
 UPDATE public.products p SET category_id=w.new_category,updated_at=stamp FROM sf_v33_products w
 WHERE p.id=w.id AND p.category_id=w.old_category AND p.updated_at=w.old_updated_at;
 GET DIAGNOSTICS n=ROW_COUNT;
 IF n<>164 THEN RAISE EXCEPTION 'V33 expected exactly164 changes'; END IF;
 -- If triggers change any business field, abort rather than silently accepting it.
 IF EXISTS(SELECT 1 FROM public.products p JOIN seekfactory_migration_audit.legacy_subcategories_v33 a ON a.product_id=p.id JOIN sf_v33_products w ON w.id=p.id
   WHERE p.category_id IS DISTINCT FROM w.new_category OR p.updated_at IS DISTINCT FROM stamp
     OR (to_jsonb(p)-'category_id'-'updated_at') IS DISTINCT FROM (a.before_row-'category_id'-'updated_at'))
 THEN RAISE EXCEPTION 'V33 unexpected trigger/field changes'; END IF;
 UPDATE seekfactory_migration_audit.legacy_subcategories_v33 a SET after_row=to_jsonb(p) FROM public.products p WHERE p.id=a.product_id;
END $restore$;
