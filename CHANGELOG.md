# Changelog

All notable changes to this repository are documented here.

The format is based on Keep a Changelog and the project uses Semantic Versioning.

## [Unreleased]

### Added

- Party identifiers (`party_identifier`) and addresses (`party_address`) with
  idempotent PostgreSQL and H2 DDL, tenant-scoped list/add endpoints on both
  `/internal/v1/parties/{partyId}` and `/api/v1/parties/{partyId}`, validation,
  duplicate-identifier 409 responses, and `party.party.identifier-added` /
  `party.party.address-added` outbox events.
- Transactional outbox writes for `party.party.created` and `party.party.updated`.
- Party status update endpoint and conditional Quartz outbox dispatch.
- Outbox schema DDL.
