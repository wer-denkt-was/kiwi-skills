---
name: kiwi-upgrade
description: Upgrade an application from Kiwi Framework 1.4 to 1.5 (Spring Boot 3.5 → 4.1, Jackson 2 → 3, Hibernate 6 → 7, Kotlin 2.3), including its Vue frontends from Kiwi-Vue 1.x to 2.0 (@werdenktwas/kiwi-vue), phase by phase with builds, tests, commits and a report of decisions for the user. Also upgrades frontend-only projects using Kiwi-Vue 1.x. Run with an optional phase argument; "detect" only writes the findings report.
disable-model-invocation: true
argument-hint: "[detect|<phase number>]"
---

# Upgrade a Kiwi 1.4 application to Kiwi 1.5 (and Kiwi-Vue 1.x to 2.0)

You are migrating an existing application, so the goal is the **same behavior on the new framework version**, with
every unavoidable behavior change made visible to the user. Resist improving or refactoring code along the way: a
reviewer has to be able to tell migration changes apart from anything else, and the test suite is the main evidence
that behavior was preserved.

Paths below are relative to this skill's base directory:

- `references/migration-1.5.md`: the official migration guide, the source of truth. Read it completely first.
- `references/generic-commands.md`: how to rewrite generic create/update commands and handlers (the largest manual
  part of the upgrade), with before/after examples.
- `references/kiwi-vue-2.md`: how to upgrade frontends from Kiwi-Vue 1.x to 2.0, with the rewrites and a checklist.
- `scripts/detect-1.4-usages.sh [project-dir]`: grep-based detection of 1.4 usages (server and Kiwi-Vue frontends),
  printing a Markdown report.
- `../kiwi-server/references/`: the full Kiwi 1.5 documentation (chapter files), for details on any API.
- `../kiwi-vue/references/`: the Kiwi-Vue 2.0 documentation (`README.md`) and changelog with the 1.x migration guide
  (`CHANGELOG.md`).

Kiwi-Vue 2.0 is the client library for Kiwi-Server 1.5, and Kiwi-Vue 1.x the one for 1.4: frontends using
`@werdenktwas/kiwi-vue` must be upgraded together with their server, and deployed together with it.

**Frontend-only projects**: if the project depends on `@werdenktwas/kiwi-vue` 1.x but contains no Kiwi server (no
`kiwi-bundle`/`kiwi-framework` dependency), the server phases 2–6 don't apply. Use the branch `upgrade/kiwi-vue-2`,
do phases 0, 1, 7 (frontend part), 8 and 9, and title the report "Kiwi-Vue 2.0 upgrade". Ask the user whether the
server is already on Kiwi 1.5 and where its regenerated TypeScript model is, if it isn't in the repository.

Arguments: `$ARGUMENTS`. The run modes:

- **Empty**: run all phases. If `KIWI-UPGRADE.md` from an earlier `detect` run exists on the upgrade branch, reuse
  its baseline and findings instead of repeating phases 0–1 (re-run the detection script only if the code changed
  since).
- **`detect`**: phases 0–1 only. Creates the upgrade branch and commits `KIWI-UPGRADE.md` there as
  `Kiwi 1.5 upgrade: detection report`, but changes no code, build or configuration file. Committing keeps the
  working tree clean for the next run.
- **A phase number** (2–9): continue the upgrade from that phase. Run the checks of phase 0 (clean tree, branch),
  but don't record the baseline again if `KIWI-UPGRADE.md` has one. If `KIWI-UPGRADE.md` is missing (e.g. earlier
  phases were done on another branch, or by hand), run phase 1 first to create it, assume the earlier phases are done
  as the user says, and state in the report which phases this run covered. Do only the phases the user asked for.

## Phase 0: Preflight

1. Check that the git working tree is clean. If it isn't, stop and ask the user, because the upgrade commits must not
   mix with unrelated changes.
