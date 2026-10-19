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

Events use the envelope from `eia-platform/libs`; `book` is `DIRECT` because
the current Party API has no book input and Party is shared master data.

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
