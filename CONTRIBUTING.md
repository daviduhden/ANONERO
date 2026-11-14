# Contributing

All contributions are welcome, including those created or assisted by artificial intelligence (AI).

**The use of AI is explicitly permitted**, without any requirement to disclose its use.

All changes must be appropriately tested and verified before submission, regardless of whether they were produced manually or with AI assistance.

Contributors are responsible for ensuring that their changes work as intended and do not introduce regressions.

## Design expectations

ANONERO is an anonymity-focused Monero wallet. Privacy and security are required pillars.
When proposing a change:

- explain how it affects linkability, metadata exposure, and trust in remote services;
- do not add tracking, persistent identifiers, analytics, or network paths outside the
  selected transport;
- preserve fail-closed transport behavior and the LWS consent/trust rules;
- document residual anonymity, privacy, and security limits instead of claiming guarantees;
- add or update tests and documentation when behavior or guarantees change.

Read [`AGENTS.md`](AGENTS.md) and the relevant `.agents/skills/*/SKILL.md` before changing
key handling, transactions, transports, LWS, backups, or local-exposure features.

A static review or a passing test suite is not proof of anonymity or security.
