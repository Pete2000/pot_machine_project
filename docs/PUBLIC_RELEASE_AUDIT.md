# Public Release Audit

## Current public release status (2026-08-11)

This project was published as a separate public Git repository from a sanitized release snapshot. Its history begins with a new clean root and contains no prior project commits. The legacy archive remains private and must not be made public.

The public repository uses the `dev` default branch and Apache-2.0. Its tracked tree was checked for `local_order_store.xml`, `local_order_store_utf8.xml`, and `docs/order-api.docx`; the count was zero. GitHub Actions were triggered after publication. The sections below are the retained pre-publication audit record.

**Status:** Apache-2.0 selected locally — production order-interface data exclusion staged; history gate remains.
**Audit date:** 2026-08-11
**Baseline inspected:** Git `HEAD` `b83426f5e09b6ccd2d7fdfcfa9560285ce8c12fe` and the local public-release preparation changes.

## Scope and limits

This is a path, history, and filename-only pattern audit intended to identify material public-release risks without printing possible local operational data into the audit log. It is **not** a claim that the repository is free of credentials, personal data, customer data, or third-party material. The content and redistribution-rights review below remains required.

## Completed safely in the local worktree

The following generated, local, or potentially operational artifacts were removed from Git tracking and added to `.gitignore`. Their local files were retained; this operation did not delete them from disk.

| Path | Reason | Current local action |
| --- | --- | --- |
| `local_order_store.xml` | Potential captured order data | Staged removal from Git; local file retained |
| `local_order_store_utf8.xml` | Potential captured order data | Staged removal from Git; local file retained |
| `docs/order-api.docx` | Maintainer-classified non-public order-interface data | Staged removal from Git; local file retained |
| `tmp-kotlinc-order-check/` | Generated Kotlin compiler output | Staged removal from Git; local directory retained |
| `tmp_modbus_manual.pdf` | Temporary / possible third-party manual | Staged removal from Git; local file retained |

The public-build defaults have also been changed to a mock PLC transport and loopback service URLs for a fresh install. Existing installed-device settings stored in SharedPreferences were not modified. See [PUBLIC_RELEASE_BOUNDARY.md](PUBLIC_RELEASE_BOUNDARY.md) for the intended boundary and [AppConfigPublicDefaultsTest.kt](../app/src/test/java/com/example/plccontroller/AppConfigPublicDefaultsTest.kt) for the regression check.

## Maintainer publication decision

On 2026-08-11, the maintainer confirmed that all material other than production order-interface data may be public. The public tree must exclude customer order records, captured local order stores, and production order-interface request/response payloads.

`docs/order-api.docx` is classified as non-public. A values-free structural audit found 81 long-identifier patterns and one endpoint-path pattern; visual rendering could not run because the local environment has no LibreOffice runtime. The document is therefore removed from Git tracking while retained locally. Other tracked order-related Markdown design documents had zero long-identifier patterns and remain in the public scope under the maintainer decision.
## Release blockers and required decisions

1. **Keep order-interface data excluded.** The current public tree must not include `docs/order-api.docx`, local order-store exports, customer order records, or production request/response payloads.
2. **Decide whether Git history needs remediation.** The artifacts listed above remain in existing commits. Removing them from the next commit prevents further publication but does not remove their historical contents. If they contain sensitive or customer data, inspect affected history, rotate any exposed credentials if applicable, then decide whether to rewrite history before wider release.
3. **Review device-control disclosure.** This public repository contains PLC register maps, command flows, and a native serial library. The safety boundary, supported mock behavior, and warning against using public builds for uncontrolled hardware must remain clear; release validation still requires an authorized real-device process.
4. **Review all pattern-scan candidates manually.** Filename-only scans reported configuration, token, password, or secret terminology in ordinary source and example files. No values were copied into this document and no candidate is cleared by this scan alone.

## Static content-scan record

On 2026-08-11, a filename-only static scan examined 208 readable files currently tracked by Git. It excluded Git history, untracked files, and binary artifacts, and it did not copy matched values into this document.

| Rule | Candidate files | Matches | Interpretation |
| --- | ---: | ---: | --- |
| Credential-like quoted assignment | 0 | 0 | No match; not proof that credentials are absent |
| Network locator | 26 | 55 | Needs manual review; source may include harmless XML namespaces or documentation examples |
| Serial endpoint | 12 | 18 | Device-specific integration / test references require disclosure review |
| PLC register reference | 35 | 3,073 | Control implementation and protocol documents require safety and rights review |

These are review triggers, not findings or clearance. No release decision should be based on this scan alone.
## Current verification record

| Check | Result | Boundary |
| --- | --- | --- |
| Isolated clean-worktree Android build | `:app:assembleDebug :app:testDebugUnitTest --no-daemon` passed | Validates public defaults and unit tests without unrelated local WIP |
| Main worktree Android build | Blocked by pre-existing `SettingsDevicePage.kt` / settings-editor declaration conflicts | Not caused or changed by this release-preparation work |
| Local-ignore verification | Candidate XML files, order API DOCX, temporary PDF, and compiler directory are ignored after staged untracking | Local artifacts remain available and are no longer selected for a future Git add |
| Remote GitHub Actions workflow | Added locally only | Has not run remotely; no GitHub push or release was performed |

## Before the first public-release commit

- Include the selected Apache-2.0 `LICENSE` in the intentional release commit.
- Confirm the production order-interface payload and local-order-store exclusions remain intact in the intended release commit.
- Decide whether history cleanup is necessary before publishing.
- Ensure the commit contains only intentional release files and does not include unrelated local work, especially the existing settings-page WIP.
- After a clean commit, run the Android build and unit tests again; run hardware checks only with authorized equipment and a safe test plan.

## Non-goals of this audit

- No Git history was rewritten.
- No local artifact above was physically deleted.
- No release, push, pull request, deployment, or device command was performed.
