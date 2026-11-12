# MyBatis Repository/XML Migration Implementation Plan

> **For agentic workers:** Use superpowers:subagent-driven-development for implementation and independent review. Do not commit existing user changes.

**Goal:** Move runtime database access from JdbcTemplate/NamedParameterJdbcTemplate and SQL annotations to named MyBatis Repository interfaces and XML, preserving HTTP and business contracts.

**Architecture:** Controllers delegate to Spring-managed services; services own validation and transactions; repositories own persistence via mapper XML. Keep complex response maps initially, with explicit aliases/null mapping; introduce typed rows or mutable insert commands for generated IDs as needed.

**Tech Stack:** Existing Java 17, Spring Boot, MyBatis, MySQL, Flyway, JUnit/Mockito.

**Spec:** User-approved migration approach in this chat, 2026-11-12.

## Global Constraints
- Preserve all pre-existing uncommitted changes. Work on the existing feature branch; no commit, deployment, restart or mutation of the user's application database.
- Keep endpoint URLs, response shapes, authorization, business rules and schema unchanged.
- Preserve transaction isolation/rollback behavior, lock ordering, SQL clock semantics and file transaction callbacks.
- Replace manual Service construction with injected dependencies without introducing cycles.
- Use named repository methods and XML SQL; no generic SQL-execution adapter or ${sql} escape hatch.
- Keep Flyway Java migrations outside runtime migration scope; they must retain their original implementation/checksum behavior.
- Disable session caching for current/locking reads and preserve null-valued map fields.

## Review Focus
- Cross-enterprise access and employee/admin isolation.
- INSERT SELECT/generated IDs and copied exam/material relationships.
- Missing rows, nullable columns, boolean and timestamp response compatibility.
- Concurrent learning heartbeats, completion guards and copied exam cleanup rollback.
- Dynamic SQL empty lists, XML escapes and missing mapper statements.

### Task 1: Runtime repositories, XML and call sites
- [x] Baseline existing tests and snapshot current source before edits.
- [x] Move department/account/learner persistence to repository interfaces/XML; adapt service unit tests.
- [x] Move training/audience/eligibility/material/copy persistence; replace manual service construction at all callers.
- [x] Move learning sessions, shared clock, video/page progress and associated controller SQL.
- [x] Move public exam/auth/profile/news/favorites/selftest/test-paper/dashboard/reminder/log SQL; extract controller workflows to services where needed.
- [x] Move existing MyBatis annotation SQL into XML.
- [x] Run full Maven tests; verify no runtime JdbcTemplate or inline SQL remains.

### Task 2: Real mapper and MySQL verification
- [x] Add a mapping contract test that parses every mapper and checks each repository statement binding.
- [x] Add isolated MySQL integration tests for generated IDs, null mappings, ownership, learning progress/locks and rollback; seed only a disposable database.
- [x] Run tests against disposable MySQL with all Flyway migrations; record actual results and limitations.
- [x] Run backend package and check frontend contract impact; update development conventions and migration report.

### Task 3: Independent review
- [x] Compare against saved pre-migration source, reviewing changed behavior, not pre-existing unrelated changes.
- [x] Resolve regressions and rerun affected checks.
- [x] Summarize completed scope and verification without claiming deployment.

## Execution ledger
- Existing workspace contains 77 user changes on codex/training-upgrade; baseline source archived outside the repository. Ruling: work in place to preserve the exact uncommitted state the user asked to migrate.
- Ruling: keep the separately noted department-ID assignment defect out of the behavior-preserving migration; document if still present.

- Task 1 complete: runtime persistence migrated; typed Repository APIs and 241 migrated XML statements. Existing JDBC mock tests adapted without removing cases.
- Task 2 complete: `mvn -q package` with disposable MySQL enabled passed, 173 tests / 0 failures / 0 errors / 1 pre-existing external storage test skipped. Eleven real MySQL integration tests passed. Fresh disposable schema ran Flyway through V45.
- Task 3 complete: independent migration-only review found and fixed mutable random-question-list regression; service transaction ownership corrected. Added random exam snapshot/resume regression. No unresolved migration regressions reported.
- Frontend working diff and historical Flyway files verified unchanged from pre-migration snapshot. No app restart, commit, push or deployment performed.
