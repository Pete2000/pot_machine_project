# Pot Machine Project

Android control-application reference for food-preparation equipment. The project keeps the path from a backend order to a PLC command explicit and reviewable; it is **not** a certified machine controller or a ready-to-deploy production image.

## Status and safety boundary

This repository is being prepared for a public `v0.1` release and is licensed under [Apache-2.0](LICENSE). The remaining publication gates are documented in [the public release audit](docs/PUBLIC_RELEASE_AUDIT.md).

- Fresh installs use loopback HTTP endpoints and the in-process `mock` Modbus transport. They do not open a real serial device or contact a store backend.
- Existing installations retain their previously stored device settings. This repository change is not a migration or a safety mechanism for live devices.
- Real PLC integration, heating, emergency-stop logic, liquid-level sensing, and pump interlocks must be validated on the target equipment. The PLC is the final hardware-safety authority.
- Do not connect an unverified build to production equipment, and do not use this project as a substitute for an approved safety assessment.

See [the public release boundary](docs/PUBLIC_RELEASE_BOUNDARY.md) before adding a document, screenshot, endpoint, or device configuration to the repository.

## What is in the public core

- Kotlin and Jetpack Compose operator UI.
- Order parsing, recipe matching, queue projection, lifecycle handling, and Room-backed order/audit persistence, using synthetic or operator-entered data only.
- A `MachineCoordinator` and `PlcController` boundary between business rules and Modbus commands.
- Queued Modbus transport, a serial adapter, and a mock transport for local development and automated tests.

The public core deliberately does not claim that a particular register map, vendor manual, endpoint, recipe, or physical machine is safe to publish or operate.

## Architecture

```text
Compose UI / MainViewModel
          |
  MachineCoordinator ---- Order repository ---- HTTP adapter
          |
     PlcController
          |
Queued Modbus transport
     |             |
  mock (default)   serial adapter (device-specific integration)
```

## Build and verify (Windows PowerShell)

Prerequisites: Android SDK installed and JDK 17 available to Gradle.

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
```

The tests and a successful debug build provide source-level evidence only; they do not perform live PLC or safety validation.

## Contributing and disclosure

- Read [CONTRIBUTING.md](CONTRIBUTING.md) before proposing a change.
- Report security or safety-sensitive findings using [SECURITY.md](SECURITY.md); do not publish production order-interface payloads, customer order records, device credentials, live endpoints, register maps, or a procedure that could energize equipment.

## Before the first public release

1. Review every tracked document, image, and commit for publication authority, secrets, customer data, vendor material, and unsafe physical-control detail.
2. Complete build, unit-test, and review checks; record physical-device testing separately and only when it actually occurs.
