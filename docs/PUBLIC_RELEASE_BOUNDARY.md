# Public release boundary

This document defines what may be part of the open-source `v0.1` core and what requires explicit maintainer and rights-holder approval before publication.

## Public core

The following are intended to be reusable and testable without physical equipment:

- Android/Compose UI, domain models, parsing, recipe matching, queue and order lifecycle rules.
- Persistence adapters, audit models, test fixtures, and mock Modbus behavior.
- Transport interfaces and non-device-specific serial implementation code.
- Build, test, contribution, and safety-boundary documentation.

Order parsing and API model code may be public, but public fixtures and documents must use synthetic data and must not embed production order-interface payloads.

Fresh configuration defaults to loopback HTTP and `mock` Modbus. The mock path never opens a tty device. Existing installations keep stored settings; this default is not a mechanism for changing a live device.

## Protected or review-required material

Do not publish the following until a maintainer confirms ownership, disclosure permission, and safety impact:

- Real store/backend URLs, credentials, device codes, serial paths, or hardware configuration.
- Customer orders, production order-interface requests or responses, captured local order stores, photographs containing operational data, or captured logs.
- Vendor manuals, PLC register maps, ladder programs, and device-specific control sequences.
- Material that could help an unqualified user bypass an interlock, energize equipment, or operate an unverified machine.

If protected material was previously committed, removing the current file is not enough. Review Git history, rotate any exposed credentials, and obtain maintainer approval before rewriting or publishing history.

## Release gates

Before tagging or applying to an open-source program, a maintainer must verify:

1. The Apache-2.0 `LICENSE` is committed at the repository root.
2. The tracked tree and history have been reviewed for rights, secrets, customer data, and unsafe physical-control detail.
3. README, contribution, and reporting documentation matches actual behavior.
4. `:app:assembleDebug` and `:app:testDebugUnitTest` pass from a clean checkout.
5. Device testing, if any, is recorded separately from build and test evidence.

The local Codex-for-OSS application packet is a preparation aid, not proof that these gates are complete.
