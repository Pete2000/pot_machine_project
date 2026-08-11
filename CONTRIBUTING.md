# Contributing to Pot Machine Project

Thank you for improving the public, simulation-first core of this project.

## Scope

Contributions should improve reusable business rules, UI, persistence, tests, or the transport abstraction. Do not submit real store endpoints, credentials, customer orders, captured production order-interface requests or responses, proprietary recipes, vendor manuals, unapproved register maps, or device-specific PLC settings.

Changes that affect command ordering, retries, heating, emergency behavior, pumps, liquid level, or other physical control need focused tests and a clear statement of whether validation was simulated, source-level only, or performed on authorized equipment.

## Development checks

Run the narrow checks that cover your change, then run the release baseline in Windows PowerShell before requesting review:

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
```

Do not describe a build or unit test as a physical-device acceptance test.

## Change expectations

1. Keep order parsing, recipe matching, phase planning, PLC I/O, and Compose presentation separated.
2. Reuse shared rules instead of copying logic between manual and order modes.
3. Preserve the fresh-install default of loopback HTTP plus `mock` Modbus. Device-specific integration must be opt-in and documented separately.
4. Add or update focused tests for behavior changes.
5. Update public documentation when changing a public contract or release gate.

## License

The project is licensed under [Apache-2.0](LICENSE). Submit only contributions that you have the right to contribute and that can be distributed under Apache-2.0. Unless explicitly agreed otherwise in writing, contributions submitted for inclusion are made under that license.
