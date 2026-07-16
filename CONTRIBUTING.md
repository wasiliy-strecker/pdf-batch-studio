# Contributing

## Development setup

Use a full JDK 21 and the checked-in Maven Wrapper. Start the zero-setup local
profile with `./scripts/dev.sh`, start the offline JavaFX app with
`./scripts/desktop-dev.sh`, and run all checks with `./scripts/test-all.sh`.

## Change expectations

1. Keep changes scoped and preserve the module dependency direction.
2. Add a focused test for every behavior change.
3. Never use real customer documents as fixtures.
4. Run `./mvnw verify` before opening a pull request.
5. Update user-facing docs and `CHANGELOG.md` when appropriate.
6. For desktop UI changes, run the FXML smoke test and include a safe screenshot.

Commits should use short imperative subjects, for example `Validate duplicate
CSV headers`. Issues and reproducible bug reports are welcome.

The project is not currently accepting outside code contributions or pull
requests. This keeps copyright ownership clear for consistent source-available
and commercial licensing. A contributor agreement and explicit acceptance
process must be introduced before that policy changes.
