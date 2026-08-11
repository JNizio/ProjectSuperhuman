# Project Superhuman 11.2.8 performance pass

- Sleep history is month-scoped with a 10-day look-back for the 7-night baseline.
- Month switches perform bounded indexed Data Vault reads instead of lifetime scans.
- Sleep calendar cells/date lookup are memoized.
- Hydration monthly + 7-day history is built from two batched range reads.
- Existing 11.2 Data Vault indexing, pagination and daily aggregates are retained unchanged.
- Full-history reads remain reserved for backup/export/admin paths.