2. Switch to the branch `upgrade/kiwi-1.5`, creating it if needed. If the user is already on a dedicated branch, or
   asks for a different one (e.g. because a colleague's branch already uses that name), use that branch instead.
3. Find the build tool and modules (`settings.gradle.kts`, `pom.xml`), the Kiwi modules in use (`kiwi-bundle`,
   `kiwi-storage`, `kiwi-audit-log`, `kiwi-mjml`) and where versions are declared (version catalog, properties, BOM).
   Find frontends: every `package.json` (outside `node_modules`) depending on `@werdenktwas/kiwi-vue`, their package
   manager and lock file, and their build/test scripts. Ask the user about frontends in other repositories.
4. Run the build with tests on the unchanged project and record the result as baseline (number of tests, failing
   tests). Tests that already fail on 1.4 are not your regressions, but list them. If the build needs services that
   aren't available (database, Docker for Testcontainers), record which test tasks can't run, and continue with the
   ones that can. If the build can't run at all (no network, no build tool), record the baseline as "not recorded"
   with the reason, continue, and tell the user to record it before merging; the later phases then rely on reading
   the code, and phase 8 on whatever compiles. For frontends, record the type check and tests the same way
   (`npm run build` or `vue-tsc --noEmit`, `npm test`); they need installed `node_modules`.

## Phase 1: Detect

Run `scripts/detect-1.4-usages.sh <project-root>` and create `KIWI-UPGRADE.md` in the project root from the template
at the end of this file, including the detection report. The findings are heuristics: use them as a checklist,
confirm each one by reading the code, and add what the script can't see (e.g. custom `Specification`s used in
access control, frontends in other repositories).

Stop here for `detect`: commit `KIWI-UPGRADE.md` (only this file), and summarize the findings, the decisions the
user has to make, and the expected effort.

## Phases 2–7: Migrate

Work through the phases in order. Each phase ends with a commit `Kiwi 1.5 upgrade: <phase>` on the upgrade branch
(never push; skip the commits if the user asks to), and a short entry in `KIWI-UPGRADE.md`. The project usually doesn't compile until phase 5 is done;
from then on, compile after every step.

2. **Build and dependencies**: Kiwi 1.5.x (artifact `kiwi-bundle` → `kiwi-framework`), Spring Boot 4.1.x, Kotlin
   2.3.x, Java 21 toolchain, renamed starters, `spring-boot-starter-liquibase`, `hibernate-processor`, Testcontainers 2
   artifacts, Jackson 3 artifacts (`tools.jackson.module:jackson-module-kotlin`), test starters for slices. Check that
   the Gradle (or Maven) version supports Spring Boot 4, and update the wrapper if not. Remove explicit versions of
   libraries that the Spring Boot BOM manages, rather than bumping them by hand.
