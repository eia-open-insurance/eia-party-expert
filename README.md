# eia-party-expert

- GitHub organization: `eia-open-insurance`
- Repository URL: https://github.com/eia-open-insurance/eia-party-expert
- Type: Spring Boot service
- Deployable: Yes
- Owner: Party
- Runtime: Java 22, Spring Boot, Spring Batch, Quartz
- Data store: eia_party (ent, ref, audit schemas)
- Depends on: eia-contracts, eia-platform/libs

## Purpose

Party persistence, invariants, read models, batch jobs, Quartz schedules, and outbox/inbox. Party identity, roles, merge, KYC/AML, sanctions, and consent.

## Responsibilities

- Keep this repository focused on one deployable component or one platform concern.
- Follow the repository standard in `../CONVENTIONS.md` and the specification in `../../SPEC.md`.
- Publish and consume contracts through `eia-contracts`.
- Maintain README, CODEOWNERS, SECURITY, CHANGELOG, tests, and local verification.
- Contain no business logic in shared libraries or front-end remotes.

## Transactional outbox

Party mutations and their event row commit in one database transaction. The
outbox table is created by `src/main/resources/schema.sql`.

| Operation | Event | Payload |
|---|---|---|
| `POST /internal/v1/parties` | `party.party.created` | `partyId`, `partyNumber`, `partyType`, `status`, `roles[]` |
| `PATCH /internal/v1/parties/{id}/status` | `party.party.updated` | `partyId`, `partyNumber`, `changedFields`, `version`, `from`, `to`, `reason` |
| `POST /internal/v1/parties/{partyId}/identifiers` | `party.party.identifier-added` | `partyId`, `partyNumber`, `version`, `identifierId`, `identifierType`, `identifierValue`, `issuingAuthority`, `effectiveDate`, `createdBy`, `createdAt` |
| `PATCH /internal/v1/parties/{partyId}/identifiers/{identifierId}` | `party.party.identifier-updated` | `partyId`, `partyNumber`, `version`, `identifierId`, `changedFields[]`, `from`, `to` |
| `DELETE /internal/v1/parties/{partyId}/identifiers/{identifierId}` | `party.party.identifier-deleted` | `partyId`, `partyNumber`, `version`, `identifierId`, deleted snapshot (`identifierType`, `identifierValue`, `issuingAuthority`, `effectiveDate`, `createdBy`, `createdAt`) |
| `POST /internal/v1/parties/{partyId}/addresses` | `party.party.address-added` | `partyId`, `partyNumber`, `version`, `addressId`, `addressType`, `line1`, `line2`, `city`, `stateRegion`, `postalCode`, `country`, `effectiveDate`, `createdBy`, `createdAt` |
| `PATCH /internal/v1/parties/{partyId}/addresses/{addressId}` | `party.party.address-updated` | `partyId`, `partyNumber`, `version`, `addressId`, `changedFields[]`, `from`, `to` |
| `DELETE /internal/v1/parties/{partyId}/addresses/{addressId}` | `party.party.address-deleted` | `partyId`, `partyNumber`, `version`, `addressId`, deleted snapshot (`addressType`, `line1`, `line2`, `city`, `stateRegion`, `postalCode`, `country`, `effectiveDate`, `createdBy`, `createdAt`) |

Events use the envelope from `eia-platform/libs`; `book` is `DIRECT` because
the current Party API has no book input and Party is shared master data.

## Party identifiers and addresses

Identifiers and addresses are child facts of the Party aggregate. The same
resources are exposed on the internal expert route and on the public path used
by the Party domain service:

| Operation | Internal | Public |
|---|---|---|
| List identifiers | `GET /internal/v1/parties/{partyId}/identifiers` | `GET /api/v1/parties/{partyId}/identifiers` |
| Add identifier | `POST /internal/v1/parties/{partyId}/identifiers` | `POST /api/v1/parties/{partyId}/identifiers` |
| Update identifier | `PATCH /internal/v1/parties/{partyId}/identifiers/{identifierId}` | `PATCH /api/v1/parties/{partyId}/identifiers/{identifierId}` |
| Delete identifier | `DELETE /internal/v1/parties/{partyId}/identifiers/{identifierId}` | `DELETE /api/v1/parties/{partyId}/identifiers/{identifierId}` |
| List addresses | `GET /internal/v1/parties/{partyId}/addresses` | `GET /api/v1/parties/{partyId}/addresses` |
| Add address | `POST /internal/v1/parties/{partyId}/addresses` | `POST /api/v1/parties/{partyId}/addresses` |
| Update address | `PATCH /internal/v1/parties/{partyId}/addresses/{addressId}` | `PATCH /api/v1/parties/{partyId}/addresses/{addressId}` |
| Delete address | `DELETE /internal/v1/parties/{partyId}/addresses/{addressId}` | `DELETE /api/v1/parties/{partyId}/addresses/{addressId}` |

The child path segment must belong to the party and tenant in the URL; a child
of another party or tenant is the same 404 as an unknown id, so the response
cannot confirm that a foreign row exists. `PATCH` replaces every mutable field
of the child (`createdBy` and `createdAt` are immutable), applies the same
normalization and validation as `POST`, and returns the updated child. An
update that changes nothing is a no-op with no version bump and no event.
`DELETE` returns `204 No Content`; deleting an already removed child is a 404.

The tenant is supplied as `?tenantId=` for internal calls or as the
`X-EIA-Tenant` header when the request arrives through the gateway. A missing
tenant is a 400 and a party that does not belong to the tenant is a 404, so one
tenant can never read or write another tenant's reference data.

Identifier types are `TAX_ID`, `REGISTRATION`, `LICENSE`, `PASSPORT`, and
`OTHER`. Address types are `PRIMARY`, `MAILING`, `BILLING`, and `OTHER`. Types
are upper-cased and every text field is trimmed. Required fields are rejected
when blank, `identifierValue` is limited to 128 characters, address `line1` to
128, `city` to 64, `stateRegion` to 64, `postalCode` to 16, and `country` must
be exactly two letters. A duplicate
`(tenant, party, identifierType, identifierValue)` is a 409 `CONFLICT`, both on
create and on an update that moves the identifier onto another row's key.

Adding, updating, or deleting reference data bumps the Party aggregate version
and writes the event in the same transaction as the child write. The
`-updated` event carries the changed field names and `from`/`to` snapshots; the
`-deleted` event carries the last stored snapshot because the row is gone after
commit. The database schema creates both tables idempotently in PostgreSQL
(`src/main/resources/schema.sql`) and H2 (`src/test/resources/schema-h2.sql`).

Dispatch is disabled by default and is scheduled by Quartz only when
`eia.outbox.enabled=true`:

- `EIA_OUTBOX_ENABLED` (default `false`)
- `EIA_OUTBOX_ENDPOINT` (required when enabled)
- `EIA_OUTBOX_BATCH_SIZE` (default `100`)
- `EIA_OUTBOX_MAX_ATTEMPTS` (default `12`)
- `EIA_OUTBOX_SIGNING_SECRET` (optional HMAC secret)

The Gradle build uses a composite build against `../eia-platform` so
`com.eia:libs` resolves to the local `:libs` project. There is no broker; the
dispatcher posts the envelope over HTTP.

## Verification

- Lint and format
- Unit tests with 100% business-logic coverage where applicable
- Contract tests
- Integration or Playwright tests where applicable
- Security scan and SBOM
- Build and publish

## Notes

Writes data; does not decide business outcomes.

## License

Apache License 2.0. See [LICENSE](LICENSE) and [NOTICE](NOTICE).
