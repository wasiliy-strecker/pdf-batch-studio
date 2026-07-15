# Contributing

## Development setup

Use a full JDK 21 and the checked-in Maven Wrapper. Start the zero-setup local
profile with `./scripts/dev.sh` and run all checks with
`./scripts/test-all.sh`.

## Change expectations

1. Keep changes scoped and preserve the module dependency direction.
2. Add a focused test for every behavior change.
3. Never use real customer documents as fixtures.
4. Run `./mvnw verify` before opening a pull request.
5. Update user-facing docs and `CHANGELOG.md` when appropriate.

Commits should use short imperative subjects, for example `Validate duplicate
CSV headers`. Pull requests should explain the problem, approach, verification
commands, security impact, and screenshots for visible UI changes.

Do not submit code you do not have the right to license. Contributions require
explicit acceptance by the project owner before they become part of a future
commercially dual-licensed codebase.
