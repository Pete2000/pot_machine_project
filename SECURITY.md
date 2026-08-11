# Security and safety reporting

This project can sit near physical equipment. A defect that exposes a live endpoint, alters PLC commands, bypasses an interlock, or affects heating, emergency-stop, liquid-level, or pump behavior is both a security and a safety issue.

## Please do not disclose publicly

Do not put any of the following in a public issue, pull request, log, or screenshot:

- Credentials, tokens, store addresses, device identifiers, or serial paths.
- Production order-interface requests or responses, customer order records, captured local order stores, or other customer data.
- A live-machine proof of concept or steps that could change physical output.

Use GitHub's private security advisory flow when it is enabled for the repository. If it is unavailable, contact the maintainer privately through the contact method listed on the repository owner's GitHub profile and include only the minimum information needed to coordinate a safe disclosure.

## Scope and response expectations

Reports are especially useful for unintended network access, unsafe default configuration, command replay, missing authorization, information disclosure, and PLC-state/UI-state disagreement. Include a reproduction that uses mock or otherwise non-production data whenever possible.

No response-time, bounty, supported-version, or live-device validation promise is made by this document.
