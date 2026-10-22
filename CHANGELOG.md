# Changelog

All notable changes to this repository are documented here.

The format is based on Keep a Changelog and the project uses Semantic Versioning.

## [Unreleased]

### Added

- Party identifier and address update/delete endpoints
  (`PATCH`/`DELETE /internal/v1/parties/{partyId}/identifiers/{identifierId}`
  and `.../addresses/{addressId}`, also on `/api/v1`) with full-replacement
  validation, tenant- and party-scoped 404s, duplicate-identifier 409 on
  update, Party version bumps, and `party.party.identifier-updated` /
  `party.party.identifier-deleted` / `party.party.address-updated` /
  `party.party.address-deleted` outbox events. Update events carry
  `changedFields` and `from`/`to` snapshots; delete events carry the last
  stored snapshot.
- Party identifiers (`party_identifier`) and addresses (`party_address`) with
  idempotent PostgreSQL and H2 DDL, tenant-scoped list/add endpoints on both
  `/internal/v1/parties/{partyId}` and `/api/v1/parties/{partyId}`, validation,
  duplicate-identifier 409 responses, and `party.party.identifier-added` /
  `party.party.address-added` outbox events.
- Transactional outbox writes for `party.party.created` and `party.party.updated`.
- Party status update endpoint and conditional Quartz outbox dispatch.
- Outbox schema DDL.
