# Manufacturer phone registration

Supplier email signup now requires `phone` with an explicit international country code. The frontend may suggest a country, but the backend never infers a phone prefix from server location. The existing `phone` JSON field is unchanged. Blank buyer phones remain optional.

All new signup and profile phone writes are validated by Google's libphonenumber 9.0.43 and stored as E.164. Invalid/missing supplier phone returns 400; duplicate phone returns 409. The existing `users.phone` UNIQUE constraint rejects simultaneous canonical duplicate writes; saveAndFlush detects conflicts before signup side effects. Existing global exception handling translates database conflicts to 409.

Formatted legacy numbers are checked read-only using their explicit international prefix or stored ISO/English country. No existing rows are rewritten and no migration is added. Legacy national numbers with missing/unknown country cannot safely be equated to an international number; audit and verify those separately rather than guessing or merging users. Existing ambiguous identities fail closed on phone login. The legacy candidate query strips punctuation and matches the national suffix, so it may scan phone rows during signup/profile changes; it is not on catalog or ordinary email-login paths. A verified canonical backfill/index can be a separate controlled follow-up at scale.

This validates syntax and prevents duplicate canonical registrations. It does NOT prove phone ownership or manufacturer identity. Real SMS verification is not implemented by this change. The existing phone-login mock OTP remains disabled by default. Do not enable mock OTP in production. No SMS provider, paid service, deployment, or production database operation is configured here.

## Validation

Focused tests: `./mvnw -Dtest=PhoneNumberServiceTest,AuthServiceImplAdminGateTest test` on the project's Java 25 toolchain. Covers CN/IN/UK formatting, absent supplier phone, optional buyer phone, malformed/national-only/extension input, exact and formatted legacy duplicates, ambiguous identities, and own-account edits. The admin phone-login regression test retains its TOTP gate.

Integration acceptance: submit two concurrent supplier registrations with differently formatted versions of the same number; exactly one must succeed and the other return 409. Confirm existing email login still works, and a supplier profile cannot clear its phone. No live database should be used for this check.
