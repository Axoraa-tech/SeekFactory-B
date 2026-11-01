# Proposed external validation scope (not provisioned)

This is the concrete remaining approval boundary, not permission already granted or a production plan.
The total production hosting target remains INR4,000/month for the hybrid Azure VM/self-managed
PostgreSQL plus R2 design. Local tests do not prove500 production users within that budget.

Proposed isolated24-hour test:

- Dedicated Azure resource group `seekfactory-validation-20261101`, East Asia/Hong Kong, Linux
  `Standard_B1ms` (one vCPU,2 GiB),32 GiB SSD and one public IP. Run Next.js, Java25 and a fresh local
  PostgreSQL database with synthetic accounts. Use independent staging secrets, no SMTP, no production
  data/restore, no autoscaling and no automatic SKU changes. Stop on errors, resource saturation or
  p95 above the agreed limit.
- Dedicated private R2 bucket `seekfactory-validation-20261101`, bucket-scoped test credentials,
  region `auto`, public domains disabled, public-base URL empty. Proposed CORS fixture is
  `staging-r2-cors.json`; use the approved staging origin. Test signed-link expiry/renewal, denied
  unsigned private-object access, range seeking, cache headers and original availability during encoding.
- Bound synthetic test data to1 GiB stored and10 GiB delivered; cap API operations at200,000. Measure
  observed request/storage/network charges and remaining account free allowances rather than assume
  they are unused. No paid third-party load or geographic-probe service is proposed.
- Obtain authorized ordinary-network probes in India and mainland China against the actual staging
  URLs. Test startup, sustained playback, seek/reconnect and expiry. A VPN/cloud proxy in another region
  is not evidence of a mainland user's access.
- Remove the dedicated staging resources after the approved test/report window. Disks/IPs must be
  included; stopping a VM alone does not remove their charges. Do not change production resources.

Approval would need to cover a proposed **INR150 maximum test budget over24 hours**, only if the final
portal estimate fits that envelope. This is an approval ceiling, not an Azure hard billing cap.
Microsoft's public Retail Prices API returned INR2.7807/hour for Linux B1ms East Asia on2026-11-01:
INR66.74 compute-only for24 hours. Disk, IP, requests, storage, egress, backups and actual tax are extra;
confirm the combined quote/account allowances before creating anything. The limit does not authorize
a larger SKU if the small configuration fails.

Price source: [Azure Retail Prices API](https://learn.microsoft.com/en-us/rest/api/cost-management/retail-prices/azure-retail-prices),
filtered by `armRegionName=eastasia`, `armSkuName=Standard_B1ms`, consumption, INR, excluding Windows,
Spot and Low Priority. No resources or credentials have been created by this proposal.