3. **Application setup**: remove `@Import` of Kiwi configurations and `@EntityScan`/`@EnableJpaRepositories` that only
   list Kiwi packages (keep application packages if they are outside the main class's package), renamed packages
   (`de.kiwi.auditlog`, `de.kiwi.mjml`), moved Spring Boot classes. Use the
   [Spring Boot 4.0 migration guide](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide)
   for Spring changes not caused by Kiwi.
4. **Jackson 3**: imports, renamed types and methods, immutable mapper builders, and the `JsonNode.map {}` pitfall
   (iterate arrays with `node.values()`), in all source sets including tests. Phase 4 owns every Jackson change, also
   in files that phase 5 rewrites later. Custom serializers/deserializers change their base classes and signatures;
   check them against the Jackson 3 API rather than renaming imports only.
5. **Kiwi API**: generic commands per entity following `references/generic-commands.md`, then the remaining API
   changes from the guide (JWT removal, nullness signatures, `Specification`s in access control, `MailTemplateRenderer`,
   custom `TransactionService` and `DetailsProvider` implementations). Do one entity at a time and compile after each.
   If phase 4 isn't done in this branch (e.g. the user asked for phase 5 only), keep the Jackson 2 imports in the
   files you touch, and note in the report that they compile only together with phase 4.
6. **Configuration properties**: renamed and removed `kiwi.*` properties, explicit `kiwi.storage.provider`, the local
   storage token secret. Temporarily adding `org.springframework.boot:spring-boot-properties-migrator` (runtime) reports
   renamed Spring Boot properties at startup; remove it again before finishing.
7. **HTTP clients, tests and frontends**: `PUT` → `PATCH` for generic entity endpoints in tests and in client code
   inside this repository that doesn't use Kiwi-Vue; adjust tests that expect the old status codes or error codes for
   invalid bodies. Upgrade every Kiwi-Vue frontend to 2.0 following `references/kiwi-vue-2.md`: dependency, the
   regenerated TypeScript model (from the server after phase 5), CRUD service types, update bodies with changed
   properties only and the new JSON keys, custom requests, error messages. Kiwi-Vue sends `PATCH` itself; don't
   patch its calls by hand. One commit per frontend: `Kiwi 1.5 upgrade: Kiwi-Vue 2.0 (<frontend>)`.

When something is ambiguous (the guide doesn't cover it, or two plausible rewrites change behavior differently), ask
the user instead of guessing, and note the question in `KIWI-UPGRADE.md` if you can continue elsewhere.

## Phase 8: Verify

1. Run the full build with tests and compare with the baseline. Fix regressions caused by the upgrade. Never make
   tests pass by deleting, disabling or weakening them; if a test encodes 1.4 behavior that changed on purpose (e.g.
   `PUT` endpoints), update the expectation and list it in the report.
2. For each frontend, run the type check, the tests and the lint/build scripts it has, and compare with the
   baseline. Without installed dependencies (no network), list them as not run.
3. Start the application context (an existing `@SpringBootTest`, or the application with its dev profile) and read
   the startup log: Kiwi fails fast on invalid schemas, duplicate generic classes and invalid body declarations, and
   warns when a body property's name differs from its JSON key.
4. Don't modify Liquibase changesets that may have been applied already. The audit-log module migrates its table with
   its own changelog (binary details → JSON); mention that production databases need a backup before deploying.

## Phase 9: Report

Complete `KIWI-UPGRADE.md` and summarize it for the user. The behavior changes are the part the user must decide
on, so describe for each one what changes for *this* application, based on the code you saw:

- **Roles (security relevant)**: roles are authorities without `ROLE_`. Users created with
  `User.roles("ADMIN")`, or with an authority `ROLE_ADMIN`, were *not* Kiwi admins in 1.4 and *are* in 1.5. List where
  users get their authorities and whether any user gains admin rights; `kiwi.security.role-prefix=""` restores the
  1.4 behavior.
- **Client-facing API**: `PATCH` instead of `PUT`, changed JSON keys of generic commands, keys that are rejected now,
  trailing slashes no longer matching, `400 entity-body-invalid` for invalid bodies. Frontends must be updated together
  with the deployment; Kiwi-Vue 2.0 frontends don't work with a 1.4 server, and 1.x frontends can't update entities
  on a 1.5 server.
- **Frontends (Kiwi-Vue 2.0)**: changed request bodies and keys, error messages shown for the new error codes
  (`entity-body-invalid`, `access-denied`, `unauthorized`), request paths that resolve differently, and anything the
  frontend's tests don't cover (forms editing several fields).
- **Storage**: `kiwi.storage.provider` is required (startup fails without it, unless the application declares its own
  `FileStorageService` bean), local file URIs signed with HMAC tokens (1.4 signed URIs become invalid),
  S3 subdomain URIs of non-AWS providers.
- **Audit log**: errors persist in a separate transaction; the table migration.
- **Paging**: `totalLimit` caps paged queries as well.

## KIWI-UPGRADE.md template

```markdown
# Kiwi 1.5 upgrade

## Status
Phase: <last completed phase> · Branch: upgrade/kiwi-1.5 · Build: <green/red> · Tests: <passed>/<total> (baseline: …)
Frontends: <frontend>: Kiwi-Vue <version> · type check <green/red/not run> · tests <passed>/<total>

## Baseline (Kiwi 1.4)
<build result, failing tests, test tasks that couldn't run; frontend type checks and tests>

## Findings
<detection report, confirmed/dismissed per item>

## Changes
- <phase>: <summary> (<commit>)

## Decisions for the user
- [ ] <behavior change or question>: <what changes for this application, options, recommendation>

## Client-facing changes
<endpoints, methods, JSON keys, error responses that frontends and API clients must adapt to>

## Open issues
<remaining failures, skipped items, follow-ups>
```
