# Contributing

Thanks for helping. Ground rules:
- Safety first: T-UI is a secondary display. Nothing may hide or weaken openpilot's own alerts, and no feature may need
  touch input while driving.
- Core (`core/`) stays plain Java 8, no Android imports, host-testable. Device-specific behaviour goes into an add-on
  (docs/ADDONS.md): head-unit hooks, extra data sources, second screens.
- Run before every pull request: `tools/hosttest.sh`, `TUI_NO_PRIVATE_MARKERS=1 tools/clean-export-check.sh`,
  `tools/secret-scan.sh`, and `tools/build.sh`.
- Test data is synthetic. Never commit real drives, routes, coordinates, screenshots with addresses, keys or tokens.
- New third-party code or assets: add them to THIRD_PARTY.md with their licence in the same pull request.
- Licence: contributions are accepted under the Apache License 2.0 (LICENSE).
